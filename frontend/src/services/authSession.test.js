import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from './apiClient.js'
import { createAuthService } from './authService.js'
import { createAuthSession } from './authSession.js'
import { createVillageExplorerService } from './villageExplorerService.js'
import { createVillageHomeService } from './villageHomeService.js'
import { publicVillageContractsVerified, villageDataMode } from './villageMode.js'

const credentials = { username: 'reader', password: 'sample-password' }
const response = (dto, status = 200, headers) => new Response(JSON.stringify(dto), { status, headers })
const loginDto = { accessToken: 'opaque-test-token', tokenType: 'Bearer', expiresIn: 30 }
function deferred() {
  let resolve, reject
  const promise = new Promise((a, b) => { resolve = a; reject = b })
  return { promise, resolve, reject }
}
function clock() {
  let time = 100000, next = 0
  const timers = new Map()
  return {
    now: () => time,
    schedule(fn, delay) { const id = ++next; timers.set(id, { fn, at: time + delay, delay }); return id },
    cancelSchedule: id => timers.delete(id),
    advance(ms, run = true) {
      time += ms
      if (run) for (const [id, timer] of [...timers]) if (timer.at <= time) { timers.delete(id); timer.fn() }
    },
    timers,
  }
}
function fixture(t, handler) {
  const time = clock(), calls = []
  const fetchImpl = async (url, init) => {
    calls.push({ url, init })
    return handler ? handler(url, init, calls.length) : response(url.endsWith('/login') ? loginDto : { username: 'verified-name', roles: [] })
  }
  const store = createAuthSession({ service: createAuthService({ request: createApiClient({ fetchImpl }) }), ...time })
  t.after(() => store.dispose())
  return { store, time, calls }
}

test('anonymous startup makes no requests; a session requires the verified profile and exposes no token/password', async t => {
  const { store, time, calls } = fixture(t)
  const initial = store.getSnapshot()
  assert.equal(store.getSnapshot(), initial)
  assert.equal(initial.status, 'anonymous')
  assert.equal(calls.length, 0)
  assert.equal(time.timers.size, 0)
  const user = await store.login(credentials)
  assert.deepEqual(user, { username: 'verified-name', roles: [], rolesVerified: true })
  assert.equal(store.getSnapshot().status, 'authenticated')
  assert.equal(store.getSnapshot().expiresAt, 130000)
  assert.equal(time.timers.size, 1)
  assert.deepEqual(calls.map(call => call.url), ['/api/v1/auth/login', '/api/v1/auth/me'])
  assert.equal(calls[1].init.headers.get('Authorization'), 'Bearer opaque-test-token')
  assert.equal(JSON.stringify(store.getSnapshot()).includes('opaque-test-token'), false)
  assert.equal(JSON.stringify(store.getSnapshot()).includes(credentials.password), false)
  store.cancelLogin() // A successful LoginPage unmount must not log out its new session.
  assert.equal(store.getSnapshot().status, 'authenticated')
})

test('logout clears identity, private credentials, expiry and timers without inventing a logout endpoint', async t => {
  const { store, time, calls } = fixture(t)
  await store.login(credentials)
  store.logout()
  assert.equal(store.getSnapshot().status, 'anonymous')
  assert.equal(store.getSnapshot().user, null)
  assert.equal(store.getSnapshot().expiresAt, null)
  assert.equal(store.getSnapshot().reason, 'logout')
  assert.equal(time.timers.size, 0)
  await assert.rejects(store.request('/auth/me'), { code: 'AUTH_REQUIRED' })
  assert.equal(calls.length, 2)
})

test('expiry is conservative from POST start, includes network time and clears an active session', async t => {
  let time
  const fixtureResult = fixture(t, url => {
    if (url.endsWith('/login')) { time.advance(5000); return response(loginDto) }
    time.advance(2000)
    return response({ username: 'reader' })
  })
  time = fixtureResult.time
  const { store, calls } = fixtureResult
  await store.login(credentials)
  assert.equal(store.getSnapshot().expiresAt, 130000)
  assert.equal(time.now(), 107000)
  time.advance(23000)
  assert.equal(store.getSnapshot().reason, 'expired')
  assert.equal(store.getSnapshot().user, null)
  assert.equal(time.timers.size, 0)
  await assert.rejects(store.request('/auth/me'), { code: 'AUTH_REQUIRED' })
  assert.equal(calls.length, 2)
})

