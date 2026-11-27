import { toBackendId, toRouteId } from './villageAdapters.js'
import { reviewedTimestamp } from './verificationContracts.js'
import { jsonValue, plainObject } from './conflictContracts.js'

// AuditLogService.ALLOWED_ACTIONS (also the audit_logs CHECK constraint).
export const auditActions = Object.freeze(['CREATE', 'UPDATE', 'DELETE', 'VERIFY', 'REJECT', 'RESTORE'])
export const auditActionLabels = Object.freeze({ CREATE: 'إنشاء', UPDATE: 'تعديل', DELETE: 'حذف', VERIFY: 'تحقق', REJECT: 'رفض', RESTORE: 'استعادة' })
export const auditModes = Object.freeze(['entity', 'actor', 'action'])

// Entity types currently written by backend services. Historical or other stored
// types remain searchable: these are suggestions, not an allowlist.
export const knownAuditEntityTypes = Object.freeze([
  ['CULTURAL_HERITAGE', 'تراث ثقافي'], ['DATA_CONFLICT', 'تعارض بيانات'], ['HERITAGE_SITE', 'موقع تراثي'],
  ['HISTORICAL_EVENT', 'حدث تاريخي'], ['POPULATION_RECORD', 'سجل سكان'], ['SOURCE', 'مصدر'],
  ['VERIFICATION_RECORD', 'سجل تحقق'], ['VILLAGE_HISTORY', 'نبذة تاريخية'], ['VILLAGE_SOURCE', 'ربط مصدر بقرية (المعرّف هو معرّف القرية)'],
])

export class AuditError extends Error {
  constructor(code, fields = {}) { super(code); this.name = 'AuditError'; this.code = code; this.fields = fields }
}

// AuditLogService.record accepts any non-blank type of at most 80 characters
// (String.length, as the column is VARCHAR(80)); the read endpoint only requires a
// non-blank type and matches it exactly. Nothing longer can be stored, so only blank
// or over-long values are rejected, and the value is sent exactly as entered.
export const auditEntityTypeMax = 80
const validEntityType = value => typeof value === 'string' && value.trim() !== '' && value.length <= auditEntityTypeMax

function numericId(value, field, message) {
  try { return toBackendId(value) } catch { throw new AuditError('AUDIT_VALIDATION', { [field]: message }) }
}

// Exactly one query mode per request; the backend has no combined filtering.
export function auditRequest(query) {
  const mode = query?.mode
  if (mode === 'entity') {
    const fields = {}
    if (!validEntityType(query.entityType)) fields.entityType = `أدخل نوع الكيان كما سُجّل تمامًا (مطلوب، حتى ${auditEntityTypeMax} حرفًا).`
    let entityId
    try { entityId = numericId(query.entityId, 'entityId', 'أدخل معرّف كيان رقميًا موجبًا.') } catch (error) { Object.assign(fields, error.fields) }
    if (Object.keys(fields).length) throw new AuditError('AUDIT_VALIDATION', fields)
    return { path: '/audit-logs/entity', query: { entityType: query.entityType, entityId } }
  }
  if (mode === 'actor') {
    return { path: `/audit-logs/actor/${numericId(query.actorUserId, 'actorUserId', 'أدخل معرّف مستخدم رقميًا موجبًا.')}` }
  }
  if (mode === 'action') {
    if (!auditActions.includes(query.action)) throw new AuditError('AUDIT_VALIDATION', { action: 'اختر إجراءً مدعومًا.' })
    return { path: `/audit-logs/action/${query.action}` }
  }
  throw new AuditError('AUDIT_VALIDATION', { mode: 'اختر طريقة بحث مدعومة.' })
}

export function auditQueryFromParams(params) {
  const mode = params.get('mode')
  if (mode === null) return null
  if (mode === 'entity') return { mode, entityType: params.get('entityType'), entityId: params.get('entityId') }
  if (mode === 'actor') return { mode, actorUserId: params.get('actorUserId') }
  if (mode === 'action') return { mode, action: params.get('action') }
  return { mode }
}

export function auditParamsFromQuery(query) {
  auditRequest(query)
  const keys = { entity: ['entityType', 'entityId'], actor: ['actorUserId'], action: ['action'] }[query.mode]
  return new URLSearchParams([['mode', query.mode], ...keys.map(key => [key, query[key]])])
}

function nullableText(value) {
  if (value == null) return null
  if (typeof value !== 'string') throw new TypeError('Expected audit text or null')
  return value
}
function snapshot(value) {
  if (value == null) return null
  if (!plainObject(value) || !jsonValue(value)) throw new TypeError('Malformed audit snapshot')
  return deepFreeze(value)
}
function deepFreeze(value) {
  if (value && typeof value === 'object') { for (const item of Object.values(value)) deepFreeze(item); Object.freeze(value) }
  return value
}

export function adaptAuditLog(dto, query) {
  if (!plainObject(dto)) throw new TypeError('Expected AuditLogResponse')
  const id = toRouteId(dto.id), entityId = toRouteId(dto.entityId)
  if (typeof dto.entityType !== 'string' || !dto.entityType || typeof dto.action !== 'string' || !dto.action) throw new TypeError('Malformed audit log')
  if (dto.actorUserId != null) toRouteId(dto.actorUserId)
  if (query?.mode === 'entity' && (dto.entityType !== query.entityType || entityId !== query.entityId)) throw new TypeError('Audit log belongs to another entity')
  if (query?.mode === 'actor' && String(dto.actorUserId) !== query.actorUserId) throw new TypeError('Audit log belongs to another actor')
  if (query?.mode === 'action' && dto.action !== query.action) throw new TypeError('Audit log has another action')
  return Object.freeze({
    id, entityType: dto.entityType, entityId, action: dto.action,
    actorUserId: dto.actorUserId ?? null, actorLabel: nullableText(dto.actorLabel),
    occurredAt: dto.occurredAt == null ? null : reviewedTimestamp(dto.occurredAt),
    beforeData: snapshot(dto.beforeData), afterData: snapshot(dto.afterData),
    correlationId: nullableText(dto.correlationId), reason: nullableText(dto.reason),
  })
}

export function adaptAuditLogList(dto, query) {
  if (!Array.isArray(dto)) throw new TypeError('Audit log list must be an array')
  const ids = new Set()
  // Preserve backend occurredAt-desc order, including its unspecified tie order.
  return dto.map(row => {
    const log = adaptAuditLog(row, query)
    if (ids.has(log.id)) throw new TypeError('Duplicate audit log ID')
    ids.add(log.id)
    return log
  })
}

export function auditErrorMessage(error) {
  if (error?.code === 'AUTH_REQUIRED' || error?.status === 401) return 'سجّل الدخول مجددًا لعرض سجل التدقيق.'
  if (error?.code === 'AUDIT_ROLES_UNAVAILABLE') return 'سجل التدقيق غير متاح لأن صلاحيات المدير لا يمكن التحقق منها.'
  if (error?.code === 'AUDIT_FORBIDDEN' || error?.status === 403) return 'سجل التدقيق متاح للمديرين فقط.'
  if (error?.code === 'AUDIT_VALIDATION' || error?.status === 400) return 'تحقق من معايير البحث المدخلة.'
  if (error?.status === 404) return 'خدمة سجل التدقيق غير متاحة على هذا المسار.'
  if (error?.status === 429) return 'طلبات كثيرة. يرجى المحاولة لاحقًا.'
  if (error?.kind === 'network') return 'تعذّر الاتصال بالخدمة لتحميل سجل التدقيق.'
  return 'تعذّر تحميل سجل التدقيق.'
}
