import { toBackendId, toRouteId } from './villageAdapters.js'

export const populationPageSizes = Object.freeze([10, 20, 50, 100])
const pageFields = Object.freeze(['content', 'page', 'size', 'totalElements', 'totalPages'])
const recordFields = Object.freeze([
  'id', 'villageId', 'referenceYear', 'referenceDate', 'populationCount', 'methodology', 'sourceId', 'notes',
  'verificationStatus', 'createdAt', 'updatedAt',
])

export class PopulationError extends Error {
  constructor(code, fields = {}) { super(code); this.name = 'PopulationError'; this.code = code; this.fields = fields }
}

function object(value, fields, label) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)
    || Object.keys(value).length !== fields.length || fields.some(field => !Object.hasOwn(value, field))) {
    throw new TypeError(`Expected ${label}`)
  }
}

function integer(value, field, minimum = 0) {
  if (!Number.isSafeInteger(value) || value < minimum) throw new TypeError(`Invalid ${field}`)
  return value
}

function nullableText(value, field) {
  if (value === null) return null
  if (typeof value !== 'string') throw new TypeError(`Invalid ${field}`)
  return value
}

function routeId(value, field) {
  try { return toRouteId(value) } catch { throw new TypeError(`Invalid ${field}`) }
}

export function adaptPopulationRecord(dto) {
  object(dto, recordFields, 'PopulationRecordResponse')
  const id = routeId(dto.id, 'population record ID')
  const villageId = routeId(dto.villageId, 'population village ID')
  const sourceId = routeId(dto.sourceId, 'population source ID')
  const referenceYear = integer(dto.referenceYear, 'referenceYear', 1)
  const populationCount = integer(dto.populationCount, 'populationCount')
  if (typeof dto.methodology !== 'string' || !dto.methodology || typeof dto.verificationStatus !== 'string' || !dto.verificationStatus) {
    throw new TypeError('Invalid population enums')
  }
  return Object.freeze({
    id, backendId: Number(id), villageId, villageBackendId: Number(villageId), referenceYear, populationCount,
    methodology: dto.methodology, verificationStatus: dto.verificationStatus, sourceId, sourceBackendId: Number(sourceId),
    referenceDate: nullableText(dto.referenceDate, 'referenceDate'), notes: nullableText(dto.notes, 'notes'),
    createdAt: nullableText(dto.createdAt, 'createdAt'), updatedAt: nullableText(dto.updatedAt, 'updatedAt'),
  })
}

export function adaptPopulationPage(dto, requested) {
  object(dto, pageFields, 'PopulationRecordPage')
  if (!Array.isArray(dto.content)) throw new TypeError('PopulationRecordPage content must be an array')
  const page = integer(dto.page, 'page')
  const size = integer(dto.size, 'size', 1)
  const totalElements = integer(dto.totalElements, 'totalElements')
  const totalPages = integer(dto.totalPages, 'totalPages')
  if (size > 100 || totalPages !== Math.ceil(totalElements / size)) throw new TypeError('Inconsistent PopulationRecordPage')
  if (requested && (page !== requested.page || size !== requested.size)) throw new TypeError('PopulationRecordPage differs from its request')
  const expected = page < totalPages ? Math.min(size, totalElements - page * size) : 0
  if (dto.content.length !== expected) throw new TypeError('PopulationRecordPage content does not match metadata')
  const ids = new Set()
  const items = dto.content.map(row => {
    const record = adaptPopulationRecord(row)
    if (ids.has(record.id)) throw new TypeError('Duplicate population record ID')
    ids.add(record.id)
    if (requested?.villageId !== undefined && record.villageBackendId !== requested.villageId) {
      throw new TypeError('Population record belongs to another village')
    }
    return record
  })
  return Object.freeze({ page, size, totalElements, totalPages, items: Object.freeze(items), empty: items.length === 0 })
}

function requestInteger(value, field, minimum) {
  if (!Number.isSafeInteger(value) || value < minimum) throw new PopulationError('POPULATION_VALIDATION', { [field]: 'قيمة رقمية غير صالحة.' })
  return value
}

export function populationRequest({ villageId = '', page = 0, size = 20 } = {}) {
  const requestedPage = requestInteger(page, 'page', 0)
  const requestedSize = requestInteger(size, 'size', 1)
  if (requestedSize > 100) throw new PopulationError('POPULATION_VALIDATION', { size: 'حجم الصفحة يجب أن يكون بين 1 و100.' })
  let backendVillageId
  if (villageId !== '' && villageId != null) {
    try { backendVillageId = toBackendId(String(villageId)) } catch {
      throw new PopulationError('POPULATION_VALIDATION', { villageId: 'أدخل معرّف قرية رقميًا موجبًا.' })
    }
  }
  return { path: '/population-records', query: { page: requestedPage, size: requestedSize, villageId: backendVillageId },
    requested: { page: requestedPage, size: requestedSize, villageId: backendVillageId } }
}

function parameterInteger(params, name, fallback, minimum) {
  const value = params.get(name)
  if (value === null) return fallback
  if (!/^(?:0|[1-9]\d*)$/.test(value)) throw new PopulationError('POPULATION_VALIDATION', { [name]: 'قيمة الرابط غير صالحة.' })
  return requestInteger(Number(value), name, minimum)
}

export function populationQueryFromParams(params) {
  const villageId = params.get('villageId') ?? ''
  const page = parameterInteger(params, 'page', 0, 0)
  const size = parameterInteger(params, 'size', 20, 1)
  return populationRequest({ villageId, page, size }).requested
}

export function populationParamsFromQuery(query) {
  const { requested } = populationRequest(query)
  const params = new URLSearchParams([['page', String(requested.page)], ['size', String(requested.size)]])
  if (requested.villageId !== undefined) params.set('villageId', String(requested.villageId))
  return params
}

export function populationErrorMessage(error) {
  if (error?.code === 'AUTH_REQUIRED' || error?.status === 401) return 'سجّل الدخول مجددًا لعرض سجلات السكان.'
  if (error?.code === 'POPULATION_ROLES_UNAVAILABLE') return 'سجلات السكان غير متاحة لأن الصلاحيات لا يمكن التحقق منها.'
  if (error?.code === 'POPULATION_FORBIDDEN' || error?.status === 403) return 'لا تتوفر صلاحية لعرض سجلات السكان.'
  if (error?.code === 'POPULATION_VALIDATION' || error?.status === 400) return 'تحقق من معرّف القرية أو أرقام الصفحات في الرابط.'
  if (error?.status === 429) return 'طلبات كثيرة. يرجى المحاولة لاحقًا.'
  if (error?.kind === 'network') return 'تعذّر الاتصال بالخدمة لتحميل سجلات السكان.'
  return 'تعذّر تحميل سجلات السكان.'
}
