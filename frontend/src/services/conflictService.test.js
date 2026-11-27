import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createAuthService } from './authService.js'
import { createAuthSession } from './authSession.js'
import { createConflictService } from './conflictService.js'
import { conflictPermissions } from './conflictAccess.js'

const values = [{ value: '1200', source: 'تعداد 1945' }, { value: '1350' }]
const dto = { id: 9, entityType: 'VILLAGE', entityId: 42, fieldName: 'population', conflictingValues: values, status: 'OPEN',
  resolutionNotes: null, resolvedBy: null, createdAt: '2026-11-27T10:00:00.123456+02:00', resolvedAt: null }
const reviewed = { ...dto, status: 'IN_REVIEW' }
const resolved = { ...dto, status: 'RESOLVED', resolutionNotes: 'تم الاعتماد', resolvedBy: 3, resolvedAt: '2026-11-27T11:00:00Z' }
const response = (data, status = 200) => new Response(JSON.stringify(data), { status })
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function fixture(handler = () => response([dto]), { roles = null, verified = false } = {}) {
  const calls = []
  let session = { status: 'authenticated', user: { username: 'isolated-fixture', roles, rolesVerified: verified }, expiresAt: Date.now() + 3600000 }
  const client = createApiClient({ fetchImpl: (url, init) => { calls.push({ url, init }); return handler(url, init) } })
  const service = createConflictService({ request: (path, options) => client(path, { ...options, token: 'fixture-token' }), getSession: () => session, rolesContractVerified: verified })
  return { service, calls, getSession: () => session, replaceSession(value) { session = value } }
}
const admin = { roles: ['ADMIN'], verified: true }

test('village conflicts GET uses the exact VILLAGE query and Bearer authentication without requiring roles', async () => {
  const { service, calls } = fixture()
  const [conflict] = await service.listForVillage('42')
  assert.equal(conflict.id, '9')
  assert.equal(conflict.entityId, '42')
  assert.equal(calls[0].url, '/api/v1/data-conflicts?entityType=VILLAGE&entityId=42')
  assert.equal(calls[0].init.method, 'GET')
  assert.equal(calls[0].init.body, undefined)
  assert.equal(calls[0].init.headers.get('Authorization'), 'Bearer fixture-token')
  assert.equal(calls[0].init.credentials, 'omit')
  assert.equal(calls.length, 1)
})

test('status GET uses the path enum; each status is accepted only for verified EDITOR/ADMIN', async () => {
  for (const status of ['OPEN', 'IN_REVIEW', 'RESOLVED']) {
    for (const roles of [['EDITOR'], ['ADMIN']]) {
      const row = status === 'OPEN' ? dto : status === 'IN_REVIEW' ? reviewed : resolved
      const { service, calls } = fixture(() => response([row]), { roles, verified: true })
      assert.equal((await service.listByStatus(status))[0].status, status)
      assert.equal(calls[0].url, `/api/v1/data-conflicts/status/${status}`)
      assert.equal(calls[0].init.method, 'GET')
      assert.equal(calls[0].init.headers.get('Authorization'), 'Bearer fixture-token')
    }
  }
  const { service, calls } = fixture(undefined, admin)
  for (const status of ['open', 'CLOSED', '', 'OPEN/../x', null]) await assert.rejects(service.listByStatus(status), { code: 'CONFLICT_VALIDATION' })
  await assert.rejects(fixture(() => response([dto]), admin).service.listByStatus('RESOLVED'))
  assert.equal(calls.length, 0)
})

test('POST sends only CreateDataConflictRequest fields with numeric entityId and preserved value maps', async () => {
  for (const roles of [['EDITOR'], ['ADMIN']]) {
    const { service, calls } = fixture(() => response(dto, 201), { roles, verified: true })
    const created = await service.create('42', '  population ', values)
    assert.equal(created.status, 'OPEN')
    assert.equal(calls[0].url, '/api/v1/data-conflicts')
    assert.equal(calls[0].init.method, 'POST')
    assert.deepEqual(JSON.parse(calls[0].init.body), { entityType: 'VILLAGE', entityId: 42, fieldName: '  population ', conflictingValues: values })
    assert.equal(calls[0].init.headers.get('Content-Type'), 'application/json')
    assert.equal(calls.length, 1)
  }
})

