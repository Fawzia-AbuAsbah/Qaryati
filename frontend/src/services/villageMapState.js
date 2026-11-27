import { hasCoordinates } from './villageService.js'
import { toBackendId } from './villageAdapters.js'
import { VillageAccessError } from './villageMode.js'
import { detailsErrorMessage } from './villageDetailsState.js'

export function villageMapHref(village, source, map) {
  if (!source.available || !map.available || source.mode !== map.mode || !hasCoordinates(village)) return null
  if (map.mode === 'api') {
    try { toBackendId(village.id) } catch { return null }
  } else if (map.mode !== 'mock' || typeof village.id !== 'string' || !village.id) return null
  return `/map?${new URLSearchParams({ village: village.id })}`
}

export function mapPresentation(dataset, selectedId, mode = 'api') {
  if (!dataset) return { markers: [], selected: null, selection: 'pending' }
  if (dataset.complete !== true || !Array.isArray(dataset.items) || dataset.items.length !== dataset.total) {
    throw new VillageAccessError('INCOMPLETE_MAP')
  }
  // Coordinate eligibility affects markers only; no publication/status filtering.
  const markers = dataset.items.filter(hasCoordinates)
  if (selectedId == null || selectedId === '') return { markers, selected: null, selection: 'none' }
  if (mode === 'api') {
    try { toBackendId(selectedId) } catch { return { markers, selected: null, selection: 'invalid' } }
  }
  const village = dataset.items.find(item => item.id === selectedId)
  const selection = !village ? 'not-found' : hasCoordinates(village) ? 'selected' : 'no-coordinates'
  return { markers, selected: selection === 'selected' ? village : null, selection }
}

export function mapSelectionMessage(selection) {
  if (selection === 'invalid') return 'معرّف القرية المحددة غير صالح.'
  if (selection === 'not-found') return 'القرية المطلوبة غير متاحة ضمن بيانات الخريطة.'
  if (selection === 'no-coordinates') return 'إحداثيات القرية المحددة غير متاحة حاليًا.'
  return null
}

export function mapErrorMessage(error) {
  if (error?.code === 'PUBLIC_CONTRACTS_UNCONFIRMED') return 'بيانات الخريطة من الخدمة غير متاحة حاليًا.'
  if (error?.code === 'MAP_LIMIT_EXCEEDED') return 'يتجاوز عدد القرى حد التحميل المتاح للخريطة. لم تُعرض نتائج جزئية.'
  if (['INCOMPLETE_MAP', 'MAP_DUPLICATE_IDS', 'MAP_DATA_CHANGED'].includes(error?.code)) {
    return 'تعذّر تحميل بيانات الخريطة كاملة. أعد المحاولة للحصول على بيانات متسقة.'
  }
  return detailsErrorMessage(error)
}
