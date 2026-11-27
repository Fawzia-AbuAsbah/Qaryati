const defaultBaseUrl = import.meta.env?.VITE_API_BASE_URL?.trim() || '/api/v1'
const relativeOrigin = 'https://api-client.invalid'

const httpCodes = {
  400: 'BAD_REQUEST',
  401: 'UNAUTHORIZED',
  403: 'FORBIDDEN',
  404: 'NOT_FOUND',
  409: 'CONFLICT',
  429: 'RATE_LIMITED',
}

export class ApiError extends Error {
  constructor(message, { kind, code, response, details = null, malformedBody = false, cause } = {}) {
    super(message, { cause })
    this.name = 'ApiError'
    this.kind = kind
    this.code = code
    this.status = response?.status ?? null
    this.statusText = response?.statusText ?? null
    this.details = details
    this.headers = response?.headers ?? null
    this.retryAfter = response?.headers.get('Retry-After') ?? null
    this.malformedBody = malformedBody
  }
}

function prepareBase(baseUrl) {
  if (typeof baseUrl !== 'string' || !baseUrl.trim() || baseUrl.includes('\\')) {
    throw new TypeError('API base URL must be an HTTP(S) URL or an origin-relative path')
  }
  const value = baseUrl.trim()
  const relative = value.startsWith('/') && !value.startsWith('//')
  const url = new URL(value, relativeOrigin)
  if ((!relative && !/^https?:\/\//i.test(value)) || !['http:', 'https:'].includes(url.protocol)
    || url.username || url.password || url.search || url.hash) {
    throw new TypeError('API base URL must be an HTTP(S) URL or an origin-relative path without credentials, query or fragment')
  }
  url.pathname = `${url.pathname.replace(/\/+$/, '')}/`
  return { url, relative }
}

function requestUrl(base, path, query) {
  if (typeof path !== 'string' || /^[a-z][a-z\d+.-]*:/i.test(path)
    || path.startsWith('//') || path.includes('\\') || path.includes('#')) {
    throw new TypeError('API endpoints must be paths within the configured base URL')
  }
  const resource = path.replace(/^\/+/, '')
  if (resource.split('?')[0].split('/').some(segment => ['.', '..'].includes(decodeURIComponent(segment)))) {
    throw new TypeError('API endpoints cannot traverse outside the configured base URL')
  }
  const url = new URL(resource, base.url)
  if (url.origin !== base.url.origin || !url.pathname.startsWith(base.url.pathname) || url.username || url.password) {
    throw new TypeError('API endpoints must stay within the configured base URL')
  }
  const entries = query instanceof URLSearchParams ? [...query] : Object.entries(query ?? {})
    .flatMap(([key, value]) => (Array.isArray(value) ? value : [value])
      .filter(item => item !== null && item !== undefined).map(item => [key, item]))
  const replaced = new Set()
  for (const [key, value] of entries) {
    if (!replaced.has(key)) url.searchParams.delete(key)
    replaced.add(key)
    url.searchParams.append(key, String(value))
  }
  return base.relative ? `${url.pathname}${url.search}` : url.href
}

function backendMessage(details, status) {
  for (const value of [details?.message, details?.error]) {
    if (typeof value === 'string' && value.trim()) return value
  }
  return `API request failed (HTTP ${status})`
}

function transportError(cause, signal, response) {
  if (signal?.aborted || cause?.name === 'AbortError') throw cause
  return new ApiError('Unable to read a response from the API', {
    kind: 'network', code: 'NETWORK_ERROR', response, cause,
  })
}

export function createApiClient({ baseUrl = defaultBaseUrl, fetchImpl = globalThis.fetch } = {}) {
  const base = prepareBase(baseUrl)
  if (typeof fetchImpl !== 'function') throw new TypeError('A Fetch implementation is required')

  return async function request(path, { method = 'GET', query, body, token, signal, headers } = {}) {
    signal?.throwIfAborted()
    const url = requestUrl(base, path, query)
    const verb = method.toUpperCase()
    const requestHeaders = new Headers(headers)
    if (!requestHeaders.has('Accept')) requestHeaders.set('Accept', 'application/json')
    if (token !== undefined && token !== null) {
      if (typeof token !== 'string' || !token.trim()) throw new TypeError('Bearer token must be a nonempty string')
      requestHeaders.set('Authorization', `Bearer ${token}`)
    }
    let json
    if (body !== undefined) {
      if (verb === 'GET' || verb === 'HEAD') throw new TypeError('GET and HEAD requests cannot have a JSON body')
      json = JSON.stringify(body)
      if (json === undefined) throw new TypeError('Request body must be JSON serializable')
      if (!requestHeaders.has('Content-Type')) requestHeaders.set('Content-Type', 'application/json')
    }

    let response
    try {
      response = await fetchImpl(url, {
        method: verb, headers: requestHeaders, body: json, signal, credentials: 'omit',
      })
    } catch (cause) {
      throw transportError(cause, signal)
    }

    let text
    try {
      text = verb === 'HEAD' || response.status === 204 || response.status === 205 ? '' : await response.text()
    } catch (cause) {
      throw transportError(cause, signal, response)
    }

    let details = null
    let malformedBody = false
    let parseError
    if (text.trim()) {
      try {
        details = JSON.parse(text)
      } catch (cause) {
        details = text
        malformedBody = true
        parseError = cause
      }
    }

    // HTTP status remains authoritative even when a gateway returns HTML or an empty error body.
    if (!response.ok) {
      throw new ApiError(backendMessage(details, response.status), {
        kind: 'http',
        code: httpCodes[response.status] ?? (response.status >= 500 ? 'SERVER_ERROR' : 'HTTP_ERROR'),
        response, details, malformedBody, cause: parseError,
      })
    }
    if (malformedBody) {
      throw new ApiError('API returned an invalid JSON response', {
        kind: 'malformed-response', code: 'MALFORMED_RESPONSE', response, details, malformedBody, cause: parseError,
      })
    }
    return details
  }
}

export const apiClient = createApiClient()