test('background expiry is checked before requests even when the timer has not run', async t => {
  const { store, time, calls } = fixture(t)
  await store.login(credentials)
  time.advance(30001, false)
  await assert.rejects(store.refreshProfile(), { code: 'AUTH_REQUIRED' })
  assert.equal(store.getSnapshot().reason, 'expired')
  assert.equal(time.timers.size, 0)
  assert.equal(calls.length, 2)
})

test('long verified lifetimes use bounded timer chunks; overflowing expiry is rejected', async t => {
  for (const [expiresIn, valid] of [[3000000, true], [Number.MAX_SAFE_INTEGER, false]]) {
    const { store, time } = fixture(t, url => response(url.endsWith('/login') ? { ...loginDto, expiresIn } : { username: 'reader' }))
    if (valid) {
      await store.login(credentials)
      assert.equal([...time.timers.values()][0].delay, 2147483647)
      time.advance(2147483647)
      assert.equal(store.getSnapshot().status, 'authenticated')
      assert.ok([...time.timers.values()][0].delay < 2147483647)
    } else {
      await assert.rejects(store.login(credentials), { code: 'INVALID_AUTH_RESPONSE' })
      assert.equal(store.getSnapshot().status, 'anonymous')
      assert.equal(time.timers.size, 0)
    }
  }
})

test('concurrent submissions perform exactly one login POST', async t => {
  const pending = deferred()
  const { store, calls } = fixture(t, url => url.endsWith('/login') ? pending.promise : response({ username: 'reader' }))
  const first = store.login(credentials)
  assert.equal(store.getSnapshot().status, 'authenticating')
  await assert.rejects(store.login(credentials), { code: 'LOGIN_IN_PROGRESS' })
  assert.equal(calls.length, 1)
  pending.resolve(response(loginDto))
  await first
  assert.equal(calls.length, 2)
})

test('logout cancels login and a late response cannot overwrite a newer session', async t => {
  const pending = deferred()
  let postCount = 0
  const { store, calls } = fixture(t, url => url.endsWith('/login')
    ? (++postCount === 1 ? pending.promise : response({ ...loginDto, accessToken: 'new-token' }))
    : response({ username: 'new-profile' }))
  const first = store.login(credentials)
  store.logout()
  assert.equal(calls[0].init.signal.aborted, true)
  await store.login(credentials)
  pending.resolve(response(loginDto)) // Transport deliberately ignores AbortSignal.
  await assert.rejects(first)
  assert.equal(store.getSnapshot().status, 'authenticated')
  assert.equal(store.getSnapshot().user.username, 'new-profile')
  await store.request('/auth/me')
  assert.equal(calls.at(-1).init.headers.get('Authorization'), 'Bearer new-token')
  assert.equal(calls.length, 4)
})

test('logout during profile verification cancels the candidate token and ignores late identity', async t => {
  const pending = deferred(), started = deferred()
  const { store, calls, time } = fixture(t, url => {
    if (url.endsWith('/login')) return response(loginDto)
    started.resolve()
    return pending.promise
  })
  const attempt = store.login(credentials)
  await started.promise
  assert.equal(store.getSnapshot().user, null)
  assert.equal(store.getSnapshot().status, 'authenticating')
  store.logout()
  assert.equal(calls[1].init.signal.aborted, true)
  assert.equal(time.timers.size, 0)
  pending.resolve(response({ username: 'stale-profile' }))
  await assert.rejects(attempt)
  assert.equal(store.getSnapshot().user, null)
  assert.equal(store.getSnapshot().reason, 'logout')
})

test('expiration during profile verification cannot establish an expired session', async t => {
  const pending = deferred(), started = deferred()
  const { store, time, calls } = fixture(t, url => {
    if (url.endsWith('/login')) return response(loginDto)
    started.resolve()
    return pending.promise
  })
  const attempt = store.login(credentials)
  await started.promise
  time.advance(30000)
  assert.equal(calls[1].init.signal.aborted, true)
  pending.resolve(response({ username: 'too-late' }))
  await assert.rejects(attempt)
  assert.equal(store.getSnapshot().status, 'anonymous')
  assert.equal(store.getSnapshot().reason, 'expired')
})

