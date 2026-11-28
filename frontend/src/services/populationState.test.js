import assert from 'node:assert/strict'
import test from 'node:test'
import { createPopulationState } from './populationState.js'

const result = Object.freeze({ page: 0, size: 20, totalElements: 1, totalPages: 1, empty: false, items: Object.freeze([{ id: '9' }]) })
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function fixture(t, list = async () => result) {
  let current = true; const calls = []
  const service = { isCurrent: () => current, permissions: () => ({ read: current, reason: current ? null : 'AUTH_REQUIRED' }),
    list(query, options) { calls.push({ query, ...options }); return list(query, options) } }
  const store = createPopulationState(service, { page: 0, size: 20, villageId: 7 })
  t.after(() => store.dispose())
  return { store, calls, end() { current = false } }
}

test('population loader starts idle, then preserves the requested page result', async t => {
  const { store, calls } = fixture(t)
  assert.deepEqual(store.getSnapshot(), { status: 'idle', result: null, error: null })
  await store.load().done
  assert.equal(calls[0].query.villageId, 7)
  assert.equal(store.getSnapshot().result.items[0].id, '9')
})

test('empty results and failures are distinct', async t => {
  const empty = fixture(t, async () => ({ ...result, empty: true, totalElements: 0, totalPages: 0, items: [] }))
  await empty.store.load().done
  assert.equal(empty.store.getSnapshot().status, 'ready')
  assert.equal(empty.store.getSnapshot().result.empty, true)
  const error = { status: 403 }, failed = fixture(t, async () => { throw error })
  await failed.store.load().done
  assert.deepEqual(failed.store.getSnapshot(), { status: 'error', result: null, error })
})

test('a superseded village/page read is aborted and cannot overwrite current results', async t => {
  const old = deferred(); let count = 0
  const { store, calls } = fixture(t, () => ++count === 1 ? old.promise : Promise.resolve(result))
  const first = store.load(); await Promise.resolve(); await store.load().done
  assert.equal(calls[0].signal.aborted, true)
  old.resolve({ ...result, items: [{ id: 'old' }] }); await first.done
  assert.equal(store.getSnapshot().result.items[0].id, '9')
})

test('dispose or session end aborts the request and redacts protected results', async t => {
  const pending = deferred(), { store, calls, end } = fixture(t, () => pending.promise)
  const read = store.load(); await Promise.resolve(); end(); store.dispose()
  assert.equal(calls[0].signal.aborted, true)
  pending.resolve(result); await read.done
  assert.deepEqual(store.getSnapshot(), { status: 'idle', result: null, error: null })
})
