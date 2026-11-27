import { useEffect, useId, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuditService } from '../services/useAuditService.js'
import { createAuditLogState } from '../services/auditLogState.js'
import {
  auditActionLabels, auditActions, auditErrorMessage, auditParamsFromQuery, auditQueryFromParams, auditRequest, knownAuditEntityTypes,
} from '../services/auditContracts.js'
import AuditLogRecord from '../components/audit/AuditLogRecord.jsx'
import '../styles/conflicts.css'
import '../styles/audit.css'

export default function AuditLogsPage() {
  const service = useAuditService()
  const permissions = service?.permissions()
  return <>
    <div className="page-banner"><div className="shell"><h1>سجل التدقيق</h1><p>عرض سجلات العمليات المسجّلة للقراءة فقط.</p></div></div>
    <section className="shell section audit-page" aria-label="سجل التدقيق">
      {!permissions?.read ? <div className="detail-panel" role="status"><h2>سجل التدقيق غير متاح حاليًا</h2>
        <p>{auditErrorMessage({ code: permissions?.reason ?? 'AUTH_REQUIRED' })}</p>
        <p>يتطلب سجل التدقيق صلاحيات مدير متحققًا منها. لا تُرسل طلبات دون الصلاحية المطلوبة.</p>
      </div> : <AuditWorkspace service={service} />}
    </section>
  </>
}

function AuditWorkspace({ service }) {
  const [params, setParams] = useSearchParams()
  const query = auditQueryFromParams(params)
  let request = null, invalid = false
  if (query) { try { request = auditRequest(query) } catch { invalid = true } }
  const resultsKey = request ? auditParamsFromQuery(query).toString() : null
  return <>
    <div className="detail-panel"><AuditSearchForm key={params.toString()} initial={query} onSearch={next => setParams(auditParamsFromQuery(next))} /></div>
    {!query ? <p className="conflict-notice">اختر طريقة بحث واحدة وأدخل قيمتها لعرض السجلات.</p>
      : invalid ? <p className="conflict-error" role="alert">معايير البحث في الرابط غير صالحة. لم يُرسل أي طلب.</p>
        : <AuditResults key={resultsKey} service={service} queryKey={resultsKey} />}
  </>
}

function AuditSearchForm({ initial, onSearch }) {
  const prefix = useId(), errorBox = useRef(null)
  const [mode, setMode] = useState(['entity', 'actor', 'action'].includes(initial?.mode) ? initial.mode : 'entity')
  const [entityType, setEntityType] = useState(initial?.entityType ?? '')
  const [entityId, setEntityId] = useState(initial?.entityId ?? '')
  const [actorUserId, setActorUserId] = useState(initial?.actorUserId ?? '')
  const [action, setAction] = useState(initial?.action ?? '')
  const [fields, setFields] = useState({})
  useEffect(() => { if (Object.keys(fields).length) errorBox.current?.focus() }, [fields])
  function submit(event) {
    event.preventDefault()
    const next = mode === 'entity' ? { mode, entityType, entityId } : mode === 'actor' ? { mode, actorUserId } : { mode, action }
    try { auditRequest(next) } catch (validation) { setFields(validation.fields ?? {}); return }
    setFields({}); onSearch(next)
  }
  const field = (name, label, input) => <>
    <label htmlFor={`${prefix}-${name}`}>{label}{input}</label>
    {fields[name] && <p className="conflict-error" id={`${prefix}-${name}-error`}>{fields[name]}</p>}
  </>
  const describe = name => ({ 'aria-invalid': Boolean(fields[name]), 'aria-describedby': fields[name] ? `${prefix}-${name}-error` : undefined })
  return <form className="conflict-form" onSubmit={submit} noValidate>
    <h2>البحث في سجل التدقيق</h2>
    <div ref={errorBox} tabIndex={-1}>{Object.keys(fields).length > 0 && <p className="conflict-error" role="alert">تحقق من الحقول المحددة.</p>}</div>
    <fieldset className="audit-modes"><legend>طريقة البحث (واحدة فقط)</legend>
      {[['entity', 'حسب الكيان'], ['actor', 'حسب المستخدم المنفّذ'], ['action', 'حسب الإجراء']].map(([value, label]) =>
        <label key={value} className="audit-mode"><input type="radio" name={`${prefix}-mode`} value={value} checked={mode === value} onChange={() => { setMode(value); setFields({}) }} />{label}</label>)}
    </fieldset>
    {mode === 'entity' && <>
      {field('entityType', 'نوع الكيان (مطلوب)', <input id={`${prefix}-entityType`} dir="ltr" list={`${prefix}-types`} value={entityType} {...describe('entityType')}
        onChange={event => { setEntityType(event.target.value); setFields({}) }} />)}
      <datalist id={`${prefix}-types`}>{knownAuditEntityTypes.map(([value, label]) => <option key={value} value={value} label={label} />)}</datalist>
      {field('entityId', 'معرّف الكيان (مطلوب)', <input id={`${prefix}-entityId`} dir="ltr" inputMode="numeric" value={entityId} {...describe('entityId')}
        onChange={event => { setEntityId(event.target.value); setFields({}) }} />)}
      <p className="conflict-notice">تُعرض فقط سجلات نوع الكيان والمعرّف المحددين. سجلات التعارضات والتحقق والمصادر وروابطها تُحفظ بأنواع ومعرّفات خاصة بها، لذلك لا تمثّل النتائج تاريخًا كاملًا لقرية.</p>
    </>}
    {mode === 'actor' && field('actorUserId', 'معرّف المستخدم المنفّذ (مطلوب)', <input id={`${prefix}-actorUserId`} dir="ltr" inputMode="numeric" value={actorUserId}
      {...describe('actorUserId')} onChange={event => { setActorUserId(event.target.value); setFields({}) }} />)}
    {mode === 'action' && field('action', 'الإجراء (مطلوب)', <select id={`${prefix}-action`} value={action} {...describe('action')}
      onChange={event => { setAction(event.target.value); setFields({}) }}>
      <option value="">اختر الإجراء</option>{auditActions.map(value => <option key={value} value={value}>{auditActionLabels[value]} ({value})</option>)}
    </select>)}
    <button className="button primary" type="submit">عرض السجلات</button>
  </form>
}

function AuditResults({ service, queryKey }) {
  // The canonical query string keeps one store per filter across re-renders; a filter
  // change remounts this component, cancelling the previous read.
  const store = useMemo(() => createAuditLogState(service, auditQueryFromParams(new URLSearchParams(queryKey))), [service, queryKey])
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  useEffect(() => { store.load(); return () => store.dispose() }, [store])
  return <section className="detail-panel audit-results" aria-label="نتائج سجل التدقيق" aria-busy={state.status === 'loading'}>
    <h2>النتائج</h2>
    {['idle', 'loading'].includes(state.status) && <p role="status">جارٍ تحميل سجل التدقيق…</p>}
    {state.error && <div className="conflict-error" role="alert">
      <p>{auditErrorMessage(state.error)}</p>
      <button className="button outline" type="button" onClick={() => store.load()}>إعادة المحاولة</button>
    </div>}
    {state.status === 'ready' && state.items.length === 0 && <p>لا توجد سجلات تدقيق مطابقة.</p>}
    {state.status === 'ready' && state.items.length > 0 && <p className="audit-count">عدد السجلات: <bdi>{state.items.length}</bdi> (بترتيب الخادم، الأحدث أولًا)</p>}
    {state.items?.map(log => <AuditLogRecord key={log.id} log={log} />)}
  </section>
}
