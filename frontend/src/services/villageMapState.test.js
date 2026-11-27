import assert from 'node:assert/strict'
import test from 'node:test'
import { mapPresentation, mapSelectionMessage, villageMapHref, mapErrorMessage } from './villageMapState.js'
import { villageDetailsHref, detailsReturnNavigation } from './villageDetailsState.js'
import { createVillageMapService, villageMapService } from './villageMapService.js'
import { createVillageExplorerService } from './villageExplorerService.js'
import { createVillageDetailsService } from './villageDetailsService.js'

const ready = { mode: 'api', available: true }
const rows = [{ id: '42', name: 'بتير', coordinates: [31, 35] }, { id: '43', name: null, coordinates: null }, { id: '44', coordinates: [0, 0] }]
const data = { items: rows, total: 3, complete: true }

test('markers include only valid coordinates without dropping missing-location villages from the complete index', () => {
  const result = mapPresentation(data, '42')
  assert.deepEqual(result.markers.map(item => item.id), ['42', '44'])
  assert.equal(result.selected, rows[0])
  assert.equal(result.selection, 'selected')
  assert.equal(data.items.length, 3)
  assert.equal(data.items[1].coordinates, null)
})

test('invalid numeric coordinate values never produce markers', () => {
  const items = [[91, 0], [0, 181], [NaN, 0], [0, Infinity], ['31', 35], [null, null], []].map((coordinates, i) => ({ id: String(i + 1), coordinates }))
  assert.deepEqual(mapPresentation({ items, total: items.length, complete: true }).markers, [])
})

test('empty, absent, missing-location and unknown selections have distinct states', () => {
  assert.equal(mapPresentation(null, '42').selection, 'pending')
  assert.equal(mapPresentation(data, null).selection, 'none')
  assert.equal(mapPresentation(data, '43').selection, 'no-coordinates')
  assert.equal(mapPresentation(data, '99').selection, 'not-found')
  const empty = mapPresentation({ items: [], total: 0, complete: true }, null)
  assert.deepEqual(empty.markers, [])
  assert.equal(empty.selection, 'none')
})

for (const id of ['battir', '042', '0', '-1', '42/other', '9007199254740992', 42]) {
  test(`invalid API selected identifier ${String(id)} does not select an arbitrary marker`, () => {
    assert.equal(mapPresentation(data, id).selection, 'invalid')
    assert.equal(mapPresentation(data, id).selected, null)
  })
}

test('partial or mismatched datasets cannot be presented as complete', () => {
  for (const dataset of [{ ...data, complete: false }, { ...data, total: 4 }, { ...data, items: null }]) {
    assert.throws(() => mapPresentation(dataset, '42'), { code: 'INCOMPLETE_MAP' })
  }
})

test('Explorer/Details to Map links require matching available integrations and valid coordinates', () => {
  const explorer = createVillageExplorerService({ mode: 'api', publicationVerified: true })
  const details = createVillageDetailsService({ mode: 'api', publicationVerified: true })
  const map = createVillageMapService({ mode: 'api', publicationVerified: true })
  assert.equal(villageMapHref(rows[0], explorer, map), '/map?village=42')
  assert.equal(villageMapHref(rows[0], details, map), '/map?village=42')
  assert.equal(villageMapHref(rows[1], details, map), null)
  assert.equal(villageMapHref(rows[0], details, createVillageMapService({ mode: 'api' })), null)
  assert.equal(villageMapHref(rows[0], details, villageMapService), null)
  assert.equal(villageMapHref({ ...rows[0], id: 'battir' }, details, map), null)
  assert.equal(villageMapHref(rows[0], { ...ready, available: false }, map), null)
})

test('Map selection, popup Details navigation and return navigation retain numeric IDs and query parameters', () => {
  const mapHref = villageMapHref(rows[0], ready, ready)
  const selectedId = new URL(mapHref, 'https://frontend.example.test').searchParams.get('village')
  const selected = mapPresentation(data, selectedId).selected
  assert.equal(villageDetailsHref(selected.id, ready, ready), '/villages/42')
  const mapReturnTo = `${mapHref}&campaign=demo&tag=a&tag=b`
  assert.deepEqual(detailsReturnNavigation({ mapReturnTo }), { href: mapReturnTo, label: 'خريطة القرى' })
  const explorerReturnTo = '/villages?q=بتير&governorateId=2&page=2'
  assert.deepEqual(detailsReturnNavigation({ explorerReturnTo }), { href: explorerReturnTo, label: 'دليل القرى' })
  for (const mapReturnTo of ['//evil.example', 'https://evil.example', '/map-other', '/map/42']) {
    assert.equal(detailsReturnNavigation({ mapReturnTo, explorerReturnTo }).href, explorerReturnTo)
  }
})

test('mock slug selection and navigation remain separate from API numeric identifiers', () => {
  const mock = { mode: 'mock', available: true }
  const village = { id: 'battir', coordinates: [31, 35] }
  assert.equal(villageMapHref(village, mock, mock), '/map?village=battir')
  assert.equal(mapPresentation({ items: [village], total: 1, complete: true }, 'battir', 'mock').selected, village)
  assert.equal(villageDetailsHref('battir', mock, mock), '/villages/battir')
  assert.equal(villageMapHref(village, mock, ready), null)
})

test('map errors and selection notices report incomplete data and missing locations without echoing backend details', () => {
  for (const code of ['INCOMPLETE_MAP', 'MAP_DUPLICATE_IDS', 'MAP_DATA_CHANGED', 'MAP_LIMIT_EXCEEDED']) {
    const message = mapErrorMessage({ code, message: 'Sensitive backend text' })
    assert.match(message, /خريطة/)
    assert.equal(message.includes('Sensitive backend text'), false)
  }
  assert.notEqual(mapErrorMessage({ status: 401 }), mapErrorMessage({ kind: 'network' }))
  for (const selection of ['invalid', 'not-found', 'no-coordinates']) assert.match(mapSelectionMessage(selection), /القرية/)
  assert.equal(mapSelectionMessage('selected'), null)
})
