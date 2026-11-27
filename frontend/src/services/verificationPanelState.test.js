import assert from 'node:assert/strict'
import test from 'node:test'
import { createVerificationPanelState } from './verificationPanelState.js'
import { createVerificationService } from './verificationService.js'
import { createApiClient } from './apiClient.js'
import { publicVillageContractsVerified, villageDataMode } from './villageMode.js'
import { createVillageDetailsService } from './villageDetailsService.js'

const record = Object.freeze({ id: '7', entityId: '42', decision: 'VERIFIED', reason: null, reviewerUserId: 12, reviewedAt: '2026-11-27T10:00:00Z' })
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function fixture(t, { list = async () => [record], create = async () => record, admin = true } = {}) {
  let current = true
  const calls = []
  const service = {
    isCurrent: () => current,
    permissions: () => ({ read: current, create: current && admin, reason: admin ? null : 'VERIFICATION_ROLES_UNAVAILABLE' }),
    list(id, options) { calls.push({ method: 'GET', id, ...options }); return list(id, options) },
    create(id, decision, reason, options) { calls.push({ method: 'POST', id, decision, reason, ...options }); return create(id, decision, reason, options) },
  }
  const store = createVerificationPanelState(service, '42')
  t.after(() => store.dispose())
  return { store, service, calls, endSession() { current = false } }
}

test('panel begins without a request and exposes stable snapshots until explicit authenticated load', async t => {
  const { store, calls } = fixture(t)
  assert.equal(store.getSnapshot(), store.getSnapshot())
  assert.equal(calls.length, 0)
  await store.load().done
  assert.equal(store.getSnapshot().historyStatus, 'ready')
  assert.deepEqual(store.getSnapshot().records, [record])
  assert.equal(Object.isFrozen(store.getSnapshot().records), true)
  assert.equal(calls[0].id, '42')
})

test('empty history and read failures remain distinct', async t => {
  const empty = fixture(t, { list: async () => [] })
  await empty.store.load().done
  assert.equal(empty.store.getSnapshot().historyStatus, 'ready')
  assert.deepEqual(empty.store.getSnapshot().records, [])
  const error = { status: 403 }
  const denied = fixture(t, { list: async () => { throw error } })
  await denied.store.load().done
  assert.equal(denied.store.getSnapshot().historyStatus, 'error')
  assert.equal(denied.store.getSnapshot().records, null)
  assert.equal(denied.store.getSnapshot().historyError, error)
})

for (const refreshError of [{ kind: 'network' }, { status: 403 }, { status: 404 }]) {
  test(`confirmed creation survives a subsequent history refresh failure ${JSON.stringify(refreshError)}`, async t => {
    let count = 0
    const refreshed = deferred()
    const { store, calls } = fixture(t, { list: async () => {
      if (++count === 1) return []
      refreshed.resolve()
      throw refreshError
    } })
    await store.load().done
    let finished
    const complete = new Promise(resolve => { finished = resolve })
    const unsubscribe = store.subscribe(() => { if (store.getSnapshot().historyStatus === 'error') finished() })
    await store.submit('VERIFIED', null).done
    await refreshed.promise; await complete; unsubscribe()
    const state = store.getSnapshot()
    assert.equal(state.mutationStatus, 'success')
    assert.equal(state.created, record)
    assert.equal(state.mutationError, null)
    assert.equal(state.historyStatus, 'error')
    assert.equal(state.historyError, refreshError)
    assert.deepEqual(calls.map(call => call.method), ['GET', 'POST', 'GET'])
    await store.load().done // Explicit history retry issues another GET, never another POST.
    assert.equal(calls.filter(call => call.method === 'POST').length, 1)
    assert.equal(store.getSnapshot().mutationStatus, 'success')
  })
}

test('successful refresh displays server order and does not mutate village identity/status', async t => {
  const village = Object.freeze({ id: '42', backendId: 42, status: 'DRAFT' })
  const dto = { id: 7, entityType: 'VILLAGE', entityId: 42, reviewerUserId: 12, decision: 'VERIFIED', reason: null, reviewedAt: '2026-11-27T10:00:00Z' }
  const calls = []
  let gets = 0
  const client = createApiClient({ fetchImpl: (url, init) => {
    calls.push({ url, init })
    return new Response(JSON.stringify(init.method === 'POST' ? dto : ++gets === 1 ? [] : [{ ...dto, id: 9 }, dto]), { status: init.method === 'POST' ? 201 : 200 })
  } })
  const session = { status: 'authenticated', user: { username: 'isolated-admin-fixture', roles: ['ADMIN'], rolesVerified: true }, expiresAt: Date.now() + 3600000 }
  const service = createVerificationService({ request: (path, options) => client(path, { ...options, token: 'fixture-token' }), getSession: () => session, rolesContractVerified: true })
  const store = createVerificationPanelState(service, village.id)
  t.after(() => store.dispose())
  await store.load().done
  const refreshed = new Promise(resolve => {
    const unsubscribe = store.subscribe(() => { if (store.getSnapshot().mutationStatus === 'success' && store.getSnapshot().historyStatus === 'ready') { unsubscribe(); resolve() } })
  })
  await store.submit('VERIFIED', null).done; await refreshed
  assert.deepEqual(store.getSnapshot().records.map(row => row.id), ['9', '7'])
  assert.equal(village.status, 'DRAFT')
  assert.equal(calls.length, 3)
  assert.ok(calls.every(call => call.url.startsWith('/api/v1/verifications')))
})

