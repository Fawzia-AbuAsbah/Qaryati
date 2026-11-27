import { toBackendId } from './villageAdapters.js'
import { verificationPermissions } from './verificationAccess.js'
import { adaptVerification, adaptVerificationHistory, VerificationError, verificationBody, verificationEntityType } from './verificationContracts.js'

export function createVerificationService({ request, getSession, rolesContractVerified } = {}) {
  if (typeof request !== 'function' || typeof getSession !== 'function') throw new TypeError('Authenticated session required')
  const initial = getSession(), user = initial.user, expiresAt = initial.expiresAt
  function isCurrent() {
    const session = getSession()
    return session.status === 'authenticated' && session.user === user && session.expiresAt === expiresAt && expiresAt > Date.now()
  }
  function permissions() {
    return isCurrent() ? verificationPermissions(getSession(), { rolesContractVerified }) : { read: false, create: false, reason: 'AUTH_REQUIRED' }
  }
  function authorize(operation) {
    const access = permissions()
    if (!access[operation]) throw new VerificationError(access.reason)
  }
  async function send(operation, options) {
    authorize(operation)
    options.signal?.throwIfAborted()
    const dto = await request('/verifications', options)
    options.signal?.throwIfAborted()
    authorize(operation)
    return dto
  }
  return {
    isCurrent, permissions,
    async list(villageId, { entityType = 'VILLAGE', signal } = {}) {
      const query = { entityType: verificationEntityType(entityType), entityId: toBackendId(villageId) }
      return adaptVerificationHistory(await send('read', { query, signal }), villageId)
    },
    async create(villageId, decision, reason, { entityType = 'VILLAGE', signal } = {}) {
      const body = verificationBody(villageId, decision, reason, entityType)
      return adaptVerification(await send('create', { method: 'POST', body, signal }), villageId)
    },
  }
}
