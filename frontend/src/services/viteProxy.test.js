import assert from 'node:assert/strict'
import test from 'node:test'
import process from 'node:process'
import { Buffer } from 'node:buffer'
import { createServer as createHttpServer, request as httpRequest } from 'node:http'
import { mkdtemp, writeFile, unlink, rmdir, stat } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, createLogger, preview } from 'vite'
import viteConfig from '../../vite.config.js'
import { apiProxyContext, createApiProxy, createApiProxyLogger, defaultApiProxyTarget, resolveApiProxyTarget } from '../../config/apiProxy.js'
import { createApiClient } from './apiClient.js'

const projectDirectory = fileURLToPath(new URL('../../', import.meta.url))
const syntheticToken = 'synthetic-proxy-test-token'

function configFor(target) {
  const previous = process.env.DEV_API_PROXY_TARGET
  if (target === undefined) delete process.env.DEV_API_PROXY_TARGET
  else process.env.DEV_API_PROXY_TARGET = target
  try { return viteConfig({ command: 'serve', mode: 'test' }) } finally {
    if (previous === undefined) delete process.env.DEV_API_PROXY_TARGET
    else process.env.DEV_API_PROXY_TARGET = previous
  }
}

test('default target is native loopback 8080; inspecting the default never contacts it', () => {
  assert.equal(resolveApiProxyTarget(), 'http://127.0.0.1:8080')
  assert.equal(defaultApiProxyTarget, 'http://127.0.0.1:8080')
})
for (const [value, expected] of [
  ['http://127.0.0.1:8081', 'http://127.0.0.1:8081'],
  [' http://127.0.0.1:43210/ ', 'http://127.0.0.1:43210'],
  ['https://localhost:8443/', 'https://localhost:8443'],
  ['http://[::1]:43210', 'http://[::1]:43210'],
]) {
  test('valid origin-only targets normalize without adding an API path', () => {
    assert.equal(resolveApiProxyTarget(value), expected)
    const proxy = createApiProxy(value)[apiProxyContext]
    assert.equal(proxy.target, expected)
    assert.equal(proxy.changeOrigin, true)
    assert.equal(proxy.secure, true)
    for (const option of ['rewrite', 'headers', 'bypass', 'followRedirects', 'xfwd']) assert.equal(Object.hasOwn(proxy, option), false)
  })
}
for (const value of ['', ' ', null, 42, {}, 'localhost:8080', '//localhost:8080', 'ftp://localhost', 'ws://localhost',
  'http:/localhost', 'http://', 'http://127.0.0.1:99999', 'http://localhost/api/v1', 'http://localhost/.',
  'http://localhost/?token=synthetic-private', 'http://localhost?', 'http://localhost/#fragment',
  'http://name:synthetic-private@localhost', 'http://localhost\\path', 'http://local\nhost']) {
  test(`malformed proxy target is rejected without printing its value (${typeof value})`, () => {
    assert.throws(() => resolveApiProxyTarget(value), error => {
      assert.match(error.message, /^DEV_API_PROXY_TARGET must be/)
      assert.equal(error.message.includes('synthetic-private'), false)
      return true
    })
  })
}
test('real Vite configuration loads DEV_ from the frontend directory and binds both servers to loopback', () => {
  const config = configFor('http://127.0.0.1:43210')
  assert.equal(resolve(config.root), resolve(projectDirectory))
  assert.equal(resolve(config.envDir), resolve(projectDirectory))
  assert.equal(config.envPrefix, undefined) // Retain Vite's VITE_-only client environment.
  for (const server of [config.server, config.preview]) {
    assert.equal(server.host, '127.0.0.1')
    assert.equal(server.proxy[apiProxyContext].target, 'http://127.0.0.1:43210')
  }
  assert.throws(() => configFor('http://localhost/api/v1'), /DEV_API_PROXY_TARGET/)
})
test('proxy matching respects the API version segment without rewriting it', () => {
  const match = new RegExp(apiProxyContext)
  for (const path of ['/api/v1', '/api/v1?x=1', '/api/v1/public/villages']) assert.equal(match.test(path), true)
  for (const path of ['/api/v10', '/api/v1-other', '/other/api/v1']) assert.equal(match.test(path), false)
})
test('proxy connection logs discard query values, tokens, bodies and upstream stacks; other errors remain useful', () => {
  const calls = [], logger = createApiProxyLogger({ error: (...args) => calls.push(args) })
  logger.error('http proxy error: /api/v1?private=synthetic-private\nbody: synthetic-body', { error: new Error(syntheticToken) })
  logger.error('ws proxy socket error: synthetic-private', { error: new Error(syntheticToken) })
  logger.error('ordinary build error', { timestamp: false })
  assert.equal(calls.length, 3)
  assert.equal(JSON.stringify(calls).includes('synthetic-private'), false)
  assert.equal(JSON.stringify(calls).includes('synthetic-body'), false)
  assert.equal(JSON.stringify(calls).includes(syntheticToken), false)
  assert.deepEqual(calls[2], ['ordinary build error', { timestamp: false }])
})

