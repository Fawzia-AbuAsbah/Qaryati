function record(value, field) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new TypeError(`${field} must be an object`)
  }
}

function nullableText(value, field) {
  if (value == null) return null
  if (typeof value !== 'string') throw new TypeError(`${field} must be a string or null`)
  return value.trim() ? value : null
}

function backendId(value) {
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new TypeError('Backend ID must be a positive safe integer')
  }
  return value
}

function nullableId(value) {
  return value == null ? null : backendId(value)
}

function nullableNumber(value, field) {
  if (value == null) return null
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new TypeError(`${field} must be a finite number or null`)
  }
  return value
}

function coordinate(value, limit, field) {
  const number = nullableNumber(value, field)
  if (number !== null && Math.abs(number) > limit) {
    throw new TypeError(`${field} is outside its geographic range`)
  }
  return number
}

export function toRouteId(id) {
  return String(backendId(id))
}

// Existing mock slugs are deliberately outside the numeric backend ID contract.
export function toBackendId(routeId) {
  if (typeof routeId !== 'string' || !/^[1-9]\d*$/.test(routeId)) {
    throw new TypeError('Route ID must be a canonical positive decimal string')
  }
  return backendId(Number(routeId))
}

export function toCoordinates(latitude, longitude) {
  const lat = coordinate(latitude, 90, 'latitude')
  const lng = coordinate(longitude, 180, 'longitude')
  return lat === null || lng === null ? null : [lat, lng]
}

export function adaptGovernorateCatalog(payload) {
  if (!Array.isArray(payload)) throw new TypeError('Governorate catalog must be an array')
  const namesById = new Map()
  const items = payload.map(dto => {
    record(dto, 'GovernorateResponse')
    const id = backendId(dto.id)
    if (namesById.has(id)) throw new TypeError('Governorate catalog contains a duplicate ID')
    const nameAr = nullableText(dto.nameAr, 'nameAr')
    namesById.set(id, nameAr)
    return {
      id: toRouteId(id), backendId: id, name: nameAr, nameAr,
      nameEn: nullableText(dto.nameEn, 'nameEn'), code: nullableText(dto.code, 'code'),
    }
  })
  return { items, namesById }
}

export function adaptVillage(dto, governorateNames = new Map()) {
  record(dto, 'VillageResponse')
  if (!(governorateNames instanceof Map)) throw new TypeError('Governorate names must be a Map keyed by backend ID')
  const id = backendId(dto.id)
  const governorateId = nullableId(dto.governorateId)
  const governorate = nullableText(governorateNames.get(governorateId), 'governorate name')
  const nameAr = nullableText(dto.nameAr, 'nameAr')
  const latitude = coordinate(dto.latitude, 90, 'latitude')
  const longitude = coordinate(dto.longitude, 180, 'longitude')
  const areaKm2 = nullableNumber(dto.areaKm2, 'areaKm2')
  if (areaKm2 !== null && areaKm2 <= 0) throw new TypeError('areaKm2 must be positive or null')
  const status = nullableText(dto.status, 'status')
  if (status !== null && !['DRAFT', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED'].includes(status)) {
    throw new TypeError('Unknown VillageStatus')
  }
  return {
    id: toRouteId(id), backendId: id, governorateId,
    localAuthorityId: nullableId(dto.localAuthorityId),
    name: nameAr, nameAr, nameEn: nullableText(dto.nameEn, 'nameEn'), governorate,
    nameOrigin: nullableText(dto.nameOrigin, 'nameOrigin'),
    locationDescription: nullableText(dto.locationDescription, 'locationDescription'),
    latitude, longitude, coordinates: toCoordinates(latitude, longitude),
    elevationM: nullableNumber(dto.elevationM, 'elevationM'), areaKm2, status,
    // VillageStatus is preserved, without inferring publication or verification of other data.
    // VillageResponse supplies none of the following fields used by the mock view model.
    featured: null, contentStatus: null, overview: null, population: null,
    history: null, heritage: null, agriculture: null, community: null,
  }
}
