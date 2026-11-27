import { useCallback, useEffect, useRef } from 'react'
import { ChevronDown, MapPin, Search } from 'lucide-react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import PageState from '../components/PageState.jsx'
import VillageCard from '../components/VillageCard.jsx'
import { villageExplorerService, explorerErrorMessage, villageSortFields } from '../services/villageExplorerService.js'
import { clearExplorerQuery, readExplorerQuery, updateExplorerQuery } from '../services/explorerQuery.js'
import { useExplorerData } from '../services/useExplorerData.js'
import { villageDetailsService } from '../services/villageDetailsService.js'
import { villageDetailsHref } from '../services/villageDetailsState.js'
import { villageMapService } from '../services/villageMapService.js'
import { villageMapHref } from '../services/villageMapState.js'

const number = new Intl.NumberFormat('ar')
const sortLabels = { id: 'المعرّف', nameAr: 'الاسم العربي', nameEn: 'الاسم الإنجليزي' }

function ApiVillageCard({ village, returnTo }) {
  const detailsHref = villageDetailsHref(village.id, villageExplorerService, villageDetailsService)
  const mapHref = villageMapHref(village, villageExplorerService, villageMapService)
  return (
    <article className="village-card">
      <div className="card-scene missing-scene"><MapPin aria-hidden="true" /><p>صورة القرية غير متاحة</p></div>
      <div className="card-body">
        <p className="location"><MapPin size={16} aria-hidden="true" />اسم المحافظة غير متاح</p>
        <h3>{village.name ?? 'اسم القرية غير متاح'}</h3>
        <div className="actions">
          {detailsHref ? <Link className="card-link" to={detailsHref} state={{ explorerReturnTo: returnTo }}>تفاصيل القرية</Link> : <p>تفاصيل القرية غير متاحة حاليًا.</p>}
          {mapHref && <Link className="card-link" to={mapHref}>عرض الموقع على الخريطة</Link>}
        </div>
      </div>
    </article>
  )
}

