import assert from 'node:assert/strict'
import test from 'node:test'
import { createConflictListState } from './conflictListState.js'
import { publicVillageCapabilities, villageDataMode } from './villageMode.js'
import { createVillageDetailsService } from './villageDetailsService.js'

const open = Object.freeze({ id: '9', entityId: '42', status: 'OPEN', fieldName: 'population', conflictingValues: [] })
const inReview = Object.freeze({ ...open, status: 'IN_REVIEW' })
const resolved = Object.freeze({ ...open, status: 'RESOLVED', resolutionNotes: 'notes' })
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function fixture(t, { list = async () => [open], create = async () => open, review = async () => inReview, resolve = async () => resolved,
  roles = { read: true, manage: true, create: true, review: true, resolve: true, reason: null }, operation = 'read' } = {}) {
  let current = true
  const calls = []
  const service = {
    isCurrent: () => current,
    permissions: () => current ? roles : { read: false, manage: false, create: false, review: false, resolve: false, reason: 'AUTH_REQUIRED' },
    listForVillage(id, options) { calls.push({ method: 'GET', id, ...options }); return list(id, options) },
    create(id, fieldName, values, options) { calls.push({ method: 'POST', id, fieldName, values, ...options }); return create(id, fieldName, values, options) },
    review(id, options) { calls.push({ method: 'REVIEW', id, ...options }); return review(id, options) },
    resolve(id, notes, options) { calls.push({ method: 'RESOLVE', id, notes, ...options }); return resolve(id, notes, options) },
  }
  const store = createConflictListState(service, { operation, load: signal => service.listForVillage('42', { signal }) })
  t.after(() => store.dispose())
  return { store, calls, endSession() { current = false } }
}
const settle = store => new Promise(resolve => {
  const check = () => { const s = store.getSnapshot(); if (s.mutationStatus !== 'pending' && s.listStatus !== 'loading') { unsubscribe(); resolve(s) } }
  const unsubscribe = store.subscribe(check)
})

test('lists begin without requests, then load frozen server-ordered items', async t => {
  const { store, calls } = fixture(t, { list: async () => [resolved, open] })
  assert.equal(store.getSnapshot(), store.getSnapshot())
  assert.equal(calls.length, 0)
  await store.load().done
  assert.deepEqual(store.getSnapshot().items, [resolved, open])
  assert.equal(Object.isFrozen(store.getSnapshot().items), true)
})

test('empty lists and read failures remain distinct', async t => {
  const empty = fixture(t, { list: async () => [] })
  await empty.store.load().done
  assert.equal(empty.store.getSnapshot().listStatus, 'ready')
  assert.deepEqual(empty.store.getSnapshot().items, [])
  const error = { status: 403 }
  const denied = fixture(t, { list: async () => { throw error } })
  await denied.store.load().done
  assert.equal(denied.store.getSnapshot().listStatus, 'error')
  assert.equal(denied.store.getSnapshot().items, null)
  assert.equal(denied.store.getSnapshot().listError, error)
})

test('management scopes send no request when status-list permission is missing', async t => {
  const { store, calls } = fixture(t, { operation: 'manage', roles: { read: true, manage: false, create: false, review: false, resolve: false, reason: 'CONFLICT_ROLES_UNAVAILABLE' } })
  assert.equal(store.load(), null)
  assert.equal(calls.length, 0)
})

for (const [mutation, run] of [['create', store => store.create('42', 'population', [{ a: '1' }, { a: '2' }])], ['review', store => store.review('9')]]) {
  for (const refreshError of [{ kind: 'network' }, { status: 403 }]) {
    test(`confirmed ${mutation} survives a subsequent refresh failure ${JSON.stringify(refreshError)}`, async t => {
      let count = 0
      const { store, calls } = fixture(t, { list: async () => { if (++count === 1) return [open]; throw refreshError } })
      await store.load().done
      const done = settle(store)
      await run(store).done
      const state = await done
      assert.equal(state.mutationStatus, 'success')
      assert.equal(state.mutation, mutation)
      assert.equal(state.mutationError, null)
      assert.equal(state.listStatus, 'error')
      assert.equal(state.listError, refreshError)
      assert.deepEqual(calls.map(call => call.method), ['GET', mutation === 'create' ? 'POST' : 'REVIEW', 'GET'])
    })
  }
}

test('resolve passes notes only for IN_REVIEW conflicts and refreshes once', async t => {
  let count = 0
  const { store, calls } = fixture(t, { list: async () => ++count === 1 ? [inReview] : [resolved] })
  await store.load().done
  const done = settle(store)
  await store.resolve('9', '  notes ').done
  const state = await done
  assert.equal(state.result, resolved)
  assert.deepEqual(state.items, [resolved])
  assert.deepEqual(calls.map(call => [call.method, call.notes]), [['GET', undefined], ['RESOLVE', '  notes '], ['GET', undefined]])
})

test('createdId changes only after a confirmed creation, not after review or resolve', async t => {
  let count = 0
  const { store } = fixture(t, { list: async () => ++count <= 2 ? [open] : [inReview], create: async () => ({ ...open, id: '12' }) })
  await store.load().done
  let done = settle(store)
  await store.create('42', 'population', [{ a: '1' }, { a: '2' }]).done
  assert.equal((await done).createdId, '12')
  done = settle(store)
  await store.review('9').done
  const state = await done
  assert.equal(state.mutation, 'review')
  assert.equal(state.createdId, '12')
})

