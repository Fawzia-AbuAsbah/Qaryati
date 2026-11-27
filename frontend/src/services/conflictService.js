import { toBackendId } from './villageAdapters.js'
import { conflictPermissions } from './conflictAccess.js'
import {
  adaptConflict, adaptConflictList, ConflictError, conflictCreateBody, conflictEntityType, conflictId, conflictResolveBody, conflictStatus,
} from './conflictContracts.js'

export function createConflictService({ request, getSession, rolesContractVerified } = {}) {
  if (typeof request !== 'function' || typeof getSession !== 'function') throw new TypeError('Authenticated session required')
  const initial = getSession(), user = initial.user, expiresAt = initial.expiresAt
  function isCurrent() {
    const session = getSession()
    return session.status === 'authenticated' && session.user === user && session.expiresAt === expiresAt && expiresAt > Date.now()
  }
  function permissions() {
    return isCurrent() ? conflictPermissions(getSession(), { rolesContractVerified }) : conflictPermissions({ status: 'anonymous' })
  }
  function authorize(operation) {
    const access = permissions()
    if (!access[operation]) throw new ConflictError(access.reason ?? 'CONFLICT_FORBIDDEN')
  }
  async function send(operation, path, options) {
    authorize(operation)
    options.signal?.throwIfAborted()
    const dto = await request(path, options)
    options.signal?.throwIfAborted()
    authorize(operation)
    return dto
  }
  return {
    isCurrent, permissions,
    async listForVillage(villageId, { entityType = 'VILLAGE', signal } = {}) {
      const query = { entityType: conflictEntityType(entityType), entityId: toBackendId(villageId) }
      return adaptConflictList(await send('read', '/data-conflicts', { query, signal }), { villageId })
    },
    async listByStatus(status, { signal } = {}) {
      const path = `/data-conflicts/status/${conflictStatus(status)}`
      return adaptConflictList(await send('manage', path, { signal }), { status })
    },
    async create(villageId, fieldName, conflictingValues, { entityType = 'VILLAGE', signal } = {}) {
      const body = conflictCreateBody(villageId, fieldName, conflictingValues, entityType)
      return adaptConflict(await send('create', '/data-conflicts', { method: 'POST', body, signal }), { villageId })
    },
    async review(id, { signal } = {}) {
      const path = `/data-conflicts/${conflictId(id)}/review`
      const conflict = adaptConflict(await send('review', path, { method: 'PATCH', signal }))
      if (conflict.id !== id || conflict.status !== 'IN_REVIEW') throw new TypeError('Unexpected review response')
      return conflict
    },
    async resolve(id, resolutionNotes, { signal } = {}) {
      const path = `/data-conflicts/${conflictId(id)}/resolve`
      const body = conflictResolveBody(resolutionNotes)
      const conflict = adaptConflict(await send('resolve', path, { method: 'PATCH', body, signal }))
      if (conflict.id !== id || conflict.status !== 'RESOLVED') throw new TypeError('Unexpected resolve response')
      return conflict
    },
  }
}
