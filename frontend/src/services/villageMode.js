// Independently reviewed contracts. Mode selection cannot approve missing APIs.
export const publicVillageCapabilities = Object.freeze({
  browsing: true,
  identity: true,
  geographicCatalogues: false,
  richSections: false,
})
export const villageDataMode = import.meta.env?.VITE_VILLAGE_EXPLORER_MODE?.trim() || 'mock'

export class VillageAccessError extends Error {
  constructor(code) {
    super(code)
    this.name = 'VillageAccessError'
    this.code = code
  }
}

export function createVillageAccess({ mode = 'mock', contractVerified = false } = {}) {
  return {
    mode,
    available: mode === 'mock' || (mode === 'api' && contractVerified === true),
    requireApi() {
      if (mode !== 'api') throw new VillageAccessError('INVALID_MODE')
      if (contractVerified !== true) throw new VillageAccessError('PUBLIC_CONTRACTS_UNCONFIRMED')
    },
  }
}
