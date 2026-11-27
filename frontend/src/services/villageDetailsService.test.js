import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createVillageDetailsService, villageDetailsService } from './villageDetailsService.js'
import { createLatestExplorerRequest } from './explorerRequest.js'
import { publicVillageCapabilities } from './villageMode.js'

const village = (id = 42, extra = {}) => ({ id, governorateId: 2, localAuthorityId: null, nameAr: 'بتير', nameEn: null,
  latitude: null, longitude: null, nameOrigin: null, locationDescription: 'Public village location', elevationM: null, areaKm2: null, ...extra })
const json = (dto, status = 200) => new Response(JSON.stringify(dto), { status })
function fixture(t, handler = () => json(village()), options = {}) {
  const fetchImpl = t.mock.fn((url, init) => {
    const parsed = new URL(url, 'https://frontend.example.test')
    assert.match(parsed.pathname, /^\/api\/v1\/public\/villages\/[1-9]\d*$/)
    return handler(parsed, init)
  })
  const mockGet = t.mock.fn(() => { throw new Error('Do not fall back to mock data') })
  const service = createVillageDetailsService({ mode: 'api', request: createApiClient({ fetchImpl }), mockGet, ...options })
  return { service, fetchImpl, mockGet }
}

test('Details defaults to mock while only verified basic public capabilities are enabled', () => {
  assert.equal(villageDetailsService.mode, 'mock')
  assert.deepEqual(publicVillageCapabilities, { browsing: true, identity: true, geographicCatalogues: false, richSections: false })
  assert.equal(Object.isFrozen(publicVillageCapabilities), true)
})

