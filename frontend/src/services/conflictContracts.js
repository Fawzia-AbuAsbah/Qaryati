import { toBackendId, toRouteId } from './villageAdapters.js'
import { reviewedTimestamp } from './verificationContracts.js'

// DataConflictStatus, in the only order the backend service permits:
// OPEN -> IN_REVIEW (PATCH /review), IN_REVIEW -> RESOLVED (PATCH /resolve).
export const conflictStatuses = Object.freeze(['OPEN', 'IN_REVIEW', 'RESOLVED'])
export const conflictStatusLabels = Object.freeze({ OPEN: 'مفتوح', IN_REVIEW: 'قيد المراجعة', RESOLVED: 'محسوم' })
export const conflictEntityTypes = Object.freeze(['VILLAGE'])
export const conflictFieldNameMax = 100

export class ConflictError extends Error {
  constructor(code, fields = {}) { super(code); this.name = 'ConflictError'; this.code = code; this.fields = fields }
}

export function conflictEntityType(value) {
  // The read endpoint compares entityType exactly; never send padded variants.
  if (typeof value !== 'string' || !conflictEntityTypes.includes(value)) {
    throw new ConflictError('CONFLICT_VALIDATION', { entityType: 'نوع الكيان المدعوم حاليًا هو القرية فقط.' })
  }
  return value
}

export function conflictStatus(value) {
  if (!conflictStatuses.includes(value)) throw new ConflictError('CONFLICT_VALIDATION', { status: 'اختر حالة تعارض مدعومة.' })
  return value
}

export function conflictId(value) {
  try { return toBackendId(value) } catch { throw new ConflictError('CONFLICT_VALIDATION', { id: 'معرّف التعارض غير صالح.' }) }
}

// Valid transitions exactly as DataConflictService enforces them.
export function conflictNextAction(status) {
  return status === 'OPEN' ? 'review' : status === 'IN_REVIEW' ? 'resolve' : null
}

export const plainObject = value => value !== null && typeof value === 'object' && !Array.isArray(value)
  && [Object.prototype, null].includes(Object.getPrototypeOf(value))

export function jsonValue(value, depth = 0) {
  if (depth > 32) return false
  if (value === null || typeof value === 'string' || typeof value === 'boolean') return true
  if (typeof value === 'number') return Number.isFinite(value)
  if (Array.isArray(value)) return value.every(item => jsonValue(item, depth + 1))
  return plainObject(value) && Object.values(value).every(item => jsonValue(item, depth + 1))
}

// CreateDataConflictRequest: List<@NotEmpty Map<@NotBlank String, @NotNull Object>>,
// @NotEmpty, and the service requires at least two competing values.
export function conflictValuesError(values) {
  if (!Array.isArray(values) || values.length < 2) return 'أدخل قيمتين متنافستين على الأقل.'
  for (const map of values) {
    if (!plainObject(map) || Object.keys(map).length === 0) return 'كل قيمة متنافسة يجب أن تحتوي على حقل واحد على الأقل.'
    for (const [key, item] of Object.entries(map)) {
      if (!key.trim()) return 'أسماء الحقول داخل القيم المتنافسة مطلوبة.'
      if (item === null || item === undefined) return 'قيم الحقول داخل القيم المتنافسة لا يمكن أن تكون فارغة (null).'
      if (!jsonValue(item)) return 'القيم المتنافسة يجب أن تكون بيانات JSON صالحة.'
    }
  }
  return null
}

// Draft rows from the form: [[{ key, value }]] of user-entered text. Text is kept
// exactly as typed; Object.fromEntries keeps keys such as "__proto__" as own data.
export function conflictValuesFromDraft(draft) {
  const fields = {}
  if (!Array.isArray(draft) || draft.length < 2) fields.conflictingValues = 'أدخل قيمتين متنافستين على الأقل.'
  const values = (Array.isArray(draft) ? draft : []).map((entries, index) => {
    const rows = Array.isArray(entries) ? entries : []
    if (!rows.length) fields[`value-${index}`] = 'أضف حقلًا واحدًا على الأقل.'
    const keys = new Set()
    for (const row of rows) {
      if (typeof row?.key !== 'string' || !row.key.trim()) fields[`value-${index}`] = 'أسماء الحقول مطلوبة.'
      else if (keys.has(row.key)) fields[`value-${index}`] = 'أسماء الحقول داخل القيمة الواحدة يجب ألا تتكرر.'
      else if (typeof row.value !== 'string') fields[`value-${index}`] = 'أدخل قيمة نصية لكل حقل.'
      keys.add(row?.key)
    }
    return Object.fromEntries(rows.map(row => [row?.key, row?.value]))
  })
  if (Object.keys(fields).length) throw new ConflictError('CONFLICT_VALIDATION', fields)
  return values
}

