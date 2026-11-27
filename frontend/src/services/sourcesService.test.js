import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createSourcesService } from './sourcesService.js'
import { adaptSource } from './sourceContracts.js'
import { createAuthService } from './authService.js'
import { createAuthSession } from './authSession.js'
import { createLatestExplorerRequest } from './explorerRequest.js'
import { createSourceMutation } from './sourceMutation.js'

const body = { title: 'مرجع', publisher: 'ناشر', author: null, sourceType: 'ARCHIVE', url: 'https://example.test/ref', publicationDate: '1948-05-15', accessedAt: null, citation: 'اقتباس' }
const dto = { id: 12, ...body, createdAt: '2026-11-27T12:00:00Z' }
const link = { villageId: 42, sourceId: 12, claimNote: 'ملاحظة' }
const response = (data, status = 200) => new Response(JSON.stringify(data), { status })
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }

function fixture(t, handler = () => response(dto), roles = ['EDITOR'], verified = true) {
  const calls = []
  let session = { status: 'authenticated', user: { username: 'isolated-role-fixture', roles, rolesVerified: true }, expiresAt: Date.now() + 3600000 }
  const client = createApiClient({ fetchImpl: (url, init) => { calls.push({ url, init }); return handler(url, init) } })
  const request = (path, options) => client(path, { ...options, token: 'fixture-token' })
  const service = createSourcesService({ request, getSession: () => session, rolesContractVerified: verified })
  t.after(() => { session = { status: 'anonymous' } })
  return { service, calls, replaceSession(value) { session = value }, getSession: () => session }
}

test('GET collection, identity and both link collections use exact unpaginated endpoints and Bearer credentials', async t => {
  const { service, calls } = fixture(t, url => response(url === '/api/v1/sources' ? [dto]
    : url === '/api/v1/sources/12' ? dto : [link]))
  assert.equal((await service.list())[0].id, '12')
  assert.equal((await service.get('12')).title, body.title)
  assert.equal((await service.villageSources('42'))[0].sourceId, '12')
  assert.equal((await service.sourceVillages('12'))[0].villageId, '42')
  assert.deepEqual(calls.map(call => call.url), ['/api/v1/sources', '/api/v1/sources/12', '/api/v1/villages/42/sources', '/api/v1/sources/12/villages'])
  for (const { init } of calls) {
    assert.equal(init.method, 'GET'); assert.equal(init.body, undefined)
    assert.equal(init.headers.get('Authorization'), 'Bearer fixture-token')
    assert.equal(init.credentials, 'omit')
  }
})

test('create uses only the exact CreateSourceRequest body and consumes the returned server ID', async t => {
  const { service, calls } = fixture(t, () => response(dto, 201))
  assert.equal((await service.create({ ...body, id: 999 })).id, '12')
  assert.equal(calls[0].url, '/api/v1/sources')
  assert.equal(calls[0].init.method, 'POST')
  assert.deepEqual(JSON.parse(calls[0].init.body), body)
  assert.equal(calls.length, 1)
})

test('update sends complete replacement metadata and ignores neither explicit null nor untouched optional fields', async t => {
  const { service, calls } = fixture(t, () => response({ ...dto, title: 'جديد', author: 'كاتب' }))
  const original = adaptSource(dto)
  await service.update(original, { title: 'جديد', author: 'كاتب' })
  assert.equal(calls[0].url, '/api/v1/sources/12')
  assert.equal(calls[0].init.method, 'PUT')
  assert.deepEqual(JSON.parse(calls[0].init.body), { ...body, title: 'جديد', author: 'كاتب' })
  const clear = fixture(t, () => response({ ...dto, url: null }))
  await clear.service.update(original, { url: null })
  assert.equal(JSON.parse(clear.calls[0].init.body).url, null)
  assert.equal(original.url, body.url)
})

test('link uses numeric sourceId and exact nullable claimNote without creating/replacing a source', async t => {
  const { service, calls } = fixture(t, () => response({ ...link, claimNote: null }, 201))
  assert.deepEqual(await service.link('42', '12', null), { villageId: '42', sourceId: '12', claimNote: null })
  assert.equal(calls[0].url, '/api/v1/villages/42/sources')
  assert.equal(calls[0].init.method, 'POST')
  assert.deepEqual(JSON.parse(calls[0].init.body), { sourceId: 12, claimNote: null })
})

test('duplicate link 409 stays a conflict with no overwrite, follow-up PUT, or automatic retry', async t => {
  const { service, calls } = fixture(t, () => response({ error: 'Source is already linked to this village' }, 409))
  await assert.rejects(service.link('42', '12', 'replacement attempt'), { status: 409, details: { error: 'Source is already linked to this village' } })
  assert.equal(calls.length, 1)
  assert.equal(calls[0].init.method, 'POST')
})

for (const id of ['battir', '012', '', '0', '-1', '1.1', '1e2', '12/other', '9007199254740992', 12, null]) {
  test(`noncanonical/unsafe backend ID ${JSON.stringify(id)} never reaches HTTP`, async t => {
    const { service, calls } = fixture(t)
    await assert.rejects(service.get(id))
    await assert.rejects(service.villageSources(id))
    await assert.rejects(service.sourceVillages(id))
    await assert.rejects(service.link(id, '12', null))
    await assert.rejects(service.link('42', id, null))
    assert.equal(calls.length, 0)
  })
}

