import assert from 'node:assert/strict'
import test from 'node:test'
import { createVillageHomeService, villageHomeService, homeSearchLocation } from './villageHomeService.js'
import { readExplorerQuery } from './explorerQuery.js'
import { villageMapService } from './villageMapService.js'
import { villageExplorerService } from './villageExplorerService.js'
import { villageDetailsService } from './villageDetailsService.js'
import { publicVillageContractsVerified } from './villageMode.js'

test('all application data services share the mock default and still-closed gate', () => {
  for (const service of [villageHomeService, villageMapService, villageExplorerService, villageDetailsService]) assert.equal(service.mode, 'mock')
  assert.equal(publicVillageContractsVerified, false)
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

for (const publicationVerified of [false, true, 'true']) {
  test(`API featured content stays unavailable with no HTTP or arbitrary selection (gate ${String(publicationVerified)})`, async t => {
    const request = t.mock.fn(() => { throw new Error('No approved featured endpoint') })
    const result = await createVillageHomeService({ mode: 'api', publicationVerified, request }).getFeatured()
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
