import assert from 'node:assert/strict'
import test from 'node:test'
import { adaptHistoricalOverview, adaptHistoricalEvent, adaptCulturalHeritage, adaptHeritageSite } from './heritageAdapters.js'

const common = { id: 7, villageId: 42, sourceIds: [9, 2], verificationStatus: 'DISPUTED' }
const adapters = [adaptHistoricalOverview, adaptHistoricalEvent, adaptCulturalHeritage, adaptHeritageSite]

test('historical overview preserves text, source order, timestamp precision and verification status', () => {
  const dto = Object.freeze({ ...common, overview: 'نبذة\nتاريخية', updatedAt: '2026-11-27T10:00:00.123456789Z', sourceIds: Object.freeze([9, 2]) })
  const result = adaptHistoricalOverview(dto)
  assert.deepEqual(result, { ...common, id: '7', backendId: 7, overview: dto.overview, updatedAt: dto.updatedAt })
  assert.notEqual(result.sourceIds, dto.sourceIds)
})

for (const date of ['1948-05-15', '2000-02-29', '0001-01-01', '-0044-03-15', '+10000-01-01']) {
  test(`exact LocalDate ${date} stays unchanged without timezone conversion`, () => {
    const dto = { ...common, title: 'حدث', description: 'وصف', eventDate: date, datePrecision: 'EXACT', yearFrom: null, yearTo: null,
      createdAt: '2026-11-27T10:00:00.123456789Z' }
    assert.deepEqual(adaptHistoricalEvent(dto), { ...dto, id: '7', backendId: 7 })
  })
}

for (const fields of [{ datePrecision: 'YEAR', yearFrom: 1948, yearTo: null },
  { datePrecision: 'PERIOD', yearFrom: 1900, yearTo: 1910 }, { datePrecision: 'UNKNOWN', yearFrom: null, yearTo: null }]) {
  test(`${fields.datePrecision} precision never invents an exact historical date`, () => {
    const result = adaptHistoricalEvent({ ...common, ...fields })
    assert.equal(result.eventDate, null)
    assert.equal(result.yearFrom, fields.yearFrom)
    assert.equal(result.yearTo, fields.yearTo)
    assert.equal(result.datePrecision, fields.datePrecision)
  })
}

test('cultural heritage and heritage-site DTO names and geographic fields are preserved', () => {
  const culture = { ...common, name: 'التطريز', heritageType: 'CRAFT', description: 'وصف تراثي' }
  assert.deepEqual(adaptCulturalHeritage(culture), { ...culture, id: '7', backendId: 7 })
  const site = { ...common, name: 'نبع', siteType: 'SPRING', description: null, latitude: 0, longitude: -180, conditionNotes: 'ملاحظات' }
  assert.deepEqual(adaptHeritageSite(site), { ...site, id: '7', backendId: 7, coordinates: [0, -180] })
})

for (const adapt of adapters) {
  test(`${adapt.name}: nullable and missing fields remain missing without verification/source defaults`, () => {
    const result = adapt({ id: 7, villageId: 42 })
    assert.equal(result.verificationStatus, null)
    assert.equal(result.sourceIds, null)
    for (const [field, value] of Object.entries(result)) {
      if (!['id', 'backendId', 'villageId'].includes(field)) assert.equal(value, null, field)
    }
    const empty = adapt({ id: 7, villageId: 42, sourceIds: [] })
    assert.deepEqual(empty.sourceIds, [])
    assert.equal(empty.verificationStatus, null)
  })
  for (const verificationStatus of ['DRAFT', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED', 'DISPUTED']) {
    test(`${adapt.name}: ${verificationStatus} is retained without client security filtering`, () => {
      assert.equal(adapt({ ...common, verificationStatus }).verificationStatus, verificationStatus)
    })
  }
  for (const invalid of [null, [], {}, { ...common, id: '7' }, { ...common, villageId: null },
    { ...common, villageId: '42' }, { ...common, sourceIds: [null] }, { ...common, sourceIds: '9' },
    { ...common, sourceIds: [Number.MAX_SAFE_INTEGER + 1] }, { ...common, verificationStatus: 'PUBLISHED' }]) {
    test(`${adapt.name}: malformed DTO ${JSON.stringify(invalid)} fails explicitly`, () => {
      assert.throws(() => adapt(invalid), TypeError)
    })
  }
}

for (const invalid of ['1948-02-30', '1900-02-29', '1948-13-01', '1948-01-00', '1948-05-15T00:00:00Z', '1948', 1948, 'not-a-date']) {
  test(`invalid event date ${invalid} is rejected`, () => {
    assert.throws(() => adaptHistoricalEvent({ ...common, eventDate: invalid }), TypeError)
  })
}

test('malformed domain text, enums, years and coordinates are rejected', () => {
  assert.throws(() => adaptHistoricalOverview({ ...common, overview: {} }), TypeError)
  assert.throws(() => adaptHistoricalOverview({ ...common, updatedAt: 123 }), TypeError)
  assert.throws(() => adaptHistoricalEvent({ ...common, datePrecision: 'CENTURY' }), TypeError)
  for (const yearFrom of ['1948', 0, -1, 2201, 1.5]) assert.throws(() => adaptHistoricalEvent({ ...common, yearFrom }), TypeError)
  assert.throws(() => adaptCulturalHeritage({ ...common, heritageType: 'UNVERIFIED_TYPE' }), TypeError)
  assert.throws(() => adaptHeritageSite({ ...common, siteType: 'UNVERIFIED_TYPE' }), TypeError)
  for (const pair of [[null, '35'], [91, 0], [0, 181], [NaN, 35], [1, Infinity]]) {
    assert.throws(() => adaptHeritageSite({ ...common, latitude: pair[0], longitude: pair[1] }), TypeError)
  }
  assert.equal(adaptHeritageSite({ ...common, latitude: 31, longitude: null }).coordinates, null)
})
