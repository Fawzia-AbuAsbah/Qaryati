import assert from 'node:assert/strict'
import test from 'node:test'
import { adaptPublicVillage, publicVillageFields } from './publicVillageAdapters.js'
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
