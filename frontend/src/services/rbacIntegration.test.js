import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createAuthService } from './authService.js'
import { createAuthSession } from './authSession.js'
import { authAccessDecision, memberRoutes } from './authAccess.js'
import { effectiveRolesContractVerified } from './authRoles.js'
import { effectiveSourceRolesVerified } from './sourceAccess.js'
import { createSourcesService } from './sourcesService.js'
import { adaptSource } from './sourceContracts.js'
import { createVerificationService } from './verificationService.js'
import { createConflictService } from './conflictService.js'
import { createAuditService } from './auditService.js'
import { publicVillageCapabilities, villageDataMode } from './villageMode.js'

// All HTTP here is simulated. No services, users or database are contacted.
const sourceBody = { title: 'Synthetic source', publisher: null, author: null, sourceType: 'BOOK', url: null,
  publicationDate: null, accessedAt: null, citation: null }
const source = { id: 12, ...sourceBody, createdAt: '2026-11-27T12:00:00Z' }
const link = { villageId: 42, sourceId: 12, claimNote: null }
const verification = { id: 7, entityType: 'VILLAGE', entityId: 42, reviewerUserId: 12,
  decision: 'VERIFIED', reason: null, reviewedAt: '2026-11-27T12:00:00Z' }
const values = [{ value: 'A' }, { value: 'B' }]
const conflict = { id: 9, entityType: 'VILLAGE', entityId: 42, fieldName: 'nameAr', conflictingValues: values,
  status: 'OPEN', resolutionNotes: null, resolvedBy: null, createdAt: '2026-11-27T12:00:00Z', resolvedAt: null }
const json = (dto, status = 200) => new Response(JSON.stringify(dto), { status })
const credentials = { username: 'synthetic-login', password: 'synthetic-input' }
const auditQuery = { mode: 'entity', entityType: 'VILLAGE', entityId: '42' }

function deferred() { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }

function modules(store) {
  const options = { request: store.request, getSession: store.getSnapshot }
  return { sources: createSourcesService(options), verification: createVerificationService(options),
    conflicts: createConflictService(options), audit: createAuditService(options) }
}

function fixture(t, profile = { username: 'synthetic-profile', roles: [] }) {
  let currentProfile = profile, handler = null
  const calls = []
  const client = createApiClient({ fetchImpl: async (url, init) => {
    calls.push({ url, init })
    if (url === '/api/v1/auth/login') return json({ accessToken: 'synthetic-token', tokenType: 'Bearer', expiresIn: 900 })
    assert.equal(init.headers.get('Authorization'), 'Bearer synthetic-token')
    if (handler) return handler(url, init)
    if (url === '/api/v1/auth/me') return json(currentProfile)
    if (url.startsWith('/api/v1/sources')) {
      if (url.endsWith('/villages')) return json([link])
      return json(url === '/api/v1/sources' && init.method === 'GET' ? [source] : source)
    }
    if (url === '/api/v1/villages/42/sources') return json(init.method === 'POST' ? link : [link])
    if (url.startsWith('/api/v1/verifications')) return json(init.method === 'POST' ? verification : [verification])
    if (url.startsWith('/api/v1/data-conflicts')) {
      if (url.endsWith('/review')) return json({ ...conflict, status: 'IN_REVIEW' })
      if (url.endsWith('/resolve')) return json({ ...conflict, status: 'RESOLVED', resolutionNotes: 'Synthetic review',
        resolvedBy: 12, resolvedAt: '2026-11-27T13:00:00Z' })
      return json(init.method === 'POST' ? conflict : [conflict])
    }
    if (url.startsWith('/api/v1/audit-logs/')) return json([])
    assert.fail(`Unexpected endpoint: ${url}`)
  } })
  const store = createAuthSession({ service: createAuthService({ request: client }) })
  t.after(() => store.dispose())
  return { store, calls, profile(value) { currentProfile = value }, handle(value) { handler = value } }
}

function operations(services, { read, write, admin, authenticated = true }) {
  return [
    [read, () => services.sources.list()],
    [read, () => services.sources.get('12')],
    [read, () => services.sources.villageSources('42')],
    [read, () => services.sources.sourceVillages('12')],
    [write, () => services.sources.create(sourceBody)],
    [write, () => services.sources.update(adaptSource(source), {})],
    [write, () => services.sources.link('42', '12', null)],
    [authenticated, () => services.verification.list('42')],
    [admin, () => services.verification.create('42', 'VERIFIED', null)],
    [authenticated, () => services.conflicts.listForVillage('42')],
    [write, () => services.conflicts.listByStatus('OPEN')],
    [write, () => services.conflicts.create('42', 'nameAr', values)],
    [admin, () => services.conflicts.review('9')],
    [admin, () => services.conflicts.resolve('9', 'Synthetic review')],
    [admin, () => services.audit.search(auditQuery)],
  ]
}

