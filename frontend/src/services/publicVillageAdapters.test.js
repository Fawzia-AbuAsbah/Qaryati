import assert from 'node:assert/strict'
import test from 'node:test'
import { adaptPublicVillage, adaptPublicVillageDetail, publicVillageFields, publicVillageDetailFields } from './publicVillageAdapters.js'
import { adaptPublicVillagePage } from './paginationAdapters.js'

const village = patch => ({ id: 42, nameAr: 'بتير', nameEn: null, governorateId: 2, localAuthorityId: null,
  latitude: null, longitude: null, ...patch })
const page = patch => ({ content: [village()], number: 0, size: 6, totalElements: 1, totalPages: 1,
  numberOfElements: 1, first: true, last: true, hasNext: false, hasPrevious: false, empty: false, ...patch })

test('explicit public adapter preserves numeric identities, exact nullable fields and no invented metadata', () => {
  const dto = village({ nameEn: ' Battir ', localAuthorityId: 7, latitude: 0, longitude: 0 })
  const adapted = adaptPublicVillage(dto)
  assert.equal(adapted.id, '42')
  assert.equal(adapted.backendId, 42)
  for (const field of publicVillageFields.filter(field => field !== 'id')) assert.equal(adapted[field], dto[field])
  assert.equal(adapted.name, dto.nameAr)
  assert.deepEqual(adapted.coordinates, [0, 0])
  assert.equal(Object.isFrozen(adapted), true)
  for (const field of ['governorate', 'localAuthority', 'description', 'status', 'contentStatus', 'images', 'population', 'history', 'heritage']) {
    assert.equal(Object.hasOwn(adapted, field), false)
  }
})
for (const field of publicVillageFields) {
  test(`missing public field ${field} is invalid rather than fabricated`, () => {
    const dto = village(); delete dto[field]
    assert.throws(() => adaptPublicVillage(dto), TypeError)
  })
}
for (const patch of [
  { id: '42' }, { id: 0 }, { id: -1 }, { id: 1.5 }, { id: 9007199254740992 },
  { governorateId: null }, { governorateId: '2' }, { governorateId: 9007199254740992 },
  { localAuthorityId: 0 }, { localAuthorityId: '7' }, { localAuthorityId: 9007199254740992 },
  { nameAr: null }, { nameAr: '' }, { nameAr: 42 }, { nameEn: 7 },
  { latitude: '31' }, { latitude: 91 }, { longitude: 181 }, { latitude: Infinity },
  { latitude: undefined }, { longitude: undefined }, { longitude: NaN },
]) {
  test(`invalid public field values fail closed: ${JSON.stringify(patch)}`, () => {
    assert.throws(() => adaptPublicVillage(village(patch)), TypeError)
  })
}
for (const dto of [null, [], 'village', { ...village(), status: 'VERIFIED' }, { ...village(), population: 100 }]) {
  test('non-public or expanded DTO cannot silently become approved public content', () => {
    assert.throws(() => adaptPublicVillage(dto), TypeError)
  })
}
for (const patch of [{}, { latitude: null, longitude: 35 }, { latitude: 31, longitude: null }]) {
  test(`nullable coordinates preserve wire values without a fabricated point: ${JSON.stringify(patch)}`, () => {
    const dto = village(patch), adapted = adaptPublicVillage(dto)
    assert.equal(adapted.coordinates, null)
    assert.equal(adapted.latitude, dto.latitude)
    assert.equal(adapted.longitude, dto.longitude)
    assert.equal(adapted.nameEn, null)
    assert.equal(adapted.localAuthorityId, null)
  })
}
test('safe integer boundaries remain numeric on the adapted public model', () => {
  const dto = village({ id: Number.MAX_SAFE_INTEGER, governorateId: Number.MAX_SAFE_INTEGER, localAuthorityId: Number.MAX_SAFE_INTEGER })
  const adapted = adaptPublicVillage(dto)
  assert.equal(adapted.backendId, Number.MAX_SAFE_INTEGER)
  assert.equal(adapted.localAuthorityId, Number.MAX_SAFE_INTEGER)
})
test('public pagination preserves all verified metadata and converts page zero to UI page one', () => {
  const result = adaptPublicVillagePage(page())
  assert.equal(result.page, 1)
  assert.equal(result.pageSize, 6)
  assert.equal(result.total, 1)
  assert.equal(result.totalPages, 1)
  assert.equal(result.items[0].backendId, 42)
  for (const field of ['numberOfElements', 'first', 'last', 'hasNext', 'hasPrevious', 'empty']) assert.equal(result[field], page()[field])
})
for (const patch of [{ first: false }, { last: false }, { hasNext: true }, { hasPrevious: true }, { empty: true },
  { numberOfElements: 2 }, { totalElements: 2 }, { totalPages: 2 }, { number: -1 }, { size: 0 }, { content: [{}] }]) {
  test(`inconsistent public page metadata or content rejects the whole page: ${JSON.stringify(patch)}`, () => {
    assert.throws(() => adaptPublicVillagePage(page(patch)), TypeError)
  })
}

