import { useCallback, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useSourcesService } from '../services/useSourcesService.js'
import { useExplorerData } from '../services/useExplorerData.js'
import { toBackendId } from '../services/villageAdapters.js'
import SourceState from '../components/sources/SourceState.jsx'
import SourceForm from '../components/sources/SourceForm.jsx'
import SourceDetails from '../components/sources/SourceDetails.jsx'
import SourceVillages from '../components/sources/SourceVillages.jsx'
import VillageSourcesPanel from '../components/sources/VillageSourcesPanel.jsx'

export default function SourcesPage() {
  const service = useSourcesService()
  const permissions = service?.permissions()
  return <>
    <div className="page-banner"><div className="shell"><h1>المصادر</h1><p>فهرس المراجع وبياناتها وروابطها بالقرى.</p></div></div>
    <section className="shell section sources-page" aria-label="إدارة المصادر">
      {!permissions?.read ? <div className="detail-panel" role="status"><h2>إدارة المصادر غير متاحة حاليًا</h2>
        <p>تتطلب قراءة المصادر صلاحيات عارض أو محرر أو مدير. لا تتيح الخدمة حاليًا التحقق من صلاحيات الحساب، لذلك لم تُرسل أي طلبات مصادر.</p>
        <p>الإنشاء والتعديل وربط القرى غير متاحة حتى يمكن التحقق من الصلاحيات المطلوبة.</p>
      </div> : <SourcesWorkspace service={service} />}
    </section>
  </>
}

function SourcesWorkspace({ service }) {
  const [params, setParams] = useSearchParams()
  const [creating, setCreating] = useState(false), [saved, setSaved] = useState(false)
  const [createPending, setCreatePending] = useState(false)
  const sourceId = params.get('source')
  const load = useCallback(signal => service.list({ signal }), [service])
  const catalog = useExplorerData(load)
  const permissions = service.permissions()
  let invalidId = false
  if (sourceId !== null) { try { toBackendId(sourceId) } catch { invalidId = true } }
  function select(id) {
    const next = new URLSearchParams(params)
    next.set('source', id); setParams(next); setSaved(false)
  }
  function created(source) { setCreating(false); catalog.retry(); select(source.id); setSaved(true) }
  return <>
    {saved && <p className="source-notice" role="status">تم حفظ المصدر.</p>}
    <section className="detail-panel source-panel"><h2>فهرس المصادر</h2>
      <SourceState {...catalog} empty={catalog.data?.length === 0} />
      {catalog.data && <ul className="source-list">{catalog.data.map(source => <li key={source.id}><button className="button outline" type="button" aria-pressed={sourceId === source.id} onClick={() => select(source.id)}>{source.title}</button></li>)}</ul>}
      {permissions.write ? <button className="button primary" type="button" disabled={createPending} onClick={() => setCreating(value => !value)} aria-expanded={creating}>{creating ? 'إغلاق نموذج الإضافة' : 'إضافة مصدر'}</button>
        : <p className="source-notice">إنشاء المصادر وتعديلها وربطها يتطلب صلاحيات محرر أو مدير.</p>}
    </section>
    {creating && permissions.write && <div className="detail-panel"><SourceForm service={service} onSaved={created} onPendingChange={setCreatePending} /></div>}
    {invalidId ? <p className="source-error" role="alert">معرّف المصدر غير صالح.</p>
      : sourceId ? <SelectedSource key={sourceId} service={service} sourceId={sourceId} onSaved={() => { catalog.retry(); setSaved(true) }} />
        : <p className="source-notice">اختر مصدرًا لعرض التفاصيل والقرى المرتبطة به.</p>}
    <VillageSourcesPanel service={service} />
  </>
}

function SelectedSource({ service, sourceId, onSaved }) {
  const [editing, setEditing] = useState(false)
  const [editPending, setEditPending] = useState(false)
  const load = useCallback(signal => service.get(sourceId, { signal }), [service, sourceId])
  const details = useExplorerData(load)
  return <div className="detail-panel source-panel">
    <SourceState {...details} />
    {details.data && <>
      <SourceDetails source={details.data} />
      {service.permissions().write && <button className="button outline" type="button" disabled={editPending} onClick={() => setEditing(value => !value)} aria-expanded={editing}>{editing ? 'إغلاق نموذج التعديل' : 'تعديل المصدر'}</button>}
      {editing && service.permissions().write && <SourceForm key={details.data.id} service={service} original={details.data} onPendingChange={setEditPending} onSaved={() => { setEditing(false); details.retry(); onSaved() }} />}
      <SourceVillages service={service} sourceId={sourceId} />
    </>}
  </div>
}