test('duplicate submissions perform a single POST; successful creation triggers only one history refresh', async t => {
  const pending = deferred()
  const { store, calls } = fixture(t, { create: () => pending.promise })
  await store.load().done
  const first = store.submit('VERIFIED', null)
  assert.equal(store.submit('REJECTED', 'second click'), null)
  await Promise.resolve()
  assert.equal(calls.filter(call => call.method === 'POST').length, 1)
  assert.equal(store.getSnapshot().mutationStatus, 'pending')
  pending.resolve(record)
  await first.done
  assert.equal(calls.filter(call => call.method === 'POST').length, 1)
  assert.equal(calls.filter(call => call.method === 'GET').length, 2)
})

test('a mutation error never refreshes history, erases the loaded history, or retries itself', async t => {
  const error = { status: 400 }
  const { store, calls } = fixture(t, { create: async () => { throw error } })
  await store.load().done
  await store.submit('VERIFIED', null).done
  assert.equal(store.getSnapshot().mutationError, error)
  assert.equal(store.getSnapshot().mutationStatus, 'error')
  assert.deepEqual(store.getSnapshot().records, [record])
  assert.deepEqual(calls.map(call => call.method), ['GET', 'POST'])
})

test('missing roles allow history but block submissions with no POST', async t => {
  const { store, calls } = fixture(t, { admin: false })
  await store.load().done
  assert.equal(store.submit('VERIFIED', null), null)
  assert.equal(store.getSnapshot().mutationError.code, 'VERIFICATION_ROLES_UNAVAILABLE')
  assert.equal(store.getSnapshot().historyStatus, 'ready')
  assert.equal(calls.length, 1)
})

for (const failure of [false, true]) {
  test(`superseded history ${failure ? 'failure' : 'success'} cannot overwrite a newer load`, async t => {
    const old = deferred()
    let count = 0
    const { store, calls } = fixture(t, { list: () => ++count === 1 ? old.promise : Promise.resolve([record]) })
    const first = store.load()
    await Promise.resolve()
    await store.load().done
    assert.equal(calls[0].signal.aborted, true)
    if (failure) old.reject({ status: 403 })
    else old.resolve([{ ...record, id: '99' }])
    await first.done
    assert.deepEqual(store.getSnapshot().records, [record])
    assert.equal(store.getSnapshot().historyError, null)
  })
}

test('village navigation/unmount aborts history and mutation, clears protected data and suppresses a late refresh', async t => {
  const pendingRead = deferred(), pendingPost = deferred()
  let count = 0
  const { store, calls } = fixture(t, { list: () => ++count === 1 ? Promise.resolve([record]) : pendingRead.promise, create: () => pendingPost.promise })
  await store.load().done
  const read = store.load(), write = store.submit('VERIFIED', null)
  await Promise.resolve()
  store.dispose()
  assert.equal(calls[1].signal.aborted, true)
  assert.equal(calls[2].signal.aborted, true)
  assert.equal(store.getSnapshot().records, null)
  pendingRead.resolve([{ ...record, id: '88' }]); pendingPost.resolve(record)
  await read.done; await write.done
  assert.equal(store.getSnapshot().created, null)
  assert.equal(calls.length, 3)
})

test('logout/session invalidation immediately redacts snapshots and suppresses late history/mutation callbacks', async t => {
  const pending = deferred()
  const { store, endSession, calls } = fixture(t, { create: () => pending.promise })
  await store.load().done
  const task = store.submit('VERIFIED', null)
  await Promise.resolve()
  endSession()
  assert.equal(store.getSnapshot().records, null)
  assert.equal(store.getSnapshot().created, null)
  pending.resolve(record)
  await task.done
  assert.equal(store.load(), null)
  assert.equal(store.getSnapshot().mutationStatus, 'idle')
  assert.equal(calls.length, 2)
})

test('cleanup cancels pre-dispatch work, detaches listeners and supports a StrictMode remount', async t => {
  const { store, calls } = fixture(t)
  let changes = 0
  const unsubscribe = store.subscribe(() => changes++)
  const task = store.load()
  store.dispose()
  await task.done
  assert.equal(calls.length, 0)
  assert.equal(store.getSnapshot().records, null)
  unsubscribe()
  const before = changes
  await store.load().done
  assert.equal(changes, before)
  assert.equal(store.getSnapshot().historyStatus, 'ready')
})

test('public API Details remains closed and default mock identity browsing makes no verification requests', async () => {
  assert.equal(publicVillageContractsVerified, false)
  assert.equal(villageDataMode, 'mock')
  const request = () => assert.fail('No public API or protected verification requests')
  const api = createVillageDetailsService({ mode: 'api', request })
  await assert.rejects(api.getVillage('42'), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
  const mock = createVillageDetailsService({ request })
  assert.equal((await mock.getVillage('battir')).id, 'battir')
})
