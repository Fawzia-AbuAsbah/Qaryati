import { apiClient } from './apiClient.js'
import { toBackendId } from './villageAdapters.js'
import { adaptPublicVillagePage, toBackendPage } from './paginationAdapters.js'
import { listVillages as listMockVillages } from './villageService.js'
import { createVillageAccess, publicVillageCapabilities, villageDataMode } from './villageMode.js'

export const villageSortFields = Object.freeze(['id', 'nameAr', 'nameEn'])

export class ExplorerError extends Error {
  constructor(code) {
    super(code)
    this.name = 'ExplorerError'
    this.code = code
  }
}

function listingQuery(options) {
  const allowed = ['query', 'governorate', 'governorateId', 'localAuthorityId', 'page', 'pageSize', 'sortBy', 'sortDir', 'signal']
  if (Object.keys(options).some(key => !allowed.includes(key))) throw new TypeError('Unsupported public query option')
  const { query = '', governorate = '', governorateId = '', localAuthorityId = '', page = 1, pageSize = 6, sortBy = 'id', sortDir = 'asc' } = options
  const backendPage = toBackendPage(page)
  if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100 || backendPage * pageSize > 2147483647) {
    throw new TypeError('Unsupported pagination')
  }
  if (typeof query !== 'string') throw new TypeError('Unsupported search')
  // Match Java String.strip(): non-breaking spaces remain literal search text.
  // eslint-disable-next-line no-control-regex -- Java whitespace includes these control characters.
  const term = query.replace(/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu, '')
  if (term.length > 180) throw new TypeError('Unsupported search')
  if (governorate && (governorateId == null || governorateId === '')) throw new ExplorerError('LEGACY_GOVERNORATE_FILTER')
  if (!villageSortFields.includes(sortBy) || typeof sortDir !== 'string' || !['asc', 'desc'].includes(sortDir.toLowerCase())) {
    throw new TypeError('Unsupported sorting')
  }
  return {
    page: backendPage, size: pageSize, search: term || undefined,
    governorateId: governorateId == null || governorateId === '' ? undefined : toBackendId(governorateId),
    localAuthorityId: localAuthorityId == null || localAuthorityId === '' ? undefined : toBackendId(localAuthorityId),
    sortBy, sortDir: sortDir.toLowerCase(),
  }
}

export function createVillageExplorerService({
  mode = 'mock', browsingVerified = publicVillageCapabilities.browsing,
  request = apiClient, mockList = listMockVillages,
} = {}) {
  const access = createVillageAccess({ mode, contractVerified: browsingVerified })

  async function listGovernorates({ signal } = {}) {
    access.requireApi()
    signal?.throwIfAborted()
    throw new ExplorerError('PUBLIC_CATALOGUE_UNAVAILABLE')
  }

  async function listVillages(options = {}) {
    const { signal } = options
    signal?.throwIfAborted()
    if (mode === 'mock') {
      const result = await mockList(options)
      signal?.throwIfAborted()
      return { ...result, governorates: result.governorates.map(name => ({ id: name, name })) }
    }
    access.requireApi()
    const query = listingQuery(options)
    let backendPage = query.page
    for (let corrections = 0; ; corrections++) {
      signal?.throwIfAborted()
      const dto = await request('/public/villages', { query: { ...query, page: backendPage }, signal })
      signal?.throwIfAborted()
      const result = adaptPublicVillagePage(dto)
      if (result.page !== backendPage + 1 || result.pageSize !== query.size) {
        throw new TypeError('Village response does not match the requested page and size')
      }
      if (!result.outOfRange) return { ...result, governorates: [], governorateNamesAvailable: false }
      // Never relabel an empty out-of-range response as the last page's data.
      // A shrinking dataset may require another correction; bound repeated reads.
      if (corrections === 2) throw new ExplorerError('PAGINATION_CHANGED')
      backendPage = toBackendPage(Math.max(1, result.totalPages))
    }
  }

  return { mode, available: access.available, listGovernorates, listVillages }
}

export function explorerErrorMessage(error) {
  if (error?.code === 'PUBLIC_CONTRACTS_UNCONFIRMED') return 'عرض بيانات القرى من الخدمة غير متاح حاليًا.'
  if (error?.code === 'PUBLIC_CATALOGUE_UNAVAILABLE') return 'أسماء المحافظات والهيئات المحلية غير متاحة للعامة حاليًا.'
  if (error?.code === 'INVALID_MODE') return 'إعداد مصدر بيانات القرى غير صالح.'
  if (error?.code === 'LEGACY_GOVERNORATE_FILTER') return 'مرشّح المحافظة المحفوظ يستخدم اسمًا تجريبيًا لا يدعمه هذا المصدر. امسح الفلاتر للمتابعة.'
  if (error?.code === 'PAGINATION_CHANGED') return 'تغيّر عدد صفحات النتائج أثناء التحميل. أعد المحاولة للحصول على الصفحة الحالية.'
  if (error?.status === 401) return 'تعذّر الوصول إلى خدمة القرى العامة. أعد المحاولة لاحقًا.'
  if (error?.status === 403) return 'لا تتوفر صلاحية لعرض هذه البيانات.'
  if (error?.status === 429) return 'طلبات كثيرة خلال وقت قصير. يرجى المحاولة لاحقًا.'
  if (error?.kind === 'network') return 'تعذّر الاتصال بالخدمة. تحقق من الاتصال ثم أعد المحاولة.'
  return 'تعذّر تحميل نتائج البحث. تحقق من معايير البحث أو أعد المحاولة لاحقًا.'
}

export const villageExplorerService = createVillageExplorerService({
  mode: villageDataMode,
})
