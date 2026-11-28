import { toRouteId } from './villageAdapters.js'

export const publicGovernorateFields = Object.freeze(['id', 'nameAr', 'nameEn'])
export const publicLocalAuthorityFields = Object.freeze(['id', 'nameAr', 'nameEn', 'governorateId'])

function record(value, fields, label) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)
    || Object.keys(value).length !== fields.length || fields.some(field => !Object.hasOwn(value, field))) {
    throw new TypeError(`Expected the public ${label} contract`)
  }
}

function identifier(value, label) {
  try {
    const id = toRouteId(value)
    return { id, backendId: Number(id) }
  } catch {
    throw new TypeError(`Invalid public ${label} ID`)
  }
}

function nullableName(value, field) {
  if (value === null) return null
  if (typeof value !== 'string' || !value.trim()) throw new TypeError(`Invalid public ${field}`)
  return value.trim()
}

function displayName(nameAr, nameEn, label) {
  if (nameAr === null && nameEn === null) throw new TypeError(`Public ${label} must have a name`)
  return nameAr ?? nameEn
}

function catalogue(payload, fields, label, adapt) {
  if (!Array.isArray(payload)) throw new TypeError(`Public ${label} catalogue must be an array`)
  const namesById = new Map()
  const items = payload.map(dto => {
    record(dto, fields, label)
    const item = adapt(dto)
    if (namesById.has(item.backendId)) throw new TypeError(`Public ${label} catalogue contains a duplicate ID`)
    namesById.set(item.backendId, item.name)
    return Object.freeze(item)
  })
  return Object.freeze({ items: Object.freeze(items), namesById })
}

export function adaptPublicGovernorateCatalogue(payload) {
  return catalogue(payload, publicGovernorateFields, 'governorate', dto => {
    const identity = identifier(dto.id, 'governorate')
    const nameAr = nullableName(dto.nameAr, 'governorate nameAr')
    const nameEn = nullableName(dto.nameEn, 'governorate nameEn')
    return { ...identity, nameAr, nameEn, name: displayName(nameAr, nameEn, 'governorate') }
  })
}

export function adaptPublicLocalAuthorityCatalogue(payload) {
  return catalogue(payload, publicLocalAuthorityFields, 'local authority', dto => {
    const identity = identifier(dto.id, 'local authority')
    const governorate = identifier(dto.governorateId, 'local authority governorate')
    const nameAr = nullableName(dto.nameAr, 'local authority nameAr')
    const nameEn = nullableName(dto.nameEn, 'local authority nameEn')
    return {
      ...identity, nameAr, nameEn, name: displayName(nameAr, nameEn, 'local authority'),
      governorateId: governorate.backendId,
    }
  })
}
