import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createVillageMapService, villageMapService } from './villageMapService.js'
import { createLatestExplorerRequest } from './explorerRequest.js'

const village = (id, extra = {}) => ({ id, nameAr: `قرية ${id}`, governorateId: 2, localAuthorityId: null, nameEn: null, latitude: 31, longitude: 35, ...extra })
const json = (dto, status = 200) => new Response(JSON.stringify(dto), { status })
function page(number, size, total, content) {
  const totalPages = Math.ceil(total / size)
  return { number, size, totalElements: total, totalPages, content, numberOfElements: content.length,
    first: number === 0, last: number + 1 >= totalPages, hasNext: number + 1 < totalPages,
    hasPrevious: number > 0, empty: content.length === 0 }
}
function fixture(t, handler, options = {}) {
  const fetchImpl = t.mock.fn((url, init) => {
    const parsed = new URL(url, 'https://frontend.example.test')
    assert.equal(parsed.pathname, '/api/v1/public/villages')
    return handler(parsed, init)
  })
  const mockList = t.mock.fn(() => { throw new Error('Map API errors must not use mock data') })
  const service = createVillageMapService({ mode: 'api', browsingVerified: true, request: createApiClient({ fetchImpl }), pageSize: 2, mockList, ...options })
  return { service, fetchImpl, mockList }
}

test('Map defaults to mock mode', () => { assert.equal(villageMapService.mode, 'mock') })