test('malformed create/update/link bodies are rejected before HTTP', async t => {
  const { service, calls } = fixture(t)
  await assert.rejects(service.create({ ...body, title: '' }), { code: 'SOURCE_VALIDATION' })
  await assert.rejects(service.update(adaptSource(dto), { citation: undefined }), { code: 'SOURCE_VALIDATION' })
  await assert.rejects(service.link('42', '12', undefined), { code: 'SOURCE_VALIDATION' })
  assert.equal(calls.length, 0)
})

test('all seven operations fail closed without a verified effective-role contract, including authenticated reads', async t => {
  const { service, calls } = fixture(t, undefined, ['ADMIN'], false)
  for (const run of [() => service.list(), () => service.get('12'), () => service.create(body), () => service.update(adaptSource(dto), {}),
    () => service.villageSources('42'), () => service.link('42', '12', null), () => service.sourceVillages('12')]) {
    await assert.rejects(run(), { code: 'SOURCE_ROLES_UNAVAILABLE' })
  }
  assert.equal(calls.length, 0)
})

test('isolated VIEWER contract fixture allows reads but denies every mutation before HTTP', async t => {
  const { service, calls } = fixture(t, () => response([dto]), ['VIEWER'])
  await service.list()
  await assert.rejects(service.create(body), { code: 'SOURCE_FORBIDDEN' })
  await assert.rejects(service.update(adaptSource(dto), {}), { code: 'SOURCE_FORBIDDEN' })
  await assert.rejects(service.link('42', '12', null), { code: 'SOURCE_FORBIDDEN' })
  assert.equal(calls.length, 1)
})

for (const status of [400, 401, 403, 404, 409]) {
  test(`HTTP ${status} semantics survive reads, create, update and linking without retries`, async t => {
    const { service, calls } = fixture(t, () => response({ error: 'backend semantics' }, status))
    for (const run of [() => service.list(), () => service.create(body), () => service.update(adaptSource(dto), {}), () => service.link('42', '12', null)]) {
      await assert.rejects(run(), { status, kind: 'http', details: { error: 'backend semantics' } })
    }
    assert.equal(calls.length, 4)
  })
}

test('network failures retain their cause and never automatically retry mutations', async t => {
  const cause = new TypeError('offline')
  const { service, calls } = fixture(t, () => { throw cause })
  await assert.rejects(service.create(body), { kind: 'network', cause })
  await assert.rejects(service.update(adaptSource(dto), {}), { kind: 'network', cause })
  await assert.rejects(service.link('42', '12', 'note'), { kind: 'network', cause })
  assert.equal(calls.length, 3)
})

test('malformed/wrong-identity responses fail instead of becoming empty lists or successful edits', async t => {
  for (const payload of [null, {}, { content: [dto] }, [dto, dto]]) {
    const { service } = fixture(t, () => response(payload))
    await assert.rejects(service.list())
  }
  const { service } = fixture(t, () => response({ ...dto, id: 13 }))
  await assert.rejects(service.get('12'))
  await assert.rejects(service.update(adaptSource(dto), {}))
  const wrong = fixture(t, () => response({ ...link, sourceId: 13 }, 201))
  await assert.rejects(wrong.service.link('42', '12', null))
  const wrongList = fixture(t, () => response([{ ...link, villageId: 43 }]))
  await assert.rejects(wrongList.service.villageSources('42'))
})

test('empty catalogs and link lists are valid unpaginated empty datasets', async t => {
  const { service } = fixture(t, () => response([]))
  assert.deepEqual(await service.list(), [])
  assert.deepEqual(await service.villageSources('42'), [])
  assert.deepEqual(await service.sourceVillages('12'), [])
})

test('cancellation before and during HTTP cannot deliver a late response', async t => {
  const pending = deferred(), controller = new AbortController()
  const { service, calls } = fixture(t, () => pending.promise)
  controller.abort()
  await assert.rejects(service.list({ signal: controller.signal }))
  assert.equal(calls.length, 0)
  const active = new AbortController()
  const request = service.get('12', { signal: active.signal })
  assert.equal(calls[0].init.signal, active.signal)
  active.abort()
  pending.resolve(response(dto))
  await assert.rejects(request)
})

test('logout/session replacement rejects obsolete services and late reads/mutations even when HTTP ignores abort', async t => {
  for (const mutation of [false, true]) {
    const pending = deferred()
    const { service, calls, replaceSession, getSession } = fixture(t, () => pending.promise)
    const request = mutation ? service.create(body) : service.list()
    const original = getSession()
    replaceSession({ ...original, user: { ...original.user } }) // Even same username/expiry is a new verified session/profile.
    pending.resolve(response(mutation ? dto : [dto]))
    await assert.rejects(request, { code: 'AUTH_REQUIRED' })
    await assert.rejects(service.list(), { code: 'AUTH_REQUIRED' })
    assert.equal(calls.length, 1)
  }
})

