import { adaptVillage } from './villageAdapters.js'

function integer(value, field, minimum = 0) {
  if (!Number.isSafeInteger(value) || value < minimum) {
    throw new TypeError(`${field} must be a safe integer >= ${minimum}`)
  }
  return value
}

export function toBackendPage(page) {
  return integer(page, 'Frontend page', 1) - 1
}

export function toFrontendPage(page) {
  integer(page, 'Backend page')
  if (page === Number.MAX_SAFE_INTEGER) throw new TypeError('Backend page cannot be safely incremented')
  return page + 1
}

function envelope(dto, pageField) {
  if (dto === null || typeof dto !== 'object' || Array.isArray(dto) || !Array.isArray(dto.content)) {
    throw new TypeError('Page response must be an object with a content array')
  }
  const page = toFrontendPage(dto[pageField])
  const size = integer(dto.size, 'size', 1)
  const total = integer(dto.totalElements, 'totalElements')
  const totalPages = integer(dto.totalPages, 'totalPages')
  if (totalPages !== Math.ceil(total / size)) throw new TypeError('Inconsistent totalPages')
  const outOfRange = dto[pageField] >= Math.max(totalPages, 1)
  const expectedCount = dto[pageField] < totalPages ? Math.min(size, total - dto[pageField] * size) : 0
  if (dto.content.length !== expectedCount) throw new TypeError('Content length disagrees with pagination metadata')
  if (dto.content.some(item => item === null || typeof item !== 'object' || Array.isArray(item))) {
    throw new TypeError('Page content must contain DTO objects')
  }
  return {
    items: [...dto.content], total, page, pageSize: size, totalPages,
    numberOfElements: dto.content.length, first: page === 1, last: page >= totalPages,
    hasNext: page < totalPages, hasPrevious: page > 1, empty: dto.content.length === 0,
    outOfRange,
  }
}

export function adaptVillagePage(dto, governorateNames) {
  const result = envelope(dto, 'number')
  for (const field of ['numberOfElements', 'first', 'last', 'hasNext', 'hasPrevious', 'empty']) {
    if (dto[field] !== result[field]) throw new TypeError(`Inconsistent VillagePageResponse.${field}`)
  }
  return { ...result, items: dto.content.map(item => adaptVillage(item, governorateNames)) }
}

// Only the shared envelope is adapted; population/heritage domain DTOs stay untouched.
export function adaptRecordPage(dto) {
  return envelope(dto, 'page')
}
