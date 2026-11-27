import assert from 'node:assert/strict'
import test from 'node:test'
import { ApiError, apiClient, createApiClient } from './apiClient.js'

function fixture(t, body = '{}', init = {}, options = {}) {
  const fetchImpl = t.mock.fn(async () => new Response(body, init))
  return { request: createApiClient({ ...options, fetchImpl }), fetchImpl }
}

test('default base, JSON accept header, and bearer-only credentials policy', async t => {
  const { request, fetchImpl } = fixture(t, '{"id":1}')
  assert.deepEqual(await request('/villages/1'), { id: 1 })
  const [url, options] = fetchImpl.mock.calls[0].arguments
  assert.equal(url, '/api/v1/villages/1')
  assert.equal(options.method, 'GET')
  assert.equal(options.headers.get('Accept'), 'application/json')
  assert.equal(options.headers.has('Authorization'), false)
  assert.equal(options.headers.has('Content-Type'), false)
  assert.equal(options.credentials, 'omit')
  assert.equal(typeof apiClient, 'function')
})

for (const baseUrl of ['/api/v1/', 'https://api.example.test/api/v1/', '/gateway/qaryati/api/v1']) {
  test(`configurable base ${baseUrl}`, async t => {
    const { request, fetchImpl } = fixture(t, '[]', {}, { baseUrl })
    await request('/villages')
    assert.equal(fetchImpl.mock.calls[0].arguments[0], `${baseUrl.replace(/\/$/, '')}/villages`)
  })
}

test('query encoding preserves Arabic, zero, false, empty strings and repeated values', async t => {
  const { request, fetchImpl } = fixture(t)
  await request('/villages?page=99&keep=yes', {
    query: { page: 0, search: 'بتير & أرطاس', active: false, blank: '', tag: ['one', 'two'], absent: null, missing: undefined },
  })
  const url = new URL(fetchImpl.mock.calls[0].arguments[0], 'https://frontend.example.test')
  assert.equal(url.searchParams.get('page'), '0')
  assert.equal(url.searchParams.get('keep'), 'yes')
  assert.equal(url.searchParams.get('search'), 'بتير & أرطاس')
  assert.equal(url.searchParams.get('active'), 'false')
  assert.equal(url.searchParams.get('blank'), '')
  assert.deepEqual(url.searchParams.getAll('tag'), ['one', 'two'])
  assert.equal(url.searchParams.has('absent'), false)
  assert.equal(url.searchParams.has('missing'), false)
})

test('URLSearchParams supports repeated values and overrides existing query keys', async t => {
  const { request, fetchImpl } = fixture(t)
  await request('villages?tag=old&keep=yes', { query: new URLSearchParams([['tag', 'a'], ['tag', 'b']]) })
  const url = new URL(fetchImpl.mock.calls[0].arguments[0], 'https://frontend.example.test')
  assert.deepEqual(url.searchParams.getAll('tag'), ['a', 'b'])
  assert.equal(url.searchParams.get('keep'), 'yes')
})

for (const method of ['post', 'PUT', 'PATCH', 'DELETE']) {
  test(`${method} sends one JSON request with caller headers, token and signal`, async t => {
    const { request, fetchImpl } = fixture(t, '{"id":1}', { status: 201 })
    const controller = new AbortController()
    await request('/sources', {
      method, body: { title: 'مرجع', sourceType: 'BOOK' }, token: 'caller-token', signal: controller.signal,
      headers: { 'X-Request-ID': 'request-1' },
    })
    assert.equal(fetchImpl.mock.callCount(), 1)
    const options = fetchImpl.mock.calls[0].arguments[1]
    assert.equal(options.method, method.toUpperCase())
    assert.deepEqual(JSON.parse(options.body), { title: 'مرجع', sourceType: 'BOOK' })
    assert.equal(options.headers.get('Authorization'), 'Bearer caller-token')
    assert.equal(options.headers.get('Content-Type'), 'application/json')
    assert.equal(options.headers.get('X-Request-ID'), 'request-1')
    assert.equal(options.signal, controller.signal)
  })
}

