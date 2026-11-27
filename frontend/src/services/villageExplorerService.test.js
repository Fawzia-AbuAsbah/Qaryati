import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createVillageExplorerService, explorerErrorMessage, villageExplorerService, villageSortFields } from './villageExplorerService.js'

const village = (id = 42, overrides = {}) => ({ id, governorateId: 2, localAuthorityId: null, nameAr: 'بتير', nameEn: null, latitude: null, longitude: null, ...overrides })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status })

function page(number = 0, size = 6, total = 1, content = [village()]) {
  const totalPages = Math.ceil(total / size)
  return {
    content, number, size, totalElements: total, totalPages, numberOfElements: content.length,
    first: number === 0, last: number + 1 >= totalPages, hasNext: number + 1 < totalPages,
    hasPrevious: number > 0, empty: content.length === 0,
  }
}

function fixture(t, handler = () => json(page()), overrides = {}) {
  const fetchImpl = t.mock.fn((url, init) => {
    const parsed = new URL(url, 'https://frontend.example.test')
    assert.equal(parsed.pathname, '/api/v1/public/villages')
    return handler(parsed, init)
  })
  const mockList = t.mock.fn(() => { throw new Error('API failures must not reach mock data') })
  const service = createVillageExplorerService({
    mode: 'api', browsingVerified: true, request: createApiClient({ fetchImpl }), mockList, ...overrides,
  })
  return { service, fetchImpl, mockList }
}

test('the application defaults to mock mode even with basic public browsing available', () => {
  assert.equal(villageExplorerService.mode, 'mock')
  assert.equal(villageExplorerService.available, true)
})

