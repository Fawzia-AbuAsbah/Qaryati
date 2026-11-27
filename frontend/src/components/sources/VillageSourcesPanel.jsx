import { useCallback, useId, useState } from 'react'
import { sourceVillageIdentity } from '../../services/sourceAccess.js'
import { useExplorerData } from '../../services/useExplorerData.js'
import { useSourceMutation } from '../../services/useSourcesService.js'
import SourceState from './SourceState.jsx'

export default function VillageSourcesPanel({ service, village, integration }) {
  const villageId = sourceVillageIdentity(village, integration)
  if (!villageId || !service?.permissions().read) return <p className="source-notice">مصادر القرية غير متاحة دون هوية قرية حقيقية وتكامل وصلاحيات متحقق منها.</p>
  return <LinkedSources key={villageId} service={service} villageId={villageId} />
}

function LinkedSources({ service, villageId }) {
  const prefix = useId()
  const [sourceId, setSourceId] = useState(''), [claimNote, setClaimNote] = useState('')
  const loadLinks = useCallback(signal => service.villageSources(villageId, { signal }), [service, villageId])
  const loadCatalog = useCallback(signal => service.list({ signal }), [service])
  const links = useExplorerData(loadLinks), catalog = useExplorerData(loadCatalog)
  const mutation = useSourceMutation(service)
  const writable = service.permissions().write
  function submit(event) {
    event.preventDefault()
    if (!writable || mutation.pending || !catalog.data?.some(source => source.id === sourceId)) return
    mutation.submit(signal => service.link(villageId, sourceId, claimNote === '' ? null : claimNote, { signal }), () => {
      setClaimNote(''); setSourceId(''); links.retry()
    })
  }
  return <section className="source-panel" aria-label="مصادر القرية"><h2>مصادر القرية</h2>
    <SourceState {...links} empty={links.data?.length === 0} />
    {links.data && <ul className="source-list">{links.data.map(link => <li key={link.sourceId}>
      {catalog.data?.find(source => source.id === link.sourceId)?.title ?? `معرّف المصدر: ${link.sourceId}`}
      <p>ملاحظة الربط: {link.claimNote ?? 'غير متاحة'}</p>
    </li>)}</ul>}
    <SourceState loading={catalog.loading} error={catalog.error} retry={catalog.retry} />
    <form onSubmit={submit} className="source-form" aria-busy={mutation.pending}>
      <h3>ربط مصدر موجود</h3>
      {!writable && <p className="source-notice">الربط يتطلب صلاحيات محرر أو مدير يمكن التحقق منها.</p>}
      <SourceState error={mutation.error} mutation />
      <fieldset disabled={!writable || mutation.pending || !catalog.data?.length || links.loading || Boolean(links.error)}>
        <label htmlFor={`${prefix}-source`}>المصدر (مطلوب)<select id={`${prefix}-source`} value={sourceId} required onChange={event => setSourceId(event.target.value)}>
          <option value="">اختر مصدرًا</option>{catalog.data?.map(source => <option key={source.id} value={source.id}>{source.title}</option>)}
        </select></label>
        <label htmlFor={`${prefix}-claim`}>ملاحظة الربط (اختياري)<textarea id={`${prefix}-claim`} rows={3} value={claimNote} onChange={event => setClaimNote(event.target.value)} /></label>
        <button className="button primary" type="submit">{mutation.pending ? 'جارٍ الربط…' : 'ربط المصدر'}</button>
      </fieldset>
    </form>
  </section>
}