function send(origin, path, { method = 'GET', headers = {}, body } = {}) {
  const data = body === undefined ? undefined : JSON.stringify(body)
  const endpoint = new URL(origin)
  assert.ok(!['8080', '8081'].includes(endpoint.port), 'Fixtures must never contact real backend ports')
  return new Promise((resolveResponse, reject) => {
    const req = httpRequest({ hostname: endpoint.hostname, port: endpoint.port, path, method, agent: false,
      headers: { ...headers, ...(data === undefined ? {} : { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) }) } }, res => {
      const chunks = []
      res.on('data', chunk => chunks.push(chunk))
      res.on('error', reject)
      res.on('end', () => resolveResponse({ status: res.statusCode, headers: res.headers, text: Buffer.concat(chunks).toString('utf8') }))
    })
    req.setTimeout(5000, () => req.destroy(new Error('Loopback fixture request timed out')))
    req.on('error', reject)
    req.end(data)
  })
}
async function cleanupAll(tasks) {
  const failures = []
  for (const cleanup of tasks) {
    try { await cleanup() } catch (error) { failures.push(error) }
  }
  if (failures.length) throw new AggregateError(failures, 'Proxy fixture cleanup failed')
}
async function listenHttp(server) {
  await new Promise((done, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => { server.off('error', reject); done() })
  })
}
async function closeHttp(server) {
  await cleanupAll([
    () => server.closeAllConnections(),
    async () => { if (server.listening) await new Promise((done, reject) => server.close(error => error ? reject(error) : done())) },
  ])
}

test('cleanup after a synthetic test failure still closes the loopback server and removes preview files when another cleanup step throws', async t => {
  const server = createHttpServer((_req, res) => res.end())
  const directory = await mkdtemp(join(tmpdir(), 'qaryati-vite-proxy-'))
  const page = join(directory, 'index.html')
  // Independent safety cleanup also runs if a regression assertion below fails.
  t.after(() => cleanupAll([
    () => closeHttp(server),
    async () => { try { await unlink(page) } catch (error) { if (error.code !== 'ENOENT') throw error } },
    async () => { try { await rmdir(directory) } catch (error) { if (error.code !== 'ENOENT') throw error } },
  ]))
  await writeFile(page, '<!doctype html><title>Synthetic preview</title>')
  await listenHttp(server)
  const failure = new Error('Synthetic cleanup failure')
  const cleaned = []
  await assert.rejects((async () => {
    try { assert.fail('Synthetic test failure') } finally {
      await cleanupAll([
        () => { throw failure },
        async () => { await closeHttp(server); cleaned.push('server') },
        async () => { await unlink(page); cleaned.push('file') },
        async () => { await rmdir(directory); cleaned.push('directory') },
      ])
    }
  })(), error => error instanceof AggregateError && error.errors.length === 1 && error.errors[0] === failure)
  assert.deepEqual(cleaned, ['server', 'file', 'directory'])
  assert.equal(server.listening, false)
  await assert.rejects(stat(directory), { code: 'ENOENT' })
})

