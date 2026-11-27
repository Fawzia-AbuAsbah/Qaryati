import { toBackendId, toRouteId } from './villageAdapters.js'

export const sourceTypes = Object.freeze(['CENSUS', 'GOVERNMENT', 'BOOK', 'ARTICLE', 'ARCHIVE', 'WEBSITE', 'INTERVIEW', 'OTHER'])
export const sourceTypeLabels = Object.freeze({ CENSUS: 'تعداد', GOVERNMENT: 'جهة حكومية', BOOK: 'كتاب', ARTICLE: 'مقال', ARCHIVE: 'أرشيف', WEBSITE: 'موقع إلكتروني', INTERVIEW: 'مقابلة', OTHER: 'أخرى' })
export const sourceFields = Object.freeze(['title', 'publisher', 'author', 'sourceType', 'url', 'publicationDate', 'accessedAt', 'citation'])

export class SourceError extends Error {
  constructor(code, fields = {}) { super(code); this.name = 'SourceError'; this.code = code; this.fields = fields }
}

function record(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new TypeError('Expected source DTO')
}
function text(value) {
  if (value !== null && typeof value !== 'string') throw new TypeError('Expected text or explicit null')
  return value
}
export function sourceLocalDate(value) {
  if (value === null) return null
  const match = typeof value === 'string' && /^([+-]?\d{4,9})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) throw new TypeError('Invalid LocalDate')
  const [, y, m, d] = match.map(Number)
  const isoYear = y < 0 ? `-${String(-y).padStart(4, '0')}` : y > 9999 ? `+${y}` : String(y).padStart(4, '0')
  if (match[1] !== isoYear) throw new TypeError('Noncanonical LocalDate year')
  const days = [31, y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (m < 1 || m > 12 || d < 1 || d > days[m - 1]) throw new TypeError('Invalid LocalDate')
  return value
}
function sourceUrl(value) {
  if (value === null) return null
  if (typeof value !== 'string' || !/^https?:\/\/[^\s/]+(?:[/?#][^\s]*)?$/i.test(value) || value.includes('\\')) throw new TypeError('Invalid source URL')
  const url = new URL(value)
  if (!['http:', 'https:'].includes(url.protocol) || !url.hostname) throw new TypeError('Invalid source URL')
  return value
}

export function sourceFieldErrors(value) {
  const errors = {}
  if (typeof value?.title !== 'string' || !value.title.trim() || value.title.length > 350) errors.title = 'العنوان مطلوب وبحد أقصى ٣٥٠ حرفًا.'
  if (!sourceTypes.includes(value?.sourceType)) errors.sourceType = 'اختر نوع مصدر مدعومًا.'
  for (const field of ['publisher', 'author']) {
    if (value?.[field] !== null && (typeof value?.[field] !== 'string' || value[field].length > 200)) errors[field] = 'أدخل نصًا بحد أقصى ٢٠٠ حرف.'
  }
  try { sourceUrl(value?.url) } catch { errors.url = 'أدخل رابط HTTP أو HTTPS صالحًا أو اترك الحقل فارغًا.' }
  for (const field of ['publicationDate', 'accessedAt']) {
    try { sourceLocalDate(value?.[field]) } catch { errors[field] = 'أدخل تاريخًا صالحًا بصيغة سنة-شهر-يوم.' }
  }
  if (value?.citation !== null && typeof value?.citation !== 'string') errors.citation = 'أدخل نص الاقتباس أو اترك الحقل فارغًا.'
  return errors
}

export function sourceBody(value) {
  const errors = sourceFieldErrors(value)
  if (Object.keys(errors).length) throw new SourceError('SOURCE_VALIDATION', errors)
  return Object.fromEntries(sourceFields.map(field => [field, value[field]]))
}

export function adaptSource(dto) {
  record(dto)
  const id = toRouteId(dto.id)
  // Require explicit nullable fields. A partial read is unsafe as a PUT baseline.
  for (const field of sourceFields) if (!Object.hasOwn(dto, field)) throw new TypeError('Incomplete SourceResponse')
  const fields = sourceBody(dto)
  const createdAt = text(dto.createdAt)
  if (createdAt !== null && (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(createdAt) || !Number.isFinite(Date.parse(createdAt)))) throw new TypeError('Invalid source timestamp')
  return Object.freeze({ id, backendId: dto.id, ...fields, createdAt })
}

export function adaptSourceCatalog(dto) {
  if (!Array.isArray(dto)) throw new TypeError('Source catalog must be an array')
  const ids = new Set()
  return dto.map(row => {
    const source = adaptSource(row)
    if (ids.has(source.id)) throw new TypeError('Duplicate source ID')
    ids.add(source.id)
    return source
  })
}

export function sourceReplacement(original, changes) {
  record(original); record(changes)
  if (toBackendId(original.id) !== original.backendId) throw new TypeError('Invalid source baseline identity')
  // Validate the full baseline before merging. Undefined is never an implicit clear.
  sourceBody(original)
  for (const field of Object.keys(changes)) if (!sourceFields.includes(field)) throw new TypeError('Unsupported source field')
  return sourceBody({ ...original, ...changes })
}

export function adaptSourceLinks(dto, expected = {}) {
  if (!Array.isArray(dto)) throw new TypeError('Source links must be an array')
  const pairs = new Set()
  return dto.map(row => {
    record(row)
    const villageId = toRouteId(row.villageId), sourceId = toRouteId(row.sourceId)
    if ((expected.villageId && villageId !== expected.villageId) || (expected.sourceId && sourceId !== expected.sourceId)) throw new TypeError('Mismatched link identity')
    const pair = `${villageId}/${sourceId}`
    if (pairs.has(pair)) throw new TypeError('Duplicate village-source pair')
    pairs.add(pair)
    return Object.freeze({ villageId, sourceId, claimNote: text(row.claimNote) })
  })
}

export function sourceErrorMessage(error, mutation = false) {
  if (error?.code === 'SOURCE_ROLES_UNAVAILABLE') return 'إدارة المصادر غير متاحة حاليًا لأن صلاحيات الحساب لا يمكن التحقق منها.'
  if (error?.code === 'SOURCE_FORBIDDEN' || error?.status === 403) return 'لا تتوفر صلاحية لتنفيذ هذه العملية.'
  if (error?.code === 'AUTH_REQUIRED' || error?.status === 401) return 'انتهت الجلسة أو تعذّر التحقق منها. سجّل الدخول مجددًا.'
  if (error?.code === 'SOURCE_VALIDATION' || error?.status === 400) return 'تحقق من الحقول المطلوبة وصيغة البيانات.'
  if (error?.status === 404) return 'المصدر أو القرية المطلوبة غير موجودة.'
  if (error?.status === 409) return 'المصدر مرتبط بهذه القرية مسبقًا. لم تُستبدل ملاحظة الربط الموجودة.'
  if (error?.status === 429) return 'طلبات كثيرة. يرجى المحاولة لاحقًا.'
  if (error?.kind === 'network' || mutation) return mutation ? 'تعذّر تأكيد نتيجة الحفظ. تحقق من البيانات قبل إعادة المحاولة؛ قد يكون الطلب قد نُفّذ.' : 'تعذّر الاتصال بالخدمة. أعد المحاولة لاحقًا.'
  return 'تعذّر تحميل البيانات المطلوبة.'
}
