import { useEffect, useId, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { sourceVillageIdentity } from '../services/sourceAccess.js'
import { safeAuthReturnTo } from '../services/authAccess.js'
import { useVerificationService } from '../services/useVerificationService.js'
import { createVerificationPanelState } from '../services/verificationPanelState.js'
import { verificationBody, verificationDecisionLabels, verificationDecisions, verificationErrorMessage } from '../services/verificationContracts.js'
import '../styles/verification.css'

export default function VerificationPanel({ village, integration }) {
  const service = useVerificationService(), location = useLocation()
  const villageId = sourceVillageIdentity(village, integration)
  if (!villageId) return <section className="verification-panel"><h2>سجل التحقق</h2><p>سجل التحقق غير متاح دون هوية قرية حقيقية وتكامل متاح.</p></section>
  if (!service?.permissions().read) return <section className="verification-panel"><h2>سجل التحقق</h2><p>سجّل الدخول لعرض معلومات التحقق المحمية.</p>
    <Link className="button outline" to="/login" state={{ from: safeAuthReturnTo(`${location.pathname}${location.search}${location.hash}`) }}>تسجيل الدخول</Link>
  </section>
  return <AvailableVerificationPanel key={villageId} service={service} villageId={villageId} />
}

function RecordedDecision({ record }) {
  return <article className="verification-record">
    <h3>{verificationDecisionLabels[record.decision]}</h3>
    <p>السبب: <span className="verification-text">{record.reason === null ? 'غير متاح' : record.reason === '' ? 'نص فارغ' : record.reason}</span></p>
    <p>معرّف المراجع: {record.reviewerUserId === null ? 'غير متاح' : <bdi>{record.reviewerUserId}</bdi>}</p>
    <p>وقت المراجعة: {record.reviewedAt === null ? 'غير متاح' : <time dateTime={record.reviewedAt}><bdi>{record.reviewedAt}</bdi></time>}</p>
  </article>
}

function AvailableVerificationPanel({ service, villageId }) {
  const store = useMemo(() => createVerificationPanelState(service, villageId), [service, villageId])
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  useEffect(() => { store.load(); return () => store.dispose() }, [store])
  const permissions = service.permissions()
  const pending = state.mutationStatus === 'pending'
  return <section className="verification-panel" aria-label="سجل تحقق القرية">
    <h2>سجل التحقق</h2>
    <p className="verification-notice">هذه سجلات قرارات مراجعة. تسجيل القرار لا يغيّر حالة القرية.</p>
    {state.mutationStatus === 'success' && state.created && <div className="verification-notice" role="status">
      <p>تم تسجيل القرار بنجاح. معرّف السجل: <bdi>{state.created.id}</bdi>.</p>
      <RecordedDecision record={state.created} />
    </div>}
    {['idle', 'loading'].includes(state.historyStatus) && <p role="status">جارٍ تحميل سجل التحقق…</p>}
    {state.historyError && <div className="verification-error" role="alert">
      {state.mutationStatus === 'success' && <p>تم تسجيل القرار، لكن تعذّر تحديث قائمة السجل.</p>}
      <p>{verificationErrorMessage(state.historyError)}</p>
      <button className="button outline" type="button" onClick={() => store.load()}>إعادة تحميل السجل</button>
    </div>}
    {state.historyStatus === 'ready' && state.records.length === 0 && <p>لا توجد سجلات تحقق لهذه القرية.</p>}
    {state.records?.map(record => <RecordedDecision key={record.id} record={record} />)}
    {permissions.create ? <VerificationForm key={state.created?.id ?? 'new'} villageId={villageId} store={store} pending={pending}
      error={state.mutationError} disabled={state.historyStatus !== 'ready'} />
      : <p className="verification-notice">{verificationErrorMessage({ code: permissions.reason })}</p>}
  </section>
}

function VerificationForm({ villageId, store, pending, error, disabled }) {
  const prefix = useId(), errorBox = useRef(null)
  const [decision, setDecision] = useState(''), [reason, setReason] = useState(''), [fields, setFields] = useState({})
  useEffect(() => { if (error || Object.keys(fields).length) errorBox.current?.focus() }, [error, fields])
  function submit(event) {
    event.preventDefault()
    if (pending || disabled) return
    const note = reason === '' ? null : reason
    try { verificationBody(villageId, decision, note) } catch (validation) { setFields(validation.fields ?? {}); return }
    setFields({}); store.submit(decision, note)
  }
  return <form className="verification-form" onSubmit={submit} noValidate aria-busy={pending}>
    <h3>تسجيل قرار تحقق</h3>
    <div ref={errorBox} tabIndex={-1}>{(error || Object.keys(fields).length > 0) && <p className="verification-error" role="alert">{error ? verificationErrorMessage(error, true) : 'تحقق من الحقول المحددة.'}</p>}</div>
    <fieldset disabled={pending || disabled}>
      <label htmlFor={`${prefix}-decision`}>القرار (مطلوب)<select id={`${prefix}-decision`} required value={decision} aria-invalid={Boolean(fields.decision)} aria-describedby={fields.decision ? `${prefix}-decision-error` : undefined} onChange={event => { setDecision(event.target.value); setFields({}) }}>
        <option value="">اختر القرار</option>{verificationDecisions.map(value => <option key={value} value={value}>{verificationDecisionLabels[value]}</option>)}
      </select></label>
      {fields.decision && <p className="verification-error" id={`${prefix}-decision-error`}>{fields.decision}</p>}
      <label htmlFor={`${prefix}-reason`}>السبب (اختياري)<textarea id={`${prefix}-reason`} rows={4} value={reason} aria-invalid={Boolean(fields.reason)} aria-describedby={fields.reason ? `${prefix}-reason-error` : undefined} onChange={event => { setReason(event.target.value); setFields({}) }} /></label>
      {fields.reason && <p className="verification-error" id={`${prefix}-reason-error`}>{fields.reason}</p>}
      <button className="button primary" type="submit">{pending ? 'جارٍ تسجيل القرار…' : 'تسجيل القرار'}</button>
    </fieldset>
    {pending && <p role="status">جارٍ تسجيل القرار. لا تغلق الصفحة حتى تظهر النتيجة.</p>}
  </form>
}