for (const kind of ['development', 'preview']) {
  test(`${kind} proxy forwards synthetic loopback HTTP without a real backend`, async t => {
    const received = [], logs = []
    const upstream = createHttpServer(async (req, res) => {
      const chunks = []
      for await (const chunk of req) chunks.push(chunk)
      const body = Buffer.concat(chunks).toString('utf8')
      const record = { url: req.url, method: req.method, headers: req.headers, body }
      received.push(record)
      const status = Number(/^\/api\/v1\/__fixture\/status\/(\d+)$/u.exec(req.url)?.[1] ?? (req.method === 'POST' ? 201 : 200))
      res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store',
        'X-Fixture-Response': 'synthetic-response', ...(status === 429 ? { 'Retry-After': '31' } : {}),
        ...(status === 401 ? { 'WWW-Authenticate': 'Bearer' } : {}), ...(status === 201 ? { Location: '/api/v1/sources/12' } : {}) })
      res.end(JSON.stringify(record))
    })
    let vite, directory
    t.after(() => cleanupAll([
      () => vite?.httpServer.closeAllConnections(),
      () => upstream.closeAllConnections(),
      () => vite?.close(),
      () => closeHttp(upstream),
      async () => { if (directory) { try { await unlink(join(directory, 'index.html')) } catch (error) { if (error.code !== 'ENOENT') throw error } } },
      async () => { if (directory) await rmdir(directory) },
    ]))
    await listenHttp(upstream)
    const upstreamPort = upstream.address().port
    assert.ok(![8080, 8081].includes(upstreamPort))
    const target = `http://127.0.0.1:${upstreamPort}`, config = configFor(target)
    const capture = (...args) => logs.push(args)
    config.customLogger = createApiProxyLogger(createLogger('info', { console: { log: capture, warn: capture, error: capture }, allowClearScreen: false }))
    if (kind === 'development') {
      vite = await createServer({ ...config, configFile: false, cacheDir: join(projectDirectory, 'node_modules/.vite-proxy-fixtures'),
        server: { ...config.server, port: 0, hmr: false, watch: null }, optimizeDeps: { noDiscovery: true, include: [] } })
      await vite.listen()
    } else {
      directory = await mkdtemp(join(tmpdir(), 'qaryati-vite-proxy-'))
      await writeFile(join(directory, 'index.html'), '<!doctype html><title>Synthetic fixture SPA</title>')
      vite = await preview({ ...config, configFile: false, build: { outDir: directory }, preview: { ...config.preview, port: 0 } })
    }
    assert.equal(vite.httpServer.address().address, '127.0.0.1')
    assert.equal(vite.config.env.DEV_API_PROXY_TARGET, undefined)
    const origin = `http://127.0.0.1:${vite.httpServer.address().port}`

    await t.test('public collection/detail and API root preserve exact paths, Arabic/reserved queries and response headers', async () => {
      const path = '/api/v1/public/villages?' + new URLSearchParams({ search: 'بتير & Battir + %_!/#?', page: '0', size: '6' })
      for (const endpoint of [path, '/api/v1/public/villages/42', '/api/v1?fixture=1', '/api/v1/__fixture/a%2Fb?tag=a&tag=b&encoded=%252F']) {
        const before = received.length, response = await send(origin, endpoint)
        assert.equal(response.status, 200)
        assert.equal(JSON.parse(response.text).url, endpoint)
        assert.equal(response.headers['x-fixture-response'], 'synthetic-response')
        assert.equal(response.headers['cache-control'], 'no-store')
        assert.equal(received.length, before + 1)
        assert.equal(received.at(-1).headers.authorization, undefined)
        assert.equal(received.at(-1).headers.cookie, undefined)
        assert.equal(received.at(-1).headers.host, `127.0.0.1:${upstreamPort}`)
      }
    })
    await t.test('authenticated GET and JSON POST/PUT/PATCH retain supplied headers and bodies exactly', async () => {
      for (const [method, path] of [['GET', '/api/v1/auth/me'], ['POST', '/api/v1/sources'], ['PUT', '/api/v1/sources/12'], ['PATCH', '/api/v1/data-conflicts/9/resolve']]) {
        const body = method === 'GET' ? undefined : { title: 'مصدر تجريبي & + %_!', note: 'synthetic-only', values: [0, null, false] }
        const before = received.length, response = await send(origin, path, { method, body,
          headers: { Authorization: `Bearer ${syntheticToken}`, 'X-Fixture-Request': 'synthetic-request' } })
        assert.equal(response.status, method === 'POST' ? 201 : 200)
        const record = JSON.parse(response.text)
        assert.equal(record.url, path)
        assert.equal(record.method, method)
        assert.equal(record.headers.authorization, `Bearer ${syntheticToken}`)
        assert.equal(record.headers['x-fixture-request'], 'synthetic-request')
        assert.equal(record.body, body === undefined ? '' : JSON.stringify(body))
        assert.equal(received.length, before + 1)
        if (method === 'POST') assert.equal(response.headers.location, '/api/v1/sources/12')
      }
    })
    for (const status of [400, 401, 403, 404, 409, 429, 500]) {
      await t.test(`HTTP ${status} is preserved as JSON, without retries or SPA fallback`, async () => {
        const before = received.length, response = await send(origin, `/api/v1/__fixture/status/${status}`)
        assert.equal(response.status, status)
        assert.match(response.headers['content-type'], /application\/json/)
        assert.equal(JSON.parse(response.text).url, `/api/v1/__fixture/status/${status}`)
        assert.equal(received.length, before + 1)
        if (status === 429) assert.equal(response.headers['retry-after'], '31')
        if (status === 401) assert.equal(response.headers['www-authenticate'], 'Bearer')
      })
    }
    await t.test('existing API client receives Retry-After and sends tokens only when supplied', async () => {
      const client = createApiClient({ baseUrl: `${origin}/api/v1`, fetchImpl: (url, init) => {
        const headers = new Headers(init.headers); headers.set('Connection', 'close')
        return fetch(url, { ...init, headers, signal: AbortSignal.timeout(5000) })
      } })
      assert.equal((await client('/public/villages')).headers.authorization, undefined)
      assert.equal((await client('/auth/me', { token: syntheticToken })).headers.authorization, `Bearer ${syntheticToken}`)
      await assert.rejects(client('/__fixture/status/429'), { status: 429, retryAfter: '31' })
    })
    await t.test('lookalike paths are never forwarded outside the API version boundary', async () => {
      const before = received.length
      for (const path of ['/api/v10/public/villages', '/api/v1-other']) await send(origin, path)
      assert.equal(received.length, before)
    })
    await t.test('unavailable dynamic upstream returns 502 with redacted connection logs', async () => {
      await closeHttp(upstream)
      const response = await send(origin, '/api/v1/public/villages?search=synthetic-private-query', {
        method: 'POST', headers: { Authorization: `Bearer ${syntheticToken}` }, body: { note: 'synthetic-private-body' },
      })
      assert.equal(response.status, 502)
      assert.ok(logs.some(args => String(args[0]).includes('API proxy connection failed.')))
      for (const value of [syntheticToken, 'synthetic-private-query', 'synthetic-private-body']) assert.equal(JSON.stringify(logs).includes(value), false)
    })
  })
}
