import assert from 'node:assert/strict'
import test from 'node:test'
import { adaptGovernorateCatalog, adaptVillage, toBackendId, toCoordinates, toRouteId } from './villageAdapters.js'
import { adaptRecordPage, adaptVillagePage, toBackendPage, toFrontendPage } from './paginationAdapters.js'

function village(overrides = {}) {
  return {
    id: 42, governorateId: 2, localAuthorityId: 7, nameAr: 'بتير', nameEn: 'Battir',
    nameOrigin: null, locationDescription: 'غرب بيت لحم', latitude: 31.728, longitude: 35.139,
    elevationM: 750.25, areaKm2: 6.8, status: 'DRAFT',
    createdAt: '2026-11-26T12:00:00Z', updatedAt: null, archivedAt: null, ...overrides,
  }
}

function villagePage(overrides = {}) {
  return {
    content: [village()], number: 0, size: 1, totalElements: 1, totalPages: 1,
    numberOfElements: 1, first: true, last: true, hasNext: false, hasPrevious: false,
    empty: false, ...overrides,
  }
}

function recordPage(overrides = {}) {
  return { content: [{ id: 1, villageId: 42 }], page: 0, size: 1, totalElements: 1, totalPages: 1, ...overrides }
}

test('village identity/geography uses backend fields and the independent governorate catalog', () => {
  const dto = Object.freeze(village())
  const catalog = adaptGovernorateCatalog([
    { id: 2, nameAr: 'بيت لحم', nameEn: 'Bethlehem', code: 'BTH' },
    { id: 9, nameAr: 'نابلس', nameEn: null, code: null },
  ])
  const result = adaptVillage(dto, catalog.namesById)
  assert.deepEqual(result, {
    id: '42', backendId: 42, governorateId: 2, localAuthorityId: 7,
    name: 'بتير', nameAr: 'بتير', nameEn: 'Battir', governorate: 'بيت لحم',
    nameOrigin: null, locationDescription: 'غرب بيت لحم',
    latitude: 31.728, longitude: 35.139, coordinates: [31.728, 35.139],
    elevationM: 750.25, areaKm2: 6.8, status: 'DRAFT',
    featured: null, contentStatus: null, overview: null, population: null,
    history: null, heritage: null, agriculture: null, community: null,
  })
  assert.deepEqual(catalog.items.map(item => item.id), ['2', '9'])
  assert.equal(catalog.items[0].backendId, 2)
  assert.equal(catalog.items[0].name, 'بيت لحم')
  assert.equal(catalog.items[0].nameEn, 'Bethlehem')
  assert.equal(catalog.items[0].code, 'BTH')
  assert.equal(catalog.namesById.get(9), 'نابلس')
})

test('missing information stays null without borrowing mock or unrelated DTO fields', () => {
  const result = adaptVillage({ id: 42, nameEn: 'Battir', population: 999, overview: 'uncontracted', image: 'fake.jpg' })
  assert.equal(result.name, null)
  assert.equal(result.nameEn, 'Battir')
  for (const field of ['governorateId', 'localAuthorityId', 'governorate', 'nameOrigin', 'locationDescription',
    'latitude', 'longitude', 'coordinates', 'elevationM', 'areaKm2', 'status', 'population', 'history', 'overview',
    'heritage', 'agriculture', 'community', 'contentStatus', 'featured']) {
    assert.equal(result[field], null, field)
  }
  assert.equal(Object.hasOwn(result, 'image'), false)
  assert.equal(Object.hasOwn(result, 'verificationStatus'), false)
  assert.equal(adaptVillage(village({ nameAr: '  ', nameEn: '' })).name, null)
})

