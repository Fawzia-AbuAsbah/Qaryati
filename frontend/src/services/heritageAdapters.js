import { toCoordinates, toRouteId } from './villageAdapters.js'

function text(value, field) {
  if (value == null) return null
  if (typeof value !== 'string') throw new TypeError(`${field} must be text or null`)
  return value.trim() ? value : null
}

function enumeration(value, values, field) {
  if (value == null) return null
  if (!values.includes(value)) throw new TypeError(`Unknown ${field}`)
  return value
}

function identity(dto) {
  if (dto === null || typeof dto !== 'object' || Array.isArray(dto)) throw new TypeError('Expected a domain DTO')
  const id = toRouteId(dto.id)
  toRouteId(dto.villageId)
  let sourceIds = null
  if (dto.sourceIds != null) {
    if (!Array.isArray(dto.sourceIds)) throw new TypeError('sourceIds must be an array or null')
    sourceIds = dto.sourceIds.map(value => { toRouteId(value); return value })
  }
  return {
    id, backendId: dto.id, villageId: dto.villageId, sourceIds,
    verificationStatus: enumeration(dto.verificationStatus, ['DRAFT', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED', 'DISPUTED'], 'verificationStatus'),
  }
}

function year(value) {
  if (value == null) return null
  if (!Number.isInteger(value) || value < 1 || value > 2200) throw new TypeError('Invalid historical year')
  return value
}

function localDate(value) {
  if (value == null) return null
  if (typeof value !== 'string') throw new TypeError('Invalid historical date')
  // Preserve Java LocalDate strings, including signed historical years, without
  // timezone conversion or inventing a day/month from YEAR or PERIOD precision.
  const match = /^([+-]?\d{4,9})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) throw new TypeError('Invalid historical date')
  const [, y, m, d] = match.map(Number)
  const leap = y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (m < 1 || m > 12 || d < 1 || d > days[m - 1]) throw new TypeError('Invalid historical date')
  return value
}

export function adaptHistoricalOverview(dto) {
  return { ...identity(dto), overview: text(dto.overview, 'overview'), updatedAt: text(dto.updatedAt, 'updatedAt') }
}

export function adaptHistoricalEvent(dto) {
  return {
    ...identity(dto), title: text(dto.title, 'title'), description: text(dto.description, 'description'),
    eventDate: localDate(dto.eventDate), yearFrom: year(dto.yearFrom), yearTo: year(dto.yearTo),
    datePrecision: enumeration(dto.datePrecision, ['EXACT', 'YEAR', 'PERIOD', 'UNKNOWN'], 'datePrecision'),
    createdAt: text(dto.createdAt, 'createdAt'),
  }
}

export function adaptCulturalHeritage(dto) {
  return {
    ...identity(dto), name: text(dto.name, 'name'), description: text(dto.description, 'description'),
    heritageType: enumeration(dto.heritageType, ['CRAFT', 'FOOD', 'TRADITION', 'PRACTICE', 'OTHER'], 'heritageType'),
  }
}

export function adaptHeritageSite(dto) {
  const common = identity(dto)
  const coordinates = toCoordinates(dto.latitude, dto.longitude)
  return {
    ...common, name: text(dto.name, 'name'), description: text(dto.description, 'description'),
    siteType: enumeration(dto.siteType, ['ARCHAEOLOGICAL', 'TRADITIONAL_BUILDING', 'SPRING', 'RELIGIOUS', 'VIEWPOINT', 'OTHER'], 'siteType'),
    latitude: dto.latitude ?? null, longitude: dto.longitude ?? null, coordinates,
    conditionNotes: text(dto.conditionNotes, 'conditionNotes'),
  }
}