for (const fails of [false, true]) test(`existing latest-response controller ignores stale Sources ${fails ? 'failure' : 'success'}`, async () => {
  const runner = createLatestExplorerRequest(), pending = deferred(), values = []
  const old = runner.run(() => pending.promise, { onSuccess: value => values.push(value), onError: error => values.push(error) })
  await Promise.resolve()
  const current = runner.run(() => [dto], { onSuccess: value => values.push(value), onError: error => values.push(error) })
  await current.done
  if (fails) pending.reject(new Error('stale failure'))
  else pending.resolve(['stale'])
  await old.done
  assert.deepEqual(values, [[dto]])
})

for (const cancellation of ['unmount', 'session-change']) test(`mutation ${cancellation} suppresses late failures as well as success`, async () => {
  const pending = deferred(), values = []
  let current = true
  const runner = createSourceMutation(() => current)
  const task = runner.run(() => pending.promise, { onSuccess: value => values.push(value), onError: error => values.push(error), onSettled: () => values.push('settled') })
  await Promise.resolve()
  if (cancellation === 'unmount') runner.cancel()
  else current = false
  pending.reject({ status: 409 })
  await task.done
  assert.deepEqual(values, [])
})

test('mutation controller prevents duplicates, never retries and suppresses callbacks after unmount/logout', async () => {
  const pending = deferred(), changes = []
  let requests = 0, live = true
  const runner = createSourceMutation(() => live)
  const task = runner.run(() => { requests++; return pending.promise }, { onSuccess: value => changes.push(value), onError: error => changes.push(error), onSettled: () => changes.push('settled') })
  assert.throws(() => runner.run(() => {}), { code: 'MUTATION_IN_PROGRESS' })
  await Promise.resolve()
  assert.equal(requests, 1)
  live = false; runner.cancel()
  pending.resolve(dto)
  await task.done
  assert.deepEqual(changes, [])
  assert.equal(requests, 1)
})

test('mutation controller preserves conflicts and releases its submission latch without retry', async () => {
  const conflict = { status: 409 }, errors = []
  const runner = createSourceMutation(() => true)
  let count = 0
  await runner.run(() => { count++; throw conflict }, { onError: error => errors.push(error) }).done
  assert.deepEqual(errors, [conflict])
  assert.equal(count, 1)
  await runner.run(() => { count++; return dto }).done
  assert.equal(count, 2)
})

test('real auth profile still supplies no roles: Sources makes zero requests after a successful login', async t => {
  const calls = []
  const client = createApiClient({ fetchImpl: (url, init) => {
    calls.push({ url, init })
    if (url.endsWith('/auth/login')) return response({ accessToken: 'memory-token', tokenType: 'Bearer', expiresIn: 900 })
    if (url.endsWith('/auth/me')) return response({ username: 'server-profile', roles: ['ADMIN'] })
    assert.fail('Unverified roles must never enable Sources')
  } })
  const store = createAuthSession({ service: createAuthService({ request: client }) })
  t.after(() => store.dispose())
  await store.login({ username: 'existing-test-fixture', password: 'fixture' })
  const service = createSourcesService({ request: store.request, getSession: store.getSnapshot })
  assert.equal(service.permissions().read, false)
  await assert.rejects(service.list(), { code: 'SOURCE_ROLES_UNAVAILABLE' })
  assert.equal(calls.length, 2)
  store.logout()
  await assert.rejects(service.list(), { code: 'AUTH_REQUIRED' })
})

test('AuthProvider-scoped requests cancel on logout and clear confirmed 401 sessions in isolated future-role fixtures', async t => {
  for (const outcome of ['logout', '401', '403']) {
    const pending = deferred(), started = deferred()
    let sourceSignal
    const client = createApiClient({ fetchImpl: (url, init) => {
      if (url.endsWith('/auth/login')) return response({ accessToken: 'memory-token', tokenType: 'Bearer', expiresIn: 900 })
      if (url.endsWith('/auth/me')) return response({ username: 'role-fixture' })
      sourceSignal = init.signal
      assert.equal(init.headers.get('Authorization'), 'Bearer memory-token')
      started.resolve(); return pending.promise
    } })
    const actual = createAuthService({ request: client })
    // Test-only role information stands in for a future approved /me DTO. The
    // production mapper/gate above remain unchanged and reject these claims.
    const store = createAuthSession({ service: { ...actual, async getProfile(token, options) {
      return { ...await actual.getProfile(token, options), roles: ['VIEWER'], rolesVerified: true }
    } } })
    t.after(() => store.dispose())
    await store.login({ username: 'fixture', password: 'fixture' })
    const service = createSourcesService({ request: store.request, getSession: store.getSnapshot, rolesContractVerified: true })
    const request = service.list()
    await started.promise
    if (outcome === 'logout') { store.logout(); assert.equal(sourceSignal.aborted, true) }
    pending.resolve(response(outcome === 'logout' ? [dto] : { error: 'denied' }, outcome === 'logout' ? 200 : Number(outcome)))
    await assert.rejects(request)
    assert.equal(store.getSnapshot().status, outcome === '403' ? 'authenticated' : 'anonymous')
  }
})