test('PATCH review sends no body and PATCH resolve sends only resolutionNotes; neither touches village data', async () => {
  const { service, calls } = fixture(url => response(url.endsWith('/review') ? reviewed : resolved), admin)
  assert.equal((await service.review('9')).status, 'IN_REVIEW')
  assert.equal((await service.resolve('9', '  تم الاعتماد  ')).status, 'RESOLVED')
  assert.deepEqual(calls.map(call => [call.init.method, call.url, call.init.body]), [
    ['PATCH', '/api/v1/data-conflicts/9/review', undefined],
    ['PATCH', '/api/v1/data-conflicts/9/resolve', JSON.stringify({ resolutionNotes: '  تم الاعتماد  ' })],
  ])
  assert.ok(calls.every(call => !call.url.includes('/villages')))
})

test('mutation responses with an unexpected identity or lifecycle state are rejected', async () => {
  for (const [operation, body] of [['review', dto], ['review', { ...reviewed, id: 10 }], ['resolve', reviewed], ['resolve', { ...resolved, id: 10 }]]) {
    const { service } = fixture(() => response(body), admin)
    await assert.rejects(operation === 'review' ? service.review('9') : service.resolve('9', 'notes'))
  }
  await assert.rejects(fixture(() => response({ ...dto, entityId: 43 }, 201), admin).service.create('42', 'population', values))
})

test('production roles fail closed: reads work, while status lists and every mutation send nothing', async () => {
  const { service, calls } = fixture(undefined, { roles: ['ADMIN'], verified: false })
  assert.deepEqual(service.permissions(), { read: true, manage: false, create: false, review: false, resolve: false, reason: 'CONFLICT_ROLES_UNAVAILABLE' })
  await service.listForVillage('42')
  for (const run of [() => service.listByStatus('OPEN'), () => service.create('42', 'population', values), () => service.review('9'), () => service.resolve('9', 'notes')]) {
    await assert.rejects(run(), { code: 'CONFLICT_ROLES_UNAVAILABLE' })
  }
  assert.equal(calls.length, 1)
  const snapshot = { status: 'authenticated', user: { username: 'admin', roles: ['ADMIN'], rolesVerified: true }, expiresAt: Date.now() + 10000 }
  const production = createConflictService({ request: () => assert.fail('No production role override'), getSession: () => snapshot })
  await assert.rejects(production.create('42', 'population', values), { code: 'CONFLICT_ROLES_UNAVAILABLE' })
})

test('isolated role fixtures match backend @PreAuthorize rules', async () => {
  const session = roles => ({ status: 'authenticated', user: { username: 'x', roles, rolesVerified: true }, expiresAt: Date.now() + 10000 })
  const check = roles => conflictPermissions(session(roles), { rolesContractVerified: true })
  assert.deepEqual(check(['VIEWER']), { read: true, manage: false, create: false, review: false, resolve: false, reason: 'CONFLICT_FORBIDDEN' })
  assert.deepEqual(check(['EDITOR']), { read: true, manage: true, create: true, review: false, resolve: false, reason: null })
  assert.deepEqual(check(['ADMIN']), { read: true, manage: true, create: true, review: true, resolve: true, reason: null })
  assert.equal(check(['ROOT']).reason, 'CONFLICT_ROLES_UNAVAILABLE')
  assert.equal(conflictPermissions({ status: 'anonymous' }).reason, 'AUTH_REQUIRED')
  for (const roles of [['VIEWER'], ['EDITOR']]) {
    const { service, calls } = fixture(undefined, { roles, verified: true })
    await assert.rejects(service.review('9'), { code: 'CONFLICT_FORBIDDEN' })
    await assert.rejects(service.resolve('9', 'notes'), { code: 'CONFLICT_FORBIDDEN' })
    assert.equal(calls.length, 0)
  }
  await assert.rejects(fixture(undefined, { roles: ['VIEWER'], verified: true }).service.listByStatus('OPEN'), { code: 'CONFLICT_FORBIDDEN' })
})

for (const id of ['battir', '042', '0', '-1', '', '1.1', '1e2', '9007199254740992', 42, null]) {
  test(`invalid numeric village/conflict ID ${JSON.stringify(id)} never calls HTTP`, async () => {
    const { service, calls } = fixture(undefined, admin)
    await assert.rejects(service.listForVillage(id))
    await assert.rejects(service.create(id, 'population', values))
    await assert.rejects(service.review(id))
    await assert.rejects(service.resolve(id, 'notes'))
    assert.equal(calls.length, 0)
  })
}

