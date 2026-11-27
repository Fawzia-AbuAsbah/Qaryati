import assert from 'node:assert/strict'
import test from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createAuditLogState } from './auditLogState.js'
import { adaptAuditLog, auditErrorMessage } from './auditContracts.js'
import { safeAuthReturnTo } from './authAccess.js'
import { StructuredValue } from '../components/conflicts/StructuredValue.js'
import { publicVillageCapabilities, villageDataMode } from './villageMode.js'
import { createVillageDetailsService } from './villageDetailsService.js'

const log = Object.freeze({ id: '21', entityType: 'SOURCE', entityId: '7', action: 'UPDATE' })
const query = Object.freeze({ mode: 'entity', entityType: 'SOURCE', entityId: '7' })
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function fixture(t, { search = async () => [log], read = true } = {}) {
  let current = true
  const calls = []
  const service = {
    isCurrent: () => current,
    permissions: () => ({ read: current && read, reason: read ? null : 'AUDIT_ROLES_UNAVAILABLE' }),
    search(value, options) { calls.push({ query: value, ...options }); return search(value, options) },
  }
  const store = createAuditLogState(service, query)
  t.after(() => store.dispose())
  return { store, calls, endSession() { current = false } }
}

test('the store starts idle and loads frozen server-ordered results for its query', async t => {
  const { store, calls } = fixture(t, { search: async () => [{ ...log, id: '9' }, log] })
  assert.equal(store.getSnapshot().status, 'idle')
  assert.equal(calls.length, 0)
  await store.load().done
  assert.deepEqual(store.getSnapshot().items.map(item => item.id), ['9', '21'])
  assert.equal(Object.isFrozen(store.getSnapshot().items), true)
  assert.equal(calls[0].query, query)
})

test('empty results and errors stay distinct', async t => {
  const empty = fixture(t, { search: async () => [] })
  await empty.store.load().done
  assert.deepEqual(empty.store.getSnapshot(), { status: 'ready', items: [], error: null })
  const error = { status: 403 }
  const denied = fixture(t, { search: async () => { throw error } })
  await denied.store.load().done
  assert.deepEqual(denied.store.getSnapshot(), { status: 'error', items: null, error })
})

test('missing ADMIN permission means no load and no request', async t => {
  const { store, calls } = fixture(t, { read: false })
  assert.equal(store.load(), null)
  assert.equal(calls.length, 0)
})

for (const failure of [false, true]) {
  test(`superseded ${failure ? 'failure' : 'success'} cannot overwrite a newer load`, async t => {
    const old = deferred()
    let count = 0
    const { store, calls } = fixture(t, { search: () => ++count === 1 ? old.promise : Promise.resolve([log]) })
    const first = store.load()
    await Promise.resolve()
    await store.load().done
    assert.equal(calls[0].signal.aborted, true)
    if (failure) old.reject({ status: 401 }); else old.resolve([{ ...log, id: '99' }])
    await first.done
    assert.deepEqual(store.getSnapshot().items, [log])
  })
}

test('filter change or leaving the page aborts the read and redacts results', async t => {
  const pending = deferred()
  let count = 0
  const { store, calls } = fixture(t, { search: () => ++count === 1 ? Promise.resolve([log]) : pending.promise })
  await store.load().done
  const read = store.load()
  await Promise.resolve()
  store.dispose()
  assert.equal(calls[1].signal.aborted, true)
  assert.equal(store.getSnapshot().items, null)
  pending.resolve([{ ...log, id: '88' }])
  await read.done
  assert.equal(store.getSnapshot().items, null)
})

test('logout/session change immediately clears protected audit data and suppresses late results', async t => {
  const pending = deferred()
  let count = 0
  const { store, endSession } = fixture(t, { search: () => ++count === 1 ? Promise.resolve([log]) : pending.promise })
  await store.load().done
  const read = store.load()
  endSession()
  assert.deepEqual(store.getSnapshot(), { status: 'idle', items: null, error: null })
  pending.resolve([{ ...log, id: '77' }])
  await read.done
  assert.equal(store.getSnapshot().items, null)
  assert.equal(store.load(), null)
})

test('audit snapshots render as escaped text with only fixed structural tags', () => {
  const adapted = adaptAuditLog({ id: 1, entityType: 'SOURCE', entityId: 7, action: 'UPDATE',
    afterData: { '<b>title</b>': '<script>alert(1)</script>', url: 'javascript:alert(1)', nested: { '<img src=x onerror=alert(1)>': [null, ''] } } })
  const html = renderToStaticMarkup(createElement(StructuredValue, { value: adapted.afterData }))
  assert.equal(html.includes('<script'), false)
  assert.equal(html.includes('<img'), false)
  assert.equal(html.includes('<b>'), false)
  const tags = html.match(/<[^>]*>/g)
  assert.ok(tags.every(tag => /^<\/?(dl|dt|dd|ol|li|bdi|span)( class="conflict-(structure|text|meta)")?>$/.test(tag)), tags.join(' '))
  assert.ok(html.includes('&lt;script&gt;alert(1)&lt;/script&gt;'))
  assert.ok(html.includes('قيمة فارغة (null)'))
})

test('error messages distinguish auth, missing roles, ADMIN-only, validation and network failures', () => {
  assert.match(auditErrorMessage({ status: 401 }), /سجّل الدخول/)
  assert.match(auditErrorMessage({ code: 'AUDIT_ROLES_UNAVAILABLE' }), /لا يمكن التحقق/)
  assert.match(auditErrorMessage({ status: 403 }), /للمديرين فقط/)
  assert.match(auditErrorMessage({ status: 400 }), /معايير البحث/)
  assert.match(auditErrorMessage({ kind: 'network' }), /تعذّر الاتصال/)
})

test('login return URLs accept the protected audit page with its filters only', () => {
  assert.equal(safeAuthReturnTo('/audit-logs?mode=action&action=CREATE'), '/audit-logs?mode=action&action=CREATE')
  assert.equal(safeAuthReturnTo('/audit-logs/21'), '/')
  assert.equal(safeAuthReturnTo('//audit-logs'), '/')
})

test('disabled identity capability sends no HTTP and mock browsing makes no audit requests', async () => {
  assert.deepEqual(publicVillageCapabilities, { browsing: true, identity: true, geographicCatalogues: false, richSections: false })
  assert.equal(villageDataMode, 'mock')
  const request = () => assert.fail('No public API or audit requests')
  await assert.rejects(createVillageDetailsService({ mode: 'api', identityVerified: false, request }).getVillage('42'), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
  assert.equal((await createVillageDetailsService({ request }).getVillage('battir')).id, 'battir')
})