test('only the action valid for the current status is sent; unknown targets are ignored', async t => {
  const { store, calls } = fixture(t, { list: async () => [open, { ...resolved, id: '10' }] })
  await store.load().done
  assert.equal(store.resolve('9', 'notes'), null) // OPEN cannot be resolved.
  assert.equal(store.review('10'), null) // RESOLVED cannot return to review.
  assert.equal(store.resolve('10', 'notes'), null)
  assert.equal(store.review('404'), null)
  assert.equal(calls.length, 1)
})

test('duplicate and concurrent conflicting mutations send a single request', async t => {
  const pending = deferred()
  const { store, calls } = fixture(t, { list: async () => [open, { ...open, id: '10' }], review: () => pending.promise })
  await store.load().done
  const first = store.review('9')
  assert.equal(store.review('9'), null)
  assert.equal(store.review('10'), null)
  assert.equal(store.create('42', 'population', []), null)
  assert.equal(store.getSnapshot().mutationStatus, 'pending')
  pending.resolve(inReview)
  await first.done
  assert.deepEqual(calls.map(call => call.method), ['GET', 'REVIEW', 'GET'])
})

test('a mutation error keeps the loaded list, is attributed to its target and is never retried', async t => {
  const error = { status: 409 }
  const { store, calls } = fixture(t, { review: async () => { throw error } })
  await store.load().done
  await store.review('9').done
  const state = store.getSnapshot()
  assert.equal(state.mutationStatus, 'error')
  assert.equal(state.mutation, 'review')
  assert.equal(state.targetId, '9')
  assert.equal(state.mutationError, error)
  assert.deepEqual(state.items, [open])
  assert.equal(calls.length, 2)
})

test('missing roles block mutations with no request and a role error', async t => {
  const { store, calls } = fixture(t, { roles: { read: true, manage: false, create: false, review: false, resolve: false, reason: 'CONFLICT_ROLES_UNAVAILABLE' } })
  await store.load().done
  assert.equal(store.create('42', 'population', []), null)
  assert.equal(store.getSnapshot().mutationError.code, 'CONFLICT_ROLES_UNAVAILABLE')
  assert.equal(store.review('9'), null)
  assert.equal(calls.length, 1)
})

for (const failure of [false, true]) {
  test(`superseded list ${failure ? 'failure' : 'success'} cannot overwrite a newer load`, async t => {
    const old = deferred()
    let count = 0
    const { store, calls } = fixture(t, { list: () => ++count === 1 ? old.promise : Promise.resolve([open]) })
    const first = store.load()
    await Promise.resolve()
    await store.load().done
    assert.equal(calls[0].signal.aborted, true)
    if (failure) old.reject({ status: 403 })
    else old.resolve([{ ...open, id: '99' }])
    await first.done
    assert.deepEqual(store.getSnapshot().items, [open])
    assert.equal(store.getSnapshot().listError, null)
  })
}

test('village navigation or status-filter change aborts reads and mutations and suppresses late results', async t => {
  const pendingRead = deferred(), pendingPatch = deferred()
  let count = 0
  const { store, calls } = fixture(t, { list: () => ++count === 1 ? Promise.resolve([open]) : pendingRead.promise, review: () => pendingPatch.promise })
  await store.load().done
  const write = store.review('9')
  await Promise.resolve()
  const read = store.load()
  await Promise.resolve()
  store.dispose()
  assert.ok(calls.slice(1).every(call => call.signal.aborted))
  assert.equal(store.getSnapshot().items, null)
  pendingRead.resolve([{ ...open, id: '88' }]); pendingPatch.resolve(inReview)
  await read.done; await write.done
  assert.equal(store.getSnapshot().result, null)
  assert.equal(calls.length, 3)
})

test('logout/session invalidation redacts snapshots and suppresses late callbacks', async t => {
  const pending = deferred()
  const { store, endSession, calls } = fixture(t, { create: () => pending.promise })
  await store.load().done
  const task = store.create('42', 'population', [{ a: '1' }, { a: '2' }])
  await Promise.resolve()
  endSession()
  assert.equal(store.getSnapshot().items, null)
  pending.resolve(open)
  await task.done
  assert.equal(store.load(), null)
  assert.equal(store.getSnapshot().mutationStatus, 'idle')
  assert.equal(store.getSnapshot().result, null)
  assert.equal(calls.length, 2)
})

test('cleanup cancels pre-dispatch work and supports a StrictMode remount', async t => {
  const { store, calls } = fixture(t)
  const task = store.load()
  store.dispose()
  await task.done
  assert.equal(calls.length, 0)
  await store.load().done
  assert.equal(store.getSnapshot().listStatus, 'ready')
})

test('disabled identity capability sends no HTTP and mock browsing makes no conflict requests', async () => {
  assert.deepEqual(publicVillageCapabilities, { browsing: true, identity: true, geographicCatalogues: false, richSections: false })
  assert.equal(villageDataMode, 'mock')
  const request = () => assert.fail('No public API or protected conflict requests')
  await assert.rejects(createVillageDetailsService({ mode: 'api', identityVerified: false, request }).getVillage('42'), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
  assert.equal((await createVillageDetailsService({ request }).getVillage('battir')).id, 'battir')
})
