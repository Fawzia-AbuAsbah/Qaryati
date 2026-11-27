import { toBackendId, toRouteId } from './villageAdapters.js'

export const verificationDecisions = Object.freeze(['VERIFIED', 'REJECTED', 'NEEDS_REVISION'])
export const verificationDecisionLabels = Object.freeze({ VERIFIED: 'تم التحقق', REJECTED: 'مرفوض', NEEDS_REVISION: 'بحاجة إلى مراجعة' })
export const verificationEntityTypes = Object.freeze(['VILLAGE'])

export class VerificationError extends Error {
  constructor(code, fields = {}) { super(code); this.name = 'VerificationError'; this.code = code; this.fields = fields }
}

export function verificationEntityType(value) {
  if (typeof value !== 'string' || !value.trim() || value.length > 80 || !verificationEntityTypes.includes(value.trim())) {
    throw new VerificationError('VERIFICATION_VALIDATION', { entityType: 'نوع الكيان المدعوم حاليًا هو القرية فقط.' })
  }
  return value.trim()
}

export function verificationBody(villageId, decision, reason, entityType = 'VILLAGE') {
  const entityId = toBackendId(villageId)
  const fields = {}
  if (!verificationDecisions.includes(decision)) fields.decision = 'اختر قرار تحقق مدعومًا.'
  if (reason !== null && typeof reason !== 'string') fields.reason = 'أدخل نص السبب أو اترك الحقل فارغًا.'
  if (Object.keys(fields).length) throw new VerificationError('VERIFICATION_VALIDATION', fields)
  return { entityType: verificationEntityType(entityType), entityId, decision, reason }
}

function nullableText(value) {
  if (value == null) return null
  if (typeof value !== 'string') throw new TypeError('Expected verification text or null')
  return value
}
function reviewedTimestamp(value) {
  const text = nullableText(value)
  if (text === null) return null
  // Preserve the actual OffsetDateTime, including fractional seconds and offset.
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?(?:Z|([+-])(\d{2}):(\d{2}))$/.exec(text)
  if (!match || !Number.isFinite(Date.parse(text))) throw new TypeError('Invalid reviewedAt')
  const [, year, month, day, hour, minute, second, , offsetHour = 0, offsetMinute = 0] = match
  const y = Number(year), m = Number(month), d = Number(day)
  const days = [31, y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (m < 1 || m > 12 || d < 1 || d > days[m - 1] || Number(hour) > 23 || Number(minute) > 59 || Number(second) > 59
    || Number(offsetHour) > 18 || Number(offsetMinute) > 59 || (Number(offsetHour) === 18 && Number(offsetMinute) !== 0)) throw new TypeError('Invalid reviewedAt')
  return text
}

export function adaptVerification(dto, villageId) {
  if (!dto || typeof dto !== 'object' || Array.isArray(dto)) throw new TypeError('Expected VerificationRecordResponse')
  const id = toRouteId(dto.id), entityId = toRouteId(dto.entityId)
  if (dto.entityType !== 'VILLAGE' || (villageId !== undefined && entityId !== villageId) || !verificationDecisions.includes(dto.decision)) throw new TypeError('Unsupported or mismatched verification record')
  if (dto.reviewerUserId != null) toRouteId(dto.reviewerUserId)
  return Object.freeze({ id, backendId: dto.id, entityType: dto.entityType, entityId, reviewerUserId: dto.reviewerUserId ?? null,
    decision: dto.decision, reason: nullableText(dto.reason), reviewedAt: reviewedTimestamp(dto.reviewedAt) })
}

export function adaptVerificationHistory(dto, villageId) {
  if (!Array.isArray(dto)) throw new TypeError('Verification history must be an array')
  const ids = new Set()
  // Preserve backend reviewedAt-desc order, including its unspecified tie order.
  return dto.map(row => {
    const record = adaptVerification(row, villageId)
    if (ids.has(record.id)) throw new TypeError('Duplicate verification ID')
    ids.add(record.id)
    return record
  })
}

export function verificationErrorMessage(error, mutation = false) {
  if (error?.code === 'AUTH_REQUIRED' || error?.status === 401) return 'سجّل الدخول مجددًا لعرض سجل التحقق.'
  if (error?.code === 'VERIFICATION_ROLES_UNAVAILABLE') return 'تسجيل القرار غير متاح لأن صلاحيات المدير لا يمكن التحقق منها.'
  if (error?.code === 'VERIFICATION_FORBIDDEN' || error?.status === 403) return 'لا تتوفر صلاحية لتنفيذ هذه العملية.'
  if (error?.code === 'VERIFICATION_VALIDATION' || error?.status === 400) return 'تحقق من القرار والسبب ومعرّف القرية.'
  if (error?.status === 404) return 'القرية المطلوبة غير موجودة.'
  if (error?.status === 429) return 'طلبات كثيرة. يرجى المحاولة لاحقًا.'
  if (mutation) return 'تعذّر تأكيد تسجيل القرار. تحقق من السجل قبل إعادة المحاولة؛ قد يكون الطلب قد نُفّذ.'
  if (error?.kind === 'network') return 'تعذّر الاتصال بالخدمة لتحميل سجل التحقق.'
  return 'تعذّر تحميل سجل التحقق.'
}