export function conflictCreateBody(villageId, fieldName, conflictingValues, entityType = 'VILLAGE') {
  const entityId = toBackendId(villageId)
  const fields = {}
  if (typeof fieldName !== 'string' || !fieldName.trim()) fields.fieldName = 'اسم الحقل المتنازع عليه مطلوب.'
  else if (fieldName.length > conflictFieldNameMax) fields.fieldName = `اسم الحقل لا يتجاوز ${conflictFieldNameMax} حرفًا.`
  const valuesError = conflictValuesError(conflictingValues)
  if (valuesError) fields.conflictingValues = valuesError
  if (Object.keys(fields).length) throw new ConflictError('CONFLICT_VALIDATION', fields)
  return { entityType: conflictEntityType(entityType), entityId, fieldName, conflictingValues }
}

export function conflictResolveBody(resolutionNotes) {
  // ResolveDataConflictRequest: @NotBlank, no length limit. The server trims.
  if (typeof resolutionNotes !== 'string' || !resolutionNotes.trim()) {
    throw new ConflictError('CONFLICT_VALIDATION', { resolutionNotes: 'ملاحظات الحسم مطلوبة.' })
  }
  return { resolutionNotes }
}

function nullableText(value) {
  if (value == null) return null
  if (typeof value !== 'string') throw new TypeError('Expected conflict text or null')
  return value
}
function nullableTime(value) {
  return value == null ? null : reviewedTimestamp(value)
}
function deepFreeze(value) {
  if (value && typeof value === 'object') { for (const item of Object.values(value)) deepFreeze(item); Object.freeze(value) }
  return value
}

export function adaptConflict(dto, { villageId, status } = {}) {
  if (!plainObject(dto)) throw new TypeError('Expected DataConflictResponse')
  const id = toRouteId(dto.id), entityId = toRouteId(dto.entityId)
  if (typeof dto.entityType !== 'string' || !dto.entityType || typeof dto.fieldName !== 'string' || !conflictStatuses.includes(dto.status)) {
    throw new TypeError('Malformed data conflict')
  }
  if (villageId !== undefined && (dto.entityType !== 'VILLAGE' || entityId !== villageId)) throw new TypeError('Conflict belongs to another entity')
  if (status !== undefined && dto.status !== status) throw new TypeError('Conflict status does not match the requested filter')
  if (!Array.isArray(dto.conflictingValues) || !dto.conflictingValues.every(map => plainObject(map) && jsonValue(map))) {
    throw new TypeError('Malformed conflicting values')
  }
  if (dto.resolvedBy != null) toRouteId(dto.resolvedBy)
  return Object.freeze({
    id, backendId: dto.id, entityType: dto.entityType, entityId, fieldName: dto.fieldName,
    conflictingValues: deepFreeze(dto.conflictingValues), status: dto.status,
    resolutionNotes: nullableText(dto.resolutionNotes), resolvedBy: dto.resolvedBy ?? null,
    createdAt: nullableTime(dto.createdAt), resolvedAt: nullableTime(dto.resolvedAt),
  })
}

export function adaptConflictList(dto, options) {
  if (!Array.isArray(dto)) throw new TypeError('Data conflict list must be an array')
  const ids = new Set()
  // Preserve backend createdAt-desc order, including its unspecified tie order.
  return dto.map(row => {
    const conflict = adaptConflict(row, options)
    if (ids.has(conflict.id)) throw new TypeError('Duplicate data conflict ID')
    ids.add(conflict.id)
    return conflict
  })
}

const subjects = { read: 'تحميل التعارضات', create: 'تسجيل التعارض', review: 'نقل التعارض إلى المراجعة', resolve: 'حسم التعارض' }

export function conflictErrorMessage(error, operation = 'read') {
  const mutation = operation !== 'read'
  if (error?.code === 'AUTH_REQUIRED' || error?.status === 401) return 'سجّل الدخول مجددًا للوصول إلى تعارضات البيانات.'
  if (error?.code === 'CONFLICT_ROLES_UNAVAILABLE') return 'هذه العملية غير متاحة لأن صلاحيات الحساب لا يمكن التحقق منها.'
  if (error?.code === 'CONFLICT_FORBIDDEN' || error?.status === 403) return 'لا تتوفر صلاحية لتنفيذ هذه العملية.'
  if (error?.code === 'CONFLICT_VALIDATION' || error?.status === 400) return 'تحقق من الحقول المدخلة ومعرّف القرية.'
  if (error?.status === 404) return operation === 'create' || operation === 'read' ? 'القرية المطلوبة غير موجودة.' : 'التعارض المطلوب غير موجود.'
  if (error?.status === 409) return 'لا تسمح حالة التعارض الحالية بهذا الإجراء. أعد تحميل القائمة لعرض الحالة الحالية.'
  if (error?.status === 429) return 'طلبات كثيرة. يرجى المحاولة لاحقًا.'
  if (mutation) return `تعذّر تأكيد ${subjects[operation] ?? 'العملية'}. أعد تحميل القائمة قبل إعادة المحاولة؛ قد يكون الطلب قد نُفّذ.`
  if (error?.kind === 'network') return 'تعذّر الاتصال بالخدمة لتحميل تعارضات البيانات.'
  return 'تعذّر تحميل تعارضات البيانات.'
}