for (const identityVerified of [null, false, 'true', 'false']) {
  test(`disabled identity capability ${String(identityVerified)} prevents every HTTP request`, async t => {
    const { service, fetchImpl, mockGet } = fixture(t, undefined, { identityVerified })
    assert.equal(service.available, false)
    await assert.rejects(service.getVillage('42'), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
    await assert.rejects(service.getGovernorates(), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
    await assert.rejects(service.listSection('42', 'events'), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
    assert.equal(fetchImpl.mock.callCount(), 0)
    assert.equal(mockGet.mock.callCount(), 0)
  })
}
test('invalid mode is explicit without requests or mock fallback', async t => {
  const { service, fetchImpl, mockGet } = fixture(t, undefined, { mode: 'bad' })
  await assert.rejects(service.getVillage('42'), { code: 'INVALID_MODE' })
  assert.equal(fetchImpl.mock.callCount(), 0)
  assert.equal(mockGet.mock.callCount(), 0)
})
test('basic identity uses exactly the public endpoint without query, Bearer token or cookies', async t => {
  const controller = new AbortController()
  const { service, fetchImpl } = fixture(t, (url, init) => {
    assert.equal(url.pathname, '/api/v1/public/villages/42')
    assert.equal(url.search, '')
    assert.equal(init.signal, controller.signal)
    assert.equal(init.method, 'GET')
    assert.equal(init.credentials, 'omit')
    assert.equal(init.headers.has('Authorization'), false)
    assert.equal(init.body, undefined)
    return json(village())
  })
  const identity = await service.getVillage('42', { signal: controller.signal })
  assert.equal(identity.id, '42')
  assert.equal(identity.backendId, 42)
  assert.equal(identity.name, 'بتير')
  assert.equal(identity.coordinates, null)
  assert.equal(fetchImpl.mock.callCount(), 1)
})
for (const elevationM of [-430.5, 0, 700.25]) {
  test(`eleven-field public detail preserves descriptive fields and elevation ${elevationM}`, async t => {
    const dto = village(42, { nameOrigin: 'Public name origin', locationDescription: 'Public location description',
      latitude: 31.9, longitude: 35.2, elevationM, areaKm2: 1.125 })
    const { service, fetchImpl, mockGet } = fixture(t, () => json(dto))
    const detail = await service.getVillage('42')
    assert.equal(detail.id, '42')
    assert.equal(detail.backendId, 42)
    for (const field of ['nameOrigin', 'locationDescription', 'elevationM', 'areaKm2']) assert.equal(detail[field], dto[field])
    assert.deepEqual(detail.coordinates, [31.9, 35.2])
    assert.equal(fetchImpl.mock.callCount(), 1)
    assert.equal(mockGet.mock.callCount(), 0)
  })
}

test('seven-field collection responses cannot stand in for the eleven-field detail contract', async t => {
  const collectionDto = village()
  for (const field of ['nameOrigin', 'locationDescription', 'elevationM', 'areaKm2']) delete collectionDto[field]
  const { service, fetchImpl, mockGet } = fixture(t, () => json(collectionDto))
  await assert.rejects(service.getVillage('42'), TypeError)
  assert.equal(fetchImpl.mock.callCount(), 1)
  assert.equal(mockGet.mock.callCount(), 0)
})

for (const field of ['status', 'createdAt', 'archivedAt', 'sources', 'sourceIds', 'notes', 'auditLogs']) {
  test(`editorial field ${field} cannot expand the public detail contract`, async t => {
    const { service, fetchImpl, mockGet } = fixture(t, () => json(village(42, { [field]: null })))
    await assert.rejects(service.getVillage('42'), TypeError)
    assert.equal(fetchImpl.mock.callCount(), 1)
    assert.equal(mockGet.mock.callCount(), 0)
  })
}
for (const id of ['', '0', '-1', '01', '1.5', '1e2', 'battir', '42/other', ' 42', '9007199254740992', null, 42]) {
  test(`invalid API route ID ${String(id)} is rejected before HTTP`, async t => {
    const { service, fetchImpl } = fixture(t)
    await assert.rejects(service.getVillage(id), { code: 'INVALID_VILLAGE_ID' })
    await assert.rejects(service.listSection(id, 'events'), { code: 'INVALID_VILLAGE_ID' })
    assert.equal(fetchImpl.mock.callCount(), 0)
  })
}
for (const options of [{ token: 'secret' }, { headers: { Authorization: 'Bearer secret' } }, { query: { status: 'DRAFT' } }, { status: 'VERIFIED' }]) {
  test(`public detail rejects authentication or unsupported query options: ${Object.keys(options)}`, async t => {
    const { service, fetchImpl } = fixture(t)
    await assert.rejects(service.getVillage('42', options), TypeError)
    assert.equal(fetchImpl.mock.callCount(), 0)
  })
}
for (const explanation of ['hidden', 'archived', 'nonexistent']) {
  test(`404 ${explanation} identity remains unavailable without protected or mock fallback`, async t => {
    const { service, fetchImpl, mockGet } = fixture(t, () => new Response('', { status: 404 }))
    assert.equal(await service.getVillage('42'), null)
    assert.equal(fetchImpl.mock.callCount(), 1)
    assert.equal(mockGet.mock.callCount(), 0)
  })
}
for (const dto of [null, {}, village(43), village(42, { latitude: '31' }), village(42, { nameAr: null }), village(42, { status: 'VERIFIED' })]) {
  test('malformed, expanded or mismatched identity fails explicitly', async t => {
    const { service, mockGet } = fixture(t, () => json(dto))
    await assert.rejects(service.getVillage('42'), TypeError)
    assert.equal(mockGet.mock.callCount(), 0)
  })
}
test('nullable fields and partial coordinates stay missing without invented names or metadata', async t => {
  const { service } = fixture(t, () => json(village(42, { governorateId: 999, latitude: null, longitude: 35 })))
  const identity = await service.getVillage('42')
  assert.equal(identity.nameEn, null)
  assert.equal(identity.localAuthorityId, null)
  assert.equal(identity.coordinates, null)
  assert.equal(identity.latitude, null)
  assert.equal(identity.longitude, 35)
  assert.equal(identity.nameOrigin, null)
  assert.equal(identity.locationDescription, 'Public village location')
  assert.equal(identity.elevationM, null)
  assert.equal(identity.areaKm2, null)
  for (const field of ['governorate', 'localAuthority', 'description', 'status', 'population', 'history', 'images']) assert.equal(Object.hasOwn(identity, field), false)
})
test('basic identity availability never authorizes catalogue reads', async t => {
  const { service, fetchImpl } = fixture(t)
  await assert.rejects(service.getGovernorates(), { code: 'PUBLIC_CATALOGUE_UNAVAILABLE' })
  assert.equal(fetchImpl.mock.callCount(), 0)
  assert.equal((await service.getVillage('42')).backendId, 42)
  assert.equal(fetchImpl.mock.callCount(), 1)
})
for (const section of ['overviews', 'events', 'culture', 'sites', 'population', 'agriculture', 'community', 'economicActivities', 'not-an-endpoint', '__proto__']) {
  test(`unsupported public section ${section} never downloads protected or unapproved records`, async t => {
    const { service, fetchImpl } = fixture(t)
    await assert.rejects(service.listSection('42', section), { code: 'UNAVAILABLE_SECTION' })
    assert.equal(fetchImpl.mock.callCount(), 0)
  })
}
for (const status of [400, 401, 403, 429, 500]) {
  test(`identity HTTP ${status} remains an error without automatic retry or mock fallback`, async t => {
    const { service, fetchImpl, mockGet } = fixture(t, () => new Response('', { status }))
    await assert.rejects(service.getVillage('42'), { kind: 'http', status })
    assert.equal(fetchImpl.mock.callCount(), 1)
    assert.equal(mockGet.mock.callCount(), 0)
  })
}
test('network and HTML fallback errors preserve failure classification', async t => {
  const cause = new TypeError('Failed to fetch')
  const { service, mockGet } = fixture(t, () => { throw cause })
  await assert.rejects(service.getVillage('42'), { kind: 'network', cause })
  const html = fixture(t, () => new Response('<html>SPA fallback</html>'))
  await assert.rejects(html.service.getVillage('42'), { kind: 'malformed-response' })
  assert.equal(mockGet.mock.callCount(), 0)
})
test('cancellation before loading or while reading the response suppresses data and further reads', async t => {
  const controller = new AbortController()
  const { service, fetchImpl } = fixture(t, () => {
    const response = json(village())
    const read = response.text.bind(response)
    response.text = async () => { const text = await read(); controller.abort(); return text }
    return response
  })
  await assert.rejects(service.getVillage('42', { signal: controller.signal }), error => error === controller.signal.reason)
  await assert.rejects(service.getVillage('43', { signal: controller.signal }), error => error === controller.signal.reason)
  assert.equal(fetchImpl.mock.callCount(), 1)
})
test('navigating between villages suppresses late public identity even when HTTP ignores abort', async t => {
  let finishOld, started
  const oldStarted = new Promise(resolve => { started = resolve })
  const signals = []
  const { service } = fixture(t, (url, { signal }) => {
    signals.push(signal)
    const id = Number(url.pathname.split('/').at(-1))
    if (id === 42) return new Promise(resolve => { finishOld = () => resolve(json(village(id))); started() })
    return json(village(id))
  })
  const runner = createLatestExplorerRequest(), successes = [], errors = []
  const handlers = { onSuccess: result => successes.push(result), onError: error => errors.push(error) }
  const first = runner.run(signal => service.getVillage('42', { signal }), handlers)
  await oldStarted
  await runner.run(signal => service.getVillage('43', { signal }), handlers).done
  finishOld(); await first.done
  assert.equal(signals[0].aborted, true)
  assert.deepEqual(successes.map(result => result.backendId), [43])
  assert.deepEqual(errors, [])
})
test('default mock Details retains slug identities, mock sections and missing-village behavior without HTTP', async t => {
  const request = t.mock.fn(() => { throw new Error('Mock mode must not use HTTP') })
  const service = createVillageDetailsService({ request })
  const battir = await service.getVillage('battir')
  assert.equal(battir.id, 'battir')
  assert.equal(battir.name, 'بتير')
  assert.equal(battir.contentStatus, 'demo')
  assert.equal(battir.population, null)
  assert.match(battir.overview, /تجريبي/)
  assert.equal((await service.getVillage('beita')).coordinates, null)
  assert.equal(await service.getVillage('missing'), null)
  assert.equal(await service.getVillage('42'), null)
  assert.equal(request.mock.callCount(), 0)
})
