import { apiClient } from './apiClient.js'
import { toBackendId } from './villageAdapters.js'
import { adaptPublicVillage } from './publicVillageAdapters.js'
import { createVillageAccess, publicVillageCapabilities, villageDataMode, VillageAccessError } from './villageMode.js'
import { getVillage as getMockVillage } from './villageService.js'

function villageId(routeId) {
  try { return toBackendId(routeId) } catch { throw new VillageAccessError('INVALID_VILLAGE_ID') }
}

export function createVillageDetailsService({ mode = 'mock', identityVerified = publicVillageCapabilities.identity,
  request = apiClient, mockGet = getMockVillage } = {}) {
  const access = createVillageAccess({ mode, contractVerified: identityVerified })
  async function getVillage(routeId, options = {}) {
    const { signal } = options
    signal?.throwIfAborted()
    if (mode === 'mock') {
      const result = await mockGet(routeId)
      signal?.throwIfAborted()
      return result
    }
    const id = villageId(routeId)
    access.requireApi()
    if (Object.keys(options).some(key => key !== 'signal')) throw new TypeError('Public detail accepts no query or authentication options')
    let dto
    try { dto = await request(`/public/villages/${id}`, { signal }) } catch (error) {
      signal?.throwIfAborted()
      if (error.status === 404) return null
      throw error
    }
    signal?.throwIfAborted()
    const village = adaptPublicVillage(dto)
    if (village.backendId !== id) throw new TypeError('Village response ID does not match the route')
    return village
  }
  async function getGovernorates({ signal } = {}) {
    signal?.throwIfAborted(); access.requireApi()
    throw new VillageAccessError('PUBLIC_CATALOGUE_UNAVAILABLE')
  }
  async function listSection(routeId, _section, { signal } = {}) {
    signal?.throwIfAborted(); villageId(routeId); access.requireApi()
    // Existing protected Heritage handlers are not public section contracts.
    throw new VillageAccessError('UNAVAILABLE_SECTION')
  }
  return { mode, available: access.available, getVillage, getGovernorates, listSection }
}

export const villageDetailsService = createVillageDetailsService({ mode: villageDataMode })
