import { useCallback } from 'react'
import { ChevronDown, Search } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import PageState from '../components/PageState.jsx'
import VillageCard from '../components/VillageCard.jsx'
import { listVillages } from '../services/villageService.js'
import { useVillageData } from '../services/useVillageData.js'

const number = new Intl.NumberFormat('ar')

export default function VillageExplorer() {
  const [params, setParams] = useSearchParams()
  const query = params.get('q') ?? ''
  const governorate = params.get('governorate') ?? ''
  const requestedPage = Number(params.get('page'))
  const page = Number.isSafeInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1
  const load = useCallback(() => listVillages({ query, governorate, page }), [query, governorate, page])
  const { data, ...state } = useVillageData(load)

  function update(name, value, replace = false) {
    const next = new URLSearchParams(params)
    if (value) next.set(name, value)
    else next.delete(name)
    if (name !== 'page') next.delete('page')
    setParams(next, { replace })
  }

  return (
    <>
      <div className="page-banner"><div className="shell"><h1>استكشاف القرى</h1><p>ابحث بالاسم أو تصفّح القرى حسب المحافظة.</p></div></div>
      <section className="shell section" aria-label="دليل القرى">
        <form className="filters" role="search" onSubmit={(event) => event.preventDefault()}>
          <label htmlFor="village-search">اسم القرية
            <span className="field"><Search size={19} aria-hidden="true" /><input id="village-search" type="search" value={query} placeholder="مثال: بتير" onChange={(event) => update('q', event.target.value, true)} /></span>
          </label>
          <label htmlFor="governorate">المحافظة
            <span className="field select-field">
              <select id="governorate" value={governorate} onChange={(event) => update('governorate', event.target.value)}>
                <option value="">جميع المحافظات</option>
                {governorate && !data?.governorates.includes(governorate) && <option value={governorate}>{governorate}</option>}
                {data?.governorates.map((name) => <option key={name} value={name}>{name}</option>)}
              </select>
              <ChevronDown size={19} aria-hidden="true" />
            </span>
          </label>
          {(query || governorate) && <button className="button outline" type="button" onClick={() => setParams({})}>مسح الفلاتر</button>}
        </form>
        <p className="results" role="status">{data ? `عدد النتائج: ${number.format(data.total)}` : ''}</p>
        {data?.items.length ? (
          <div className="village-grid">{data.items.map((village) => <VillageCard key={village.id} village={village} />)}</div>
        ) : <PageState {...state} title="لم نعثر على قرى مطابقة" message={data ? 'جرّب اسمًا آخر أو اختر محافظة مختلفة.' : undefined} />}
        {data?.totalPages > 1 && (
          <nav className="pagination" aria-label="صفحات النتائج">
            <button className="button outline" disabled={data.page === 1} onClick={() => update('page', data.page - 1)}>السابق</button>
            <span>صفحة {number.format(data.page)} من {number.format(data.totalPages)}</span>
            <button className="button outline" disabled={data.page === data.totalPages} onClick={() => update('page', data.page + 1)}>التالي</button>
          </nav>
        )}
      </section>
    </>
  )
}
