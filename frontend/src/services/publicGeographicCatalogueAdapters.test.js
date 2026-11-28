import assert from 'node:assert/strict'
import test from 'node:test'
import {
  adaptPublicGovernorateCatalogue,
  adaptPublicLocalAuthorityCatalogue,
  publicGovernorateFields,
  publicLocalAuthorityFields,
} from './publicGeographicCatalogueAdapters.js'

const governorate = (patch = {}) => ({ id: 2, nameAr: 'بيت لحم', nameEn: 'Bethlehem', ...patch })
const authority = (patch = {}) => ({ id: 7, nameAr: 'مجلس بيت لحم', nameEn: null, governorateId: 2, ...patch })

test('public geographic catalogues retain only the confirmed DTO fields and numeric identities', () => {
  const governorates = adaptPublicGovernorateCatalogue([governorate()])
  const authorities = adaptPublicLocalAuthorityCatalogue([authority()])
  assert.deepEqual(publicGovernorateFields, ['id', 'nameAr', 'nameEn'])
  assert.deepEqual(publicLocalAuthorityFields, ['id', 'nameAr', 'nameEn', 'governorateId'])
  assert.deepEqual(governorates.items, [{ id: '2', backendId: 2, nameAr: 'بيت لحم', nameEn: 'Bethlehem', name: 'بيت لحم' }])
  assert.deepEqual(authorities.items, [{ id: '7', backendId: 7, nameAr: 'مجلس بيت لحم', nameEn: null, name: 'مجلس بيت لحم', governorateId: 2 }])
  assert.equal(governorates.namesById.get(2), 'بيت لحم')
  assert.equal(authorities.namesById.get(7), 'مجلس بيت لحم')
  assert.equal(Object.isFrozen(governorates.items[0]), true)
})

test('English names remain usable when a public catalogue row has no Arabic name', () => {
  const governorates = adaptPublicGovernorateCatalogue([governorate({ nameAr: null })])
  assert.equal(governorates.items[0].name, 'Bethlehem')
})

for (const payload of [null, {}, [governorate({ code: 'private' })], [governorate({ id: 0 })],
  [governorate({ nameAr: '' })], [governorate({ nameAr: null, nameEn: null })],
  [governorate(), governorate()]]) {
  test(`invalid public governorate catalogue is rejected: ${JSON.stringify(payload)}`, () => {
    assert.throws(() => adaptPublicGovernorateCatalogue(payload), TypeError)
  })
}

for (const payload of [null, {}, [authority({ authorityType: 'MUNICIPALITY' })], [authority({ governorateId: 0 })],
  [authority({ id: '7' })], [authority(), authority()]]) {
  test(`invalid public local-authority catalogue is rejected: ${JSON.stringify(payload)}`, () => {
    assert.throws(() => adaptPublicLocalAuthorityCatalogue(payload), TypeError)
  })
}
