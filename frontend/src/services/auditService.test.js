import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createAuthService } from './authService.js'
import { createAuthSession } from './authSession.js'
import { createAuditService } from './auditService.js'
import { auditPermissions } from './auditAccess.js'
import { auditActions, auditParamsFromQuery, auditQueryFromParams, auditRequest } from './auditContracts.js'

const dto = { id: 21, actorUserId: 5, actorLabel: 'admin-user', entityType: 'SOURCE', entityId: 7, action: 'UPDATE',
  occurredAt: '2026-11-27T10:00:00.5+02:00', beforeData: { title: 'قديم' }, afterData: { title: 'جديد' }, correlationId: null, reason: 'Source updated' }
const entity = { mode: 'entity', entityType: 'SOURCE', entityId: '7' }
const response = (data, status = 200) => new Response(JSON.stringify(data), { status })
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function fixture(handler = () => response([dto]), { roles = ['ADMIN'], verified = true } = {}) {
  const calls = []
  let session = { status: 'authenticated', user: { username: 'isolated-fixture', roles, rolesVerified: verified }, expiresAt: Date.now() + 3600000 }
  const client = createApiClient({ fetchImpl: (url, init) => { calls.push({ url, init }); return handler(url, init) } })
  const service = createAuditService({ request: (path, options) => client(path, { ...options, token: 'fixture-token' }), getSession: () => session, rolesContractVerified: verified })
  return { service, calls, getSession: () => session, replaceSession(value) { session = value } }
}

test('entity mode GETs /audit-logs/entity with exact entityType and numeric entityId plus Bearer authentication', async () => {
  const { service, calls } = fixture()
  const [log] = await service.search(entity)
  assert.equal(log.id, '21')
  assert.equal(calls[0].url, '/api/v1/audit-logs/entity?entityType=SOURCE&entityId=7')
  assert.equal(calls[0].init.method, 'GET')
  assert.equal(calls[0].init.body, undefined)
  assert.equal(calls[0].init.headers.get('Authorization'), 'Bearer fixture-token')
  assert.equal(calls[0].init.credentials, 'omit')
})

test('actor and action modes use path parameters only, with no extra query, pagination or date filters', async () => {
  const actor = fixture(() => response([dto]))
  await actor.service.search({ mode: 'actor', actorUserId: '5' })
  assert.equal(actor.calls[0].url, '/api/v1/audit-logs/actor/5')
  for (const action of auditActions) {
    const { service, calls } = fixture(() => response([{ ...dto, action }]))
    await service.search({ mode: 'action', action })
    assert.equal(calls[0].url, `/api/v1/audit-logs/action/${action}`)
  }
  assert.deepEqual(auditActions, ['CREATE', 'UPDATE', 'DELETE', 'VERIFY', 'REJECT', 'RESTORE'])
})

test('filters are separate modes: extra fields are never combined into one request', async () => {
  const { service, calls } = fixture(() => response([]))
  await service.search({ ...entity, actorUserId: '5', action: 'CREATE' })
  await service.search({ mode: 'actor', actorUserId: '5', entityType: 'SOURCE', entityId: '7', action: 'CREATE' })
  assert.deepEqual(calls.map(call => call.url), ['/api/v1/audit-logs/entity?entityType=SOURCE&entityId=7', '/api/v1/audit-logs/actor/5'])
  assert.equal(auditParamsFromQuery({ mode: 'action', action: 'DELETE', actorUserId: '5' }).toString(), 'mode=action&action=DELETE')
})

test('invalid modes, IDs, entity types and actions fail before HTTP', async () => {
  const { service, calls } = fixture()
  const bad = [undefined, {}, { mode: 'all' }, { mode: 'date', from: '2026-01-01' },
    ...['battir', '042', '0', '-1', '', '1.1', '1e2', '9007199254740992', 7, null].flatMap(id => [{ ...entity, entityId: id }, { mode: 'actor', actorUserId: id }]),
    ...['', '   ', '\t\n', 'A'.repeat(81), null, 7].map(entityType => ({ ...entity, entityType })),
    ...['', 'update', 'INVALID', 'VERIFIED', null].map(action => ({ mode: 'action', action }))]
  for (const query of bad) await assert.rejects(service.search(query), { code: 'AUDIT_VALIDATION' }, JSON.stringify(query))
  assert.equal(calls.length, 0)
  assert.equal(auditRequest({ ...entity, entityType: 'A'.repeat(80) }).query.entityType.length, 80)
})

