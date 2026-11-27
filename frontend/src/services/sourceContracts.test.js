import assert from 'node:assert/strict'
import test from 'node:test'
import { adaptSource, adaptSourceCatalog, adaptSourceLinks, sourceBody, sourceErrorMessage, sourceFieldErrors, sourceLocalDate, sourceReplacement, sourceTypes } from './sourceContracts.js'
import { sourcePermissions, sourceVillageIdentity } from './sourceAccess.js'

const body = { title: 'مرجع القرية', publisher: null, author: null, sourceType: 'BOOK', url: null, publicationDate: null, accessedAt: null, citation: null }
const dto = { id: 12, ...body, createdAt: '2026-11-27T09:00:00.123456+02:00' }
const session = { status: 'authenticated', user: { username: 'fixture', roles: ['ADMIN'], rolesVerified: true }, expiresAt: Date.now() + 3600000 }

test('SourceResponse mapping preserves all nullable fields and exact metadata without fabricating information', () => {
  const source = adaptSource(dto)
  assert.deepEqual(source, { ...dto, id: '12', backendId: 12 })
  const rich = { ...dto, publisher: '', author: '  كاتب  ', url: 'https://example.test/book?q=a#chapter', publicationDate: '-0044-03-15', accessedAt: '2024-02-29', citation: 'النص\nالثاني' }
  assert.deepEqual(adaptSource(rich), { ...rich, id: '12', backendId: 12 })
  assert.equal('verificationStatus' in source, false)
  assert.deepEqual(adaptSourceCatalog([]), [])
  assert.deepEqual(adaptSourceLinks([]), [])
})

test('source enum matches the verified backend enum', () => {
  assert.deepEqual(sourceTypes, ['CENSUS', 'GOVERNMENT', 'BOOK', 'ARTICLE', 'ARCHIVE', 'WEBSITE', 'INTERVIEW', 'OTHER'])
  for (const sourceType of sourceTypes) assert.equal(sourceBody({ ...body, sourceType }).sourceType, sourceType)
})

for (const patch of [{ title: '' }, { title: '  ' }, { title: 'a'.repeat(351) }, { title: null }, { publisher: 1 }, { publisher: 'a'.repeat(201) },
  { author: 'a'.repeat(201) }, { sourceType: 'book' }, { sourceType: null }, { url: '' }, { url: 'javascript:alert(1)' }, { url: 'ftp://example.test' },
  { url: 'https://example.test/a b' }, { url: '/local' }, { url: 'https://example.test\\evil' }, { publicationDate: '2023-02-29' },
  { accessedAt: '2024-04-31' }, { publicationDate: '' }, { citation: {} }, { citation: undefined }]) {
  test(`invalid source data fails before HTTP: ${JSON.stringify(patch)}`, () => {
    assert.ok(Object.keys(sourceFieldErrors({ ...body, ...patch })).length)
    assert.throws(() => sourceBody({ ...body, ...patch }), { code: 'SOURCE_VALIDATION' })
  })
}

test('field boundaries use backend lengths without imposing limits on citation/claim free text', () => {
  const allowed = { ...body, title: 'a'.repeat(350), publisher: 'a'.repeat(200), author: 'a'.repeat(200), citation: 'a'.repeat(10000) }
  assert.deepEqual(sourceBody(allowed), allowed)
  assert.deepEqual(sourceBody({ ...body, extra: 'ignored', id: 99 }), body)
})

test('LocalDate strings retain historical years and never convert timezone or invent missing dates', () => {
  for (const value of [null, '0000-02-29', '1900-02-28', '2000-02-29', '-0044-03-15', '+10000-01-01']) assert.equal(sourceLocalDate(value), value)
  for (const value of [undefined, '1900-02-29', '2024-00-01', '2024-13-01', '2024-01-00', '2024', '+0001-01-01', '-0000-01-01', '10000-01-01', 2024]) assert.throws(() => sourceLocalDate(value))
})

test('incomplete or malformed source reads cannot become destructive PUT baselines', () => {
  for (const field of [...Object.keys(body), 'createdAt']) {
    const partial = { ...dto }; delete partial[field]
    assert.throws(() => adaptSource(partial))
  }
  for (const invalid of [null, [], { ...dto, id: '12' }, { ...dto, id: 0 }, { ...dto, id: Number.MAX_SAFE_INTEGER + 1 }, { ...dto, createdAt: 'yesterday' }]) assert.throws(() => adaptSource(invalid))
  assert.throws(() => adaptSourceCatalog({ content: [dto] }))
  assert.throws(() => adaptSourceCatalog([dto, dto]))
})