test('tokens are per-request and are not retained by the client', async t => {
  const { request, fetchImpl } = fixture(t)
  await request('/auth/me', { token: 'first-token' })
  await request('/villages')
  assert.equal(fetchImpl.mock.calls[1].arguments[1].headers.has('Authorization'), false)
})

for (const value of [{ content: [], number: 0 }, [1, 2], null, false, 0, 'text']) {
  test(`successful JSON value ${JSON.stringify(value)}`, async t => {
    const { request } = fixture(t, JSON.stringify(value))
    assert.deepEqual(await request('/resource'), value)
  })
}

for (const [status, body] of [[200, ''], [200, ' \n '], [204, null], [205, null]]) {
  test(`empty success ${status} returns null`, async t => {
    const { request } = fixture(t, body, { status })
    assert.equal(await request('/resource'), null)
  })
}

test('HEAD does not attempt JSON parsing', async t => {
  const { request } = fixture(t, '<body not read>')
  assert.equal(await request('/resource', { method: 'HEAD' }), null)
})

for (const [status, code] of [[400, 'BAD_REQUEST'], [401, 'UNAUTHORIZED'], [403, 'FORBIDDEN'],
  [404, 'NOT_FOUND'], [409, 'CONFLICT'], [429, 'RATE_LIMITED'], [500, 'SERVER_ERROR'],
  [502, 'SERVER_ERROR'], [503, 'SERVER_ERROR'], [418, 'HTTP_ERROR']]) {
  test(`HTTP ${status} preserves common backend error details without retrying`, async t => {
    const details = { timestamp: '2026-11-26T12:00:00Z', status, error: 'Backend error', message: 'Specific failure', path: '/api/v1/sources' }
    const { request, fetchImpl } = fixture(t, JSON.stringify(details), { status, headers: { 'X-Request-ID': 'server-1' } })
    await assert.rejects(request('/sources', { method: 'POST', body: { title: 'sample' } }), error => {
      assert.ok(error instanceof ApiError)
      assert.equal(error.kind, 'http')
      assert.equal(error.code, code)
      assert.equal(error.status, status)
      assert.equal(error.message, 'Specific failure')
      assert.deepEqual(error.details, details)
      assert.equal(error.headers.get('X-Request-ID'), 'server-1')
      assert.equal(error.retryAfter, null)
      return true
    })
    assert.equal(fetchImpl.mock.callCount(), 1)
  })
}

test('data-quality error format uses error text and retains the whole payload', async t => {
  const { request } = fixture(t, '{"error":"Source not found"}', { status: 404 })
  await assert.rejects(request('/sources/99'), error => {
    assert.equal(error.message, 'Source not found')
    assert.deepEqual(error.details, { error: 'Source not found' })
    return true
  })
})

for (const status of [401, 403]) {
  test(`empty security-filter ${status} remains a structured HTTP error`, async t => {
    const { request } = fixture(t, '', { status })
    await assert.rejects(request('/villages'), error => {
      assert.equal(error.kind, 'http')
      assert.equal(error.status, status)
      assert.equal(error.details, null)
      assert.equal(error.malformedBody, false)
      assert.equal(error.message, `API request failed (HTTP ${status})`)
      return true
    })
  })
}

for (const retryAfter of ['60', 'Thu, 26 Nov 2026 12:01:00 GMT']) {
  test(`empty 429 preserves Retry-After ${retryAfter}`, async t => {
    const { request, fetchImpl } = fixture(t, '', { status: 429, headers: { 'Retry-After': retryAfter } })
    await assert.rejects(request('/auth/login', { method: 'POST', body: { username: 'sample', password: 'sample' } }), error => {
      assert.equal(error.status, 429)
      assert.equal(error.code, 'RATE_LIMITED')
      assert.equal(error.retryAfter, retryAfter)
      assert.equal(error.details, null)
      return true
    })
    assert.equal(fetchImpl.mock.callCount(), 1)
  })
}

test('HTML gateway errors retain HTTP classification, raw details and Retry-After', async t => {
  const { request } = fixture(t, '<html>Unavailable</html>', { status: 503, headers: { 'Retry-After': '120' } })
  await assert.rejects(request('/villages'), error => {
    assert.equal(error.kind, 'http')
    assert.equal(error.status, 503)
    assert.equal(error.code, 'SERVER_ERROR')
    assert.equal(error.details, '<html>Unavailable</html>')
    assert.equal(error.malformedBody, true)
    assert.equal(error.retryAfter, '120')
    return true
  })
})

