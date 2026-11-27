import assert from 'node:assert/strict'
import test from 'node:test'
import { getVillage, hasCoordinates, listMapVillages, listVillages } from './villageService.js'

test('mock home, explorer, detail and map data remain usable without live requests', async t => {
  t.mock.method(globalThis, 'fetch', () => { throw new Error('Mock mode must not request the API') })
  const home = await listVillages({ featured: true })
  assert.deepEqual(home.items.map(item => item.id), ['battir', 'lifta', 'deir-ghassaneh'])
  const first = await listVillages()
  const second = await listVillages({ page: 2 })
  assert.equal(first.total, 8)
  assert.equal(first.page, 1)
  assert.equal(first.items.length, 6)
  assert.equal(second.page, 2)
  assert.equal(second.items.length, 2)
  assert.equal(first.totalPages, 2)
  assert.ok(first.governorates.includes('بيت لحم'))
  assert.deepEqual((await listVillages({ query: 'بتير', governorate: 'بيت لحم' })).items.map(item => item.id), ['battir'])
  assert.equal((await listVillages({ query: 'no matching village' })).total, 0)
  const detail = await getVillage('battir')
  assert.equal(detail.name, 'بتير')
  assert.equal(detail.contentStatus, 'demo')
  assert.equal(await getVillage('missing'), null)
  const map = await listMapVillages()
  assert.equal(map.length, 8)
  assert.equal(map.filter(hasCoordinates).length, 7)
  assert.equal(hasCoordinates(map.find(item => item.id === 'beita')), false)
  assert.equal(globalThis.fetch.mock.callCount(), 0)
})
