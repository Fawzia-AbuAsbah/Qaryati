import { apiClient } from './apiClient.js'
import { createVillageExplorerService } from './villageExplorerService.js'
import { createVillageAccess, publicVillageCapabilities, villageDataMode } from './villageMode.js'
import { updateExplorerQuery } from './explorerQuery.js'
import { publicVillageSearch } from './publicVillageSearch.js'

export function homeSearchLocation(value, { mode = villageDataMode } = {}) {
  if (typeof value !== 'string') throw new TypeError('Home search must be text')
  const term = mode === 'api' ? publicVillageSearch(value) : value.trim()
  return { pathname: '/villages', search: updateExplorerQuery(new URLSearchParams(), 'q', term).toString() }
}

export function createVillageHomeService({ mode = 'mock', browsingVerified = publicVillageCapabilities.browsing, request = apiClient } = {}) {
  const access = createVillageAccess({ mode, contractVerified: browsingVerified })
  const villages = createVillageExplorerService({ mode, browsingVerified, request })
  async function getFeatured({ signal } = {}) {
    signal?.throwIfAborted()
    if (mode === 'api') {
      // No approved featured endpoint or selection policy exists. This returns
      // availability information only, with no request and no arbitrary rows.
      return { status: 'unavailable', reason: 'FEATURED_CONTRACT_UNCONFIRMED', items: [] }
    }
    if (mode !== 'mock') access.requireApi()
    const result = await villages.listVillages({ featured: true, pageSize: 3, signal })
    return { ...result, status: 'ready' }
  }
  return { mode, available: access.available, getFeatured }
}

export const villageHomeService = createVillageHomeService({ mode: villageDataMode })