export default function VillageExplorer() {
  const [params, setParams] = useSearchParams()
  const { pathname, search } = useLocation()
  const { query, governorate, governorateId, localAuthorityId, page, sortBy, sortDir } = readExplorerQuery(params)
  const apiMode = villageExplorerService.mode === 'api'
  const selectedGovernorate = apiMode ? governorateId : governorate
  const requestKey = JSON.stringify([query, governorate, governorateId, localAuthorityId, page, sortBy, sortDir])
  const normalizedResult = useRef(null)
  const load = useCallback(signal => {
    const cached = normalizedResult.current
    normalizedResult.current = null
    if (cached?.key === requestKey) {
      return Promise.resolve(cached.data)
    }
    return villageExplorerService.listVillages({ query, governorate, governorateId, localAuthorityId, page, sortBy, sortDir, signal })
  }, [query, governorate, governorateId, localAuthorityId, page, sortBy, sortDir, requestKey])
  const { data, ...state } = useExplorerData(load)

  useEffect(() => {
    if (!data || !params.has('page') || params.get('page') === String(data.page)) return
    // A navigation may have changed the browser URL before this effect runs.
    if (window.location.pathname !== pathname || window.location.search !== search) return

    // Reuse this response when correcting the URL changes the load callback.
    normalizedResult.current = { key: JSON.stringify([query, governorate, governorateId, localAuthorityId, data.page, sortBy, sortDir]), data }
    const next = new URLSearchParams(params)
    next.set('page', String(data.page))
    setParams(next, { replace: true })
  }, [data, params, setParams, pathname, search, query, governorate, governorateId, localAuthorityId, sortBy, sortDir])

  function update(name, value, replace = false) {
    setParams(updateExplorerQuery(params, name, value), { replace })
  }

  return (
    <>
      <div className="page-banner"><div className="shell"><h1>استكشاف القرى</h1><p>{apiMode ? 'ابحث بالاسم في القرى المتاحة للعامة.' : 'ابحث بالاسم أو تصفّح القرى حسب المحافظة.'}</p></div></div>
      <section className="shell section" aria-label="دليل القرى">
        <form className="filters" role="search" onSubmit={(event) => event.preventDefault()}>
          <label htmlFor="village-search">اسم القرية
            <span className="field"><Search size={19} aria-hidden="true" /><input id="village-search" type="search" value={query} maxLength={apiMode ? 180 : undefined} disabled={!villageExplorerService.available} placeholder="مثال: بتير" onChange={(event) => update('q', event.target.value, true)} /></span>
          </label>
          {apiMode ? <div className="notice"><p>أسماء المحافظات وقائمة التصفية غير متاحة من الخدمة العامة حاليًا.</p>
            {governorateId && <p>تصفية بمعرّف المحافظة: <bdi>{governorateId}</bdi>؛ الاسم غير متاح.</p>}
            {localAuthorityId && <p>تصفية بمعرّف الهيئة المحلية: <bdi>{localAuthorityId}</bdi>؛ الاسم غير متاح.</p>}
          </div> : <label htmlFor="governorate">المحافظة
            <span className="field select-field">
              <select id="governorate" value={selectedGovernorate} disabled={!villageExplorerService.available} onChange={(event) => update('governorate', event.target.value)}>
                <option value="">جميع المحافظات</option>
                {selectedGovernorate && !data?.governorates.some(item => item.id === selectedGovernorate) && <option value={selectedGovernorate}>{selectedGovernorate}</option>}
                {data?.governorates.map(item => <option key={item.id} value={item.id}>{item.name ?? 'اسم المحافظة غير متاح'}</option>)}
              </select>
              <ChevronDown size={19} aria-hidden="true" />
            </span>
          </label>}
          {apiMode && <>
            <label htmlFor="village-sort">ترتيب حسب
              <span className="field select-field"><select id="village-sort" value={sortBy} disabled={!villageExplorerService.available} onChange={event => update('sortBy', event.target.value)}>
                {!villageSortFields.includes(sortBy) && <option value={sortBy}>ترتيب غير صالح</option>}
                {villageSortFields.map(field => <option key={field} value={field}>{sortLabels[field]}</option>)}
              </select><ChevronDown size={19} aria-hidden="true" /></span>
            </label>
            <label htmlFor="village-sort-direction">اتجاه الترتيب
              <span className="field select-field"><select id="village-sort-direction" value={sortDir} disabled={!villageExplorerService.available} onChange={event => update('sortDir', event.target.value)}>
                {!['asc', 'desc'].includes(sortDir) && <option value={sortDir}>اتجاه غير صالح</option>}
                <option value="asc">تصاعدي</option><option value="desc">تنازلي</option>
              </select><ChevronDown size={19} aria-hidden="true" /></span>
            </label>
          </>}
          {(query || selectedGovernorate || governorate || localAuthorityId || (apiMode && (sortBy !== 'id' || sortDir !== 'asc'))) && <button className="button outline" type="button" onClick={() => setParams(clearExplorerQuery(params))}>مسح الفلاتر</button>}
        </form>
        {apiMode && ['createdAt', 'updatedAt'].includes(params.get('sortBy')) && <p className="notice" role="status">استُبدل ترتيب زمني قديم بترتيب المعرّف.</p>}
        <p className="results" role="status">{data ? `عدد النتائج: ${number.format(data.total)}` : ''}</p>
        {data?.items.length ? (
          <div className="village-grid">{data.items.map(village => apiMode ? <ApiVillageCard key={village.id} village={village} returnTo={`${pathname}${search}`} /> : <VillageCard key={village.id} village={village} />)}</div>
        ) : <PageState {...state} title="لم نعثر على قرى مطابقة" message={state.error ? explorerErrorMessage(state.error) : data ? apiMode ? 'جرّب اسمًا آخر أو امسح الفلاتر.' : 'جرّب اسمًا آخر أو اختر محافظة مختلفة.' : undefined} />}
        {data?.totalPages > 1 && (
          <nav className="pagination" aria-label="صفحات النتائج">
            <button className="button outline" disabled={data.page === 1} onClick={() => update('page', data.page - 1)}>السابق</button>
            <span>صفحة {number.format(data.page)} من {number.format(data.totalPages)}</span>
            <button className="button outline" disabled={data.page >= data.totalPages} onClick={() => update('page', data.page + 1)}>التالي</button>
          </nav>
        )}
      </section>
    </>
  )
}