for (const body of ['{"broken":', '<html>Vite fallback</html>']) {
  test('malformed successful response is distinct from an HTTP failure', async t => {
    const { request } = fixture(t, body)
    await assert.rejects(request('/villages'), error => {
      assert.equal(error.kind, 'malformed-response')
      assert.equal(error.code, 'MALFORMED_RESPONSE')
      assert.equal(error.status, 200)
      assert.equal(error.details, body)
      assert.ok(error.cause instanceof SyntaxError)
      return true
    })
  })
}

test('network failure retains its cause, has no HTTP status, and is not retried', async t => {
  const cause = new TypeError('Failed to fetch')
  const fetchImpl = t.mock.fn(async () => { throw cause })
  const request = createApiClient({ fetchImpl })
  await assert.rejects(request('/sources', { method: 'POST', body: {} }), error => {
    assert.equal(error.kind, 'network')
    assert.equal(error.code, 'NETWORK_ERROR')
    assert.equal(error.status, null)
    assert.equal(error.cause, cause)
    return true
  })
  assert.equal(fetchImpl.mock.callCount(), 1)
})

test('response stream failure preserves a received HTTP status', async () => {
  const response = new Response('{}', { status: 200 })
  const cause = new TypeError('Connection closed')
  response.text = async () => { throw cause }
  const request = createApiClient({ fetchImpl: async () => response })
  await assert.rejects(request('/villages'), { kind: 'network', status: 200, cause })
})

test('already-aborted requests never call fetch', async t => {
  const { request, fetchImpl } = fixture(t)
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(request('/villages', { signal: controller.signal }), error => error === controller.signal.reason)
  assert.equal(fetchImpl.mock.callCount(), 0)
})

test('in-flight cancellation remains the original abort error', async () => {
  const controller = new AbortController()
  const request = createApiClient({
    fetchImpl: (_url, { signal }) => new Promise((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(signal.reason), { once: true })
    }),
  })
  const pending = request('/villages', { signal: controller.signal })
  controller.abort()
  await assert.rejects(pending, error => error === controller.signal.reason)
})

test('custom cancellation reasons are preserved rather than reported as network failures', async () => {
  const controller = new AbortController()
  const reason = new Error('Caller cancelled')
  controller.abort(reason)
  const request = createApiClient({ fetchImpl: async () => { throw new Error('Fetch must not run') } })
  await assert.rejects(request('/villages', { signal: controller.signal }), error => error === reason)
})

test('cancellation during response reading remains an abort', async () => {
  const controller = new AbortController()
  const response = new Response('{}')
  response.text = async () => { controller.abort(); throw controller.signal.reason }
  const request = createApiClient({ fetchImpl: async () => response })
  await assert.rejects(request('/villages', { signal: controller.signal }), error => error === controller.signal.reason)
})

for (const endpoint of ['https://other.example.test/steal', '//other.example.test/steal', '../auth', '%2e%2e/auth', ' /outside', ' https://other.example.test/steal']) {
  test(`reject endpoint outside base: ${endpoint}`, async t => {
    const { request, fetchImpl } = fixture(t)
    await assert.rejects(request(endpoint, { token: 'caller-token' }), TypeError)
    assert.equal(fetchImpl.mock.callCount(), 0)
  })
}

for (const baseUrl of ['api/v1', '//other.example.test/api', 'https://user:password@api.example.test/api', '/api?token=secret', '/api#fragment']) {
  test('invalid base configuration fails before requests', () => {
    assert.throws(() => createApiClient({ baseUrl }), TypeError)
  })
}

test('invalid JSON request bodies are caller errors and never sent', async t => {
  const { request, fetchImpl } = fixture(t)
  await assert.rejects(request('/villages', { body: {} }), TypeError)
  await assert.rejects(request('/sources', { method: 'POST', body: 1n }), TypeError)
  await assert.rejects(request('/sources', { method: 'POST', body: () => {} }), TypeError)
  assert.equal(fetchImpl.mock.callCount(), 0)
})
