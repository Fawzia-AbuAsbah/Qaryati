import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createVillageDetailsService, villageDetailsService } from './villageDetailsService.js'
import { createLatestExplorerRequest } from './explorerRequest.js'
import { publicVillageContractsVerified } from './villageMode.js'

const village = (id = 42, extra = {}) => ({ id, governorateId: 2, nameAr: 'بتير', latitude: null, longitude: null, status: 'VERIFIED', ...extra })
const row = (villageId = 42) => ({ id: 7, villageId, sourceIds: [3], verificationStatus: 'DRAFT' })
const json = (dto, status = 200) => new Response(JSON.stringify(dto), { status })
const envelope = (page = 0, size = 5, total = 1, content = [row()]) => ({ content, page, size, totalElements: total, totalPages: Math.ceil(total / size) })
const sectionPaths = { overviews: '/api/v1/village-historical-overviews', events: '/api/v1/historical-events', culture: '/api/v1/cultural-heritage', sites: '/api/v1/heritage-sites' }

function fixture(t, handler = () => json(village()), options = {}) {
  const fetchImpl = t.mock.fn((url, init) => handler(new URL(url, 'https://frontend.example.test'), init))
  const mockGet = t.mock.fn(() => { throw new Error('Do not fall back to mock data') })
  const service = createVillageDetailsService({ mode: 'api', publicationVerified: true, request: createApiClient({ fetchImpl }), mockGet, ...options })
  return { service, fetchImpl, mockGet }
}

test('Details shares mock default and the still-closed source gate', () => {
  assert.equal(villageDetailsService.mode, 'mock')
  assert.equal(publicVillageContractsVerified, false)
})

