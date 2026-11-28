import { authAccessDecision } from './authAccess.js'
import { applicationRoles } from './authRoles.js'

const denied = Object.freeze({ read: false, privileged: false })

export function populationPermissions(session, { now = Date.now() } = {}) {
  const decision = authAccessDecision(session, { requiredRoles: applicationRoles, now })
  if (decision === 'allow') {
    const privileged = session.user.roles.includes('EDITOR') || session.user.roles.includes('ADMIN')
    return { read: true, privileged, reason: null }
  }
  if (decision === 'login') return { ...denied, reason: 'AUTH_REQUIRED' }
  if (decision === 'forbidden') return { ...denied, reason: 'POPULATION_FORBIDDEN' }
  return { ...denied, reason: 'POPULATION_ROLES_UNAVAILABLE' }
}