const combinations = [[], ['VIEWER'], ['EDITOR'], ['ADMIN'], ['VIEWER', 'EDITOR'], ['VIEWER', 'ADMIN'],
  ['EDITOR', 'ADMIN'], ['VIEWER', 'EDITOR', 'ADMIN']]
for (const roles of combinations) {
  test(`effective /me roles ${JSON.stringify(roles)} enforce every Member 3 operation and route`, async t => {
    const state = fixture(t, { username: 'synthetic-profile', roles })
    const user = await state.store.login(credentials)
    assert.deepEqual(user.roles, roles)
    assert.equal(user.rolesVerified, true)
    assert.ok(Object.isFrozen(user) && Object.isFrozen(user.roles))
    assert.throws(() => user.roles.push('ADMIN'), TypeError)
    const admin = roles.includes('ADMIN'), write = admin || roles.includes('EDITOR'), read = roles.length > 0
    const services = modules(state.store)
    for (const [allowed, run] of operations(services, { read, write, admin })) {
      const before = state.calls.length
      if (allowed) await run()
      else await assert.rejects(run(), error => /FORBIDDEN$/.test(error.code))
      assert.equal(state.calls.length - before, allowed ? 1 : 0)
    }
    assert.deepEqual(memberRoutes.map(route => authAccessDecision(state.store.getSnapshot(), route)),
      [read, write, admin].map(allowed => allowed ? 'allow' : 'forbidden'))
  })
}

const invalidRoles = [undefined, null, 'ADMIN', {}, 1, [null], [1], [{}], ['UNKNOWN'], ['ROLE_ADMIN'], ['admin'],
  [' ADMIN'], ['ADMIN '], ['ADMIN', 'UNKNOWN']]
for (const roles of invalidRoles) {
  test(`invalid /me roles ${JSON.stringify(roles)} close every role-dependent operation without losing authenticated reads`, async t => {
    const state = fixture(t, { username: 'ADMIN', roles, rolesVerified: true, authorities: ['ROLE_ADMIN'] })
    const user = await state.store.login(credentials)
    assert.deepEqual(user, { username: 'ADMIN', roles: null, rolesVerified: false })
    const services = modules(state.store)
    for (const [allowed, run] of operations(services, { read: false, write: false, admin: false })) {
      const before = state.calls.length
      if (allowed) await run()
      else await assert.rejects(run(), error => /ROLES_UNAVAILABLE$/.test(error.code))
      assert.equal(state.calls.length - before, allowed ? 1 : 0)
    }
    for (const route of memberRoutes) assert.equal(authAccessDecision(state.store.getSnapshot(), route), 'roles-unavailable')
  })
}

test('profile role arrays are copied, immutable, and never sourced from other properties', async () => {
  const dto = { username: 'Exact.Name', roles: ['VIEWER', 'EDITOR'], authorities: ['ROLE_ADMIN'], claims: { roles: ['ADMIN'] } }
  const service = createAuthService({ request: async () => dto })
  const user = await service.getProfile('synthetic-token')
  dto.roles.push('ADMIN')
  assert.deepEqual(user.roles, ['VIEWER', 'EDITOR'])
  assert.equal(user.username, 'Exact.Name')
  assert.equal(Object.hasOwn(user, 'claims'), false)
  for (const roles of [Array(1), ['ADMIN', ...Array(1)]]) {
    const invalid = await createAuthService({ request: async () => ({ username: 'x', roles }) }).getProfile('synthetic')
    assert.equal(invalid.rolesVerified, false)
  }
})

