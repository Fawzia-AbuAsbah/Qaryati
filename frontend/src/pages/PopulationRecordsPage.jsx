import { useEffect, useId, useMemo, useState, useSyncExternalStore } from 'react'
import { useSearchParams } from 'react-router-dom'
import PageState from '../components/PageState.jsx'
import { createPopulationState } from '../services/populationState.js'
import { populationErrorMessage, populationPageSizes, populationParamsFromQuery, populationQueryFromParams, populationRequest } from '../services/populationContracts.js'
import { usePopulationService } from '../services/usePopulationService.js'
import '../styles/population.css'

function PopulationFilter({ initial, onApply }) {
  const prefix = useId()
  const [villageId, setVillageId] = useState(initial.villageId === undefined ? '' : String(initial.villageId))
  const [size, setSize] = useState(String(initial.size))
  const [error, setError] = useState(null)
  function submit(event) {
    event.preventDefault()
    try {
      const next = populationRequest({ villageId, page: 0, size: Number(size) }).requested
      setError(null); onApply(next)
    } catch (failure) { setError(failure) }
  }
  return <form className="detail-panel population-controls" onSubmit={submit} noValidate>
    <label htmlFor={`${prefix}-village`}>معرّف القرية (اختياري)
      <input id={`${prefix}-village`} dir="ltr" inputMode="numeric" value={villageId} aria-invalid={Boolean(error?.fields?.villageId)}
        onChange={event => { setVillageId(event.target.value); setError(null) }} />
    </label>
    <label htmlFor={`${prefix}-size`}>حجم الصفحة
      <select id={`${prefix}-size`} value={size} onChange={event => setSize(event.target.value)}>
        {!populationPageSizes.includes(Number(size)) && <option value={size}>{size}</option>}
        {populationPageSizes.map(value => <option key={value} value={value}>{value}</option>)}
      </select>
    </label>
    <button className="button primary" type="submit">عرض السجلات</button>
    {error && <p className="conflict-error" role="alert">{populationErrorMessage(error)}</p>}
  </form>
}

function PopulationResults({ service, query, onPage }) {
  const store = useMemo(() => createPopulationState(service, query), [service, query])
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  useEffect(() => { store.load(); return () => store.dispose() }, [store])
  const result = state.result
  return <section className="detail-panel population-results" aria-label="سجلات السكان" aria-busy={state.status === 'loading'}>
    <h2>سجلات السكان</h2>
    {state.status === 'loading' && <p role="status">جارٍ تحميل سجلات السكان…</p>}
    {state.status === 'error' && <div className="conflict-error" role="alert"><p>{populationErrorMessage(state.error)}</p><button className="button outline" type="button" onClick={() => store.load()}>إعادة المحاولة</button></div>}
    {state.status === 'ready' && result.empty && <p>لا توجد سجلات سكان مطابقة.</p>}
    {state.status === 'ready' && !result.empty && <>
      <p className="population-meta">عدد السجلات: <bdi>{result.totalElements}</bdi> — ترتيب الخادم: سنة المرجع تنازليًا ثم المعرّف تصاعديًا.</p>
      <div className="population-table-wrap"><table className="population-table"><caption className="sr-only">نتائج سجلات السكان</caption><thead><tr><th>السنة</th><th>عدد السكان</th><th>معرّف القرية</th><th>المنهجية</th><th>حالة التحقق</th></tr></thead>
        <tbody>{result.items.map(record => <tr key={record.id}><td><bdi>{record.referenceYear}</bdi></td><td><bdi>{record.populationCount}</bdi></td><td><bdi>{record.villageId}</bdi></td><td><bdi>{record.methodology}</bdi></td><td><bdi>{record.verificationStatus}</bdi></td></tr>)}</tbody>
      </table></div>
      {result.totalPages > 1 && <nav className="pagination" aria-label="صفحات سجلات السكان"><button className="button outline" type="button" disabled={result.page === 0} onClick={() => onPage(result.page - 1)}>السابق</button><span>صفحة <bdi>{result.page + 1}</bdi> من <bdi>{result.totalPages}</bdi></span><button className="button outline" type="button" disabled={result.page + 1 >= result.totalPages} onClick={() => onPage(result.page + 1)}>التالي</button></nav>}
    </>}
  </section>
}

export default function PopulationRecordsPage() {
  const service = usePopulationService()
  const permissions = service?.permissions()
  const [params, setParams] = useSearchParams()
  let query, invalid = null
  try { query = populationQueryFromParams(params) } catch (error) { invalid = error }
  return <>
    <div className="page-banner"><div className="shell"><h1>سجلات السكان</h1><p>عرض سجلات السكان للقراءة فقط.</p></div></div>
    <section className="shell section population-page" aria-label="سجلات السكان">
      {!permissions?.read ? <div className="detail-panel" role="status"><h2>سجلات السكان غير متاحة حاليًا</h2><p>{populationErrorMessage({ code: permissions?.reason ?? 'AUTH_REQUIRED' })}</p></div>
        : invalid ? <PageState error={invalid} retry={() => setParams(populationParamsFromQuery({}))} message={populationErrorMessage(invalid)} />
          : <>
            <PopulationFilter key={params.toString()} initial={query} onApply={next => setParams(populationParamsFromQuery(next))} />
            {permissions.privileged ? <p className="notice">قد تتضمن النتائج حالات تحقق غير VERIFIED بحسب صلاحياتك.</p> : <p className="notice">تعرض هذه الصفحة السجلات التي تحققت منها المنصة فقط.</p>}
            <PopulationResults key={populationParamsFromQuery(query).toString()} service={service} query={query} onPage={page => setParams(populationParamsFromQuery({ ...query, page }))} />
          </>}
    </section>
  </>
}
