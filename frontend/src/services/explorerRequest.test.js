import assert from 'node:assert/strict'
import test from 'node:test'
import { createLatestExplorerRequest } from './explorerRequest.js'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

test('superseded responses cannot overwrite current data even if transport ignores abort', async () => {
  const runner = createLatestExplorerRequest()
  const old = deferred()
  const current = deferred()
  const successes = [], errors = [], signals = []
  const handlers = { onSuccess: data => successes.push(data), onError: error => errors.push(error) }
  const first = runner.run(signal => { signals.push(signal); return old.promise }, handlers)
  await Promise.resolve()
  const second = runner.run(signal => { signals.push(signal); return current.promise }, handlers)
  await Promise.resolve()
  assert.equal(signals[0].aborted, true)
  assert.equal(signals[1].aborted, false)
  current.resolve('current results')
  await second.done
  old.resolve('stale results')
  await first.done
  assert.deepEqual(successes, ['current results'])
  assert.deepEqual(errors, [])
})

test('a stale failure cannot replace the current successful result', async () => {
  const runner = createLatestExplorerRequest()
  const old = deferred()
  const successes = [], errors = []
  const handlers = { onSuccess: data => successes.push(data), onError: error => errors.push(error) }
  const first = runner.run(() => old.promise, handlers)
  await Promise.resolve()
  const second = runner.run(async () => 'current results', handlers)
  await second.done
  old.reject(new Error('Stale network failure'))
  await first.done
  assert.deepEqual(successes, ['current results'])
  assert.deepEqual(errors, [])
})

test('unmount cancellation suppresses responses and an immediate cancellation never starts loading', async t => {
  const runner = createLatestExplorerRequest()
  const load = t.mock.fn(async () => 'unmounted data')
  const onSuccess = t.mock.fn(), onError = t.mock.fn()
  const task = runner.run(load, { onSuccess, onError })
  task.cancel()
  await task.done
  assert.equal(load.mock.callCount(), 0)
  assert.equal(onSuccess.mock.callCount(), 0)
  assert.equal(onError.mock.callCount(), 0)
})

test('current errors are surfaced and an explicit retry can succeed', async () => {
  const runner = createLatestExplorerRequest()
  const cause = new Error('Network error')
  const successes = [], errors = []
  const handlers = { onSuccess: data => successes.push(data), onError: error => errors.push(error) }
  await runner.run(async () => { throw cause }, handlers).done
  assert.deepEqual(errors, [cause])
  await runner.run(async () => [], handlers).done
  assert.deepEqual(successes, [[]])
})
