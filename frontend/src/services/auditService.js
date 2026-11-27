import { auditPermissions } from './auditAccess.js'
import { adaptAuditLogList, AuditError, auditRequest } from './auditContracts.js'

// Read-only: the backend exposes no audit mutation, global listing or pagination.
export function createAuditService({ request, getSession, rolesContractVerified } = {}) {
  if (typeof request !== 'function' || typeof getSession !== 'function') throw new TypeError('Authenticated session required')
  const initial = getSession(), user = initial.user, expiresAt = initial.expiresAt
  function isCurrent() {
    const session = getSession()
    return session.status === 'authenticated' && session.user === user && session.expiresAt === expiresAt && expiresAt > Date.now()
  }
  function permissions() {
    return isCurrent() ? auditPermissions(getSession(), { rolesContractVerified }) : { read: false, reason: 'AUTH_REQUIRED' }
  }
  function authorize() {
    const access = permissions()
    if (!access.read) throw new AuditError(access.reason ?? 'AUDIT_FORBIDDEN')
  }
  return {
    isCurrent, permissions,
    async search(query, { signal } = {}) {
      const { path, query: params } = auditRequest(query)
      authorize()
      signal?.throwIfAborted()
      const dto = await request(path, { query: params, signal })
      signal?.throwIfAborted()
      authorize()
      return adaptAuditLogList(dto, query)
    },
  }
}
