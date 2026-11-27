import assert from 'node:assert/strict'
import test from 'node:test'
import { createPopulationService } from './populationService.js'

const record = { id: 9, villageId: 7, referenceYear: 2024, referenceDate: null, populationCount: 1200, methodology: 'CENSUS', sourceId: 3,
  notes: null, verificationStatus: 'VERIFIED', createdAt: '2026-11-28T10:00:00Z', updatedAt: '2026-11-28T10:00:00Z' }
const page = { content: [record], page: 0, size: 20, totalElements: 1, totalPages: 1 }
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function fixture({ roles = ['VIEWER'], response = async () => page } = {}) {
  let session = { status: 'authenticated', user: { username: 'fixture', roles, rolesVerified: true }, expiresAt: Date.now() + 3600000 }
  const calls = []
  const service = createPopulationService({ request: (path, options) => { calls.push({ path, ...options }); return response(path, options) }, getSession: () => session })
  return { service, calls, replaceSession(value) { session = value } }
}

for (const roles of [['VIEWER'], ['EDITOR'], ['ADMIN']]) {
  test(`${roles[0]} has authenticated read access and sends only confirmed query fields`, async () => {
    const { service, calls } = fixture({ roles })
    const result = await service.list({ villageId: '7', page: 0, size: 20 })
    assert.equal(result.items[0].villageId, '7')
    assert.deepEqual(calls[0], { path: '/population-records', query: { page: 0, size: 20, villageId: 7 }, signal: undefined })
    assert.equal(service.permissions().privileged, roles[0] !== 'VIEWER')
  })
}

test('unverified, missing and unsupported roles never send a protected population request', async () => {
  for (const [roles, code] of [[[], 'POPULATION_FORBIDDEN'], [['UNKNOWN'], 'POPULATION_ROLES_UNAVAILABLE']]) {
    const { service, calls } = fixture({ roles })
    await assert.rejects(service.list({}), { code })
    assert.equal(calls.length, 0)
  }
  const { service, calls } = fixture({ roles: null })
  await assert.rejects(service.list({}), { code: 'POPULATION_ROLES_UNAVAILABLE' })
  assert.equal(calls.length, 0)
})

test('invalid filters fail before the authenticated client is called', async () => {
  const { service, calls } = fixture()
  await assert.rejects(service.list({ villageId: '0' }), { code: 'POPULATION_VALIDATION' })
  assert.equal(calls.length, 0)
})

for (const error of [{ status: 400, kind: 'http' }, { status: 403, kind: 'http' }, { status: 429, kind: 'http' }, { kind: 'network' }]) {
  test(`server failure ${JSON.stringify(error)} is preserved without fallback or retry`, async () => {
    const { service, calls } = fixture({ response: async () => { throw error } })
    await assert.rejects(service.list({}), error)
    assert.equal(calls.length, 1)
  })
}

test('cancellation and session replacement prevent late results', async () => {
  const pending = deferred(), controller = new AbortController()
  const { service, calls, replaceSession } = fixture({ response: () => pending.promise })
  const read = service.list({ villageId: '7' }, { signal: controller.signal })
  assert.equal(calls[0].signal, controller.signal)
  controller.abort()
  pending.resolve(page)
  await assert.rejects(read)
  const late = deferred(), changed = fixture({ response: () => late.promise })
  const second = changed.service.list({}); changed.replaceSession({ status: 'anonymous' }); late.resolve(page)
  await assert.rejects(second, { code: 'AUTH_REQUIRED' })
})
