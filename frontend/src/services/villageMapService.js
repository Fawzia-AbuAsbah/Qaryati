import { apiClient } from './apiClient.js'
import { createVillageExplorerService } from './villageExplorerService.js'
import { createVillageAccess, publicVillageCapabilities, villageDataMode, VillageAccessError } from './villageMode.js'
import { listMapVillages } from './villageService.js'

export function createVillageMapService({
  mode = 'mock', browsingVerified = publicVillageCapabilities.browsing, request = apiClient,
  pageSize = 100, maxPages = 100, maxVillages = 10000, mockList = listMapVillages,
} = {}) {
  const access = createVillageAccess({ mode, contractVerified: browsingVerified })
  const villages = createVillageExplorerService({ mode, browsingVerified, request })

  async function listVillages({ signal } = {}) {
    signal?.throwIfAborted()
    if (mode === 'mock') {
      const items = await mockList()
      signal?.throwIfAborted()
      return { items, total: items.length, complete: true }
    }
    access.requireApi()
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100
      || !Number.isInteger(maxPages) || maxPages < 1 || maxPages > 100
      || !Number.isInteger(maxVillages) || maxVillages < 1 || maxVillages > 10000) {
      throw new TypeError('Invalid map traversal limits')
    }
    const items = [], ids = new Set()
    let total, totalPages, previousId = 0
    for (let page = 1; page <= maxPages; page++) {
      signal?.throwIfAborted()
      // Reuse public listing/adapters and the basic-browsing capability.
      const result = await villages.listVillages({ page, pageSize, sortBy: 'id', sortDir: 'asc', signal })
      signal?.throwIfAborted()
      if (result.page !== page || result.pageSize !== pageSize || result.outOfRange) {
        throw new VillageAccessError('INCOMPLETE_MAP')
      }
      if (page === 1) {
        total = result.total
        totalPages = result.totalPages
        if (totalPages > maxPages || total > maxVillages) throw new VillageAccessError('MAP_LIMIT_EXCEEDED')
      } else if (result.total !== total || result.totalPages !== totalPages) {
        throw new VillageAccessError('MAP_DATA_CHANGED')
      }
      for (const village of result.items) {
        if (ids.has(village.id)) throw new VillageAccessError('MAP_DUPLICATE_IDS')
        if (village.backendId <= previousId) throw new VillageAccessError('MAP_DATA_CHANGED')
        ids.add(village.id)
        previousId = village.backendId
        items.push(village)
      }
      if (page >= totalPages) {
        if (items.length !== total) throw new VillageAccessError('INCOMPLETE_MAP')
        return { items, total, complete: true }
      }
    }
    throw new VillageAccessError('MAP_LIMIT_EXCEEDED')
  }

  return { mode, available: access.available, listVillages }
}

export const villageMapService = createVillageMapService({ mode: villageDataMode })
