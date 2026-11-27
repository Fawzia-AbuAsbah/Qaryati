// The verified /auth/me response exposes no effective roles. Keep every role
// requirement closed, including unexpected claims or properties supplied by callers.
export function authAccessDecision(session, { requiredRoles = [], now = Date.now() } = {}) {
  if (session.status === 'authenticating') return 'pending'
  if (session.status !== 'authenticated' || !session.user || !(session.expiresAt > now)) return 'login'
  if (!Array.isArray(requiredRoles) || requiredRoles.length > 0) return 'roles-unavailable'
  return 'allow'
}

export function safeAuthReturnTo(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || /[\\\s]/.test(value) || value.startsWith('//')) return '/'
  let url
  try { url = new URL(value, 'https://qaryati-ui.invalid') } catch { return '/' }
  if (url.origin !== 'https://qaryati-ui.invalid' || url.pathname === '/login') return '/'
  if (!['/', '/villages', '/map'].includes(url.pathname) && !/^\/villages\/[^/]+$/.test(url.pathname)) return '/'
  return `${url.pathname}${url.search}${url.hash}`
}
