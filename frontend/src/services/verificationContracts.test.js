import assert from 'node:assert/strict'
import test from 'node:test'
import { adaptVerification, adaptVerificationHistory, verificationBody, verificationDecisions, verificationEntityType, verificationEntityTypes, verificationErrorMessage } from './verificationContracts.js'
import { verificationPermissions } from './verificationAccess.js'
import { sourceVillageIdentity } from './sourceAccess.js'

const dto = { id: 7, entityType: 'VILLAGE', entityId: 42, reviewerUserId: 12, decision: 'VERIFIED', reason: '  السبب\nالنص  ', reviewedAt: '2026-11-27T10:00:00.123456789+02:00' }
const session = { status: 'authenticated', user: { username: 'profile', roles: null, rolesVerified: false }, expiresAt: Date.now() + 3600000 }

test('verification decisions and entity types match the actual supported backend contracts', () => {
  assert.deepEqual(verificationEntityTypes, ['VILLAGE'])
  assert.deepEqual(verificationDecisions, ['VERIFIED', 'REJECTED', 'NEEDS_REVISION'])
  for (const decision of verificationDecisions) assert.deepEqual(verificationBody('42', decision, null), { entityType: 'VILLAGE', entityId: 42, decision, reason: null })
  assert.equal(verificationEntityType(' VILLAGE '), 'VILLAGE')
})

test('mapping preserves actual reviewer ID, reason and offset/nanosecond timestamp without adding a village status or reviewer name', () => {
  const record = adaptVerification(dto, '42')
  assert.deepEqual(record, { ...dto, id: '7', backendId: 7, entityId: '42' })
  assert.equal(Object.isFrozen(record), true)
  for (const field of ['reviewerName', 'reviewerUsername', 'villageStatus', 'status', 'username']) assert.equal(field in record, false)
  assert.equal(adaptVerification({ ...dto, reason: '' }).reason, '')
  assert.deepEqual(adaptVerification({ ...dto, reviewerUserId: null, reason: null, reviewedAt: null }), { ...record, reviewerUserId: null, reason: null, reviewedAt: null })
  const partial = { ...dto }; delete partial.reviewerUserId; delete partial.reason; delete partial.reviewedAt
  assert.equal(adaptVerification(partial).reviewedAt, null)
  assert.equal(adaptVerification(partial).reviewerUserId, null)
})

test('history preserves supplied ordering and timestamp ties, rather than sorting by record ID or synthesizing dates', () => {
  const rows = [{ ...dto, id: 9 }, { ...dto, id: 3 }, { ...dto, id: 12, reviewedAt: null }]
  assert.deepEqual(adaptVerificationHistory(rows, '42').map(record => record.id), ['9', '3', '12'])
  assert.deepEqual(adaptVerificationHistory([], '42'), [])
  assert.deepEqual(rows.map(row => row.id), [9, 3, 12])
  assert.throws(() => adaptVerificationHistory({ content: rows }, '42'))
  assert.throws(() => adaptVerificationHistory([dto, dto], '42'))
})

for (const entityType of [null, '', ' ', 'village', 'SOURCE', 'HISTORICAL_EVENT', 'VILLAGE'.padEnd(81, ' ')]) {
  test(`unsupported entityType ${JSON.stringify(entityType)} cannot become a verification request`, () => {
    assert.throws(() => verificationEntityType(entityType), { code: 'VERIFICATION_VALIDATION' })
    assert.throws(() => verificationBody('42', 'VERIFIED', null, entityType))
  })
}

for (const id of ['battir', '042', '', '0', '-1', '1.5', '1e2', '42/other', '9007199254740992', 42, null]) {
  test(`invalid route ID ${JSON.stringify(id)} is rejected without coercion`, () => assert.throws(() => verificationBody(id, 'VERIFIED', null)))
}

test('decision and reason validation never invents a required reason or unverified constraint', () => {
  for (const decision of [null, '', 'verified', 'APPROVED', 'DRAFT', 1]) assert.throws(() => verificationBody('42', decision, null), { code: 'VERIFICATION_VALIDATION' })
  for (const reason of [undefined, 1, [], {}]) assert.throws(() => verificationBody('42', 'VERIFIED', reason), { code: 'VERIFICATION_VALIDATION' })
  for (const reason of [null, '', '  ', 'a'.repeat(20000)]) assert.equal(verificationBody('42', 'VERIFIED', reason).reason, reason)
})

