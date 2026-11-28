import assert from 'node:assert/strict'
import test from 'node:test'
import {
  adaptPopulationPage, adaptPopulationRecord, populationParamsFromQuery, populationQueryFromParams, populationRequest,
} from './populationContracts.js'

const record = (patch = {}) => ({
  id: 9, villageId: 7, referenceYear: 2024, referenceDate: null, populationCount: 1200, methodology: 'CENSUS', sourceId: 3,
  notes: null, verificationStatus: 'VERIFIED', createdAt: '2026-11-28T10:00:00Z', updatedAt: '2026-11-28T10:00:00Z', ...patch,
})
const page = (patch = {}) => ({ content: [record()], page: 0, size: 20, totalElements: 1, totalPages: 1, ...patch })

test('population request uses only the confirmed endpoint, zero-based pagination and optional villageId', () => {
  assert.deepEqual(populationRequest({ villageId: '7', page: 2, size: 50 }), {
    path: '/population-records', query: { page: 2, size: 50, villageId: 7 }, requested: { page: 2, size: 50, villageId: 7 },
  })
  assert.deepEqual(populationRequest(), {
    path: '/population-records', query: { page: 0, size: 20, villageId: undefined }, requested: { page: 0, size: 20, villageId: undefined },
  })
  const query = populationQueryFromParams(new URLSearchParams('villageId=7&page=2&size=50&sortBy=referenceYear'))
  assert.deepEqual(query, { villageId: 7, page: 2, size: 50 })
  assert.equal(populationParamsFromQuery(query).toString(), 'page=2&size=50&villageId=7')
})

for (const query of [
  { villageId: '0' }, { villageId: '-1' }, { villageId: '07' }, { villageId: 'village' }, { villageId: '9007199254740992' },
  { page: -1 }, { page: 1.5 }, { size: 0 }, { size: 101 }, { size: '20' },
]) {
  test(`invalid population query rejects before HTTP: ${JSON.stringify(query)}`, () => {
    assert.throws(() => populationRequest(query), { code: 'POPULATION_VALIDATION' })
  })
}

for (const params of ['villageId=0', 'villageId=07', 'page=-1', 'page=1.5', 'size=0', 'size=101', 'page=9007199254740992']) {
  test(`invalid population URL parameters are not normalized: ${params}`, () => {
    assert.throws(() => populationQueryFromParams(new URLSearchParams(params)), { code: 'POPULATION_VALIDATION' })
  })
}

test('population page preserves backend zero-based metadata and fixed server order', () => {
  const dto = page({ content: [record({ id: 12, referenceYear: 2024 }), record({ id: 9, referenceYear: 2023 })], size: 2, totalElements: 4, totalPages: 2 })
  const result = adaptPopulationPage(dto, { villageId: 7, page: 0, size: 2 })
  assert.deepEqual(result.items.map(item => [item.id, item.referenceYear, item.villageId]), [['12', 2024, '7'], ['9', 2023, '7']])
  assert.equal(result.page, 0)
  assert.equal(result.totalPages, 2)
  assert.equal(result.empty, false)
  assert.equal(Object.isFrozen(result.items), true)
})

for (const dto of [null, {}, page({ sortBy: 'referenceYear' }), page({ content: [record({ id: '9' })] }),
  page({ content: [record({ villageId: 8 })] }), page({ content: [record({ verificationStatus: '' })] }),
  page({ content: [record(), record()] , size: 2, totalElements: 2, totalPages: 1 }), page({ page: 1 })]) {
  test('malformed population responses fail explicitly', () => {
    assert.throws(() => adaptPopulationPage(dto, { villageId: 7, page: 0, size: 20 }), TypeError)
  })
}

test('the response adapter keeps optional text null without inventing fields', () => {
  const result = adaptPopulationRecord(record({ referenceDate: null, notes: null }))
  assert.deepEqual({ ...result }, {
    id: '9', backendId: 9, villageId: '7', villageBackendId: 7, referenceYear: 2024, populationCount: 1200,
    methodology: 'CENSUS', verificationStatus: 'VERIFIED', sourceId: '3', sourceBackendId: 3,
    referenceDate: null, notes: null, createdAt: '2026-11-28T10:00:00Z', updatedAt: '2026-11-28T10:00:00Z',
  })
})