test('login rejects an already-expired response without requesting a profile', async t => {
  let time
  const state = fixture(t, () => { time.advance(31000); return response(loginDto) })
  time = state.time
  await assert.rejects(state.store.login(credentials), { code: 'SESSION_EXPIRED' })
  assert.equal(state.store.getSnapshot().reason, 'expired')
  assert.equal(state.calls.length, 1)
})

for (const status of [400, 401, 403, 500]) {
  test(`profile HTTP ${status} during login clears the candidate token instead of fabricating success`, async t => {
    const { store, time, calls } = fixture(t, url => url.endsWith('/login') ? response(loginDto) : response({ error: 'failure' }, status))
    await assert.rejects(store.login(credentials), { status })
    assert.equal(store.getSnapshot().status, 'anonymous')
    assert.equal(store.getSnapshot().user, null)
    assert.equal(time.timers.size, 0)
    await assert.rejects(store.request('/auth/me'), { code: 'AUTH_REQUIRED' })
    assert.equal(calls.length, 2)
  })
}

for (const status of [401, 403, 503]) {
  test(`authenticated profile HTTP ${status} ${status === 401 ? 'clears' : 'retains'} the existing session`, async t => {
    let count = 0
    const { store } = fixture(t, url => url.endsWith('/login') ? response(loginDto)
      : ++count === 1 ? response({ username: 'reader' }) : response({ error: 'failure' }, status))
    await store.login(credentials)
    await assert.rejects(store.refreshProfile(), { status })
    assert.equal(store.getSnapshot().status, status === 401 ? 'anonymous' : 'authenticated')
    assert.equal(store.getSnapshot().checking, false)
    assert.equal(store.getSnapshot().error.status, status)
  })
}

test('network failure during profile refresh retains identity with an explicit error', async t => {
  let count = 0
  const { store } = fixture(t, url => {
    if (url.endsWith('/login')) return response(loginDto)
    if (++count === 1) return response({ username: 'reader' })
    throw new TypeError('Offline')
  })
  await store.login(credentials)
  await assert.rejects(store.refreshProfile(), { kind: 'network' })
  assert.equal(store.getSnapshot().user.username, 'reader')
  assert.equal(store.getSnapshot().status, 'authenticated')
})

test('superseded profiles are aborted and stale profile success cannot overwrite current identity', async t => {
  const pending = deferred(), started = deferred()
  let count = 0
  const { store, calls } = fixture(t, url => {
    if (url.endsWith('/login')) return response(loginDto)
    if (++count === 2) { started.resolve(); return pending.promise }
    return response({ username: count === 1 ? 'initial' : 'current' })
  })
  await store.login(credentials)
  const old = store.refreshProfile()
  await started.promise
  await store.refreshProfile()
  assert.equal(calls[2].init.signal.aborted, true)
  pending.resolve(response({ username: 'stale' }))
  await assert.rejects(old)
  assert.equal(store.getSnapshot().user.username, 'current')
  assert.equal(store.getSnapshot().checking, false)
})

for (const kind of ['profile', 'request']) {
  test(`late ${kind} 401 from a logged-out generation cannot terminate a new session`, async t => {
    const pending = deferred(), started = deferred()
    let count = 0
    const { store } = fixture(t, url => {
      if (url.endsWith('/login')) return response(loginDto)
      if (++count === 2) { started.resolve(); return pending.promise }
      return response({ username: 'current' })
    })
    await store.login(credentials)
    const old = kind === 'profile' ? store.refreshProfile() : store.request('/auth/me')
    await started.promise
    store.logout()
    await store.login(credentials)
    pending.resolve(response({ error: 'expired old token' }, 401))
    await assert.rejects(old)
    assert.equal(store.getSnapshot().status, 'authenticated')
    assert.equal(store.getSnapshot().user.username, 'current')
    assert.equal(store.getSnapshot().error, null)
  })
}

for (const status of [401, 403]) {
  test(`authorized request HTTP ${status} preserves authorization versus authentication semantics`, async t => {
    let count = 0
    const { store, calls } = fixture(t, url => url.endsWith('/login') ? response(loginDto)
      : ++count === 1 ? response({ username: 'reader' }) : response(null, status))
    await store.login(credentials)
    await assert.rejects(store.request('/auth/me'), { status })
    assert.equal(calls[2].init.headers.get('Authorization'), 'Bearer opaque-test-token')
    assert.equal(store.getSnapshot().status, status === 401 ? 'anonymous' : 'authenticated')
  })
}