test('malformed DTOs and mismatched entities fail instead of appearing as empty history', () => {
  for (const row of [null, [], {}, { ...dto, id: '7' }, { ...dto, id: 0 }, { ...dto, entityId: '42' }, { ...dto, reviewerUserId: '12' },
    { ...dto, reviewerUserId: 0 }, { ...dto, entityType: 'SOURCE' }, { ...dto, decision: 'APPROVED' }, { ...dto, reason: {} }]) assert.throws(() => adaptVerification(row, '42'))
  assert.throws(() => adaptVerification({ ...dto, entityId: 43 }, '42'))
})

test('timestamps are actual valid OffsetDateTime strings and null remains unavailable', () => {
  for (const reviewedAt of ['2026-11-27T10:00:00Z', '2026-11-27T10:00:00-05:30', '2024-02-29T23:59:59+18:00', null]) assert.equal(adaptVerification({ ...dto, reviewedAt }).reviewedAt, reviewedAt)
  for (const reviewedAt of ['', 'today', 1, '2026-02-30T10:00:00Z', '2026-11-27T24:00:00Z', '2026-11-27T10:60:00Z', '2026-11-27T10:00:60Z', '2026-11-27T10:00:00+18:01']) assert.throws(() => adaptVerification({ ...dto, reviewedAt }))
})

test('history permits authenticated users without roles; creation requires verified ADMIN', () => {
  assert.deepEqual(verificationPermissions(session), { read: true, create: false, reason: 'VERIFICATION_ROLES_UNAVAILABLE' })
  assert.equal(verificationPermissions({ ...session, user: { username: 'admin', roles: ['ADMIN'], rolesVerified: true } }).create, true)
  for (const status of ['anonymous', 'authenticating']) assert.equal(verificationPermissions({ ...session, status }).read, false)
  assert.equal(verificationPermissions({ ...session, expiresAt: Date.now() - 1 }).read, false)
})

test('isolated verified-role fixtures permit only ADMIN to create; unknown roles never remove the authenticated read contract', () => {
  for (const roles of [['ADMIN'], ['EDITOR'], ['VIEWER'], [], ['UNKNOWN'], ['ROLE_ADMIN']]) {
    const permissions = verificationPermissions({ ...session, user: { ...session.user, roles, rolesVerified: true } }, { rolesContractVerified: true })
    assert.equal(permissions.read, true)
    assert.equal(permissions.create, roles.length === 1 && roles[0] === 'ADMIN')
  }
  assert.equal(verificationPermissions(session, { rolesContractVerified: true }).create, false)
  assert.equal(verificationPermissions({ ...session, user: { ...session.user, roles: ['ADMIN'], rolesVerified: true } }, { rolesContractVerified: 'true' }).create, false)
})

test('panel identity guard rejects mock IDs and unavailable real-village integrations before protected loading', () => {
  assert.equal(sourceVillageIdentity({ id: 'battir' }, { mode: 'mock', available: true }), null)
  assert.equal(sourceVillageIdentity({ id: '42', backendId: 42 }, { mode: 'api', available: false }), null)
  assert.equal(sourceVillageIdentity({ id: '42', backendId: 43 }, { mode: 'api', available: true }), null)
  assert.equal(sourceVillageIdentity({ id: '42', backendId: 42 }, { mode: 'api', available: true }), '42')
})

test('Arabic verification errors distinguish permissions, invalid data and uncertain POST results without echoing raw details', () => {
  for (const error of [{ status: 400 }, { status: 401 }, { status: 403 }, { status: 404 }, { kind: 'network' }, { code: 'VERIFICATION_ROLES_UNAVAILABLE' }]) {
    const message = verificationErrorMessage({ ...error, message: 'sensitive backend message' })
    assert.match(message, /[\u0600-\u06ff]/)
    assert.equal(message.includes('sensitive backend message'), false)
  }
  assert.match(verificationErrorMessage({ kind: 'network' }, true), /قد يكون الطلب قد نُفّذ/)
})