test('explicit nullable DTO fields and nameless governorates remain missing', () => {
  const catalog = adaptGovernorateCatalog([{ id: 2, nameAr: null, nameEn: 'Bethlehem', code: null }])
  assert.equal(catalog.items[0].name, null)
  assert.equal(catalog.namesById.get(2), null)
  const result = adaptVillage(village({ nameAr: null, localAuthorityId: null, latitude: null, longitude: null,
    areaKm2: null, elevationM: null, status: null }), catalog.namesById)
  assert.equal(result.governorate, null)
  assert.equal(result.coordinates, null)
  assert.equal(result.name, null)
  assert.equal(result.status, null)
})

test('an unknown governorate retains its backend ID without inventing a name', () => {
  const catalog = adaptGovernorateCatalog([{ id: 9, nameAr: 'نابلس' }])
  const result = adaptVillage(village(), catalog.namesById)
  assert.equal(result.governorateId, 2)
  assert.equal(result.governorate, null)
  assert.equal(catalog.namesById.has(2), false)
})

test('empty governorate catalog is valid and duplicate names retain distinct IDs', () => {
  assert.deepEqual(adaptGovernorateCatalog([]), { items: [], namesById: new Map() })
  const catalog = adaptGovernorateCatalog([{ id: 1, nameAr: 'اسم' }, { id: 2, nameAr: 'اسم' }])
  assert.equal(catalog.items.length, 2)
  assert.equal(catalog.namesById.size, 2)
})

for (const payload of [null, {}, [null], [{ id: '2' }], [{ id: 2, nameAr: 7 }], [{ id: 2, code: false }],
  [{ id: 2 }, { id: 2 }]]) {
  test(`malformed governorate catalog is rejected: ${JSON.stringify(payload)}`, () => {
    assert.throws(() => adaptGovernorateCatalog(payload), TypeError)
  })
}

for (const id of [1, 42, Number.MAX_SAFE_INTEGER]) {
  test(`numeric ID ${id} round trips without slug generation or precision loss`, () => {
    assert.equal(toRouteId(id), String(id))
    assert.equal(toBackendId(toRouteId(id)), id)
    assert.equal(adaptVillage({ id }).id, String(id))
  })
}

for (const id of [null, undefined, 0, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1, '42', true, 42n]) {
  test(`invalid numeric backend ID ${String(id)} is rejected`, () => {
    assert.throws(() => toRouteId(id), TypeError)
    assert.throws(() => adaptVillage({ id }), TypeError)
  })
}

for (const routeId of [null, undefined, 42, '', '0', '-1', '01', '+1', '1.0', '1e2', ' 42', '42 ', '42/other',
  'battir', '٤٢', '9007199254740992']) {
  test(`invalid route identifier ${String(routeId)} is rejected`, () => {
    assert.throws(() => toBackendId(routeId), TypeError)
  })
}

test('coordinate order, boundaries, zero and negative elevation are preserved', () => {
  for (const pair of [[31.728, 35.139], [0, 0], [-90, -180], [90, 180]]) {
    assert.deepEqual(toCoordinates(...pair), pair)
  }
  assert.equal(adaptVillage(village({ elevationM: -400 })).elevationM, -400)
})

for (const pair of [[null, null], [undefined, undefined], [null, 35.139], [31.728, null], [0, undefined]]) {
  test(`incomplete coordinates ${String(pair)} never become a zero location`, () => {
    assert.equal(toCoordinates(...pair), null)
    const result = adaptVillage(village({ latitude: pair[0], longitude: pair[1] }))
    assert.equal(result.coordinates, null)
    assert.equal(result.latitude, pair[0] ?? null)
    assert.equal(result.longitude, pair[1] ?? null)
  })
}

for (const pair of [[90.01, 0], [0, -180.01], [NaN, 1], [1, Infinity], ['31.728', 35.139], ['', 0],
  [true, 1], [null, '35'], [undefined, 181]]) {
  test(`invalid coordinates ${String(pair)} are rejected even when one axis is absent`, () => {
    assert.throws(() => toCoordinates(...pair), TypeError)
    assert.throws(() => adaptVillage(village({ latitude: pair[0], longitude: pair[1] })), TypeError)
  })
}

