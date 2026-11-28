import { applicationRoles, effectiveRolesContractVerified, validRoles, verifiedProfileRoles } from './authRoles.js'

// requiredRoles is an OR allowlist, matching the backend's explicit hasAnyRole
// checks. An empty requirement means authentication only, with no default role.
export function authAccessDecision(session, { requiredRoles = [], now = Date.now() } = {}) {
  if (session.status === 'authenticating') return 'pending'
  if (session.status !== 'authenticated' || !session.user || !(session.expiresAt > now)) return 'login'
  if (!validRoles(requiredRoles)) return 'roles-unavailable'
  if (requiredRoles.length === 0) return 'allow'
  if (session.checking) return 'pending'
  if (session.rolesStale || !effectiveRolesContractVerified || !verifiedProfileRoles(session.user)) return 'roles-unavailable'
  return requiredRoles.some(role => session.user.roles.includes(role)) ? 'allow' : 'forbidden'
}

export const memberRoutes = Object.freeze([
  Object.freeze({ to: '/population-records', label: 'سجلات السكان', requiredRoles: applicationRoles }),
  Object.freeze({ to: '/sources', label: 'المصادر', requiredRoles: applicationRoles }),
  Object.freeze({ to: '/data-conflicts', label: 'تعارضات البيانات', requiredRoles: Object.freeze(['EDITOR', 'ADMIN']) }),
  Object.freeze({ to: '/audit-logs', label: 'سجل التدقيق', requiredRoles: Object.freeze(['ADMIN']) }),
])

export function safeAuthReturnTo(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || /[\\\s]/.test(value) || value.startsWith('//')) return '/'
  let url
  try { url = new URL(value, 'https://qaryati-ui.invalid') } catch { return '/' }
  if (url.origin !== 'https://qaryati-ui.invalid' || url.pathname === '/login') return '/'
  if (!['/', '/villages', '/map', '/population-records', '/sources', '/data-conflicts', '/audit-logs'].includes(url.pathname) && !/^\/villages\/[^/]+$/.test(url.pathname)) return '/'
  return `${url.pathname}${url.search}${url.hash}`
}
