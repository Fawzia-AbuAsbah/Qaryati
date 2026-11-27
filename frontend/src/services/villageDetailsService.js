import { apiClient } from './apiClient.js'
import { adaptGovernorateCatalog, adaptVillage, toBackendId } from './villageAdapters.js'
import { adaptRecordPage, toBackendPage } from './paginationAdapters.js'
import { adaptHistoricalOverview, adaptHistoricalEvent, adaptCulturalHeritage, adaptHeritageSite } from './heritageAdapters.js'
import { createVillageAccess, publicVillageContractsVerified, villageDataMode, VillageAccessError } from './villageMode.js'
import { getVillage as getMockVillage } from './villageService.js'

const sections = Object.freeze({
  overviews: { path: '/village-historical-overviews', adapt: adaptHistoricalOverview },
  events: { path: '/historical-events', adapt: adaptHistoricalEvent },
  culture: { path: '/cultural-heritage', adapt: adaptCulturalHeritage },
  sites: { path: '/heritage-sites', adapt: adaptHeritageSite },
})

function villageId(routeId) {
  try { return toBackendId(routeId) } catch { throw new VillageAccessError('INVALID_VILLAGE_ID') }
}

export function createVillageDetailsService({
  mode = 'mock', publicationVerified = publicVillageContractsVerified,
  request = apiClient, mockGet = getMockVillage,
} = {}) {
  const access = createVillageAccess({ mode, publicationVerified })

  async function getVillage(routeId, { signal } = {}) {
    signal?.throwIfAborted()
    if (mode === 'mock') {
      const result = await mockGet(routeId)
      signal?.throwIfAborted()
      return result
    }
    const id = villageId(routeId)
    access.requireApi()
    let dto
    try { dto = await request(`/villages/${id}`, { signal }) } catch (error) {
      signal?.throwIfAborted()
      if (error.status === 404) return null
      throw error
    }
    signal?.throwIfAborted()
    const result = adaptVillage(dto)
    if (result.backendId !== id) throw new TypeError('Village response ID does not match the route')
    return result
  }

  async function getGovernorates({ signal } = {}) {
    access.requireApi()
    signal?.throwIfAborted()
    const dto = await request('/governorates', { signal })
    signal?.throwIfAborted()
    return adaptGovernorateCatalog(dto)
  }

  async function listSection(routeId, section, { page = 1, pageSize = 5, signal } = {}) {
    signal?.throwIfAborted()
    const id = villageId(routeId)
    access.requireApi()
    if (!Object.hasOwn(sections, section)) throw new VillageAccessError('UNAVAILABLE_SECTION')
    let backendPage = toBackendPage(page)
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100 || backendPage * pageSize > 2147483647) {
      throw new TypeError('Invalid section pagination')
    }
    const contract = sections[section]
    for (let corrections = 0; ; corrections++) {
      signal?.throwIfAborted()
      const dto = await request(contract.path, { query: { villageId: id, page: backendPage, size: pageSize }, signal })
      signal?.throwIfAborted()
      const result = adaptRecordPage(dto)
      if (result.page !== backendPage + 1 || result.pageSize !== pageSize) throw new TypeError('Mismatched section pagination')
      const items = result.items.map(contract.adapt)
      // Reject a broken filtering contract as a section error; do not hide rows
      // selectively or treat this check as a publication/authorization boundary.
      if (items.some(item => item.villageId !== id)) throw new TypeError('Section contains another village')
      if (!result.outOfRange) return { ...result, items }
      if (corrections === 2) throw new VillageAccessError('PAGINATION_CHANGED')
      backendPage = toBackendPage(Math.max(1, result.totalPages))
    }
  }

  return { mode, available: access.available, getVillage, getGovernorates, listSection }
}

export const villageDetailsService = createVillageDetailsService({ mode: villageDataMode })
