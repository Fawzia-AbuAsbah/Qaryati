import { apiClient } from './apiClient.js'
import { adaptGovernorateCatalog, toBackendId } from './villageAdapters.js'
import { adaptVillagePage, toBackendPage } from './paginationAdapters.js'
import { listVillages as listMockVillages } from './villageService.js'
import { createVillageAccess, publicVillageContractsVerified, villageDataMode } from './villageMode.js'

export const villageSortFields = Object.freeze(['id', 'nameAr', 'nameEn', 'createdAt', 'updatedAt'])

export class ExplorerError extends Error {
  constructor(code) {
    super(code)
    this.name = 'ExplorerError'
    this.code = code
  }
}

function listingQuery({ query = '', governorate = '', governorateId = '', page = 1, pageSize = 6, sortBy = 'id', sortDir = 'asc' }) {
  const backendPage = toBackendPage(page)
  if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100 || backendPage * pageSize > 2147483647) {
    throw new TypeError('Unsupported pagination')
  }
  if (typeof query !== 'string' || query.trim().length > 180) throw new TypeError('Unsupported search')
  if (governorate && (governorateId == null || governorateId === '')) throw new ExplorerError('LEGACY_GOVERNORATE_FILTER')
  if (!villageSortFields.includes(sortBy) || typeof sortDir !== 'string' || !['asc', 'desc'].includes(sortDir.toLowerCase())) {
    throw new TypeError('Unsupported sorting')
  }
  return {
    page: backendPage, size: pageSize, search: query.trim() || undefined,
    governorateId: governorateId == null || governorateId === '' ? undefined : toBackendId(governorateId),
    sortBy, sortDir: sortDir.toLowerCase(),
  }
}

export function createVillageExplorerService({
  mode = 'mock', publicationVerified = publicVillageContractsVerified,
  request = apiClient, mockList = listMockVillages,
} = {}) {
  const access = createVillageAccess({ mode, publicationVerified })

  async function listGovernorates({ signal } = {}) {
    access.requireApi()
    signal?.throwIfAborted()
    const payload = await request('/governorates', { signal })
    signal?.throwIfAborted()
    return adaptGovernorateCatalog(payload)
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
    const catalog = await listGovernorates({ signal })
    let backendPage = query.page
    for (let corrections = 0; ; corrections++) {
      signal?.throwIfAborted()
      const dto = await request('/villages', { query: { ...query, page: backendPage }, signal })
      signal?.throwIfAborted()
      const result = adaptVillagePage(dto, catalog.namesById)
      if (result.page !== backendPage + 1 || result.pageSize !== query.size) {
        throw new TypeError('Village response does not match the requested page and size')
      }
      if (!result.outOfRange) return { ...result, governorates: catalog.items }
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
  if (error?.code === 'INVALID_MODE') return 'إعداد مصدر بيانات القرى غير صالح.'
  if (error?.code === 'LEGACY_GOVERNORATE_FILTER') return 'مرشّح المحافظة المحفوظ غير متاح مع هذا المصدر. امسح المرشّحات ثم اختر المحافظة مجددًا.'
  if (error?.code === 'PAGINATION_CHANGED') return 'تغيّر عدد صفحات النتائج أثناء التحميل. أعد المحاولة للحصول على الصفحة الحالية.'
  if (error?.status === 401) return 'يتطلب عرض هذه البيانات تسجيل الدخول. تسجيل الدخول غير متاح في هذه المرحلة.'
  if (error?.status === 403) return 'لا تتوفر صلاحية لعرض هذه البيانات.'
  if (error?.status === 429) return 'طلبات كثيرة خلال وقت قصير. يرجى المحاولة لاحقًا.'
  if (error?.kind === 'network') return 'تعذّر الاتصال بالخدمة. تحقق من الاتصال ثم أعد المحاولة.'
  return 'تعذّر تحميل نتائج البحث. تحقق من معايير البحث أو أعد المحاولة لاحقًا.'
}

export const villageExplorerService = createVillageExplorerService({
  mode: villageDataMode,
})