test('caller cancellation and page-unmount cancellation never authenticate a late login', async t => {
  for (const cancel of ['signal', 'page']) {
    const pending = deferred(), controller = new AbortController()
    const { store, calls } = fixture(t, () => pending.promise)
    const attempt = store.login(credentials, { signal: controller.signal })
    if (cancel === 'signal') controller.abort()
    else store.cancelLogin()
    assert.equal(calls[0].init.signal.aborted, true)
    pending.resolve(response(loginDto))
    await assert.rejects(attempt)
    assert.equal(store.getSnapshot().status, 'anonymous')
    assert.equal(store.getSnapshot().user, null)
    assert.equal(store.getSnapshot().error, null)
  }
})

test('pre-aborted signals and invalid fields cause zero requests', async t => {
  const { store, calls } = fixture(t)
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(store.login(credentials, { signal: controller.signal }))
  await assert.rejects(store.login({ username: '', password: '' }), { code: 'LOGIN_VALIDATION' })
  assert.equal(calls.length, 0)
})

test('logout aborts protected requests; dispose cleans timers and can be reused under React StrictMode', async t => {
  const pending = deferred(), started = deferred()
  let count = 0
  const { store, calls, time } = fixture(t, url => {
    if (url.endsWith('/login')) return response(loginDto)
    if (++count === 2) { started.resolve(); return pending.promise }
    return response({ username: 'reader' })
  })
  let updates = 0
  const unsubscribe = store.subscribe(() => updates++)
  await store.login(credentials)
  const request = store.request('/auth/me')
  await started.promise
  store.dispose()
  assert.equal(calls[2].init.signal.aborted, true)
  assert.equal(time.timers.size, 0)
  assert.equal(store.getSnapshot().status, 'anonymous')
  pending.resolve(response({ username: 'late' }))
  await assert.rejects(request)
  assert.ok(updates > 0)
  unsubscribe()
  const before = updates
  await store.login(credentials)
  assert.equal(updates, before)
  assert.equal(store.getSnapshot().status, 'authenticated')
})

test('authentication is independent of publication approval and preserves default mock browsing', async t => {
  const { store, calls } = fixture(t)
  const forbiddenRequest = () => { assert.fail('The closed village gate must prevent every API request') }
  assert.equal(publicVillageContractsVerified, false)
  assert.equal(villageDataMode, 'mock')
  await store.login(credentials) // Auth requests work while public village requests remain closed.
  const apiVillages = createVillageExplorerService({ mode: 'api', request: forbiddenRequest })
  await assert.rejects(apiVillages.listVillages(), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
  await assert.rejects(apiVillages.listGovernorates(), { code: 'PUBLIC_CONTRACTS_UNCONFIRMED' })
  const mockVillages = createVillageExplorerService({ request: forbiddenRequest })
  const beforeLogout = await mockVillages.listVillages()
  assert.ok(beforeLogout.items.length > 0)
  assert.ok(beforeLogout.items.some(village => /[a-z]/i.test(village.id)))
  assert.equal((await createVillageHomeService({ request: forbiddenRequest }).getFeatured()).items.length, 3)
  store.logout()
  const afterLogout = await mockVillages.listVillages()
  assert.deepEqual(afterLogout, beforeLogout)
  assert.equal(calls.length, 2)
})

for (const header of ['60', 'Thu, 01 Jan 1970 00:02:40 GMT']) {
  test(`login cooldown respects Retry-After ${header} without any automatic retry`, async t => {
    let count = 0
    const { store, time, calls } = fixture(t, url => url.endsWith('/login')
      ? ++count === 1 ? new Response('', { status: 429, headers: { 'Retry-After': header } }) : response(loginDto)
      : response({ username: 'reader' }))
    await assert.rejects(store.login(credentials), { status: 429 })
    assert.equal(store.getSnapshot().retryAt, 160000)
    assert.equal(store.getSnapshot().error.status, 429)
    await assert.rejects(store.login(credentials), { code: 'LOGIN_THROTTLED' })
    assert.equal(calls.length, 1)
    time.advance(60000)
    assert.equal(calls.length, 1)
    await store.login(credentials)
    assert.equal(store.getSnapshot().retryAt, null)
    assert.equal(calls.length, 3)
  })
}