for (const browsingVerified of [null, false, 'true', 'false']) {
  test(`disabled browsing capability blocks HTTP: ${String(browsingVerified)}`, async t => {
    const { service, fetchImpl, mockList } = fixture(t, undefined, { browsingVerified })
    assert.equal(service.available, false)
    await assert.rejects(service.listVillages(), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
    await assert.rejects(service.listGovernorates(), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
    assert.equal(fetchImpl.mock.callCount(), 0)
    assert.equal(mockList.mock.callCount(), 0)
  })
}

test('invalid mode fails explicitly without choosing mock data or sending HTTP', async t => {
  const { service, fetchImpl, mockList } = fixture(t, undefined, { mode: 'invalid' })
  assert.equal(service.available, false)
  await assert.rejects(service.listVillages(), { code: 'INVALID_MODE' })
  assert.equal(fetchImpl.mock.callCount(), 0)
  assert.equal(mockList.mock.callCount(), 0)
})

test('mock selection retains the existing service contract without requesting HTTP', async t => {
  const controller = new AbortController()
  const options = { query: 'بتير', governorate: 'بيت لحم', page: 1, signal: controller.signal }
  const mockResult = { items: [{ id: 'battir', name: 'بتير' }], total: 1, page: 1, totalPages: 1, governorates: ['بيت لحم'] }
  const mockList = t.mock.fn(async received => { assert.equal(received, options); return mockResult })
  const { service, fetchImpl } = fixture(t, undefined, { mode: 'mock', mockList })
  const result = await service.listVillages(options)
  assert.deepEqual(result, { ...mockResult, governorates: [{ id: 'بيت لحم', name: 'بيت لحم' }] })
  assert.deepEqual(mockResult.governorates, ['بيت لحم'])
  assert.equal(fetchImpl.mock.callCount(), 0)
})

test('confirmed public catalogues use anonymous GET requests with the exact optional governorate filter', async t => {
  const calls = []
  const service = createVillageExplorerService({ mode: 'api', request: createApiClient({ fetchImpl: (url, init) => {
    const parsed = new URL(url, 'https://frontend.example.test')
    calls.push({ parsed, init })
    assert.equal(init.method, 'GET')
    assert.equal(init.credentials, 'omit')
    assert.equal(init.headers.has('Authorization'), false)
    if (parsed.pathname === '/api/v1/public/governorates') return json([{ id: 2, nameAr: 'Bethlehem', nameEn: null }])
    if (parsed.pathname === '/api/v1/public/local-authorities') return json([{ id: 7, nameAr: 'Council', nameEn: null, governorateId: 2 }])
    assert.fail(`Unexpected public endpoint: ${parsed.pathname}`)
  } }) })
  assert.deepEqual(await service.listGovernorates(), [{ id: '2', backendId: 2, nameAr: 'Bethlehem', nameEn: null, name: 'Bethlehem' }])
  assert.deepEqual(await service.listLocalAuthorities({ governorateId: '2' }), [{ id: '7', backendId: 7, nameAr: 'Council', nameEn: null, name: 'Council', governorateId: 2 }])
  assert.deepEqual(calls.map(({ parsed }) => [parsed.pathname, parsed.search]), [
    ['/api/v1/public/governorates', ''], ['/api/v1/public/local-authorities', '?governorateId=2'],
  ])
})

for (const governorateId of ['0', '-1', '02', 'authority', '9007199254740992']) {
  test(`invalid local-authority governorate filter ${governorateId} is rejected before HTTP`, async t => {
    const request = t.mock.fn(() => assert.fail('Invalid catalogue filters must not send HTTP'))
    const service = createVillageExplorerService({ mode: 'api', request })
    await assert.rejects(service.listLocalAuthorities({ governorateId }), TypeError)
    assert.equal(request.mock.callCount(), 0)
  })
}

test('HTTP integration encodes Arabic search, ID filters, sorting and zero-based pagination', async t => {
  const controller = new AbortController()
  const { service, fetchImpl, mockList } = fixture(t, (url, init) => {
    assert.equal(init.signal, controller.signal)
    assert.equal(init.method, 'GET')
    assert.equal(init.credentials, 'omit')
    assert.equal(init.headers.has('Authorization'), false)
    assert.equal(init.body, undefined)
    assert.equal(url.pathname, '/api/v1/public/villages')
    assert.deepEqual(Object.fromEntries(url.searchParams), {
      page: '1', size: '6', search: 'بتير & %_!', governorateId: '2', localAuthorityId: '7', sortBy: 'nameAr', sortDir: 'desc',
    })
    return json(page(1, 6, 7, [village()]))
  })
  const result = await service.listVillages({ query: ' بتير & %_! ', governorateId: '2', localAuthorityId: '7', page: 2, sortBy: 'nameAr', sortDir: 'DESC', signal: controller.signal })
  assert.equal(result.page, 2)
  assert.equal(result.total, 7)
  assert.equal(result.totalPages, 2)
  assert.equal(result.items[0].id, '42')
  assert.equal(Object.hasOwn(result.items[0], 'governorate'), false)
  assert.equal(result.items[0].coordinates, null)
  assert.equal(Object.hasOwn(result.items[0], 'status'), false)
  assert.deepEqual(result.governorates, [])
  assert.equal(result.governorateNamesAvailable, false)
  assert.equal(fetchImpl.mock.callCount(), 1)
  assert.equal(mockList.mock.callCount(), 0)
})

for (const sortBy of villageSortFields) {
  test(`verified backend sort field ${sortBy} is sent without client sorting`, async t => {
    const { service } = fixture(t, url => {
      assert.equal(url.searchParams.get('sortBy'), sortBy)
      assert.equal(url.searchParams.has('status'), false)
      assert.equal(url.searchParams.has('search'), false)
      assert.equal(url.searchParams.has('governorateId'), false)
      return json(page(0, 6, 2, [village(9), village(1)]))
    })
    assert.deepEqual((await service.listVillages({ sortBy })).items.map(item => item.id), ['9', '1'])
  })
}

test('out-of-range results are corrected by fetching the actual last page with identical filters', async t => {
  const pages = []
  const { service } = fixture(t, url => {
    const number = Number(url.searchParams.get('page'))
    pages.push(number)
    assert.equal(url.searchParams.get('search'), 'بتير')
    assert.equal(url.searchParams.get('governorateId'), '2')
    assert.equal(url.searchParams.get('sortBy'), 'nameAr')
    assert.equal(url.searchParams.get('sortDir'), 'desc')
    return json(page(number, 3, 7, number === 2 ? [village(77)] : []))
  })
  const result = await service.listVillages({ page: 9, pageSize: 3, query: 'بتير', governorateId: '2', sortBy: 'nameAr', sortDir: 'desc' })
  assert.deepEqual(pages, [8, 2])
  assert.equal(result.page, 3)
  assert.equal(result.outOfRange, false)
  assert.equal(result.items[0].id, '77')
})

test('empty data at a later page fetches backend page zero before normalizing the URL', async t => {
  const pages = []
  const { service } = fixture(t, url => {
    const number = Number(url.searchParams.get('page'))
    pages.push(number)
    return json(page(number, 6, 0, []))
  })
  const result = await service.listVillages({ page: 99 })
  assert.deepEqual(pages, [98, 0])
  assert.equal(result.page, 1)
  assert.equal(result.totalPages, 0)
  assert.deepEqual(result.items, [])
  assert.deepEqual(result.governorates, [])
})

test('a failed last-page correction propagates its error without returning the original empty response', async t => {
  const pages = []
  const { service, mockList } = fixture(t, url => {
    const number = Number(url.searchParams.get('page'))
    pages.push(number)
    return number === 8 ? json(page(8, 6, 1, [])) : new Response('', { status: 503 })
  })
  await assert.rejects(service.listVillages({ page: 9 }), { kind: 'http', status: 503 })
  assert.deepEqual(pages, [8, 0])
  assert.equal(mockList.mock.callCount(), 0)
})

test('a normal empty first page needs no corrective request', async t => {
  const { service, fetchImpl } = fixture(t, () => json(page(0, 6, 0, [])))
  assert.equal((await service.listVillages()).empty, true)
  assert.equal(fetchImpl.mock.callCount(), 1)
})

test('a shrinking dataset can be corrected twice without returning empty intermediate results', async t => {
  const pages = []
  const { service } = fixture(t, url => {
    const number = Number(url.searchParams.get('page'))
    pages.push(number)
    return json(page(number, 3, pages.length === 1 ? 7 : 4, number === 1 ? [village(88)] : []))
  })
  const result = await service.listVillages({ page: 9, pageSize: 3 })
  assert.deepEqual(pages, [8, 2, 1])
  assert.equal(result.page, 2)
  assert.equal(result.items[0].id, '88')
})

test('repeated dataset changes produce an explicit error after bounded correction reads', async t => {
  let count = 0
  const { service, fetchImpl, mockList } = fixture(t, url => {
    const totals = [7, 4, 1]
    return json(page(Number(url.searchParams.get('page')), 3, totals[count++], []))
  })
  await assert.rejects(service.listVillages({ page: 9, pageSize: 3 }), { code: 'PAGINATION_CHANGED' })
  assert.equal(fetchImpl.mock.callCount(), 3)
  assert.equal(mockList.mock.callCount(), 0)
})

for (const options of [{ page: 0 }, { page: -1 }, { page: 1.5 }, { page: Number.MAX_SAFE_INTEGER },
  { pageSize: 0 }, { pageSize: 101 }, { pageSize: '6' }, { governorateId: 'battir' }, { governorateId: '02' },
  { governorateId: '9007199254740992' }, { query: null }, { query: 'أ'.repeat(181) }, { localAuthorityId: 'battir' }, { localAuthorityId: '0' }, { status: 'VERIFIED' }, { createdAt: 'x' }, { updatedAt: 'x' }, { sortBy: 'createdAt' }, { sortBy: 'updatedAt' }, { token: 'secret' }, { sortBy: 'status' }, { sortDir: 'sideways' }]) {
  test(`invalid listing options fail before HTTP: ${JSON.stringify(options).slice(0, 90)}`, async t => {
    const { service, fetchImpl } = fixture(t)
    await assert.rejects(service.listVillages(options), TypeError)
    assert.equal(fetchImpl.mock.callCount(), 0)
  })
}

test('unavailable governorate names and rich metadata are not fabricated', async t => {
  const { service } = fixture(t, () => json(page(0, 6, 1, [village(42, { governorateId: 999 })])))
  const result = await service.listVillages()
  assert.equal(result.items[0].name, 'بتير')
  assert.equal(Object.hasOwn(result.items[0], 'governorate'), false)
  assert.equal(result.items[0].governorateId, 999)
  assert.equal(Object.hasOwn(result.items[0], 'population'), false)
  assert.equal(Object.hasOwn(result.items[0], 'contentStatus'), false)
})

test('legacy mock governorate-name filters are not silently ignored or guessed as backend IDs', async t => {
  const { service, fetchImpl } = fixture(t)
  await assert.rejects(service.listVillages({ governorate: 'بيت لحم' }), { code: 'LEGACY_GOVERNORATE_FILTER' })
  assert.equal(fetchImpl.mock.callCount(), 0)
})

for (const status of [401, 403, 429, 500]) {
  test(`public villages HTTP ${status} is preserved without mock fallback or automatic retry`, async t => {
    const { service, fetchImpl, mockList } = fixture(t, () => new Response('', { status }))
    await assert.rejects(service.listVillages(), { kind: 'http', status })
    assert.equal(fetchImpl.mock.callCount(), 1)
    assert.equal(mockList.mock.callCount(), 0)
  })
}

for (const response of [() => new Response('<html>SPA fallback</html>'), () => json(null), () => json({ content: [] }),
  () => json(page(1, 6, 7, [village()])), () => json(page(0, 3, 1, [village()]))]) {
  test('malformed or mismatched village response is an error rather than empty/mock content', async t => {
    const { service, mockList } = fixture(t, () => response())
    await assert.rejects(service.listVillages())
    assert.equal(mockList.mock.callCount(), 0)
  })
}

test('malformed public village page fails explicitly', async t => {
  const { service, fetchImpl } = fixture(t, () => json({ content: [] }))
  await assert.rejects(service.listVillages(), TypeError)
  assert.equal(fetchImpl.mock.callCount(), 1)
})

test('network failures retain their classification and cause without mock fallback', async t => {
  const cause = new TypeError('Failed to fetch')
  const { service, fetchImpl, mockList } = fixture(t, () => { throw cause })
  await assert.rejects(service.listVillages(), { kind: 'network', code: 'NETWORK_ERROR', cause })
  assert.equal(fetchImpl.mock.callCount(), 1)
  assert.equal(mockList.mock.callCount(), 0)
})

test('already-cancelled requests do not send HTTP', async t => {
  const { service, fetchImpl } = fixture(t)
  const controller = new AbortController()
  controller.abort(new Error('Caller cancelled'))
  await assert.rejects(service.listVillages({ signal: controller.signal }), error => error === controller.signal.reason)
  assert.equal(fetchImpl.mock.callCount(), 0)
})

test('cancellation while reading the public response prevents further processing', async t => {
  const controller = new AbortController()
  const { service, fetchImpl } = fixture(t, () => {
    const response = json(page())
    const text = response.text.bind(response)
    response.text = async () => { const body = await text(); controller.abort(); return body }
    return response
  })
  await assert.rejects(service.listVillages({ signal: controller.signal }), error => error === controller.signal.reason)
  assert.equal(fetchImpl.mock.callCount(), 1)
})

test('in-flight HTTP cancellation preserves the abort reason and does not retry', async t => {
  const controller = new AbortController()
  let started
  const pendingFetch = new Promise(resolve => { started = resolve })
  const { service, fetchImpl } = fixture(t, (_url, { signal }) => new Promise((_resolve, reject) => {
    signal.addEventListener('abort', () => reject(signal.reason), { once: true })
    started()
  }))
  const pending = service.listVillages({ signal: controller.signal })
  await pendingFetch
  controller.abort(new Error('Navigation changed'))
  await assert.rejects(pending, error => error === controller.signal.reason)
  assert.equal(fetchImpl.mock.callCount(), 1)
})

test('cancellation after an out-of-range response prevents its corrective fetch', async t => {
  const controller = new AbortController()
  const { service, fetchImpl } = fixture(t, () => {
    const response = json(page(8, 6, 1, []))
    const text = response.text.bind(response)
    response.text = async () => { const body = await text(); controller.abort(); return body }
    return response
  })
  await assert.rejects(service.listVillages({ page: 9, signal: controller.signal }), error => error === controller.signal.reason)
  assert.equal(fetchImpl.mock.callCount(), 1)
})

test('error states have Arabic messages without echoing untrusted backend details', () => {
  const messages = [
    { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' }, { code: 'INVALID_MODE' }, { code: 'PAGINATION_CHANGED' }, { code: 'LEGACY_GOVERNORATE_FILTER' },
    { status: 401 }, { status: 403 }, { status: 429 }, { kind: 'network' }, { kind: 'malformed-response' },
  ].map(error => explorerErrorMessage({ ...error, message: 'Sensitive backend details' }))
  assert.equal(new Set(messages).size, 9)
  for (const message of messages) {
    assert.match(message, /[\u0600-\u06ff]/)
    assert.equal(message.includes('Sensitive backend details'), false)
  }
})
