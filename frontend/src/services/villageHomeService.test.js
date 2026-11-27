import assert from 'node:assert/strict'
import test from 'node:test'
import { createVillageHomeService, villageHomeService, homeSearchLocation } from './villageHomeService.js'
import { readExplorerQuery } from './explorerQuery.js'
import { villageMapService } from './villageMapService.js'
import { villageExplorerService } from './villageExplorerService.js'
import { villageDetailsService } from './villageDetailsService.js'
import { publicVillageCapabilities } from './villageMode.js'
import { createVillageExplorerService } from './villageExplorerService.js'

test('all application data services keep mock default and independent public capabilities', () => {
  for (const service of [villageHomeService, villageMapService, villageExplorerService, villageDetailsService]) assert.equal(service.mode, 'mock')
  assert.deepEqual(publicVillageCapabilities, { browsing: true, identity: true, geographicCatalogues: false, richSections: false })
})

for (const query of ['بتير', ' بَتّير & أرطاس ', 'قرية?status=DRAFT&villageId=42', '100%_!', '', '  ']) {
  test(`Home search navigates through the Explorer query contract: ${query}`, () => {
    const target = homeSearchLocation(query)
    assert.equal(target.pathname, '/villages')
    const params = new URLSearchParams(target.search)
    const explorer = readExplorerQuery(params)
    assert.equal(explorer.query, query.trim())
    assert.equal(explorer.page, 1)
    assert.equal(explorer.governorateId, '')
    assert.equal(explorer.sortBy, 'id')
    assert.equal(params.has('status'), false)
    assert.equal(params.has('villageId'), false)
    assert.deepEqual([...params.keys()], query.trim() ? ['q'] : [])
  })
}

for (const browsingVerified of [false, true, 'true']) {
  test(`API featured content stays unavailable with no HTTP or arbitrary selection (gate ${String(browsingVerified)})`, async t => {
    const request = t.mock.fn(() => { throw new Error('No approved featured endpoint') })
    const result = await createVillageHomeService({ mode: 'api', browsingVerified, request }).getFeatured()
    assert.deepEqual(result, { status: 'unavailable', reason: 'FEATURED_CONTRACT_UNCONFIRMED', items: [] })
    assert.equal(request.mock.callCount(), 0)
  })
}

test('mock featured villages retain original ordering, content and slug navigation without HTTP', async t => {
  const request = t.mock.fn(() => { throw new Error('No HTTP in mock mode') })
  const result = await createVillageHomeService({ request }).getFeatured()
  assert.equal(result.status, 'ready')
  assert.deepEqual(result.items.map(item => item.id), ['battir', 'lifta', 'deir-ghassaneh'])
  assert.ok(result.items.every(item => item.featured && item.contentStatus === 'demo'))
  assert.equal(result.items[0].name, 'بتير')
  assert.equal(request.mock.callCount(), 0)
})

test('cancellation and invalid configuration never produce featured mock fallback', async t => {
  const request = t.mock.fn(() => { throw new Error('No HTTP allowed') })
  const controller = new AbortController(); controller.abort(new Error('Left Home'))
  const service = createVillageHomeService({ mode: 'api', request })
  await assert.rejects(service.getFeatured({ signal: controller.signal }), error => error === controller.signal.reason)
  await assert.rejects(createVillageHomeService({ mode: 'invalid', request }).getFeatured(), { code: 'INVALID_MODE' })
  assert.equal(request.mock.callCount(), 0)
  assert.throws(() => homeSearchLocation(null), TypeError)
})

for (const [input, term] of [[' بتير ', 'بتير'], [' Battir & Lifta? ', 'Battir & Lifta?'],
  ['C++ / 100%_!#', 'C++ / 100%_!#'], ['', ''], [' \t\n\u3000\u001c', ''], ['\u00a0Battir\u00a0', '\u00a0Battir\u00a0']]) {
  test(`API Home hands the exact search to the real public Explorer: ${JSON.stringify(input)}`, async t => {
    const target = homeSearchLocation(input, { mode: 'api' })
    assert.equal(target.pathname, '/villages')
    const params = new URLSearchParams(target.search)
    assert.deepEqual([...params.keys()], term ? ['q'] : [])
    assert.equal(params.get('q') ?? '', term)
    const request = t.mock.fn(async (path, { query }) => {
      assert.equal(path, '/public/villages')
      assert.deepEqual(query, { page: 0, size: 6, search: term || undefined, governorateId: undefined,
        localAuthorityId: undefined, sortBy: 'id', sortDir: 'asc' })
      return { content: [], number: 0, size: 6, totalElements: 0, totalPages: 0, numberOfElements: 0,
        first: true, last: true, hasNext: false, hasPrevious: false, empty: true }
    })
    await createVillageExplorerService({ mode: 'api', request }).listVillages(readExplorerQuery(params))
    assert.equal(request.mock.callCount(), 1)
  })
}

test('public Home validates the backend UTF-16 search limit without altering mock normalization', () => {
  assert.throws(() => homeSearchLocation('a'.repeat(181), { mode: 'api' }), TypeError)
  assert.throws(() => homeSearchLocation('😀'.repeat(91), { mode: 'api' }), TypeError)
  assert.equal(new URLSearchParams(homeSearchLocation('😀'.repeat(90), { mode: 'api' }).search).get('q'), '😀'.repeat(90))
  assert.equal(new URLSearchParams(homeSearchLocation('a'.repeat(181), { mode: 'mock' }).search).get('q').length, 181)
  assert.equal(homeSearchLocation('\u00a0', { mode: 'mock' }).search, '')
})
