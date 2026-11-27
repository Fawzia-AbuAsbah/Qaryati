import { createLogger } from 'vite'

export const defaultApiProxyTarget = 'http://127.0.0.1:8080'
export const apiProxyContext = '^/api/v1(?:/|\\?|$)'

export function resolveApiProxyTarget(value = defaultApiProxyTarget) {
  const invalid = () => new Error('DEV_API_PROXY_TARGET must be an absolute HTTP(S) origin without credentials, path, query or fragment.')
  if (typeof value !== 'string') throw invalid()
  const input = value.trim()
  if (!/^https?:\/\/[^/?#\\\s]+\/?$/iu.test(input)) throw invalid()
  let url
  try { url = new URL(input) } catch { throw invalid() }
  if (!url.hostname || url.username || url.password || url.pathname !== '/' || url.search || url.hash) throw invalid()
  return url.origin
}

export function createApiProxy(target) {
  return { [apiProxyContext]: { target: resolveApiProxyTarget(target), changeOrigin: true, secure: true } }
}

// Vite's default proxy-error message includes the entire request URL. Never log
// API query values, headers, payloads or upstream error stacks on that path.
export function createApiProxyLogger(logger = createLogger()) {
  const error = logger.error.bind(logger)
  logger.error = (message, options) => {
    if (typeof message === 'string' && /\b(?:http|ws) proxy(?: socket| bypass)? error:/u.test(message)) {
      error('API proxy connection failed. Check DEV_API_PROXY_TARGET and whether the backend is available.', { timestamp: true })
    } else error(message, options)
  }
  return logger
}
