import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createSourcesService } from './sourcesService.js'
import { createVillageSourcesState } from './villageSourcesState.js'
import { createAuthService } from './authService.js'
import { createAuthSession } from './authSession.js'
import { publicVillageCapabilities, villageDataMode } from './villageMode.js'

const dto = { id: 12, title: 'Synthetic source', publisher: null, author: null, sourceType: 'BOOK', url: null,
  publicationDate: null, accessedAt: null, citation: null, createdAt: '2026-11-27T12:00:00Z' }
const link = { villageId: 42, sourceId: 12, claimNote: '  Original note  ' }
const json = (data, status = 200) => new Response(JSON.stringify(data), { status })
function deferred() { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
function waitFor(store, predicate) {
  if (predicate(store.getSnapshot())) return Promise.resolve()
  return new Promise(resolve => {
    const unsubscribe = store.subscribe(() => { if (predicate(store.getSnapshot())) { unsubscribe(); resolve() } })
  })
}
function fixture(t, { roles = ['EDITOR'], verified = true, rolesStale = false, status = 'authenticated', handler, villageId = '42' } = {}) {
  let session = { status, user: { username: 'synthetic-profile', roles, rolesVerified: verified }, rolesStale, expiresAt: Date.now() + 3600000 }
  const calls = []
  let stored = []
  const client = createApiClient({ fetchImpl: (url, init) => {
    calls.push({ url, init })
    if (handler) return handler(url, init)
    if (url === '/api/v1/sources') return json([dto])
    if (init.method === 'POST') { stored = [{ ...link, villageId: Number(villageId), ...JSON.parse(init.body) }]; return json(stored[0], 201) }
    return json(stored)
  } })
  const service = createSourcesService({ request: (path, options) => client(path, { ...options, token: 'synthetic-token' }), getSession: () => session })
  const store = createVillageSourcesState(service, villageId)
  t.after(() => store.dispose())
  return { store, service, calls, getSession: () => session, replaceSession(value) { session = value } }
}

test('loading binds the real village identity and exact protected GETs without search or pagination', async t => {
  const { store, calls } = fixture(t)
  assert.equal(calls.length, 0)
  assert.equal(store.getSnapshot(), store.getSnapshot())
  const loading = store.load()
  assert.equal(store.getSnapshot().linksStatus, 'loading')
  await loading.done
  assert.equal(store.getSnapshot().linksStatus, 'ready')
  assert.deepEqual(store.getSnapshot().links, [])
  assert.equal(store.getSnapshot().catalog[0].id, '12')
  assert.ok(Object.isFrozen(store.getSnapshot().links))
  assert.deepEqual(calls.map(call => call.url), ['/api/v1/villages/42/sources', '/api/v1/sources'])
  for (const { init } of calls) {
    assert.equal(init.method, 'GET')
    assert.equal(init.headers.get('Authorization'), 'Bearer synthetic-token')
    assert.equal(init.credentials, 'omit')
  }
})

for (const villageId of ['battir', '042', '', '0', '-1', '1e2', '42/other', '9007199254740992', 42, null]) {
  test(`invalid village identity ${JSON.stringify(villageId)} cannot construct a linking store`, () => {
    assert.throws(() => createVillageSourcesState({}, villageId))
  })
}

for (const roles of [['VIEWER'], ['EDITOR'], ['ADMIN'], ['VIEWER', 'ADMIN']]) {
  test(`linking panel obeys ${roles.join('/')} with no implicit extra roles`, async t => {
    const { store, calls } = fixture(t, { roles })
    await store.load().done
    const before = calls.length
    if (roles.includes('EDITOR') || roles.includes('ADMIN')) {
      const ready = waitFor(store, state => state.mutationStatus === 'success' && state.linksStatus === 'ready')
      await store.submit('12', null).done; await ready
      assert.equal(calls.length, before + 2)
      assert.equal(store.getSnapshot().links[0].sourceId, '12')
    } else {
      assert.equal(store.submit('12', null), null)
      assert.equal(store.getSnapshot().mutationError.code, 'SOURCE_FORBIDDEN')
      assert.equal(calls.length, before)
    }
  })
}

for (const options of [{ status: 'anonymous' }, { roles: [] }, { roles: null, verified: false },
  { roles: ['ADMIN', 'UNKNOWN'] }, { roles: ['ADMIN'], rolesStale: true }]) {
  test(`insufficient or stale session ${JSON.stringify(options)} initiates zero source requests`, async t => {
    const { store, calls } = fixture(t, options)
    await store.load().done
    assert.equal(store.submit('12', null), null)
    assert.equal(calls.length, 0)
    assert.equal(store.getSnapshot().links, null)
  })
}

for (const sourceId of ['battir', '012', '0', '-1', '12/other', '9007199254740992', 12, null, '99']) {
  test(`selection ${JSON.stringify(sourceId)} outside the loaded safe source catalogue sends no POST`, async t => {
    const { store, calls } = fixture(t)
    await store.load().done
    assert.equal(store.submit(sourceId, null), null)
    assert.equal(store.getSnapshot().mutationError.code, 'SOURCE_VALIDATION')
    assert.equal(calls.length, 2)
  })
}

for (const claimNote of [null, '', '  Exact note  ', 'x'.repeat(10000)]) {
  test(`optional note ${claimNote === null ? 'null' : claimNote.length} is preserved without an invented limit`, async t => {
    const { store, calls } = fixture(t)
    await store.load().done
    const refreshed = waitFor(store, state => state.mutationStatus === 'success' && state.linksStatus === 'ready')
    await store.submit('12', claimNote).done; await refreshed
    const post = calls.find(call => call.init.method === 'POST')
    assert.equal(post.url, '/api/v1/villages/42/sources')
    assert.deepEqual(JSON.parse(post.init.body), { sourceId: 12, claimNote })
    assert.equal(store.getSnapshot().created.claimNote, claimNote)
    assert.equal(store.getSnapshot().links[0].claimNote, claimNote)
    assert.equal(calls.filter(call => call.init.method === 'POST').length, 1)
  })
}

test('invalid notes are rejected before POST', async t => {
  const { store, calls } = fixture(t)
  await store.load().done
  for (const note of [undefined, {}, 1]) assert.equal(store.submit('12', note), null)
  assert.equal(calls.length, 2)
})

for (const failure of ['network', 403, 404, 500]) {
  test(`confirmed POST success survives link-list refresh failure ${failure}; retry is GET-only`, async t => {
    let gets = 0
    const { store, calls } = fixture(t, { handler: (url, init) => {
      if (url === '/api/v1/sources') return json([dto])
      if (init.method === 'POST') return json(link, 201)
      if (++gets === 1) return json([])
      if (failure === 'network') throw new TypeError('Synthetic offline')
      return json({ error: 'Synthetic refresh failure' }, failure)
    } })
    await store.load().done
    const failedRefresh = waitFor(store, state => state.linksStatus === 'error')
    await store.submit('12', '  Original note  ').done; await failedRefresh
    assert.equal(store.getSnapshot().mutationStatus, 'success')
    assert.equal(store.getSnapshot().mutationError, null)
    assert.equal(store.getSnapshot().created.sourceId, '12')
    assert.ok(store.getSnapshot().linksError)
    await store.loadLinks().done
    assert.equal(store.getSnapshot().mutationStatus, 'success')
    assert.equal(calls.filter(call => call.init.method === 'POST').length, 1)
    assert.equal(calls.filter(call => call.url === '/api/v1/sources').length, 1)
  })
}

test('a server duplicate 409 preserves associations and does not overwrite or automatically refresh/retry', async t => {
  const existing = { ...link, sourceId: 13, claimNote: 'Existing note' }
  const { store, calls } = fixture(t, { handler: (url, init) => init.method === 'POST'
    ? json({ error: 'Source is already linked to this village' }, 409)
    : json(url === '/api/v1/sources' ? [dto, { ...dto, id: 13 }] : [existing]) })
  await store.load().done
  await store.submit('12', 'New draft note').done
  assert.equal(store.getSnapshot().mutationStatus, 'error')
  assert.equal(store.getSnapshot().mutationError.status, 409)
  assert.equal(store.getSnapshot().links[0].claimNote, 'Existing note')
  assert.equal(store.getSnapshot().created, null)
  assert.equal(calls.length, 3)
  assert.deepEqual(calls.map(call => call.init.method), ['GET', 'GET', 'POST'])
})

test('an association already in the loaded list is not offered as a new link or sent again', async t => {
  const { store, calls } = fixture(t, { handler: url => json(url === '/api/v1/sources' ? [dto] : [link]) })
  await store.load().done
  assert.equal(store.submit('12', 'Replacement note'), null)
  assert.equal(store.getSnapshot().mutationError.code, 'SOURCE_ALREADY_LINKED')
  assert.equal(store.getSnapshot().links[0].claimNote, '  Original note  ')
  assert.equal(calls.length, 2)
})

test('duplicate submissions share a synchronous latch and produce one mutation', async t => {
  const pending = deferred(), started = deferred()
  const { store, calls } = fixture(t, { handler: (url, init) => {
    if (init.method === 'POST') { started.resolve(); return pending.promise }
    return json(url === '/api/v1/sources' ? [dto] : [])
  } })
  await store.load().done
  const first = store.submit('12', null)
  assert.equal(store.submit('12', null), null)
  await started.promise
  assert.equal(store.getSnapshot().mutationStatus, 'pending')
  assert.equal(calls.filter(call => call.init.method === 'POST').length, 1)
  pending.resolve(json({ ...link, claimNote: null }, 201))
  await first.done
})

for (const target of ['links', 'catalog']) {
  test(`${target} load failure remains an error and blocks submission until a successful read`, async t => {
    const { store, calls } = fixture(t, { handler: url => {
      const catalog = url === '/api/v1/sources'
      return (target === 'catalog') === catalog ? json({ error: 'Synthetic denied read' }, 403) : json(catalog ? [dto] : [])
    } })
    await store.load().done
    assert.equal(store.getSnapshot()[`${target}Status`], 'error')
    assert.equal(store.submit('12', null), null)
    assert.equal(calls.length, 2)
  })
}

test('village navigation cancels both reads and ignores late records from the previous identity', async t => {
  const pending = deferred(), started = deferred()
  const old = fixture(t, { handler: () => { started.resolve(); return pending.promise } })
  const loading = old.store.load()
  await started.promise
  old.store.dispose()
  assert.ok(old.calls.every(call => call.init.signal.aborted))
  const next = fixture(t, { villageId: '43' })
  await next.store.load().done
  pending.resolve(json([link]))
  await loading.done
  assert.equal(old.store.getSnapshot().links, null)
  assert.deepEqual(next.store.getSnapshot().links, [])
  assert.equal(next.calls[0].url, '/api/v1/villages/43/sources')
})

for (const mode of ['logout', 'profile', 'stale', 'dispose']) {
  test(`${mode} redacts source data, suppresses late POST success and prevents further linking`, async t => {
    const pending = deferred(), started = deferred()
    const state = fixture(t, { handler: (url, init) => {
      if (init.method === 'POST') { started.resolve(); return pending.promise }
      return json(url === '/api/v1/sources' ? [dto] : [])
    } })
    await state.store.load().done
    const task = state.store.submit('12', null)
    await started.promise
    const old = state.getSession()
    if (mode === 'logout') state.replaceSession({ status: 'anonymous' })
    if (mode === 'profile') state.replaceSession({ ...old, user: { ...old.user, roles: ['VIEWER'] } })
    if (mode === 'stale') state.replaceSession({ ...old, rolesStale: true })
    if (mode === 'dispose') state.store.dispose()
    assert.equal(state.store.getSnapshot().catalog, null)
    pending.resolve(json(link, 201))
    await task.done
    assert.equal(state.store.getSnapshot().created, null)
    const before = state.calls.length
    assert.equal(state.store.submit('12', null), null)
    assert.equal(state.calls.length, before)
    if (mode === 'dispose') assert.equal(state.calls.find(call => call.init.method === 'POST').init.signal.aborted, true)
  })
}

test('failed effective-role refresh redacts the linking store and initiates no protected source reads', async t => {
  let fail = false
  const calls = []
  const client = createApiClient({ fetchImpl: (url, init) => {
    calls.push({ url, init })
    if (url.endsWith('/login')) return json({ accessToken: 'synthetic-token', tokenType: 'Bearer', expiresIn: 900 })
    if (url.endsWith('/me')) return fail ? json({ error: 'Synthetic failure' }, 503) : json({ username: 'profile', roles: ['ADMIN'] })
    return json(url === '/api/v1/sources' ? [dto] : [])
  } })
  const auth = createAuthSession({ service: createAuthService({ request: client }) })
  t.after(() => auth.dispose())
  await auth.login({ username: 'synthetic', password: 'synthetic' })
  const service = createSourcesService({ request: auth.request, getSession: auth.getSnapshot })
  const store = createVillageSourcesState(service, '42')
  t.after(() => store.dispose())
  await store.load().done
  fail = true
  await assert.rejects(auth.refreshProfile(), { status: 503 })
  assert.equal(auth.getSnapshot().rolesStale, true)
  assert.equal(store.getSnapshot().links, null)
  const before = calls.length
  await store.load().done
  assert.equal(store.submit('12', null), null)
  assert.equal(calls.length, before)
})

test('linking does not approve public catalogues or rich sections and mock remains default', () => {
  assert.deepEqual(publicVillageCapabilities, { browsing: true, identity: true, geographicCatalogues: false, richSections: false })
  assert.equal(villageDataMode, 'mock')
})