for (const browsingVerified of [null, false, 'true', 'false']) {
  test(`closed Map gate ${String(browsingVerified)} makes zero requests and never falls back`, async t => {
    const { service, fetchImpl, mockList } = fixture(t, () => { throw new Error('No HTTP allowed') }, { browsingVerified })
    await assert.rejects(service.listVillages(), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
    assert.equal(service.available, false)
    assert.equal(fetchImpl.mock.callCount(), 0)
    assert.equal(mockList.mock.callCount(), 0)
  })
}

test('invalid Map mode fails explicitly', async t => {
  const { service, fetchImpl } = fixture(t, () => {}, { mode: 'invalid' })
  await assert.rejects(service.listVillages(), { code: 'INVALID_MODE' })
  assert.equal(fetchImpl.mock.callCount(), 0)
})

test('Map retrieves every permitted page in ID order using the existing listing service', async t => {
  const pages = [], controller = new AbortController()
  const rows = [village(1), village(2), village(3), village(4, { latitude: null, longitude: null }), village(5)]
  const { service, fetchImpl, mockList } = fixture(t, (url, init) => {
    const number = Number(url.searchParams.get('page')); pages.push(number)
    assert.equal(url.pathname, '/api/v1/public/villages')
    assert.deepEqual(Object.fromEntries(url.searchParams), { page: String(number), size: '2', sortBy: 'id', sortDir: 'asc' })
    assert.equal(init.signal, controller.signal)
    assert.equal(init.method, 'GET')
    assert.equal(init.credentials, 'omit')
    assert.equal(init.headers.has('Authorization'), false)
    return json(page(number, 2, rows.length, rows.slice(number * 2, number * 2 + 2)))
  })
  const result = await service.listVillages({ signal: controller.signal })
  assert.deepEqual(pages, [0, 1, 2])
  assert.deepEqual(result.items.map(row => row.id), ['1', '2', '3', '4', '5'])
  assert.equal(result.total, 5)
  assert.equal(result.complete, true)
  assert.equal(Object.hasOwn(result.items[0], 'status'), false)
  assert.equal(result.items[2].nameEn, null)
  assert.equal(result.items[3].coordinates, null)
  assert.equal(Object.hasOwn(result.items[0], 'governorate'), false)
  assert.equal(fetchImpl.mock.callCount(), 3)
  assert.equal(mockList.mock.callCount(), 0)
})

test('a verified empty collection returns an explicitly complete empty dataset', async t => {
  const { service, fetchImpl } = fixture(t, url => {
    assert.equal(url.searchParams.get('page'), '0')
    assert.equal(url.searchParams.get('size'), '100')
    return json(page(0, 100, 0, []))
  }, { pageSize: 100 })
  assert.deepEqual(await service.listVillages(), { items: [], total: 0, complete: true })
  assert.equal(fetchImpl.mock.callCount(), 1)
})

for (const options of [{ maxPages: 1 }, { maxVillages: 2 }]) {
  test(`known traversal limit ${JSON.stringify(options)} fails rather than truncating`, async t => {
    const { service, fetchImpl } = fixture(t, () => json(page(0, 2, 3, [village(1), village(2)])), options)
    await assert.rejects(service.listVillages(), { code: 'MAP_LIMIT_EXCEEDED' })
    assert.equal(fetchImpl.mock.callCount(), 1)
  })
}

for (const options of [{ maxPages: 0 }, { maxPages: 101 }, { maxPages: Infinity }, { maxVillages: 0 }, { maxVillages: 10001 }, { pageSize: 0 }, { pageSize: 101 }, { pageSize: 1.5 }]) {
  test(`invalid traversal limits ${JSON.stringify(options)} send no HTTP`, async t => {
    const { service, fetchImpl } = fixture(t, () => {}, options)
    await assert.rejects(service.listVillages(), TypeError)
    assert.equal(fetchImpl.mock.callCount(), 0)
  })
}

for (const acrossPages of [false, true]) {
  test(`duplicate IDs ${acrossPages ? 'across pages' : 'within a page'} reject the whole dataset`, async t => {
    const { service, mockList } = fixture(t, url => {
      const number = Number(url.searchParams.get('page'))
      return json(acrossPages ? page(number, 2, 4, number === 0 ? [village(1), village(2)] : [village(2), village(3)])
        : page(0, 2, 2, [village(1), village(1)]))
    })
    await assert.rejects(service.listVillages(), { code: 'MAP_DUPLICATE_IDS' })
    assert.equal(mockList.mock.callCount(), 0)
  })
}

test('out-of-order rows fail instead of masking a broken listing contract', async t => {
  const { service } = fixture(t, () => json(page(0, 2, 2, [village(2), village(1)])))
  await assert.rejects(service.listVillages(), { code: 'MAP_DATA_CHANGED' })
})

for (const total of [3, 5]) {
  test(`totals changing from four to ${total} during traversal fail explicitly`, async t => {
    const { service } = fixture(t, url => {
      const number = Number(url.searchParams.get('page'))
      return json(number === 0 ? page(0, 2, 4, [village(1), village(2)])
        : page(1, 2, total, total === 3 ? [village(3)] : [village(3), village(4)]))
    })
    await assert.rejects(service.listVillages(), { code: 'MAP_DATA_CHANGED' })
  })
}

test('a corrected/skipped page from a shrinking collection is never accepted as a complete traversal', async t => {
  let reads = 0
  const pages = []
  const { service } = fixture(t, url => {
    const number = Number(url.searchParams.get('page')); pages.push(number)
    return json(++reads === 1 ? page(0, 2, 3, [village(1), village(2)]) : page(number, 2, 1, number === 0 ? [village(1)] : []))
  })
  await assert.rejects(service.listVillages(), { code: 'INCOMPLETE_MAP' })
  assert.deepEqual(pages, [0, 1, 0])
})

for (const failure of ['missing-content', 'wrong-page', 'network', 'http', 'html']) {
  test(`later-page ${failure} never returns the already-loaded first page as complete`, async t => {
    const { service, mockList } = fixture(t, url => {
      if (url.searchParams.get('page') === '0') return json(page(0, 2, 3, [village(1), village(2)]))
      if (failure === 'network') throw new TypeError('Connection lost')
      if (failure === 'http') return new Response('', { status: 503 })
      if (failure === 'html') return new Response('<html>SPA fallback</html>')
      return json(failure === 'wrong-page' ? page(0, 2, 3, [village(1), village(2)]) : page(1, 2, 3, []))
    })
    await assert.rejects(service.listVillages())
    assert.equal(mockList.mock.callCount(), 0)
  })
}

for (const fields of [{ latitude: '31' }, { latitude: 91 }, { longitude: 181 }, { latitude: null, longitude: '' }]) {
  test(`malformed geography ${JSON.stringify(fields)} rejects the dataset instead of inventing a point`, async t => {
    const { service } = fixture(t, () => json(page(0, 2, 1, [village(1, fields)])))
    await assert.rejects(service.listVillages(), TypeError)
  })
}

test('a partial nullable coordinate pair remains missing while real zero coordinates stay valid', async t => {
  const { service } = fixture(t, () => json(page(0, 2, 2, [village(1, { latitude: null }), village(2, { latitude: 0, longitude: 0 })])))
  const result = await service.listVillages()
  assert.equal(result.items[0].coordinates, null)
  assert.equal(result.items[0].latitude, null)
  assert.deepEqual(result.items[1].coordinates, [0, 0])
})

test('pre-cancelled traversal makes no requests', async t => {
  const { service, fetchImpl } = fixture(t, () => {})
  const controller = new AbortController(); controller.abort(new Error('Cancelled'))
  await assert.rejects(service.listVillages({ signal: controller.signal }), error => error === controller.signal.reason)
  assert.equal(fetchImpl.mock.callCount(), 0)
})

test('cancellation after a page response prevents the next page request', async t => {
  const controller = new AbortController()
  const { service, fetchImpl } = fixture(t, () => {
    const response = json(page(0, 2, 3, [village(1), village(2)]))
    const read = response.text.bind(response)
    response.text = async () => { const text = await read(); controller.abort(); return text }
    return response
  })
  await assert.rejects(service.listVillages({ signal: controller.signal }), error => error === controller.signal.reason)
  assert.equal(fetchImpl.mock.callCount(), 1)
})

test('in-flight later-page cancellation preserves the reason and exposes no partial data', async t => {
  const controller = new AbortController()
  let started
  const laterPage = new Promise(resolve => { started = resolve })
  const { service, fetchImpl } = fixture(t, (url, { signal }) => {
    if (url.searchParams.get('page') === '0') return json(page(0, 2, 3, [village(1), village(2)]))
    return new Promise((_resolve, reject) => { signal.addEventListener('abort', () => reject(signal.reason), { once: true }); started() })
  })
  const pending = service.listVillages({ signal: controller.signal })
  await laterPage; controller.abort(new Error('Left Map'))
  await assert.rejects(pending, error => error === controller.signal.reason)
  assert.equal(fetchImpl.mock.callCount(), 2)
})

test('a stale complete traversal cannot replace the newer Map load even when HTTP ignores abort', async t => {
  let finishOld, started, count = 0
  const oldStarted = new Promise(resolve => { started = resolve })
  const { service } = fixture(t, () => {
    if (++count === 1) return new Promise(resolve => { finishOld = () => resolve(json(page(0, 2, 1, [village(42)]))); started() })
    return json(page(0, 2, 1, [village(77)]))
  })
  const runner = createLatestExplorerRequest(), successes = [], errors = []
  const handlers = { onSuccess: value => successes.push(value), onError: error => errors.push(error) }
  const old = runner.run(signal => service.listVillages({ signal }), handlers)
  await oldStarted
  await runner.run(signal => service.listVillages({ signal }), handlers).done
  finishOld(); await old.done
  assert.deepEqual(successes.map(data => data.items[0].id), ['77'])
  assert.deepEqual(errors, [])
})

test('retry starts a fresh traversal instead of reusing partial rows from a failed attempt', async t => {
  let reads = 0
  const { service } = fixture(t, () => {
    if (++reads === 1) return json(page(0, 2, 3, [village(1), village(2)]))
    if (reads === 2) return new Response('', { status: 503 })
    return json(page(0, 2, 2, [village(4), village(5)]))
  })
  await assert.rejects(service.listVillages(), { status: 503 })
  assert.deepEqual((await service.listVillages()).items.map(item => item.id), ['4', '5'])
})

test('mock Map retains every slug and nullable coordinate without API requests', async t => {
  const request = t.mock.fn(() => { throw new Error('No API in mock mode') })
  const result = await createVillageMapService({ request }).listVillages()
  assert.equal(result.complete, true)
  assert.equal(result.items.length, 8)
  assert.equal(result.items[0].id, 'battir')
  assert.equal(result.items.find(row => row.id === 'beita').coordinates, null)
  assert.equal(request.mock.callCount(), 0)
})