for (const payload of [null, [], {}, village({ governorateId: '2' }), village({ localAuthorityId: 0 }),
  village({ nameAr: {} }), village({ locationDescription: 3 }), village({ elevationM: '750' }),
  village({ areaKm2: 0 }), village({ areaKm2: -1 }), village({ status: 'PUBLISHED' })]) {
  test(`malformed village is rejected: ${JSON.stringify(payload)}`, () => {
    assert.throws(() => adaptVillage(payload), TypeError)
  })
}

test('adapters do not filter village status or infer publication/verification of other domains', () => {
  for (const status of ['DRAFT', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED']) {
    const result = adaptVillagePage(villagePage({ content: [village({ status })] }))
    assert.equal(result.items.length, 1)
    assert.equal(result.items[0].status, status)
    assert.equal(result.items[0].contentStatus, null)
  }
})

test('page conversions are reversible and enforce each side of the boundary', () => {
  for (const backend of [0, 1, 23, Number.MAX_SAFE_INTEGER - 1]) {
    assert.equal(toBackendPage(toFrontendPage(backend)), backend)
    assert.equal(toFrontendPage(backend), backend + 1)
  }
  for (const invalid of [null, undefined, '1', -1, 1.5, NaN, Infinity]) {
    assert.throws(() => toBackendPage(invalid), TypeError)
    assert.throws(() => toFrontendPage(invalid), TypeError)
  }
  assert.throws(() => toBackendPage(0), TypeError)
  assert.throws(() => toFrontendPage(Number.MAX_SAFE_INTEGER), TypeError)
})

test('VillagePageResponse becomes the frontend list shape with catalog mapping and truthful metadata', () => {
  const dto = villagePage({ content: [village(), village({ id: 43 })], number: 1, size: 2,
    totalElements: 5, totalPages: 3, numberOfElements: 2, first: false, last: false,
    hasNext: true, hasPrevious: true })
  const { namesById } = adaptGovernorateCatalog([{ id: 2, nameAr: 'بيت لحم' }])
  const result = adaptVillagePage(dto, namesById)
  assert.deepEqual({ ...result, items: result.items.map(item => item.id) }, {
    items: ['42', '43'], total: 5, page: 2, pageSize: 2, totalPages: 3, numberOfElements: 2,
    first: false, last: false, hasNext: true, hasPrevious: true, empty: false, outOfRange: false,
  })
  assert.equal(result.items[0].governorate, 'بيت لحم')
})

const domainRows = {
  population: {
    id: 3, villageId: 42, referenceYear: 1945, referenceDate: null, populationCount: 1234,
    methodology: 'CENSUS', sourceId: null, notes: null, verificationStatus: 'DRAFT',
    createdAt: '2026-11-26T12:00:00Z', updatedAt: null,
  },
  heritage: { id: 4, villageId: 42, overview: 'نبذة موثقة', updatedAt: null, verificationStatus: 'DRAFT', sourceIds: [] },
}

for (const [domain, row] of Object.entries(domainRows)) {
  test(`${domain} pagination adapts only the common envelope and preserves the domain DTO`, () => {
    const rows = Object.freeze([Object.freeze(row)])
    const dto = Object.freeze(recordPage({ content: rows, page: 1, size: 1, totalElements: 2, totalPages: 2 }))
    const result = adaptRecordPage(dto)
    assert.deepEqual(result, {
      items: rows, page: 2, pageSize: 1, total: 2, totalPages: 2, numberOfElements: 1,
      first: false, last: true, hasNext: false, hasPrevious: true, empty: false, outOfRange: false,
    })
    assert.notEqual(result.items, rows)
    assert.equal(result.items[0], rows[0])
  })
}

for (const kind of ['village', 'record']) {
  const adapt = kind === 'village' ? adaptVillagePage : adaptRecordPage
  const make = kind === 'village' ? villagePage : recordPage
  const pageField = kind === 'village' ? 'number' : 'page'
  test(`${kind}: empty dataset retains zero total pages and a one-based requested page`, () => {
    const result = adapt(make({ content: [], totalElements: 0, totalPages: 0, numberOfElements: 0, empty: true }))
    assert.deepEqual(result.items, [])
    assert.equal(result.total, 0)
    assert.equal(result.totalPages, 0)
    assert.equal(result.page, 1)
    assert.equal(result.outOfRange, false)
    assert.equal(result.first, true)
    assert.equal(result.last, true)
    assert.equal(result.hasNext, false)
    assert.equal(result.hasPrevious, false)
  })

  for (const total of [0, 3]) {
    test(`${kind}: out-of-range response with total ${total} is never relabelled as a fetched valid page`, () => {
      const result = adapt(make({ [pageField]: 9, content: [], size: 2, totalElements: total,
        totalPages: Math.ceil(total / 2), numberOfElements: 0, empty: true, first: false, hasPrevious: true }))
      assert.deepEqual(result.items, [])
      assert.equal(result.page, 10)
      assert.equal(result.totalPages, Math.ceil(total / 2))
      assert.equal(result.outOfRange, true)
      assert.equal(result.hasNext, false)
      assert.equal(result.hasPrevious, true)
    })
  }

  test(`${kind}: final partial page retains its page and result count`, () => {
    const result = adapt(make({ [pageField]: 1, size: 2, totalElements: 3, totalPages: 2,
      first: false, hasPrevious: true }))
    assert.equal(result.page, 2)
    assert.equal(result.items.length, 1)
    assert.equal(result.last, true)
    assert.equal(result.outOfRange, false)
  })

  for (const overrides of [{ content: null }, { content: [null] }, { content: [3] }, { content: [[]] },
    { [pageField]: undefined }, { [pageField]: -1 }, { [pageField]: '0' }, { size: 0 }, { size: 1.5 },
    { totalElements: null }, { totalElements: -1 }, { totalElements: Number.MAX_SAFE_INTEGER + 1 },
    { totalPages: 7 }, { content: [] }, { content: [], totalElements: 0, totalPages: 1 }, { [pageField]: 10 }]) {
    test(`${kind}: malformed pagination ${JSON.stringify(overrides)} fails explicitly`, () => {
      assert.throws(() => adapt(make(overrides)), TypeError)
    })
  }
  test(`${kind}: missing envelopes do not silently turn into empty data`, () => {
    for (const value of [null, undefined, [], {}]) assert.throws(() => adapt(value), TypeError)
  })
}

test('each pagination format requires its actual page field, without guessing', () => {
  assert.throws(() => adaptVillagePage(recordPage()), TypeError)
  assert.throws(() => adaptRecordPage(villagePage()), TypeError)
})

test('VillagePageResponse count and boolean flags must agree with the envelope', () => {
  for (const field of ['numberOfElements', 'first', 'last', 'hasNext', 'hasPrevious', 'empty']) {
    assert.throws(() => adaptVillagePage(villagePage({ [field]: undefined })), TypeError)
    assert.throws(() => adaptVillagePage(villagePage({ [field]: field === 'numberOfElements' ? 2 : !villagePage()[field] })), TypeError)
  }
})

test('village and governorate adapters leave frozen source DTOs and catalog lookups untouched', () => {
  const input = Object.freeze([Object.freeze({ id: 2, nameAr: 'بيت لحم' })])
  const { namesById } = adaptGovernorateCatalog(input)
  const dto = Object.freeze(villagePage({ content: Object.freeze([Object.freeze(village())]) }))
  const result = adaptVillagePage(dto, namesById)
  result.items[0].name = 'changed output'
  result.items[0].coordinates[0] = 0
  assert.equal(dto.content[0].nameAr, 'بتير')
  assert.equal(dto.content[0].latitude, 31.728)
  assert.equal(namesById.get(2), 'بيت لحم')
  assert.equal(input[0].nameAr, 'بيت لحم')
})
