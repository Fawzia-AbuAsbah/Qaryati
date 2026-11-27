import { authAccessDecision } from './authAccess.js'
import { SourceError } from './sourceContracts.js'
import { toBackendId } from './villageAdapters.js'

// /auth/me exposes username only. No environment flag can approve role claims.
export const effectiveSourceRolesVerified = false

export function sourcePermissions(session, { rolesContractVerified = effectiveSourceRolesVerified, now = Date.now() } = {}) {
  if (authAccessDecision(session, { now }) !== 'allow') return { read: false, write: false, reason: 'AUTH_REQUIRED' }
  if (rolesContractVerified !== true || session.user.rolesVerified !== true || !Array.isArray(session.user.roles)
    || session.user.roles.some(role => !['VIEWER', 'EDITOR', 'ADMIN'].includes(role))) {
    return { read: false, write: false, reason: 'SOURCE_ROLES_UNAVAILABLE' }
  }
  const write = session.user.roles.some(role => ['EDITOR', 'ADMIN'].includes(role))
  const read = write || session.user.roles.includes('VIEWER')
  return { read, write, reason: read ? null : 'SOURCE_FORBIDDEN' }
}

export function requireSourcePermission(session, operation, options) {
  const permissions = sourcePermissions(session, options)
  if (!permissions[operation]) throw new SourceError(permissions.reason ?? 'SOURCE_FORBIDDEN')
}

export function sourceVillageIdentity(village, integration) {
  if (integration?.mode !== 'api' || integration.available !== true || !village) return null
  try { return toBackendId(village.id) === village.backendId ? village.id : null } catch { return null }
}
