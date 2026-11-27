import { toBackendId } from './villageAdapters.js'

export function villageDetailsHref(routeId, explorer, details) {
  if (!explorer.available || !details.available || explorer.mode !== details.mode) return null
  if (details.mode === 'api') {
    try { toBackendId(routeId) } catch { return null }
  } else if (details.mode !== 'mock' || typeof routeId !== 'string' || !routeId) return null
  return `/villages/${encodeURIComponent(routeId)}`
}

export function explorerReturnHref(value) {
  return typeof value === 'string' && (value === '/villages' || value.startsWith('/villages?')) ? value : '/villages'
}

export function detailsErrorMessage(error) {
  if (error?.code === 'PUBLIC_CONTRACTS_UNCONFIRMED') return 'بيانات القرية من الخدمة غير متاحة حاليًا.'
  if (error?.code === 'INVALID_MODE') return 'إعداد مصدر بيانات القرية غير صالح.'
  if (error?.code === 'INVALID_VILLAGE_ID') return 'معرّف القرية غير صالح.'
  if (error?.code === 'UNAVAILABLE_SECTION') return 'بيانات هذا القسم غير متاحة حاليًا.'
  if (error?.code === 'PAGINATION_CHANGED') return 'تغيّرت صفحات هذا القسم أثناء التحميل. أعد المحاولة.'
  if (error?.status === 401) return 'يتطلب عرض هذه البيانات تسجيل الدخول، وهو غير متاح حاليًا.'
  if (error?.status === 403) return 'لا تتوفر صلاحية لعرض هذه البيانات.'
  if (error?.status === 404) return 'تعذّر العثور على بيانات هذا القسم.'
  if (error?.kind === 'network') return 'تعذّر الاتصال بالخدمة. أعد المحاولة لاحقًا.'
  return 'تعذّر تحميل البيانات. أعد المحاولة لاحقًا.'
}

export function detailsPageTitle({ loading, error, village }) {
  const label = loading ? 'جارٍ تحميل القرية'
    : error ? (error.code === 'INVALID_VILLAGE_ID' ? 'معرّف القرية غير صالح' : 'تعذّر تحميل القرية')
      : village ? (village.name ?? 'اسم القرية غير متاح') : 'القرية غير موجودة'
  return `${label} | قريتي`
}