test('any non-blank stored entity type of at most 80 characters is searchable and sent exactly as entered', async () => {
  for (const entityType of ['VILLAGE', 'source', ' SOURCE ', 'Legacy-Type.v1', 'نوع_قديم', '1ABC']) {
    const { service, calls } = fixture(() => response([{ ...dto, entityType }]))
    await service.search({ ...entity, entityType })
    assert.equal(new URL(calls[0].url, 'https://x.invalid').searchParams.get('entityType'), entityType)
  }
})

test('response fields and nullable metadata are preserved exactly, with no fabricated values', async () => {
  const rows = [dto, { ...dto, id: 3, actorUserId: null, actorLabel: null, occurredAt: null, beforeData: null, afterData: null, reason: '', correlationId: 'c-1' }]
  const [first, second] = await fixture(() => response(rows)).service.search(entity)
  assert.deepEqual({ ...first }, { id: '21', entityType: 'SOURCE', entityId: '7', action: 'UPDATE', actorUserId: 5, actorLabel: 'admin-user',
    occurredAt: '2026-11-27T10:00:00.5+02:00', beforeData: { title: 'قديم' }, afterData: { title: 'جديد' }, correlationId: null, reason: 'Source updated' })
  assert.deepEqual({ ...second }, { id: '3', entityType: 'SOURCE', entityId: '7', action: 'UPDATE', actorUserId: null, actorLabel: null,
    occurredAt: null, beforeData: null, afterData: null, correlationId: 'c-1', reason: '' })
  assert.equal(Object.isFrozen(first.afterData), true)
})

test('server order is preserved and empty results stay an empty array', async () => {
  const rows = [{ ...dto, id: 9 }, { ...dto, id: 30 }, { ...dto, id: 1 }]
  assert.deepEqual((await fixture(() => response(rows)).service.search(entity)).map(log => log.id), ['9', '30', '1'])
  assert.deepEqual(await fixture(() => response([])).service.search(entity), [])
})

test('malformed, duplicate and mismatched rows are rejected', async () => {
  for (const payload of [null, {}, { content: [dto] }, [dto, dto], [{ ...dto, entityId: 8 }], [{ ...dto, entityType: 'VILLAGE' }],
    [{ ...dto, id: 9007199254740992 }], [{ ...dto, occurredAt: 'yesterday' }], [{ ...dto, beforeData: [1] }], [{ ...dto, afterData: 'x' }],
    [{ ...dto, actorLabel: 4 }], [{ ...dto, action: '' }], [{ ...dto, actorUserId: 'admin' }]]) {
    await assert.rejects(fixture(() => response(payload)).service.search(entity))
  }
  await assert.rejects(fixture(() => response([{ ...dto, actorUserId: 6 }])).service.search({ mode: 'actor', actorUserId: '5' }))
  await assert.rejects(fixture(() => response([{ ...dto, action: 'CREATE' }])).service.search({ mode: 'action', action: 'UPDATE' }))
})

test('ADMIN is required; missing or unverified roles send zero requests', async () => {
  const session = (roles, rolesVerified = true) => ({ status: 'authenticated', user: { username: 'x', roles, rolesVerified }, expiresAt: Date.now() + 10000 })
  assert.deepEqual(auditPermissions(session(['ADMIN']), { rolesContractVerified: true }), { read: true, reason: null })
  for (const roles of [['EDITOR'], ['VIEWER'], []]) assert.deepEqual(auditPermissions(session(roles), { rolesContractVerified: true }), { read: false, reason: 'AUDIT_FORBIDDEN' })
  assert.equal(auditPermissions(session(['ADMIN'])).reason, 'AUDIT_ROLES_UNAVAILABLE')
  assert.equal(auditPermissions(session(['ADMIN'], false), { rolesContractVerified: true }).reason, 'AUDIT_ROLES_UNAVAILABLE')
  assert.equal(auditPermissions(session(null), { rolesContractVerified: true }).reason, 'AUDIT_ROLES_UNAVAILABLE')
  assert.equal(auditPermissions({ status: 'anonymous' }).reason, 'AUTH_REQUIRED')
  for (const [roles, verified, code] of [[['EDITOR'], true, 'AUDIT_FORBIDDEN'], [null, false, 'AUDIT_ROLES_UNAVAILABLE'], [['ADMIN'], false, 'AUDIT_ROLES_UNAVAILABLE']]) {
    const { service, calls } = fixture(() => assert.fail('No audit request allowed'), { roles, verified })
    await assert.rejects(service.search(entity), { code })
    assert.equal(calls.length, 0)
  }
  const snapshot = session(['ADMIN'])
  const production = createAuditService({ request: () => assert.fail('No production role override'), getSession: () => snapshot })
  await assert.rejects(production.search(entity), { code: 'AUDIT_ROLES_UNAVAILABLE' })
})

