import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createAuthService } from './authService.js'
import { createAuthSession } from './authSession.js'
import { createVerificationService } from './verificationService.js'
import { createSourcesService } from './sourcesService.js'

const dto = { id: 7, entityType: 'VILLAGE', entityId: 42, reviewerUserId: 12, decision: 'VERIFIED', reason: null, reviewedAt: '2026-11-27T10:00:00Z' }
const response = (data, status = 200) => new Response(JSON.stringify(data), { status })
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function fixture(handler = () => response([dto]), { roles = null, verified = false } = {}) {
  const calls = []
  let session = { status: 'authenticated', user: { username: 'isolated-fixture', roles, rolesVerified: verified }, expiresAt: Date.now() + 3600000 }
  const client = createApiClient({ fetchImpl: (url, init) => { calls.push({ url, init }); return handler(url, init) } })
  const service = createVerificationService({ request: (path, options) => client(path, { ...options, token: 'fixture-token' }), getSession: () => session, rolesContractVerified: verified })
  return { service, calls, getSession: () => session, replaceSession(value) { session = value } }
}

test('GET history uses exact VILLAGE query and Bearer authentication, with no invented pagination or role requirement', async () => {
  const { service, calls } = fixture()
  assert.equal((await service.list('42'))[0].id, '7')
  assert.equal(calls[0].url, '/api/v1/verifications?entityType=VILLAGE&entityId=42')
  assert.equal(calls[0].init.method, 'GET')
  assert.equal(calls[0].init.body, undefined)
  assert.equal(calls[0].init.headers.get('Authorization'), 'Bearer fixture-token')
  assert.equal(calls[0].init.credentials, 'omit')
  assert.equal(calls.length, 1)
})

test('POST uses only CreateVerificationRequest fields and the server-provided reviewer/time/record identity', async () => {
  for (const decision of ['VERIFIED', 'REJECTED', 'NEEDS_REVISION']) {
    const { service, calls } = fixture(() => response({ ...dto, decision, reason: '  السبب  ' }, 201), { roles: ['ADMIN'], verified: true })
    const record = await service.create('42', decision, '  السبب  ')
    assert.equal(record.reviewerUserId, 12)
    assert.equal(record.reviewedAt, dto.reviewedAt)
    assert.equal(calls[0].url, '/api/v1/verifications')
    assert.equal(calls[0].init.method, 'POST')
    assert.deepEqual(JSON.parse(calls[0].init.body), { entityType: 'VILLAGE', entityId: 42, decision, reason: '  السبب  ' })
    assert.equal(calls.length, 1) // No status-changing request or automatic retry.
  }
})

test('production creation is blocked even with injected ADMIN claims; isolated EDITOR/VIEWER fixtures cannot create', async () => {
  const production = fixture(() => assert.fail('No POST allowed'))
  await assert.rejects(production.service.create('42', 'VERIFIED', null), { code: 'VERIFICATION_ROLES_UNAVAILABLE' })
  for (const roles of [['EDITOR'], ['VIEWER'], []]) {
    const { service, calls } = fixture(undefined, { roles, verified: true })
    await assert.rejects(service.create('42', 'VERIFIED', null), { code: 'VERIFICATION_FORBIDDEN' })
    assert.equal(calls.length, 0)
  }
  const getSession = () => ({ status: 'authenticated', user: { username: 'admin', roles: ['ADMIN'], rolesVerified: true }, expiresAt: Date.now() + 10000 })
  const snapshot = getSession()
  const service = createVerificationService({ request: () => assert.fail('No production override'), getSession: () => snapshot })
  await assert.rejects(service.create('42', 'VERIFIED', null), { code: 'VERIFICATION_ROLES_UNAVAILABLE' })
})

for (const id of ['battir', '042', '0', '-1', '', '1.1', '1e2', '9007199254740992', 42, null]) {
  test(`invalid numeric village ID ${JSON.stringify(id)} never calls HTTP`, async () => {
    const { service, calls } = fixture(undefined, { roles: ['ADMIN'], verified: true })
    await assert.rejects(service.list(id))
    await assert.rejects(service.create(id, 'VERIFIED', null))
    assert.equal(calls.length, 0)
  })
}

test('unsupported entity types, invalid decisions and invalid reason types fail before HTTP', async () => {
  const { service, calls } = fixture(undefined, { roles: ['ADMIN'], verified: true })
  for (const entityType of ['SOURCE', '', 'OTHER', 'VILLAGE'.padEnd(81)]) {
    await assert.rejects(service.list('42', { entityType }))
    await assert.rejects(service.create('42', 'VERIFIED', null, { entityType }))
  }
  await assert.rejects(service.create('42', 'APPROVED', null))
  await assert.rejects(service.create('42', 'VERIFIED', undefined))
  assert.equal(calls.length, 0)
})

test('empty history is an array and malformed or wrong-village envelopes are errors', async () => {
  assert.deepEqual(await fixture(() => response([])).service.list('42'), [])
  for (const payload of [null, {}, { content: [dto] }, [{ ...dto, entityId: 43 }], [dto, dto]]) await assert.rejects(fixture(() => response(payload)).service.list('42'))
  const badPost = fixture(() => response({ ...dto, entityId: 43 }, 201), { roles: ['ADMIN'], verified: true })
  await assert.rejects(badPost.service.create('42', 'VERIFIED', null))
})

