import assert from 'node:assert/strict'
import test from 'node:test'
import { ApiError, createApiClient } from './apiClient.js'
import { authErrorMessage, createAuthService, loginValidation, retryDeadline } from './authService.js'

const response = (dto, status = 200, headers) => new Response(JSON.stringify(dto), { status, headers })
function fixture(t, handler) {
  const fetchImpl = t.mock.fn(handler)
  return { service: createAuthService({ request: createApiClient({ fetchImpl }) }), fetchImpl }
}

test('login uses the exact verified POST DTO, preserves credential whitespace and omits cookie/Bearer credentials', async t => {
  const { service, fetchImpl } = fixture(t, (url, init) => {
    assert.equal(url, '/api/v1/auth/login')
    assert.equal(init.method, 'POST')
    assert.deepEqual(JSON.parse(init.body), { username: ' reader ', password: ' sample password ' })
    assert.equal(init.headers.get('Content-Type'), 'application/json')
    assert.equal(init.headers.has('Authorization'), false)
    assert.equal(init.credentials, 'omit')
    return response({ accessToken: 'opaque-test-token', tokenType: 'Bearer', expiresIn: 900, refreshToken: 'not-a-contract', roles: ['ADMIN'] })
  })
  assert.deepEqual(await service.login({ username: ' reader ', password: ' sample password ', roles: ['ADMIN'] }),
    { accessToken: 'opaque-test-token', tokenType: 'Bearer', expiresIn: 900 })
  assert.equal(fetchImpl.mock.callCount(), 1)
})

test('profile request sends Bearer authentication and accepts username only, without inferring effective roles', async t => {
  const controller = new AbortController()
  const { service } = fixture(t, (url, init) => {
    assert.equal(url, '/api/v1/auth/me')
    assert.equal(init.method, 'GET')
    assert.equal(init.body, undefined)
    assert.equal(init.headers.get('Authorization'), 'Bearer opaque-test-token')
    assert.equal(init.credentials, 'omit')
    assert.equal(init.signal, controller.signal)
    return response({ username: 'actual-profile-name', roles: ['ADMIN'], authorities: ['ROLE_ADMIN'] })
  })
  assert.deepEqual(await service.getProfile('opaque-test-token', { signal: controller.signal }),
    { username: 'actual-profile-name', roles: null, rolesVerified: false })
})

for (const credentials of [null, {}, { username: '', password: 'sample' }, { username: '   ', password: 'sample' },
  { username: 'reader', password: '' }, { username: 'reader', password: ' \t ' }, { username: 4, password: 'sample' }]) {
  test('NotBlank validation prevents HTTP without imposing an invented password policy', async t => {
    const { service, fetchImpl } = fixture(t, () => {})
    assert.ok(Object.keys(loginValidation(credentials)).length)
    await assert.rejects(service.login(credentials), { code: 'LOGIN_VALIDATION' })
    assert.equal(fetchImpl.mock.callCount(), 0)
  })
}

for (const dto of [null, {}, { accessToken: '', tokenType: 'Bearer', expiresIn: 900 },
  { accessToken: 'contains whitespace', tokenType: 'Bearer', expiresIn: 900 },
  { accessToken: 'opaque', tokenType: 'Basic', expiresIn: 900 }, { accessToken: 'opaque', tokenType: 'Bearer', expiresIn: '900' },
  { accessToken: 'opaque', tokenType: 'Bearer', expiresIn: 0 }, { accessToken: 'opaque', tokenType: 'Bearer', expiresIn: -1 },
  { accessToken: 'opaque', tokenType: 'Bearer', expiresIn: 1.5 }]) {
  test('malformed login responses never create a synthetic token/session', async t => {
    const { service } = fixture(t, () => response(dto))
    await assert.rejects(service.login({ username: 'reader', password: 'sample' }), { code: 'INVALID_AUTH_RESPONSE' })
  })
}

for (const [status, details] of [[400, { error: 'Invalid request data' }], [401, { error: 'Invalid credentials' }],
  [403, { error: 'Forbidden' }], [429, null], [503, { message: 'Unavailable' }]]) {
  test(`login HTTP ${status} preserves ApiError semantics without automatic retry`, async t => {
    const { service, fetchImpl } = fixture(t, () => details === null
      ? new Response('', { status, headers: { 'Retry-After': '60' } }) : response(details, status))
    await assert.rejects(service.login({ username: 'reader', password: 'sample' }), error => {
      assert.ok(error instanceof ApiError)
      assert.equal(error.status, status)
      assert.equal(error.kind, 'http')
      assert.deepEqual(error.details, details)
      assert.equal(error.retryAfter, status === 429 ? '60' : null)
      return true
    })
    assert.equal(fetchImpl.mock.callCount(), 1)
  })
}

test('profile 401/403 and network failures preserve their status/cause', async t => {
  for (const status of [401, 403]) {
    const { service } = fixture(t, () => new Response('', { status }))
    await assert.rejects(service.getProfile('opaque'), { status, kind: 'http' })
  }
  const cause = new TypeError('Failed to fetch')
  const { service } = fixture(t, () => { throw cause })
  await assert.rejects(service.login({ username: 'reader', password: 'sample' }), { kind: 'network', cause })
})

test('unsupported/missing profile information does not become a username or role', async t => {
  for (const dto of [null, {}, { username: null }, { username: 4 }, { username: '  ', roles: ['ADMIN'] }]) {
    const { service } = fixture(t, () => response(dto))
    await assert.rejects(service.getProfile('opaque'), { code: 'INVALID_AUTH_RESPONSE' })
  }
  const { service, fetchImpl } = fixture(t, () => {})
  await assert.rejects(service.getProfile('bad\ntoken'), { code: 'INVALID_AUTH_RESPONSE' })
  assert.equal(fetchImpl.mock.callCount(), 0)
})

test('authorized requests cannot override the captured Bearer token', async t => {
  const { service } = fixture(t, (url, init) => {
    assert.equal(url, '/api/v1/auth/me')
    assert.equal(init.headers.get('Authorization'), 'Bearer captured-token')
    return response({ username: 'reader' })
  })
  await service.requestWithToken('captured-token', '/auth/me', { token: 'wrong-token', headers: { Authorization: 'wrong-header' } })
})

test('Retry-After supports delay-seconds and HTTP-date without scheduling a login', () => {
  const now = Date.parse('2026-11-27T12:00:00Z')
  assert.equal(retryDeadline('60', now), now + 60000)
  assert.equal(retryDeadline('Fri, 27 Nov 2026 12:01:00 GMT', now), now + 60000)
  assert.equal(retryDeadline('Thu, 26 Nov 2026 12:01:00 GMT', now), now)
  for (const invalid of [null, '', 'bad', '-1', '1.5', '9999999999999999999999']) assert.equal(retryDeadline(invalid, now), null)
})

test('UI errors are accessible Arabic messages and never echo raw server text or credentials', () => {
  for (const error of [{ status: 400 }, { status: 401 }, { status: 403 }, { status: 429 }, { kind: 'network' },
    { code: 'SESSION_EXPIRED' }, { code: 'INVALID_AUTH_RESPONSE' }, { code: 'LOGIN_IN_PROGRESS' }]) {
    const message = authErrorMessage({ ...error, message: 'Sensitive backend text' })
    assert.match(message, /[\u0600-\u06ff]/)
    assert.equal(message.includes('Sensitive backend text'), false)
  }
  assert.ok(authErrorMessage({ status: 429 }, 60).includes(new Intl.NumberFormat('ar').format(60)))
})