test('unsupported entity types, field names, value maps and resolution notes fail before HTTP', async () => {
  const { service, calls } = fixture(undefined, admin)
  for (const entityType of ['SOURCE', '', ' VILLAGE', 'village']) {
    await assert.rejects(service.listForVillage('42', { entityType }), { code: 'CONFLICT_VALIDATION' })
    await assert.rejects(service.create('42', 'population', values, { entityType }), { code: 'CONFLICT_VALIDATION' })
  }
  for (const fieldName of ['', '   ', 'x'.repeat(101), null]) await assert.rejects(service.create('42', fieldName, values), { code: 'CONFLICT_VALIDATION' })
  await service.create('42', 'x'.repeat(100), values).catch(() => {})
  for (const bad of [null, [], [values[0]], [values[0], {}], [values[0], []], [values[0], { ' ': 'x' }], [values[0], { a: null }],
    [values[0], { a: undefined }], [values[0], { a: Number.NaN }], [values[0], { a: () => 1 }], [values[0], new Map([['a', 1]])], [values[0], 'text']]) {
    await assert.rejects(service.create('42', 'population', bad), { code: 'CONFLICT_VALIDATION' })
  }
  for (const notes of ['', '   ', '\n\t', null, undefined, 7]) await assert.rejects(service.resolve('9', notes), { code: 'CONFLICT_VALIDATION' })
  assert.equal(calls.length, 1) // Only the 100-character field name reached HTTP.
})

test('nested JSON values, nested nulls and multi-key maps are accepted as the backend allows', async () => {
  const nested = [{ value: { count: 1, note: null }, tags: ['a', 2, true] }, { value: 0, verified: false }]
  const { service, calls } = fixture(() => response({ ...dto, conflictingValues: nested }, 201), admin)
  const created = await service.create('42', 'population', nested)
  assert.deepEqual(JSON.parse(calls[0].init.body).conflictingValues, nested)
  assert.deepEqual(created.conflictingValues, nested)
  assert.equal(Object.isFrozen(created.conflictingValues[0].value), true)
})

test('empty lists are arrays; malformed, duplicate, wrong-village or wrong-status rows are errors', async () => {
  assert.deepEqual(await fixture(() => response([])).service.listForVillage('42'), [])
  for (const payload of [null, {}, { content: [dto] }, [{ ...dto, entityId: 43 }], [{ ...dto, entityType: 'SOURCE' }], [dto, dto],
    [{ ...dto, status: 'CLOSED' }], [{ ...dto, conflictingValues: null }], [{ ...dto, conflictingValues: ['x'] }],
    [{ ...dto, createdAt: 'yesterday' }], [{ ...dto, resolvedBy: 'admin' }], [{ ...dto, id: 9007199254740992 }], [{ ...dto, fieldName: 4 }]]) {
    await assert.rejects(fixture(() => response(payload)).service.listForVillage('42'))
  }
})

test('server ordering and nullable metadata are preserved exactly', async () => {
  const rows = [{ ...resolved, id: 3 }, { ...dto, id: 11, createdAt: null }, { ...reviewed, id: 5, resolutionNotes: '' }]
  const list = await fixture(() => response(rows)).service.listForVillage('42')
  assert.deepEqual(list.map(item => item.id), ['3', '11', '5'])
  assert.equal(list[0].resolvedBy, 3)
  assert.equal(list[0].resolvedAt, '2026-11-27T11:00:00Z')
  assert.equal(list[1].createdAt, null)
  assert.equal(list[1].resolutionNotes, null)
  assert.equal(list[2].resolutionNotes, '')
  assert.equal(list[0].createdAt, '2026-11-27T10:00:00.123456+02:00')
})

for (const status of [400, 401, 403, 404, 409]) {
  test(`HTTP ${status} error semantics survive every endpoint without any retry`, async () => {
    const { service, calls } = fixture(() => response({ error: 'backend error' }, status), admin)
    const expected = { status, kind: 'http', details: { error: 'backend error' } }
    await assert.rejects(service.listForVillage('42'), expected)
    await assert.rejects(service.listByStatus('OPEN'), expected)
    await assert.rejects(service.create('42', 'population', values), expected)
    await assert.rejects(service.review('9'), expected)
    await assert.rejects(service.resolve('9', 'notes'), expected)
    assert.equal(calls.length, 5)
  })
}

