import { adaptSource, adaptSourceCatalog, adaptSourceLinks, SourceError, sourceBody, sourceReplacement } from './sourceContracts.js'
import { effectiveSourceRolesVerified, requireSourcePermission, sourcePermissions } from './sourceAccess.js'
import { toBackendId } from './villageAdapters.js'

// Production receives AuthProvider's scoped request, which reuses apiClient and
// clears the session on 401. There is no token cache or unauthenticated fallback.
export function createSourcesService({ request, getSession, rolesContractVerified = effectiveSourceRolesVerified } = {}) {
  if (typeof request !== 'function' || typeof getSession !== 'function') throw new TypeError('Authenticated session required')
  const initial = getSession(), user = initial.user, expiresAt = initial.expiresAt
  function isCurrent() {
    const session = getSession()
    return session.status === 'authenticated' && session.user === user && session.expiresAt === expiresAt && expiresAt > Date.now()
  }
  function authorize(operation) {
    if (!isCurrent()) throw new SourceError('AUTH_REQUIRED')
    requireSourcePermission(getSession(), operation, { rolesContractVerified })
  }
  async function send(operation, path, options = {}) {
    authorize(operation)
    options.signal?.throwIfAborted()
    const dto = await request(path, options)
    options.signal?.throwIfAborted()
    authorize(operation)
    return dto
  }
  return {
    isCurrent,
    permissions: () => isCurrent() ? sourcePermissions(getSession(), { rolesContractVerified }) : { read: false, write: false, reason: 'AUTH_REQUIRED' },
    async list({ signal } = {}) { return adaptSourceCatalog(await send('read', '/sources', { signal })) },
    async get(id, { signal } = {}) {
      toBackendId(id)
      const source = adaptSource(await send('read', `/sources/${id}`, { signal }))
      if (source.id !== id) throw new TypeError('Mismatched SourceResponse ID')
      return source
    },
    async create(value, { signal } = {}) {
      const body = sourceBody(value)
      return adaptSource(await send('write', '/sources', { method: 'POST', body, signal }))
    },
    async update(original, changes, { signal } = {}) {
      const body = sourceReplacement(original, changes)
      const source = adaptSource(await send('write', `/sources/${original.id}`, { method: 'PUT', body, signal }))
      if (source.id !== original.id) throw new TypeError('Mismatched SourceResponse ID')
      return source
    },
    async villageSources(villageId, { signal } = {}) {
      toBackendId(villageId)
      return adaptSourceLinks(await send('read', `/villages/${villageId}/sources`, { signal }), { villageId })
    },
    async link(villageId, sourceId, claimNote, { signal } = {}) {
      toBackendId(villageId)
      const body = { sourceId: toBackendId(sourceId), claimNote }
      if (claimNote !== null && typeof claimNote !== 'string') throw new SourceError('SOURCE_VALIDATION', { claimNote: 'أدخل نص الملاحظة أو اترك الحقل فارغًا.' })
      const dto = await send('write', `/villages/${villageId}/sources`, { method: 'POST', body, signal })
      return adaptSourceLinks([dto], { villageId, sourceId })[0]
    },
    async sourceVillages(sourceId, { signal } = {}) {
      toBackendId(sourceId)
      return adaptSourceLinks(await send('read', `/sources/${sourceId}/villages`, { signal }), { sourceId })
    },
  }
}
