import { toCoordinates, toRouteId } from './villageAdapters.js'

export const publicVillageFields = Object.freeze(['id', 'nameAr', 'nameEn', 'governorateId', 'localAuthorityId', 'latitude', 'longitude'])
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