test('network failures keep the cause and no request is automatically retried', async () => {
  const cause = new TypeError('offline')
  const { service, calls } = fixture(() => { throw cause }, admin)
  await assert.rejects(service.listForVillage('42'), { kind: 'network', cause })
  await assert.rejects(service.create('42', 'population', values), { kind: 'network', cause })
  await assert.rejects(service.review('9'), { kind: 'network', cause })
  await assert.rejects(service.resolve('9', 'notes'), { kind: 'network', cause })
  assert.equal(calls.length, 4)
})

test('pre-cancellation and late transport responses cannot deliver results after abort', async () => {
  for (const operation of ['list', 'status', 'create', 'review', 'resolve']) {
    const pending = deferred(), controller = new AbortController()
    const { service, calls } = fixture(() => pending.promise, admin)
    const run = signal => ({
      list: () => service.listForVillage('42', { signal }), status: () => service.listByStatus('OPEN', { signal }),
      create: () => service.create('42', 'population', values, { signal }), review: () => service.review('9', { signal }),
      resolve: () => service.resolve('9', 'notes', { signal }),
    })[operation]()
    controller.abort()
    await assert.rejects(run(controller.signal))
    assert.equal(calls.length, 0)
    const active = new AbortController(), request = run(active.signal)
    assert.equal(calls[0].init.signal, active.signal)
    active.abort()
    pending.resolve(response(['list', 'status'].includes(operation) ? [dto] : operation === 'review' ? reviewed : operation === 'resolve' ? resolved : dto))
    await assert.rejects(request)
  }
})

test('old services reject responses and further requests after logout, expiry or a replacement session', async () => {
  for (const mode of ['logout', 'profile', 'expired']) {
    const pending = deferred()
    const { service, calls, getSession, replaceSession } = fixture(() => pending.promise)
    const request = service.listForVillage('42'), old = getSession()
    replaceSession(mode === 'logout' ? { status: 'anonymous' } : mode === 'expired' ? { ...old, expiresAt: Date.now() - 1 } : { ...old, user: { ...old.user } })
    pending.resolve(response([dto]))
    await assert.rejects(request, { code: 'AUTH_REQUIRED' })
    await assert.rejects(service.listForVillage('42'), { code: 'AUTH_REQUIRED' })
    assert.equal(service.permissions().read, false)
    assert.equal(calls.length, 1)
  }
})

test('real auth profile without roles permits village conflict reads while management and mutations stay closed', async t => {
  const calls = []
  const client = createApiClient({ fetchImpl: (url, init) => {
    calls.push({ url, init })
    if (url.endsWith('/auth/login')) return response({ accessToken: 'memory-token', tokenType: 'Bearer', expiresIn: 900 })
    if (url.endsWith('/auth/me')) return response({ username: 'server-profile', roles: ['ADMIN'] })
    assert.equal(url, '/api/v1/data-conflicts?entityType=VILLAGE&entityId=42')
    assert.equal(init.headers.get('Authorization'), 'Bearer memory-token')
    return response([dto])
  } })
  const store = createAuthSession({ service: createAuthService({ request: client }) })
  t.after(() => store.dispose())
  await store.login({ username: 'test-fixture', password: 'fixture' })
  const service = createConflictService({ request: store.request, getSession: store.getSnapshot })
  await service.listForVillage('42')
  await assert.rejects(service.listByStatus('OPEN'), { code: 'CONFLICT_ROLES_UNAVAILABLE' })
  await assert.rejects(service.create('42', 'population', values), { code: 'CONFLICT_ROLES_UNAVAILABLE' })
  await assert.rejects(service.review('9'), { code: 'CONFLICT_ROLES_UNAVAILABLE' })
  assert.equal(calls.length, 3)
  store.logout()
  await assert.rejects(service.listForVillage('42'), { code: 'AUTH_REQUIRED' })
  assert.equal(calls.length, 3)
})

test('scoped requests abort on logout, clear 401 sessions and retain 403 sessions', async t => {
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
    const service = createConflictService({ request: store.request, getSession: store.getSnapshot })
    const request = service.listForVillage('42')
    await started.promise
    if (mode === 'logout') { store.logout(); assert.equal(signal.aborted, true) }
    pending.resolve(response(mode === 'logout' ? [dto] : { error: 'denied' }, mode === 'logout' ? 200 : Number(mode)))
    await assert.rejects(request)
    assert.equal(store.getSnapshot().status, mode === '403' ? 'authenticated' : 'anonymous')
  }
})