const detail = patch => ({ ...village(), nameOrigin: null, locationDescription: 'موقع تجريبي',
  elevationM: null, areaKm2: null, ...patch })

test('public list and detail field allowlists remain separate and frozen', () => {
  assert.deepEqual(publicVillageFields, ['id', 'nameAr', 'nameEn', 'governorateId', 'localAuthorityId', 'latitude', 'longitude'])
  assert.deepEqual(publicVillageDetailFields, [...publicVillageFields, 'nameOrigin', 'locationDescription', 'elevationM', 'areaKm2'])
  assert.equal(Object.isFrozen(publicVillageFields), true)
  assert.equal(Object.isFrozen(publicVillageDetailFields), true)
})

test('public detail preserves all eleven wire fields and exposes only the approved descriptors', () => {
  const dto = detail({ nameEn: ' Battir ', localAuthorityId: 7, latitude: 0, longitude: 35.123456,
    nameOrigin: '  أصل الاسم  ', locationDescription: '  وصف الموقع  ', elevationM: -430.25, areaKm2: 12.345 })
  const adapted = adaptPublicVillageDetail(dto)

  assert.equal(adapted.id, '42')
  assert.equal(adapted.backendId, 42)
  assert.equal(adapted.name, dto.nameAr)
  for (const field of publicVillageDetailFields.filter(field => field !== 'id')) assert.equal(adapted[field], dto[field])
  assert.deepEqual(adapted.coordinates, [0, 35.123456])
  assert.deepEqual(Object.keys(adapted).sort(), ['id', 'backendId', 'name', 'nameAr', 'nameEn', 'governorateId',
    'localAuthorityId', 'latitude', 'longitude', 'coordinates', 'nameOrigin', 'locationDescription', 'elevationM', 'areaKm2'].sort())
  assert.equal(Object.isFrozen(adapted), true)
  for (const field of ['description', 'status', 'createdAt', 'updatedAt', 'archivedAt', 'governorate', 'localAuthority', 'history', 'population', 'sourceIds']) {
    assert.equal(Object.hasOwn(adapted, field), false)
  }
})

test('public detail keeps all nullable descriptors and existing nullable fields null without invented values', () => {
  const dto = detail(), adapted = adaptPublicVillageDetail(dto)
  for (const field of ['nameEn', 'localAuthorityId', 'latitude', 'longitude', 'coordinates', 'nameOrigin', 'elevationM', 'areaKm2']) {
    assert.equal(adapted[field], null)
  }
  assert.equal(adapted.locationDescription, dto.locationDescription)
  assert.equal(adapted.nameAr, dto.nameAr)
})

for (const nameOrigin of ['', ' ', '\t\n']) {
  test(`public detail preserves optional nameOrigin text verbatim: ${JSON.stringify(nameOrigin)}`, () => {
    assert.equal(adaptPublicVillageDetail(detail({ nameOrigin })).nameOrigin, nameOrigin)
  })
}

for (const elevationM of [-430.25, -1, 0, 0.5, 8848.86]) {
  test(`public detail accepts a finite elevation without losing zero or its sign: ${elevationM}`, () => {
    assert.equal(adaptPublicVillageDetail(detail({ elevationM })).elevationM, elevationM)
  })
}