test('PUT replacement preserves every untouched optional value; clearing requires explicit null', () => {
  const original = adaptSource({ ...dto, publisher: 'ناشر', author: '', url: 'https://example.test', publicationDate: '-0044-03-15', accessedAt: '2024-02-29', citation: 'citation\ntext' })
  const replacement = sourceReplacement(original, { title: 'عنوان جديد' })
  assert.deepEqual(replacement, { ...sourceBody(original), title: 'عنوان جديد' })
  assert.equal(replacement.author, '')
  assert.equal(sourceReplacement(original, { publisher: null }).publisher, null)
  assert.equal(sourceReplacement(original, {}).citation, original.citation)
  assert.throws(() => sourceReplacement(original, { citation: undefined }), { code: 'SOURCE_VALIDATION' })
  assert.throws(() => sourceReplacement(original, { createdAt: null }))
  assert.throws(() => sourceReplacement({ ...original, backendId: 13 }, {}))
  const partial = { ...original }; delete partial.url
  assert.throws(() => sourceReplacement(partial, {}))
  assert.equal(replacement.id, undefined)
  assert.equal(replacement.createdAt, undefined)
})

test('village/source links preserve exact notes and numeric identities without names or inferred statuses', () => {
  assert.deepEqual(adaptSourceLinks([{ villageId: 42, sourceId: 12, claimNote: '  ملاحظة\n' }], { villageId: '42' }), [{ villageId: '42', sourceId: '12', claimNote: '  ملاحظة\n' }])
  assert.equal(adaptSourceLinks([{ villageId: 42, sourceId: 12, claimNote: null }])[0].claimNote, null)
  for (const rows of [[{ villageId: '42', sourceId: 12, claimNote: null }], [{ villageId: 42, sourceId: 12 }],
    [{ villageId: 42, sourceId: 12, claimNote: 4 }], [{ villageId: 42, sourceId: 12, claimNote: null }, { villageId: 42, sourceId: 12, claimNote: null }]]) assert.throws(() => adaptSourceLinks(rows))
  assert.throws(() => adaptSourceLinks([{ villageId: 42, sourceId: 12, claimNote: null }], { villageId: '43' }))
  assert.throws(() => adaptSourceLinks([{ villageId: 42, sourceId: 12, claimNote: null }], { sourceId: '13' }))
})

test('the deployed role contract blocks every Sources read and write, even with fabricated caller claims', () => {
  assert.deepEqual(sourcePermissions(session), { read: false, write: false, reason: 'SOURCE_ROLES_UNAVAILABLE' })
  assert.equal(sourcePermissions(session, { rolesContractVerified: 'true' }).read, false)
  assert.equal(sourcePermissions({ ...session, status: 'anonymous' }).reason, 'AUTH_REQUIRED')
  assert.equal(sourcePermissions({ ...session, expiresAt: Date.now() - 1 }).reason, 'AUTH_REQUIRED')
  assert.equal(sourcePermissions({ ...session, user: null }).read, false)
})

test('isolated verified-role fixtures enforce backend VIEWER/EDITOR/ADMIN permissions', () => {
  for (const [roles, read, write] of [[['VIEWER'], true, false], [['EDITOR'], true, true], [['ADMIN'], true, true], [[], false, false], [['UNKNOWN'], false, false], [['ROLE_ADMIN'], false, false]]) {
    const permissions = sourcePermissions({ ...session, user: { ...session.user, roles } }, { rolesContractVerified: true })
    assert.equal(permissions.read, read)
    assert.equal(permissions.write, write)
  }
  for (const user of [{ username: 'admin' }, { username: 'admin', roles: ['ADMIN'], rolesVerified: false }, { username: 'admin', roles: 'ADMIN', rolesVerified: true }]) {
    assert.equal(sourcePermissions({ ...session, user }, { rolesContractVerified: true }).read, false)
  }
})

test('village-specific panels reject mock slugs, unavailable integrations and mismatched/unsafe numeric IDs', () => {
  const api = { mode: 'api', available: true }
  assert.equal(sourceVillageIdentity({ id: '42', backendId: 42 }, api), '42')
  for (const village of [null, { id: 'battir' }, { id: '042', backendId: 42 }, { id: '42', backendId: 43 }, { id: '0', backendId: 0 }, { id: '9007199254740992', backendId: 9007199254740992 }]) assert.equal(sourceVillageIdentity(village, api), null)
  assert.equal(sourceVillageIdentity({ id: '42', backendId: 42 }, { mode: 'mock', available: true }), null)
  assert.equal(sourceVillageIdentity({ id: '42', backendId: 42 }, { ...api, available: false }), null)
})

test('Arabic error messages distinguish forbidden, missing, duplicate and uncertain mutation outcomes without echoing backend data', () => {
  for (const error of [{ status: 400 }, { status: 401 }, { status: 403 }, { status: 404 }, { status: 409 }, { kind: 'network' }, { code: 'SOURCE_ROLES_UNAVAILABLE' }]) {
    const message = sourceErrorMessage({ ...error, message: 'sensitive server content' })
    assert.match(message, /[\u0600-\u06ff]/)
    assert.equal(message.includes('sensitive server content'), false)
  }
  assert.match(sourceErrorMessage({ status: 409 }), /لم تُستبدل/)
  assert.match(sourceErrorMessage({ kind: 'network' }, true), /قد يكون الطلب قد نُفّذ/)
})