for (const status of [400, 401, 403, 404]) {
  test(`HTTP ${status} error semantics survive GET/POST without any retry`, async () => {
    const { service, calls } = fixture(() => response({ error: 'backend error' }, status), { roles: ['ADMIN'], verified: true })
    await assert.rejects(service.list('42'), { status, kind: 'http', details: { error: 'backend error' } })
    await assert.rejects(service.create('42', 'VERIFIED', null), { status, kind: 'http', details: { error: 'backend error' } })
    assert.equal(calls.length, 2)
  })
}

test('network failures keep the cause and no GET/POST is automatically retried', async () => {
  const cause = new TypeError('offline')
  const { service, calls } = fixture(() => { throw cause }, { roles: ['ADMIN'], verified: true })
  await assert.rejects(service.list('42'), { kind: 'network', cause })
  await assert.rejects(service.create('42', 'VERIFIED', null), { kind: 'network', cause })
  assert.equal(calls.length, 2)
})

test('pre-cancellation and late transport responses cannot deliver a history or mutation after abort', async () => {
  for (const operation of ['list', 'create']) {
    const pending = deferred(), controller = new AbortController()
    const { service, calls } = fixture(() => pending.promise, { roles: ['ADMIN'], verified: true })
    controller.abort()
    const run = signal => operation === 'list' ? service.list('42', { signal }) : service.create('42', 'VERIFIED', null, { signal })
    await assert.rejects(run(controller.signal))
    assert.equal(calls.length, 0)
    const active = new AbortController(), request = run(active.signal)
    assert.equal(calls[0].init.signal, active.signal)
    active.abort()
    pending.resolve(response(operation === 'list' ? [dto] : dto))
    await assert.rejects(request)
  }
})

test('old services reject responses and further requests after logout, expiry or a replacement profile/session', async () => {
  for (const mode of ['logout', 'profile', 'expired']) {
    const pending = deferred()
    const { service, calls, getSession, replaceSession } = fixture(() => pending.promise)
    const request = service.list('42'), old = getSession()
    replaceSession(mode === 'logout' ? { status: 'anonymous' } : mode === 'expired' ? { ...old, expiresAt: Date.now() - 1 } : { ...old, user: { ...old.user } })
    pending.resolve(response([dto]))
    await assert.rejects(request, { code: 'AUTH_REQUIRED' })
    await assert.rejects(service.list('42'), { code: 'AUTH_REQUIRED' })
    assert.equal(calls.length, 1)
  }
})

test('real auth profile with no roles permits verification history while Sources and verification mutations stay closed', async t => {
  const calls = []
  const client = createApiClient({ fetchImpl: (url, init) => {
    calls.push({ url, init })
    if (url.endsWith('/auth/login')) return response({ accessToken: 'memory-token', tokenType: 'Bearer', expiresIn: 900 })
    if (url.endsWith('/auth/me')) return response({ username: 'server-profile', roles: ['ADMIN'] })
    assert.equal(url, '/api/v1/verifications?entityType=VILLAGE&entityId=42')
    assert.equal(init.headers.get('Authorization'), 'Bearer memory-token')
    return response([dto])
  } })
  const store = createAuthSession({ service: createAuthService({ request: client }) })
  t.after(() => store.dispose())
  await store.login({ username: 'test-fixture', password: 'fixture' })
  const service = createVerificationService({ request: store.request, getSession: store.getSnapshot })
  await service.list('42')
  await assert.rejects(service.create('42', 'VERIFIED', null), { code: 'VERIFICATION_ROLES_UNAVAILABLE' })
  await assert.rejects(createSourcesService({ request: store.request, getSession: store.getSnapshot }).list(), { code: 'SOURCE_ROLES_UNAVAILABLE' })
  assert.equal(calls.length, 3)
})

test('scoped authentication requests abort on logout, clear 401 sessions and retain 403 sessions', async t => {
  for (const mode of ['logout', '401', '403']) {
    const pending = deferred(), started = deferred()
    let signal
    const client = createApiClient({ fetchImpl: (url, init) => {
      if (url.endsWith('/auth/login')) return response({ accessToken: 'memory-token', tokenType: 'Bearer', expiresIn: 900 })
      if (url.endsWith('/auth/me')) return response({ username: 'profile' })
      signal = init.signal; started.resolve(); return pending.promise
    } })
    const store = createAuthSession({ service: createAuthService({ request: client }) })
    t.after(() => store.dispose())
    await store.login({ username: 'fixture', password: 'fixture' })
    const service = createVerificationService({ request: store.request, getSession: store.getSnapshot })
    const request = service.list('42')
    await started.promise
    if (mode === 'logout') { store.logout(); assert.equal(signal.aborted, true) }
    pending.resolve(response(mode === 'logout' ? [dto] : { error: 'denied' }, mode === 'logout' ? 200 : Number(mode)))
    await assert.rejects(request)
    assert.equal(store.getSnapshot().status, mode === '403' ? 'authenticated' : 'anonymous')
  }
})
