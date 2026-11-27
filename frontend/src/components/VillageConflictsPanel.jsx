import { useEffect, useMemo, useSyncExternalStore } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { sourceVillageIdentity } from '../services/sourceAccess.js'
import { safeAuthReturnTo } from '../services/authAccess.js'
import { useConflictService } from '../services/useConflictService.js'
import { createConflictListState } from '../services/conflictListState.js'
import { conflictErrorMessage } from '../services/conflictContracts.js'
import ConflictList from './conflicts/ConflictList.jsx'
import ConflictForm from './conflicts/ConflictForm.jsx'
import '../styles/conflicts.css'

export default function VillageConflictsPanel({ village, integration }) {
  const service = useConflictService(), location = useLocation()
  const villageId = sourceVillageIdentity(village, integration)
  if (!villageId) return <section className="conflict-panel"><h2>تعارضات البيانات</h2><p>تعارضات البيانات غير متاحة دون هوية قرية حقيقية وتكامل متاح.</p></section>
  if (!service?.permissions().read) return <section className="conflict-panel"><h2>تعارضات البيانات</h2><p>سجّل الدخول لعرض تعارضات البيانات المحمية.</p>
    <Link className="button outline" to="/login" state={{ from: safeAuthReturnTo(`${location.pathname}${location.search}${location.hash}`) }}>تسجيل الدخول</Link>
  </section>
  // Keyed by village and session so navigation or a new session starts clean.
  return <AvailableConflictsPanel key={villageId} service={service} villageId={villageId} />
}

function AvailableConflictsPanel({ service, villageId }) {
  const store = useMemo(() => createConflictListState(service, {
    operation: 'read', load: signal => service.listForVillage(villageId, { signal }),
  }), [service, villageId])
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  useEffect(() => { store.load(); return () => store.dispose() }, [store])
  const permissions = service.permissions()
  const createError = state.mutation === 'create' && state.mutationStatus === 'error' ? state.mutationError : null
  return <section className="conflict-panel" aria-label="تعارضات بيانات القرية">
    <h2>تعارضات البيانات</h2>
    <p className="conflict-notice">تعرض هذه السجلات قيمًا متنافسة لحقول القرية. حسم التعارض لا يعدّل بيانات القرية.</p>
    <ConflictList store={store} state={state} permissions={permissions} emptyText="لا توجد تعارضات بيانات لهذه القرية." />
    {permissions.create ? <ConflictForm key={state.createdId ?? 'new'} villageId={villageId} store={store}
      pending={state.mutationStatus === 'pending'} error={createError} disabled={state.listStatus !== 'ready'} />
      : <p className="conflict-notice">{conflictErrorMessage({ code: permissions.reason }, 'create')}</p>}
  </section>
}
