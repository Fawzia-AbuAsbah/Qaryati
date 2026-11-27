import { authAccessDecision } from './authAccess.js'
import { effectiveSourceRolesVerified, sourcePermissions } from './sourceAccess.js'

// Effective /auth/me roles govern creation; only ADMIN may record a decision.
// Verification history alone explicitly permits all authenticated users.
export function verificationPermissions(session, { rolesContractVerified = effectiveSourceRolesVerified, now = Date.now() } = {}) {
  if (authAccessDecision(session, { now }) !== 'allow') return { read: false, create: false, reason: 'AUTH_REQUIRED' }
  const roles = sourcePermissions(session, { rolesContractVerified, now })
  if (roles.reason === 'SOURCE_ROLES_UNAVAILABLE') return { read: true, create: false, reason: 'VERIFICATION_ROLES_UNAVAILABLE' }
  const create = roles.write && session.user.roles.includes('ADMIN')
  return { read: true, create, reason: create ? null : 'VERIFICATION_FORBIDDEN' }
}
