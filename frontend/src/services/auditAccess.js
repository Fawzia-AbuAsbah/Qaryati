import { authAccessDecision } from './authAccess.js'
import { effectiveSourceRolesVerified, sourcePermissions } from './sourceAccess.js'

// AuditLogController and AuditLogService both require hasRole('ADMIN') for every
// read. Without verified effective roles, no audit request may be sent.
export function auditPermissions(session, { rolesContractVerified = effectiveSourceRolesVerified, now = Date.now() } = {}) {
  if (authAccessDecision(session, { now }) !== 'allow') return { read: false, reason: 'AUTH_REQUIRED' }
  const roles = sourcePermissions(session, { rolesContractVerified, now })
  if (roles.reason === 'SOURCE_ROLES_UNAVAILABLE') return { read: false, reason: 'AUDIT_ROLES_UNAVAILABLE' }
  const read = session.user.roles.includes('ADMIN')
  return { read, reason: read ? null : 'AUDIT_FORBIDDEN' }
}
