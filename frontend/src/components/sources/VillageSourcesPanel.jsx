import { useEffect, useId, useMemo, useState, useSyncExternalStore } from 'react'
import { sourceVillageIdentity } from '../../services/sourceAccess.js'
import { useSourcesService } from '../../services/useSourcesService.js'
import { createVillageSourcesState } from '../../services/villageSourcesState.js'
import { sourceErrorMessage } from '../../services/sourceContracts.js'
import SourceState from './SourceState.jsx'

export default function VillageSourcesPanel({ village, integration }) {
  const service = useSourcesService()
  const villageId = sourceVillageIdentity(village, integration)
  if (!villageId) return null
  if (!service?.permissions().read) return <section className="source-panel" aria-label="مصادر القرية المحمية">
    <h2>مصادر القرية</h2><p>{sourceErrorMessage({ code: service?.permissions().reason ?? 'AUTH_REQUIRED' })}</p>
  </section>
  return <LinkedSources key={villageId} service={service} villageId={villageId} />
}

function LinkedSources({ service, villageId }) {
  const store = useMemo(() => createVillageSourcesState(service, villageId), [service, villageId])
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  useEffect(() => { store.load(); return () => store.dispose() }, [store])
  return <VillageSourcesView villageId={villageId} state={state} store={store} permissions={service.permissions()} />
}

export function VillageSourcesView({ villageId, state, store, permissions }) {
  if (!permissions.read) return null
  const ready = state.linksStatus === 'ready' && state.catalogStatus === 'ready'
  const available = state.catalog?.filter(source => !state.links?.some(link => link.sourceId === source.id)) ?? []
  return <section className="source-panel" aria-label="مصادر القرية" data-village-id={villageId}>
    <h2>مصادر القرية</h2>
    <button className="button outline" type="button" disabled={state.mutationStatus === 'pending' || state.linksStatus === 'loading'}
      onClick={() => store.loadLinks()}>تحديث قائمة ارتباطات القرية</button>
    {state.mutationStatus === 'success' && state.created && <div className="source-notice" role="status">
      <p>تم ربط المصدر رقم <bdi>{state.created.sourceId}</bdi> بالقرية رقم <bdi>{state.created.villageId}</bdi>.</p>
      <p>ملاحظة الربط المحفوظة: {state.created.claimNote ?? 'غير متاحة'}</p>
      {state.linksStatus === 'error' && <p>تم الربط، لكن تعذّر تحديث القائمة. أعد تحميل القائمة دون إعادة إرسال الربط.</p>}
    </div>}
    <SourceState loading={['idle', 'loading'].includes(state.linksStatus)} error={state.linksError}
      retry={() => store.loadLinks()} empty={state.linksStatus === 'ready' && state.links.length === 0} />
    {state.links && <ul className="source-list">{state.links.map(link => <li key={link.sourceId}>
      {state.catalog?.find(source => source.id === link.sourceId)?.title ?? `معرّف المصدر: ${link.sourceId}`}
      <p>ملاحظة الربط: {link.claimNote ?? 'غير متاحة'}</p>
    </li>)}</ul>}
    <SourceState loading={['idle', 'loading'].includes(state.catalogStatus)} error={state.catalogError} retry={() => store.loadCatalog()} />
    {permissions.write && state.catalogStatus === 'ready' && <button className="button outline" type="button" disabled={state.mutationStatus === 'pending'}
      onClick={() => store.loadCatalog()}>تحديث فهرس المصادر المتاحة</button>}
    {!permissions.write ? <p className="source-notice">قراءة فقط. ربط مصدر يتطلب صلاحيات محرر أو مدير.</p>
      : ready && available.length > 0 ? <LinkSourceForm key={state.created?.sourceId ?? 'new'} state={state} store={store} sources={available} />
        : ready && <p className="source-notice">{state.catalog.length === 0 ? 'لا توجد مصادر للاختيار. أضف مصدرًا من صفحة إدارة المصادر أولًا.' : 'جميع المصادر المتاحة مرتبطة بهذه القرية بالفعل.'}</p>}
  </section>
}

function LinkSourceForm({ state, store, sources }) {
  const prefix = useId()
  const [sourceId, setSourceId] = useState(''), [claimNote, setClaimNote] = useState('')
  const pending = state.mutationStatus === 'pending'
  function submit(event) {
    event.preventDefault()
    if (pending || !sources.some(source => source.id === sourceId)) return
    store.submit(sourceId, claimNote === '' ? null : claimNote)
  }
  return <form onSubmit={submit} className="source-form" aria-busy={pending}>
      <h3>ربط مصدر موجود</h3>
      <SourceState error={state.mutationError} mutation />
      <fieldset disabled={pending}>
        <label htmlFor={`${prefix}-source`}>المصدر (مطلوب)<select id={`${prefix}-source`} value={sourceId} required onChange={event => setSourceId(event.target.value)}>
          <option value="">اختر مصدرًا</option>{sources.map(source => <option key={source.id} value={source.id}>{source.title}</option>)}
        </select></label>
        <label htmlFor={`${prefix}-claim`}>ملاحظة الربط (اختياري)<textarea id={`${prefix}-claim`} rows={3} value={claimNote} onChange={event => setClaimNote(event.target.value)} /></label>
        <button className="button primary" type="submit" disabled={!sourceId || pending}>{pending ? 'جارٍ الربط…' : 'ربط المصدر'}</button>
      </fieldset>
    </form>
}