test('refresh suspends role actions, replaces roles, and invalidates old services and their late responses', async t => {
  const state = fixture(t, { username: 'synthetic-profile', roles: ['ADMIN'] })
  await state.store.login(credentials)
  const old = modules(state.store), pendingRead = deferred(), pendingProfile = deferred()
  state.handle(url => url.endsWith('/auth/me') ? pendingProfile.promise : pendingRead.promise)
  const late = old.sources.list()
  const refresh = state.store.refreshProfile()
  assert.equal(authAccessDecision(state.store.getSnapshot(), memberRoutes[0]), 'pending')
  assert.equal(old.sources.permissions().write, false)
  const before = state.calls.length
  await assert.rejects(old.sources.create(sourceBody), { code: 'SOURCE_ROLES_UNAVAILABLE' })
  assert.equal(state.calls.length, before)
  pendingProfile.resolve(json({ username: 'synthetic-profile', roles: ['VIEWER'] }))
  await refresh
  pendingRead.resolve(json([source]))
  await assert.rejects(late, { code: 'AUTH_REQUIRED' })
  for (const service of Object.values(old)) assert.equal(service.isCurrent(), false)
  const current = modules(state.store)
  assert.equal(current.sources.permissions().read, true)
  assert.equal(current.sources.permissions().write, false)
  assert.equal(current.verification.permissions().create, false)
  assert.equal(current.conflicts.permissions().manage, false)
  assert.equal(current.audit.permissions().read, false)
})

for (const roles of [[], null, ['ADMIN', 'UNKNOWN'], ['ADMIN']]) {
  test(`active profile replacement ${JSON.stringify(roles)} updates permissions without changing token expiry`, async t => {
    const admin = roles?.length === 1 && roles[0] === 'ADMIN'
    const state = fixture(t, { username: 'profile', roles: admin ? ['VIEWER'] : ['ADMIN'] })
    await state.store.login(credentials)
    const old = state.store.getSnapshot()
    state.profile({ username: 'profile', roles })
    await state.store.refreshProfile()
    assert.notEqual(state.store.getSnapshot().user, old.user)
    assert.equal(state.store.getSnapshot().expiresAt, old.expiresAt)
    const services = modules(state.store)
    assert.equal(services.audit.permissions().read, admin)
    assert.equal(services.sources.permissions().write, admin)
    assert.equal(services.sources.permissions().read, admin)
    assert.equal(services.verification.permissions().read, true)
    assert.equal(services.conflicts.permissions().read, true)
  })
}

test('late ADMIN profile refresh cannot restore grants after logout and a new VIEWER session', async t => {
  const state = fixture(t, { username: 'old-admin', roles: ['ADMIN'] })
  await state.store.login(credentials)
  const pending = deferred()
  state.handle(() => pending.promise)
  const refresh = state.store.refreshProfile()
  state.store.logout()
  state.handle(null)
  state.profile({ username: 'new-viewer', roles: ['VIEWER'] })
  await state.store.login(credentials)
  pending.resolve(json({ username: 'old-admin', roles: ['ADMIN'] }))
  await assert.rejects(refresh)
  assert.deepEqual(state.store.getSnapshot().user, { username: 'new-viewer', roles: ['VIEWER'], rolesVerified: true })
  assert.equal(state.store.getSnapshot().checking, false)
  assert.equal(modules(state.store).audit.permissions().read, false)
})

for (const status of [401, 403]) for (const module of ['sources', 'verification', 'conflicts', 'audit']) {
  test(`${module} HTTP ${status} preserves session semantics with effective ADMIN`, async t => {
    const state = fixture(t, { username: 'profile', roles: ['ADMIN'] })
    await state.store.login(credentials)
    const services = modules(state.store)
    state.handle(() => json({ error: 'Synthetic denial' }, status))
    const run = { sources: () => services.sources.list(), verification: () => services.verification.list('42'),
      conflicts: () => services.conflicts.listForVillage('42'), audit: () => services.audit.search(auditQuery) }[module]
    await assert.rejects(run(), { status })
    assert.equal(state.store.getSnapshot().status, status === 401 ? 'anonymous' : 'authenticated')
    assert.equal(state.calls.length, 3)
    if (status === 403) assert.deepEqual(state.store.getSnapshot().user.roles, ['ADMIN'])
  })
}

for (const mode of ['anonymous', 'logout', 'expired', 'replacement']) {
  test(`${mode} cannot use any old Member 3 service or role-aware route`, async t => {
    const state = fixture(t, { username: 'profile', roles: ['ADMIN'] })
    if (mode !== 'anonymous') await state.store.login(credentials)
    const services = modules(state.store)
    if (mode === 'logout') state.store.logout()
    if (mode === 'replacement') { state.store.logout(); await state.store.login(credentials) }
    if (mode === 'expired') t.mock.method(Date, 'now', () => state.store.getSnapshot().expiresAt + 1)
    const before = state.calls.length
    for (const [, run] of operations(services, { read: false, write: false, admin: false, authenticated: false })) {
      await assert.rejects(run(), { code: 'AUTH_REQUIRED' })
    }
    assert.equal(state.calls.length, before)
    const session = mode === 'replacement' ? { status: 'anonymous' } : state.store.getSnapshot()
    for (const route of memberRoutes) assert.equal(authAccessDecision(session, route), 'login')
  })
}

