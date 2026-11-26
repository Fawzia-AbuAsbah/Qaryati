import { villages } from '../data/villages.js'

const collator = new Intl.Collator('ar')
const normalize = (value) => value.normalize('NFKC').replace(/[\p{M}\u0640]/gu, '').replace(/[أإآٱ]/g, 'ا').trim().replace(/\s+/g, ' ')

export const villagePageSize = 6

// Keep future transport and response mapping here so pages can consume the same shape.
export async function listVillages({ query = '', governorate = '', featured = false, page = 1, pageSize = villagePageSize } = {}) {
  const term = normalize(query)
  const matches = villages.filter((village) =>
    (!featured || village.featured) &&
    (!governorate || village.governorate === governorate) &&
    normalize(village.name).includes(term),
  )
  const totalPages = Math.max(1, Math.ceil(matches.length / pageSize))
  const currentPage = Math.min(Math.max(1, page), totalPages)
  const start = (currentPage - 1) * pageSize
  return {
    items: matches.slice(start, start + pageSize),
    total: matches.length,
    page: currentPage,
    totalPages,
    governorates: [...new Set(villages.map((village) => village.governorate))].sort(collator.compare),
  }
}

export async function getVillage(id) {
  return villages.find((village) => village.id === id) ?? null
}

export async function listMapVillages() {
  return villages.map(({ id, name, governorate, coordinates }) => ({ id, name, governorate, coordinates }))
}

export function hasCoordinates(village) {
  const [latitude, longitude] = village?.coordinates ?? []
  return Number.isFinite(latitude) && Math.abs(latitude) <= 90 &&
    Number.isFinite(longitude) && Math.abs(longitude) <= 180
}
