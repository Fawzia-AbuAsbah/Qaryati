import assert from 'node:assert/strict'
import test from 'node:test'
import { clearExplorerQuery, readExplorerQuery, updateExplorerQuery } from './explorerQuery.js'

test('URL state preserves Arabic search, route IDs, sorting and one-based pagination', () => {
  const params = new URLSearchParams({ q: 'بَتّير & أرطاس', governorateId: '2', governorate: 'بيت لحم', page: '3', sortBy: 'nameAr', sortDir: 'desc' })
  assert.deepEqual(readExplorerQuery(params), { query: 'بَتّير & أرطاس', governorateId: '2', governorate: 'بيت لحم', localAuthorityId: '', page: 3, sortBy: 'nameAr', sortDir: 'desc' })
})

test('missing and invalid page parameters use page one, preserving other query values', () => {
  for (const page of ['', '0', '-1', '1.5', 'bad', '9007199254740992']) {
    assert.equal(readExplorerQuery(new URLSearchParams({ page })).page, 1)
  }
  assert.deepEqual(readExplorerQuery(new URLSearchParams()), { query: '', governorate: '', governorateId: '', localAuthorityId: '', page: 1, sortBy: 'id', sortDir: 'asc' })
})

test('pagination and last-page correction preserve every unrelated parameter and repeated value', () => {
  const original = new URLSearchParams('q=بتير&governorateId=2&sortBy=nameAr&sortDir=desc&page=99&tag=a&tag=b&campaign=demo')
  const next = updateExplorerQuery(original, 'page', 3)
  assert.equal(next.get('page'), '3')
  for (const key of ['q', 'governorateId', 'localAuthorityId', 'sortBy', 'sortDir', 'tag', 'campaign']) assert.deepEqual(next.getAll(key), original.getAll(key))
  assert.equal(original.get('page'), '99')
})

test('changing filters or sorting resets pagination without discarding other parameters', () => {
  for (const key of ['q', 'governorate', 'governorateId', 'localAuthorityId', 'sortBy', 'sortDir']) {
    const original = new URLSearchParams('page=4&campaign=demo&tag=a&tag=b')
    const next = updateExplorerQuery(original, key, 'new-value')
    assert.equal(next.has('page'), false)
    assert.equal(next.get(key), 'new-value')
    assert.equal(next.get('campaign'), 'demo')
    assert.deepEqual(next.getAll('tag'), ['a', 'b'])
    assert.equal(original.get('page'), '4')
    assert.equal(updateExplorerQuery(next, key, '').has(key), false)
  }
})

test('clearing filters preserves unrelated URL parameters and does not mutate input', () => {
  const params = new URLSearchParams('q=بتير&governorate=بيت+لحم&governorateId=2&page=4&sortBy=nameAr&sortDir=desc&campaign=demo&tag=a&tag=b')
  assert.equal(clearExplorerQuery(params).toString(), 'campaign=demo&tag=a&tag=b')
  assert.equal(params.get('page'), '4')
})

test('changing governorate selection removes incompatible geographic filters and preserves unrelated parameters', () => {
  const params = new URLSearchParams('governorate=بيت+لحم&governorateId=2&localAuthorityId=7&campaign=demo')
  assert.equal(updateExplorerQuery(params, 'governorateId', '9').has('governorate'), false)
  assert.equal(updateExplorerQuery(params, 'governorateId', '9').has('localAuthorityId'), false)
  assert.equal(updateExplorerQuery(params, 'governorate', 'نابلس').has('governorateId'), false)
  assert.equal(updateExplorerQuery(params, 'governorateId', '').get('campaign'), 'demo')
  assert.equal(readExplorerQuery(new URLSearchParams('sortDir=DESC')).sortDir, 'desc')
})

for (const sortBy of ['createdAt', 'updatedAt']) {
  test(`obsolete URL sort ${sortBy} safely uses public ID sorting without mutating the URL`, () => {
    const params = new URLSearchParams({ sortBy, governorateId: '2', localAuthorityId: '7', page: '3' })
    const options = readExplorerQuery(params)
    assert.equal(options.sortBy, 'id')
    assert.equal(options.page, 3)
    assert.equal(options.governorateId, '2')
    assert.equal(options.localAuthorityId, '7')
    assert.equal(params.get('sortBy'), sortBy)
    assert.equal(clearExplorerQuery(params).has('localAuthorityId'), false)
  })
}
