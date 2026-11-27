import assert from 'node:assert/strict'
import test from 'node:test'
import { detailsErrorMessage, detailsPageTitle, explorerReturnHref, villageDetailsHref } from './villageDetailsState.js'
import { createVillageExplorerService, villageExplorerService } from './villageExplorerService.js'
import { createVillageDetailsService, villageDetailsService } from './villageDetailsService.js'
import { createApiClient } from './apiClient.js'

test('Explorer connects numeric API cards only to an available matching Details integration', async t => {
  const fetchImpl = t.mock.fn(async url => {
    assert.equal(url, '/api/v1/villages/42')
    return new Response(JSON.stringify({ id: 42, nameAr: 'بتير' }))
  })
  const request = createApiClient({ fetchImpl })
  const explorer = createVillageExplorerService({ mode: 'api', publicationVerified: true, request })
  const details = createVillageDetailsService({ mode: 'api', publicationVerified: true, request })
  const href = villageDetailsHref('42', explorer, details)
  assert.equal(href, '/villages/42')
  assert.equal((await details.getVillage(href.split('/').at(-1))).name, 'بتير')
  const closed = createVillageDetailsService({ mode: 'api', request })
  assert.equal(villageDetailsHref('42', explorer, closed), null)
  assert.equal(villageDetailsHref('42', closed, details), null)
  assert.equal(villageDetailsHref('battir', explorer, details), null)
  assert.equal(villageDetailsHref('42', villageExplorerService, details), null)
  assert.equal(fetchImpl.mock.callCount(), 1)
})

test('existing mock Explorer/Details slug navigation remains usable and never changes to numeric API data', async () => {
  const href = villageDetailsHref('battir', villageExplorerService, villageDetailsService)
  assert.equal(href, '/villages/battir')
  assert.equal((await villageDetailsService.getVillage(href.split('/').at(-1))).contentStatus, 'demo')
})

test('return navigation retains the Explorer query exactly and rejects other destinations', () => {
  const from = '/villages?q=بتير&governorateId=2&page=3&sortBy=nameAr&tag=a&tag=b'
  assert.equal(explorerReturnHref(from), from)
  for (const invalid of [null, undefined, '/map', '/villages/42', '//evil.example', 'https://evil.example', '/villages-other']) {
    assert.equal(explorerReturnHref(invalid), '/villages')
  }
})

test('page titles track loading, selected village, missing names, invalid IDs, errors and not-found states', () => {
  const old = { id: '42', name: 'بتير' }
  const selected = { id: '43', name: 'أرطاس' }
  assert.equal(detailsPageTitle({ loading: true, village: old }), 'جارٍ تحميل القرية | قريتي')
  assert.equal(detailsPageTitle({ village: selected }), 'أرطاس | قريتي')
  assert.equal(detailsPageTitle({ village: { id: '43', name: null } }), 'اسم القرية غير متاح | قريتي')
  assert.equal(detailsPageTitle({ error: { code: 'INVALID_VILLAGE_ID' } }), 'معرّف القرية غير صالح | قريتي')
  assert.equal(detailsPageTitle({ error: { status: 401 } }), 'تعذّر تحميل القرية | قريتي')
  assert.equal(detailsPageTitle({ village: null }), 'القرية غير موجودة | قريتي')
})

test('unavailable, forbidden, not-found and network failures remain distinct from empty state messages', () => {
  const cases = [{ code: 'PUBLIC_CONTRACTS_UNCONFIRMED' }, { code: 'INVALID_MODE' }, { code: 'INVALID_VILLAGE_ID' },
    { code: 'UNAVAILABLE_SECTION' }, { code: 'PAGINATION_CHANGED' }, { status: 401 }, { status: 403 }, { status: 404 }, { kind: 'network' }, {}]
  const messages = cases.map(error => detailsErrorMessage({ ...error, message: 'Sensitive backend text' }))
  assert.equal(new Set(messages).size, cases.length)
  for (const message of messages) {
    assert.match(message, /[\u0600-\u06ff]/)
    assert.equal(message.includes('Sensitive backend text'), false)
  }
})