for (const status of [400, 401, 403, 404]) {
  test(`HTTP ${status} error semantics survive every mode without retry`, async () => {
    const { service, calls } = fixture(() => response({ error: 'backend error' }, status))
    const expected = { status, kind: 'http', details: { error: 'backend error' } }
    await assert.rejects(service.search(entity), expected)
    await assert.rejects(service.search({ mode: 'actor', actorUserId: '5' }), expected)
    await assert.rejects(service.search({ mode: 'action', action: 'CREATE' }), expected)
    assert.equal(calls.length, 3)
  })
}

test('network failures keep the cause and are not retried', async () => {
  const cause = new TypeError('offline')
  const { service, calls } = fixture(() => { throw cause })
  await assert.rejects(service.search(entity), { kind: 'network', cause })
  assert.equal(calls.length, 1)
})

test('pre-cancellation and late responses cannot deliver audit data after abort', async () => {
  const pending = deferred(), controller = new AbortController()
  const { service, calls } = fixture(() => pending.promise)
  controller.abort()
  await assert.rejects(service.search(entity, { signal: controller.signal }))
  assert.equal(calls.length, 0)
  const active = new AbortController(), request = service.search(entity, { signal: active.signal })
  assert.equal(calls[0].init.signal, active.signal)
  active.abort()
  pending.resolve(response([dto]))
  await assert.rejects(request)
})

test('old services reject responses and further requests after logout, expiry or a replacement session', async () => {
  for (const mode of ['logout', 'profile', 'expired']) {
    const pending = deferred()
    const { service, calls, getSession, replaceSession } = fixture(() => pending.promise)
    const request = service.search(entity), old = getSession()
    replaceSession(mode === 'logout' ? { status: 'anonymous' } : mode === 'expired' ? { ...old, expiresAt: Date.now() - 1 } : { ...old, user: { ...old.user } })
    pending.resolve(response([dto]))
    await assert.rejects(request, { code: 'AUTH_REQUIRED' })
    await assert.rejects(service.search(entity), { code: 'AUTH_REQUIRED' })
    assert.equal(service.permissions().read, false)
    assert.equal(calls.length, 1)
  }
})

test('real auth profile without roles sends zero audit requests, even if /auth/me includes uncontracted roles', async t => {
  const calls = []
  const client = createApiClient({ fetchImpl: url => {
    calls.push(url)
    if (url.endsWith('/auth/login')) return response({ accessToken: 'memory-token', tokenType: 'Bearer', expiresIn: 900 })
    if (url.endsWith('/auth/me')) return response({ username: 'server-profile', roles: ['ADMIN'] })
    assert.fail(`Unexpected request ${url}`)
  } })
  const store = createAuthSession({ service: createAuthService({ request: client }) })
  t.after(() => store.dispose())
  await store.login({ username: 'test-fixture', password: 'fixture' })
  const service = createAuditService({ request: store.request, getSession: store.getSnapshot })
  for (const query of [entity, { mode: 'actor', actorUserId: '5' }, { mode: 'action', action: 'CREATE' }]) {
    await assert.rejects(service.search(query), { code: 'AUDIT_ROLES_UNAVAILABLE' })
  }
  assert.equal(calls.length, 2)
})

test('URL parameters map to exactly one mode and stay null until a mode is chosen', () => {
  assert.equal(auditQueryFromParams(new URLSearchParams('')), null)
  assert.deepEqual(auditQueryFromParams(new URLSearchParams('mode=actor&actorUserId=5&action=CREATE')), { mode: 'actor', actorUserId: '5' })
  assert.deepEqual(auditQueryFromParams(new URLSearchParams('mode=entity&entityType=SOURCE&entityId=7')), entity)
  assert.deepEqual(auditQueryFromParams(new URLSearchParams('mode=global')), { mode: 'global' })
  assert.throws(() => auditRequest(auditQueryFromParams(new URLSearchParams('mode=global'))), { code: 'AUDIT_VALIDATION' })
  assert.throws(() => auditParamsFromQuery({ mode: 'actor', actorUserId: 'x' }), { code: 'AUDIT_VALIDATION' })
})
