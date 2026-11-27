import { populationPermissions } from './populationAccess.js'
import { adaptPopulationPage, PopulationError, populationRequest } from './populationContracts.js'

export function createPopulationService({ request, getSession } = {}) {
  if (typeof request !== 'function' || typeof getSession !== 'function') throw new TypeError('Authenticated session required')
  const initial = getSession(), user = initial.user, expiresAt = initial.expiresAt
  function isCurrent() {
    const session = getSession()
    return session.status === 'authenticated' && session.user === user && session.expiresAt === expiresAt && expiresAt > Date.now()
  }
  function permissions() {
    return isCurrent() ? populationPermissions(getSession()) : populationPermissions({ status: 'anonymous' })
  }
  function authorize() {
    const access = permissions()
    if (!access.read) throw new PopulationError(access.reason ?? 'POPULATION_FORBIDDEN')
  }
  return {
    isCurrent, permissions,
    async list(query, { signal } = {}) {
      authorize()
      signal?.throwIfAborted()
      const requestDetails = populationRequest(query)
      const dto = await request(requestDetails.path, { query: requestDetails.query, signal })
      signal?.throwIfAborted()
      authorize()
      return adaptPopulationPage(dto, requestDetails.requested)
    },
  }
}
