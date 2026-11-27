import { authAccessDecision } from './authAccess.js'
import { effectiveSourceRolesVerified, sourcePermissions } from './sourceAccess.js'

const none = Object.freeze({ read: false, manage: false, create: false, review: false, resolve: false })

// DataConflictService: village reads isAuthenticated(); create and status reads
// EDITOR/ADMIN; review and resolve ADMIN. Role-dependent operations reuse the
// verified effective-role contract and fail closed without valid profile roles.
export function conflictPermissions(session, { rolesContractVerified = effectiveSourceRolesVerified, now = Date.now() } = {}) {
  if (authAccessDecision(session, { now }) !== 'allow') return { ...none, reason: 'AUTH_REQUIRED' }
  const roles = sourcePermissions(session, { rolesContractVerified, now })
  if (roles.reason === 'SOURCE_ROLES_UNAVAILABLE') return { ...none, read: true, reason: 'CONFLICT_ROLES_UNAVAILABLE' }
  const editor = roles.write, admin = editor && session.user.roles.includes('ADMIN')
  return { read: true, manage: editor, create: editor, review: admin, resolve: admin, reason: editor ? null : 'CONFLICT_FORBIDDEN' }
}
