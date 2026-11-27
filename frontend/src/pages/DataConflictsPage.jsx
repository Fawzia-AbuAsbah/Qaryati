import { useEffect, useMemo, useSyncExternalStore } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useConflictService } from '../services/useConflictService.js'
import { createConflictListState } from '../services/conflictListState.js'
import { conflictErrorMessage, conflictStatuses, conflictStatusLabels } from '../services/conflictContracts.js'
import ConflictList from '../components/conflicts/ConflictList.jsx'
import '../styles/conflicts.css'

export default function DataConflictsPage() {
  const service = useConflictService()
  const permissions = service?.permissions()
  return <>
    <div className="page-banner"><div className="shell"><h1>تعارضات البيانات</h1><p>متابعة التعارضات المسجّلة بين قيم بيانات القرى ومراجعتها وحسمها.</p></div></div>
    <section className="shell section conflicts-page" aria-label="إدارة تعارضات البيانات">
      {!permissions?.manage ? <div className="detail-panel" role="status"><h2>إدارة التعارضات غير متاحة حاليًا</h2>
        <p>{conflictErrorMessage({ code: permissions?.reason ?? 'AUTH_REQUIRED' })}</p>
        <p>قوائم الحالة والإنشاء تتطلب محررًا أو مديرًا؛ المراجعة والحسم تتطلب مديرًا. قراءة تعارضات قرية محددة متاحة لأي حساب مسجّل عند توفر تكامل القرى.</p>
      </div> : <ConflictsWorkspace service={service} />}
    </section>
  </>
}

function ConflictsWorkspace({ service }) {
  const [params, setParams] = useSearchParams()
  const requested = params.get('status')
  const status = requested === null ? 'OPEN' : requested
  const valid = conflictStatuses.includes(status)
  function select(next) {
    const query = new URLSearchParams(params)
    query.set('status', next); setParams(query)
  }
  return <>
    <nav className="conflict-filter" aria-label="تصفية التعارضات حسب الحالة">
      {conflictStatuses.map(value => <button key={value} className="button outline" type="button" aria-pressed={status === value} onClick={() => select(value)}>{conflictStatusLabels[value]}</button>)}
    </nav>
    {valid ? <StatusConflicts key={status} service={service} status={status} />
      : <p className="conflict-error" role="alert">حالة التعارض المطلوبة غير مدعومة.</p>}
  </>
}

function StatusConflicts({ service, status }) {
  // A filter change remounts this list, cancelling the previous status read.
  const store = useMemo(() => createConflictListState(service, {
    operation: 'manage', load: signal => service.listByStatus(status, { signal }),
  }), [service, status])
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  useEffect(() => { store.load(); return () => store.dispose() }, [store])
  return <section className="detail-panel conflict-panel" aria-label={`التعارضات: ${conflictStatusLabels[status]}`}>
    <h2>التعارضات: {conflictStatusLabels[status]}</h2>
    <ConflictList store={store} state={state} permissions={service.permissions()} showEntity emptyText="لا توجد تعارضات بهذه الحالة." />
  </section>
}