for (const publicationVerified of [undefined, false, 'true', 'false']) {
  test(`closed Details gate ${String(publicationVerified)} prevents identity, catalog and every section request`, async t => {
    const { service, fetchImpl, mockGet } = fixture(t, undefined, { publicationVerified })
    assert.equal(service.available, false)
    await assert.rejects(service.getVillage('42'), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
    await assert.rejects(service.getGovernorates(), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
    for (const section of Object.keys(sectionPaths)) {
      await assert.rejects(service.listSection('42', section), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
    }
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

test('numeric identity endpoint and governorate mapping use the existing client and catalog adapter', async t => {
  const controller = new AbortController()
  const { service, fetchImpl } = fixture(t, (url, init) => {
    assert.equal(init.signal, controller.signal)
    assert.equal(init.method, 'GET')
    assert.equal(init.credentials, 'omit')
    assert.equal(init.headers.has('Authorization'), false)
    assert.equal(init.body, undefined)
    assert.equal(url.search, '')
    return json(url.pathname === '/api/v1/governorates' ? [{ id: 2, nameAr: 'بيت لحم' }] : village())
  })
  const identity = await service.getVillage('42', { signal: controller.signal })
  const catalog = await service.getGovernorates({ signal: controller.signal })
  assert.equal(fetchImpl.mock.calls[0].arguments[0], '/api/v1/villages/42')
  assert.equal(fetchImpl.mock.calls[1].arguments[0], '/api/v1/governorates')
  assert.equal(identity.id, '42')
  assert.equal(catalog.namesById.get(identity.governorateId), 'بيت لحم')
  assert.equal(identity.coordinates, null)
})

for (const id of ['', '0', '-1', '01', '1.5', '1e2', 'battir', '42/other', ' 42', '9007199254740992', null, 42]) {
  test(`invalid API route ID ${String(id)} is rejected before HTTP`, async t => {
    const { service, fetchImpl } = fixture(t)
    await assert.rejects(service.getVillage(id), { code: 'INVALID_VILLAGE_ID' })
    await assert.rejects(service.listSection(id, 'events'), { code: 'INVALID_VILLAGE_ID' })
    assert.equal(fetchImpl.mock.callCount(), 0)
  })
}

test('404 identity is missing, while malformed identity or a mismatched ID is an error', async t => {
  const { service, fetchImpl, mockGet } = fixture(t, () => new Response('', { status: 404 }))
  assert.equal(await service.getVillage('42'), null)
  assert.equal(fetchImpl.mock.callCount(), 1)
  assert.equal(mockGet.mock.callCount(), 0)
  for (const dto of [null, {}, village(43), village(42, { latitude: '31' })]) {
    const malformed = fixture(t, () => json(dto))
    await assert.rejects(malformed.service.getVillage('42'), TypeError)
  }
})

test('nullable identity and missing governorates do not invent coordinates or names', async t => {
  const { service } = fixture(t, url => json(url.pathname.endsWith('/governorates') ? [] : village(42, { nameAr: null, governorateId: 999, latitude: null, longitude: 35 })))
  const identity = await service.getVillage('42')
  const catalog = await service.getGovernorates()
  assert.equal(identity.name, null)
  assert.equal(identity.coordinates, null)
  assert.equal(identity.latitude, null)
  assert.equal(identity.longitude, 35)
  assert.equal(catalog.namesById.has(999), false)
  assert.equal(identity.governorate, null)
})

for (const [section, path] of Object.entries(sectionPaths)) {
  test(`${section} sends exact village filter and zero-based page, preserving domain records`, async t => {
    const controller = new AbortController()
    const { service, fetchImpl } = fixture(t, (url, init) => {
      assert.equal(url.pathname, path)
      assert.deepEqual(Object.fromEntries(url.searchParams), { villageId: '42', page: '1', size: '5' })
      assert.equal(init.signal, controller.signal)
      assert.equal(init.method, 'GET')
      return json(envelope(1, 5, 6))
    })
    const result = await service.listSection('42', section, { page: 2, signal: controller.signal })
    assert.equal(result.page, 2)
    assert.equal(result.items[0].villageId, 42)
    assert.equal(result.items[0].verificationStatus, 'DRAFT')
    assert.deepEqual(result.items[0].sourceIds, [3])
    assert.equal(fetchImpl.mock.callCount(), 1)
  })
}

test('section pagination fetches the actual last page while preserving villageId', async t => {
  const pages = []
  const { service } = fixture(t, url => {
    assert.equal(url.pathname, sectionPaths.events)
    assert.equal(url.searchParams.get('villageId'), '42')
    const page = Number(url.searchParams.get('page'))
    pages.push(page)
    return json(envelope(page, 5, 6, page === 1 ? [row()] : []))
  })
  const result = await service.listSection('42', 'events', { page: 9 })
  assert.deepEqual(pages, [8, 1])
  assert.equal(result.page, 2)
  assert.equal(result.items.length, 1)
})

test('out-of-range empty dataset is fetched at page zero and stays distinct from failures', async t => {
  const pages = []
  const { service } = fixture(t, url => {
    const page = Number(url.searchParams.get('page')); pages.push(page)
    return json(envelope(page, 5, 0, []))
  })
  const result = await service.listSection('42', 'culture', { page: 9 })
  assert.deepEqual(pages, [8, 0])
  assert.equal(result.page, 1)
  assert.equal(result.totalPages, 0)
  assert.deepEqual(result.items, [])
})

test('repeated section shrinkage is bounded without falsely clamping a page', async t => {
  let count = 0
  const { service, fetchImpl } = fixture(t, url => json(envelope(Number(url.searchParams.get('page')), 3, [7, 4, 1][count++], [])))
  await assert.rejects(service.listSection('42', 'events', { page: 9, pageSize: 3 }), { code: 'PAGINATION_CHANGED' })
  assert.equal(fetchImpl.mock.callCount(), 3)
})

for (const options of [{ page: 0 }, { page: -1 }, { page: 1.5 }, { page: Number.MAX_SAFE_INTEGER }, { pageSize: 0 }, { pageSize: 101 }, { pageSize: '5' }]) {
  test(`invalid section pagination ${JSON.stringify(options)} fails before HTTP`, async t => {
    const { service, fetchImpl } = fixture(t)
    await assert.rejects(service.listSection('42', 'events', options), TypeError)
    assert.equal(fetchImpl.mock.callCount(), 0)
  })
}

for (const section of ['population', 'agriculture', 'community', 'not-an-endpoint', '__proto__']) {
  test(`unresolved section ${section} never downloads unrelated or unapproved records`, async t => {
    const { service, fetchImpl } = fixture(t)
    await assert.rejects(service.listSection('42', section), { code: 'UNAVAILABLE_SECTION' })
    assert.equal(fetchImpl.mock.callCount(), 0)
  })
}

test('independent catalog/section failures leave village identity and successful sections usable', async t => {
  const { service, mockGet } = fixture(t, url => {
    if (url.pathname.endsWith('/villages/42')) return json(village())
    if (url.pathname.endsWith('/governorates')) return new Response('', { status: 503 })
    if (url.pathname === sectionPaths.events) return new Response('', { status: 403 })
    return json(envelope())
  })
  const identity = await service.getVillage('42')
  const [catalog, overview, events, culture, sites] = await Promise.allSettled([
    service.getGovernorates(), service.listSection('42', 'overviews'), service.listSection('42', 'events'),
    service.listSection('42', 'culture'), service.listSection('42', 'sites'),
  ])
  assert.equal(identity.name, 'بتير')
  assert.equal(catalog.status, 'rejected'); assert.equal(catalog.reason.status, 503)
  assert.equal(events.status, 'rejected'); assert.equal(events.reason.status, 403)
  for (const result of [overview, culture, sites]) {
    assert.equal(result.status, 'fulfilled')
    assert.equal(result.value.items.length, 1)
  }
  assert.equal(mockGet.mock.callCount(), 0)
})

for (const status of [401, 403, 404, 429, 500]) {
  test(`section HTTP ${status} remains an error, not empty or mock data`, async t => {
    const { service, fetchImpl, mockGet } = fixture(t, () => new Response('', { status }))
    await assert.rejects(service.listSection('42', 'events'), { kind: 'http', status })
    if (status !== 404) await assert.rejects(service.getVillage('42'), { kind: 'http', status })
    assert.equal(fetchImpl.mock.callCount(), status === 404 ? 1 : 2)
    assert.equal(mockGet.mock.callCount(), 0)
  })
}

for (const dto of [null, { content: [] }, envelope(1, 5, 6), envelope(0, 3), envelope(0, 5, 1, [row(43)]),
  envelope(0, 5, 1, [{ ...row(), verificationStatus: 'PUBLISHED' }])]) {
  test('malformed/mismatched section responses fail as a whole, without selective client filtering', async t => {
    const { service } = fixture(t, () => json(dto))
    await assert.rejects(service.listSection('42', 'events'), TypeError)
  })
}

test('network and HTML fallback errors preserve failure classification', async t => {
  const cause = new TypeError('Failed to fetch')
  const { service, mockGet } = fixture(t, () => { throw cause })
  await assert.rejects(service.getVillage('42'), { kind: 'network', cause })
  await assert.rejects(service.listSection('42', 'sites'), { kind: 'network', cause })
  const html = fixture(t, () => new Response('<html>SPA fallback</html>'))
  await assert.rejects(html.service.listSection('42', 'events'), { kind: 'malformed-response' })
  assert.equal(mockGet.mock.callCount(), 0)
})

test('cancellation before loading or between a response and correction prevents further reads', async t => {
  const controller = new AbortController()
  const { service, fetchImpl } = fixture(t, () => {
    const response = json(envelope(8, 5, 1, []))
    const read = response.text.bind(response)
    response.text = async () => { const text = await read(); controller.abort(); return text }
    return response
  })
  await assert.rejects(service.listSection('42', 'events', { page: 9, signal: controller.signal }), error => error === controller.signal.reason)
  await assert.rejects(service.getVillage('42', { signal: controller.signal }), error => error === controller.signal.reason)
  assert.equal(fetchImpl.mock.callCount(), 1)
})

for (const kind of ['identity', 'section']) {
  test(`navigating between villages cancels stale ${kind} even if HTTP ignores abort`, async t => {
    let finishOld, started
    const oldStarted = new Promise(resolve => { started = resolve })
    const signals = []
    const { service } = fixture(t, (url, { signal }) => {
      signals.push(signal)
      const id = kind === 'identity' ? Number(url.pathname.split('/').at(-1)) : Number(url.searchParams.get('villageId'))
      const response = () => json(kind === 'identity' ? village(id) : envelope(0, 5, 1, [row(id)]))
      if (id === 42) return new Promise(resolve => { finishOld = () => resolve(response()); started() })
      return response()
    })
    const runner = createLatestExplorerRequest()
    const successes = [], errors = []
    const handlers = { onSuccess: result => successes.push(result), onError: error => errors.push(error) }
    const load = id => signal => kind === 'identity' ? service.getVillage(id, { signal }) : service.listSection(id, 'events', { signal })
    const first = runner.run(load('42'), handlers)
    await oldStarted
    const second = runner.run(load('43'), handlers)
    await second.done
    finishOld(); await first.done
    assert.equal(signals[0].aborted, true)
    assert.equal(successes.length, 1)
    assert.equal(kind === 'identity' ? successes[0].backendId : successes[0].items[0].villageId, 43)
    assert.deepEqual(errors, [])
  })
}

test('default mock Details retains slug identities, all mock sections and missing-village behavior without HTTP', async t => {
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
