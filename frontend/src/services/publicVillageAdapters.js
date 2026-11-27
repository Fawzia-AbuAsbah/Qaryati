import { toCoordinates, toRouteId } from './villageAdapters.js'

export const publicVillageFields = Object.freeze(['id', 'nameAr', 'nameEn', 'governorateId', 'localAuthorityId', 'latitude', 'longitude'])
export const publicVillageDetailFields = Object.freeze([...publicVillageFields, 'nameOrigin', 'locationDescription', 'elevationM', 'areaKm2'])
function id(value) { toRouteId(value); return value }
function text(value, nullable = false) {
  if (nullable && value === null) return null
  if (typeof value !== 'string' || (!nullable && !value.trim())) throw new TypeError('Invalid public village name')
  return value
}

export function adaptPublicVillage(dto) {
  if (!dto || typeof dto !== 'object' || Array.isArray(dto)
    || Object.keys(dto).length !== publicVillageFields.length || publicVillageFields.some(field => !Object.hasOwn(dto, field))) {
    throw new TypeError('Expected the seven-field PublicVillageResponse')
  }
  const backendId = id(dto.id), governorateId = id(dto.governorateId)
  const localAuthorityId = dto.localAuthorityId === null ? null : id(dto.localAuthorityId)
  if (dto.latitude === undefined || dto.longitude === undefined) throw new TypeError('Missing public coordinates')
  const coordinates = toCoordinates(dto.latitude, dto.longitude)
  const nameAr = text(dto.nameAr)
  return Object.freeze({ id: toRouteId(backendId), backendId, name: nameAr, nameAr,
    nameEn: text(dto.nameEn, true), governorateId, localAuthorityId,
    latitude: dto.latitude, longitude: dto.longitude, coordinates })
}

function nullableFiniteNumber(value, field) {
  if (value === null) return null
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new TypeError(`Invalid public village ${field}`)
  return value
}

export function adaptPublicVillageDetail(dto) {
  if (!dto || typeof dto !== 'object' || Array.isArray(dto)
    || Object.keys(dto).length !== publicVillageDetailFields.length || publicVillageDetailFields.some(field => !Object.hasOwn(dto, field))) {
    throw new TypeError('Expected the eleven-field PublicVillageDetailResponse')
  }
  const village = adaptPublicVillage(Object.fromEntries(publicVillageFields.map(field => [field, dto[field]])))
  const elevationM = nullableFiniteNumber(dto.elevationM, 'elevationM')
  const areaKm2 = nullableFiniteNumber(dto.areaKm2, 'areaKm2')
  if (areaKm2 !== null && areaKm2 <= 0) throw new TypeError('Public village areaKm2 must be positive or null')
  return Object.freeze({ ...village,
    nameOrigin: text(dto.nameOrigin, true), locationDescription: text(dto.locationDescription), elevationM, areaKm2 })
}