for (const areaKm2 of [0.001, 1, 12.345]) {
  test(`public detail preserves positive finite area: ${areaKm2}`, () => {
    assert.equal(adaptPublicVillageDetail(detail({ areaKm2 })).areaKm2, areaKm2)
  })
}

for (const patch of [{ latitude: 0, longitude: 0 }, { latitude: -90, longitude: 180 },
  { latitude: 31.123456, longitude: 35.654321 }, { latitude: null, longitude: 35 }, { latitude: 31, longitude: null }]) {
  test(`public detail reuses coordinate validation and nullable point behavior: ${JSON.stringify(patch)}`, () => {
    const adapted = adaptPublicVillageDetail(detail(patch))
    assert.equal(adapted.latitude, patch.latitude)
    assert.equal(adapted.longitude, patch.longitude)
    assert.deepEqual(adapted.coordinates, patch.latitude === null || patch.longitude === null ? null : [patch.latitude, patch.longitude])
  })
}

for (const field of publicVillageDetailFields) {
  test(`public detail rejects missing required wire key ${field}`, () => {
    const dto = detail(); delete dto[field]
    assert.throws(() => adaptPublicVillageDetail(dto), TypeError)
  })
  test(`public detail rejects undefined wire value ${field} instead of substituting null`, () => {
    assert.throws(() => adaptPublicVillageDetail(detail({ [field]: undefined })), TypeError)
  })
}

for (const [index, patch] of [
  { nameOrigin: 7 }, { nameOrigin: false }, { nameOrigin: [] }, { nameOrigin: {} },
  { locationDescription: null }, { locationDescription: '' }, { locationDescription: ' ' }, { locationDescription: '\t\n' },
  { locationDescription: 7 }, { locationDescription: false }, { locationDescription: [] }, { locationDescription: {} },
  { elevationM: '0' }, { elevationM: false }, { elevationM: [] }, { elevationM: {} },
  { elevationM: NaN }, { elevationM: Infinity }, { elevationM: -Infinity },
  { areaKm2: 0 }, { areaKm2: -1 }, { areaKm2: -0.001 }, { areaKm2: '12.345' },
  { areaKm2: false }, { areaKm2: [] }, { areaKm2: {} }, { areaKm2: NaN }, { areaKm2: Infinity }, { areaKm2: -Infinity },
  { id: '42' }, { id: 0 }, { id: Number.MAX_SAFE_INTEGER + 1 }, { governorateId: null }, { localAuthorityId: 0 },
  { nameAr: ' ' }, { nameEn: 7 }, { latitude: '31' }, { latitude: 91 }, { longitude: 181 }, { latitude: Infinity }, { longitude: NaN },
].entries()) {
  test(`public detail rejects invalid wire values ${index}: ${JSON.stringify(patch)}`, () => {
    assert.throws(() => adaptPublicVillageDetail(detail(patch)), TypeError)
  })
}

for (const field of ['status', 'createdAt', 'updatedAt', 'archivedAt', 'description', 'history', 'population', 'governorate', 'sourceIds']) {
  test(`public detail rejects an additional unapproved field ${field}`, () => {
    assert.throws(() => adaptPublicVillageDetail({ ...detail(), [field]: null }), TypeError)
  })
}

for (const dto of [null, undefined, [], 42, 'village']) {
  test(`public detail rejects a non-object response: ${JSON.stringify(dto)}`, () => {
    assert.throws(() => adaptPublicVillageDetail(dto), TypeError)
  })
}

test('seven-field list items, eleven-field details and list pages cannot silently exchange contracts', () => {
  assert.throws(() => adaptPublicVillageDetail(village()), TypeError)
  assert.throws(() => adaptPublicVillage(detail()), TypeError)
  assert.throws(() => adaptPublicVillagePage(page({ content: [detail()] })), TypeError)
  assert.throws(() => adaptPublicVillageDetail(page()), TypeError)
  assert.equal(adaptPublicVillage(village()).backendId, 42)
  assert.equal(adaptPublicVillageDetail(detail()).backendId, 42)
  assert.equal(adaptPublicVillagePage(page()).items[0].backendId, 42)
})