test('effective roles and basic public capabilities are independent; catalogues/rich sections remain disabled and mock stays default', () => {
  assert.equal(effectiveRolesContractVerified, true)
  assert.equal(effectiveSourceRolesVerified, true)
  assert.deepEqual(publicVillageCapabilities, { browsing: true, identity: true, geographicCatalogues: false, richSections: false })
  assert.equal(villageDataMode, 'mock')
})

for (const failure of ['403', '503', 'network', 'invalid-json', 'invalid-profile']) {
  test(`failed profile refresh ${failure} retains identity but closes role actions and late protected responses`, async t => {
    const state = fixture(t, { username: 'profile', roles: ['ADMIN'] })
    await state.store.login(credentials)
    const previous = state.store.getSnapshot(), services = modules(state.store), pending = deferred()
    state.handle(url => {
      if (!url.endsWith('/auth/me')) return pending.promise
      if (failure === 'network') throw new TypeError('Synthetic offline')
      if (failure === 'invalid-json') return new Response('<html>Synthetic failure</html>')
      if (failure === 'invalid-profile') return json({ username: null, roles: ['ADMIN'] })
      return json({ error: 'Synthetic profile failure' }, Number(failure))
    })
    const late = services.sources.list()
    await assert.rejects(state.store.refreshProfile())
    const failed = state.store.getSnapshot()
    assert.equal(failed.status, 'authenticated')
    assert.equal(failed.user, previous.user)
    assert.equal(failed.expiresAt, previous.expiresAt)
    assert.equal(failed.rolesStale, true)
    assert.equal(failed.checking, false)
    assert.ok(failed.error)
    for (const route of memberRoutes) assert.equal(authAccessDecision(failed, route), 'roles-unavailable')
    pending.resolve(json([source]))
    await assert.rejects(late, { code: 'SOURCE_ROLES_UNAVAILABLE' })
    state.handle(null)
    for (const [allowed, run] of operations(services, { read: false, write: false, admin: false })) {
      const before = state.calls.length
      if (allowed) await run()
      else await assert.rejects(run(), error => /ROLES_UNAVAILABLE$/.test(error.code))
      assert.equal(state.calls.length - before, allowed ? 1 : 0)
    }
  })
}

test('cancelled retry cannot restore stale grants; successful refresh restores only current roles', async t => {
  const state = fixture(t, { username: 'profile', roles: ['ADMIN'] })
  await state.store.login(credentials)
  state.handle(() => { throw new TypeError('Synthetic offline') })
  await assert.rejects(state.store.refreshProfile())
  const pending = deferred(), controller = new AbortController(), old = modules(state.store)
  state.handle(() => pending.promise)
  const retry = state.store.refreshProfile({ signal: controller.signal })
  controller.abort()
  pending.resolve(json({ username: 'profile', roles: ['ADMIN'] }))
  await assert.rejects(retry)
  assert.equal(state.store.getSnapshot().rolesStale, true)
  assert.equal(old.audit.permissions().read, false)
  state.handle(null)
  state.profile({ username: 'profile', roles: ['VIEWER'] })
  await state.store.refreshProfile()
  assert.equal(state.store.getSnapshot().rolesStale, false)
  assert.equal(old.sources.isCurrent(), false)
  const current = modules(state.store)
  assert.equal(current.sources.permissions().read, true)
  assert.equal(current.sources.permissions().write, false)
  assert.equal(current.audit.permissions().read, false)
})

test('late failed refresh cannot mark a successfully replaced profile stale', async t => {
  const state = fixture(t, { username: 'profile', roles: ['ADMIN'] })
  await state.store.login(credentials)
  const pending = deferred()
  state.handle(() => pending.promise)
  const old = state.store.refreshProfile()
  state.handle(null)
  state.profile({ username: 'profile', roles: ['VIEWER'] })
  await state.store.refreshProfile()
  pending.resolve(json({ error: 'Synthetic obsolete failure' }, 403))
  await assert.rejects(old)
  assert.equal(state.store.getSnapshot().rolesStale, false)
  assert.equal(state.store.getSnapshot().error, null)
  assert.deepEqual(state.store.getSnapshot().user.roles, ['VIEWER'])
})
