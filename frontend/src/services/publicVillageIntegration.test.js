import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createAuthService } from './authService.js'
import { createAuthSession } from './authSession.js'
import { createVillageExplorerService } from './villageExplorerService.js'
import { createVillageDetailsService } from './villageDetailsService.js'
import { createSourcesService } from './sourcesService.js'
import { readExplorerQuery } from './explorerQuery.js'

const dto = { id: 42, nameAr: 'Synthetic village', nameEn: null, governorateId: 2, localAuthorityId: null, latitude: 31, longitude: 35 }
const page = { content: [dto], number: 0, size: 6, totalElements: 1, totalPages: 1, numberOfElements: 1,
  first: true, last: true, hasNext: false, hasPrevious: false, empty: false }
const json = value => new Response(JSON.stringify(value))

for (const roles of [['VIEWER'], ['EDITOR'], ['ADMIN'], ['VIEWER', 'ADMIN'], []]) {
  test(`public requests remain token-free before login, during ${roles.join('/') || 'empty roles'}, and after logout`, async t => {
    const calls = []
    let publicStatus = 200
    const client = createApiClient({ fetchImpl: async (url, init) => {
      calls.push({ url, init })
      if (url === '/api/v1/auth/login') return json({ accessToken: 'simulated-token', tokenType: 'Bearer', expiresIn: 600 })
      if (url === '/api/v1/auth/me') {
        assert.equal(init.headers.get('Authorization'), 'Bearer simulated-token')
        return json({ username: 'synthetic', roles })
      }
      assert.ok(url.startsWith('/api/v1/public/villages'))
      assert.equal(init.headers.has('Authorization'), false)
      assert.equal(init.credentials, 'omit')
      if (publicStatus !== 200) return new Response('', { status: publicStatus })
      return json(url.includes('/42') ? dto : page)
    } })
    const store = createAuthSession({ service: createAuthService({ request: client }) })
    t.after(() => store.dispose())
    const listing = createVillageExplorerService({ mode: 'api', request: client })
    const details = createVillageDetailsService({ mode: 'api', request: client })
    await listing.listVillages()
    await store.login({ username: 'synthetic', password: 'simulated-password' })
    assert.equal((await details.getVillage('42')).backendId, 42)
    await listing.listVillages()
    publicStatus = 401
    await assert.rejects(details.getVillage('42'), { status: 401 })
    assert.equal(store.getSnapshot().status, 'authenticated') // Public transport does not alter the private session.
    store.logout()
    publicStatus = 200
    await details.getVillage('42')
    assert.equal(calls.filter(call => call.url.includes('/public/')).length, 5)
  })
}

test('public identity availability never grants source permission to anonymous, empty-role or revoked profiles', async t => {
  let currentRoles = ['ADMIN']
  const calls = []
  const client = createApiClient({ fetchImpl: async (url, init) => {
    calls.push({ url, init })
    if (url.endsWith('/login')) return json({ accessToken: 'simulated-token', tokenType: 'Bearer', expiresIn: 600 })
    if (url.endsWith('/auth/me')) return json({ username: 'synthetic', roles: currentRoles })
    if (url === '/api/v1/public/villages/42') return json(dto)
    assert.fail(`Denied source operation sent HTTP: ${url}`)
  } })
  const store = createAuthSession({ service: createAuthService({ request: client }) })
  t.after(() => store.dispose())
  assert.equal((await createVillageDetailsService({ mode: 'api', request: client }).getVillage('42')).backendId, 42)
  const options = { request: store.request, getSession: store.getSnapshot }
  const anonymous = createSourcesService(options)
  await assert.rejects(anonymous.villageSources('42'))
  await store.login({ username: 'synthetic', password: 'simulated-password' })
  const old = createSourcesService(options)
  currentRoles = []
  await store.refreshProfile()
  const revoked = createSourcesService(options), before = calls.length
  await assert.rejects(old.villageSources('42'))
  await assert.rejects(revoked.villageSources('42'))
  await assert.rejects(revoked.link('42', '12', null))
  store.logout()
  await assert.rejects(old.villageSources('42'))
  assert.equal(calls.length, before)
})

test('basic browse and identity switches are independent', async t => {
  const calls = []
  const request = t.mock.fn(async path => { calls.push(path); return path.endsWith('/42') ? dto : page })
  const browse = createVillageExplorerService({ mode: 'api', browsingVerified: true, request })
  const noIdentity = createVillageDetailsService({ mode: 'api', identityVerified: false, request })
  await browse.listVillages()
  await assert.rejects(noIdentity.getVillage('42'), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
  const noBrowse = createVillageExplorerService({ mode: 'api', browsingVerified: false, request })
  const identity = createVillageDetailsService({ mode: 'api', identityVerified: true, request })
  await identity.getVillage('42')
  await assert.rejects(noBrowse.listVillages(), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
  await assert.rejects(identity.getGovernorates(), { code: 'PUBLIC_CATALOGUE_UNAVAILABLE' })
  await assert.rejects(identity.listSection('42', 'population'), { code: 'UNAVAILABLE_SECTION' })
  assert.deepEqual(calls, ['/public/villages', '/public/villages/42'])
})

for (const sortBy of ['createdAt', 'updatedAt']) {
  test(`obsolete URL sort ${sortBy} and editorial query keys never reach the public endpoint`, async t => {
    const request = t.mock.fn(async (path, options) => {
      assert.equal(path, '/public/villages')
      assert.equal(options.query.sortBy, 'id')
      assert.equal(options.query.governorateId, 2)
      assert.equal(options.query.localAuthorityId, 7)
      for (const field of ['status', 'createdAt', 'updatedAt', 'population']) assert.equal(Object.hasOwn(options.query, field), false)
      return page
    })
    const options = readExplorerQuery(new URLSearchParams({ sortBy, status: 'DRAFT', createdAt: 'x', updatedAt: 'y', governorateId: '2', localAuthorityId: '7' }))
    await createVillageExplorerService({ mode: 'api', request }).listVillages(options)
    assert.equal(request.mock.callCount(), 1)
  })
}

for (const [query, expected] of [['\u001c abc \u3000', 'abc'], ['\u00a0abc\u00a0', '\u00a0abc\u00a0'], ['100%_!', '100%_!']]) {
  test(`public search preserves backend literal whitespace and wildcard semantics ${JSON.stringify(query)}`, async () => {
    const service = createVillageExplorerService({ mode: 'api', request: async (_path, { query: params }) => {
      assert.equal(params.search, expected)
      return page
    } })
    await service.listVillages({ query })
  })
}
