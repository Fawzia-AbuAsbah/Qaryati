import { useEffect, useId, useRef, useState } from 'react'
import { conflictErrorMessage, conflictNextAction, conflictResolveBody, conflictStatusLabels } from '../../services/conflictContracts.js'
import { StructuredValue } from './StructuredValue.js'

function Meta({ label, value, time = false }) {
  const shown = value === null ? 'غير متاح' : value === '' ? 'نص فارغ' : time ? <time dateTime={value}><bdi>{value}</bdi></time> : <bdi className="conflict-text">{value}</bdi>
  return <><dt>{label}</dt><dd>{shown}</dd></>
}

export default function ConflictRecord({ conflict, store, state, permissions, showEntity = false }) {
  const action = conflictNextAction(conflict.status)
  const busy = state.mutationStatus === 'pending', listReady = state.listStatus === 'ready'
  const mine = state.targetId === conflict.id
  const headingId = useId()
  return <article className="conflict-record" aria-labelledby={headingId}>
    <h3 id={headingId}>الحقل: <bdi className="conflict-text">{conflict.fieldName === '' ? 'نص فارغ' : conflict.fieldName}</bdi></h3>
    <p><span className={`conflict-status conflict-status-${conflict.status.toLowerCase()}`}>{conflictStatusLabels[conflict.status]}</span></p>
    <dl className="conflict-metadata">
      <Meta label="معرّف التعارض" value={conflict.id} />
      {showEntity && <><Meta label="نوع الكيان" value={conflict.entityType} /><Meta label="معرّف الكيان" value={conflict.entityId} /></>}
      <Meta label="تاريخ الإنشاء" value={conflict.createdAt} time />
      <Meta label="ملاحظات الحسم" value={conflict.resolutionNotes} />
      <Meta label="معرّف من حسم التعارض" value={conflict.resolvedBy === null ? null : String(conflict.resolvedBy)} />
      <Meta label="تاريخ الحسم" value={conflict.resolvedAt} time />
    </dl>
    <h4>القيم المتنافسة ({conflict.conflictingValues.length})</h4>
    {conflict.conflictingValues.length === 0 ? <p className="conflict-meta">لا توجد قيم متنافسة في هذا السجل.</p>
      : <ol className="conflict-values">{conflict.conflictingValues.map((value, index) => <li key={index}><StructuredValue value={value} /></li>)}</ol>}
    {mine && state.mutationStatus === 'error' && <div className="conflict-error" role="alert">
      <p>{conflictErrorMessage(state.mutationError, state.mutation)}</p>
      <button className="button outline" type="button" onClick={() => store.load()}>إعادة تحميل التعارضات</button>
    </div>}
    {action === 'review' && permissions.review && <button className="button outline" type="button" disabled={busy || !listReady}
      onClick={() => store.review(conflict.id)}>{busy && mine ? 'جارٍ النقل إلى المراجعة…' : 'نقل إلى المراجعة'}</button>}
    {action === 'resolve' && permissions.resolve && <ResolveForm conflictId={conflict.id} store={store} pending={busy} disabled={!listReady} />}
  </article>
}

function ResolveForm({ conflictId, store, pending, disabled }) {
  const prefix = useId(), errorBox = useRef(null)
  const [notes, setNotes] = useState(''), [error, setError] = useState(null)
  useEffect(() => { if (error) errorBox.current?.focus() }, [error])
  function submit(event) {
    event.preventDefault()
    if (pending || disabled) return
    try { conflictResolveBody(notes) } catch (validation) { setError(validation.fields.resolutionNotes); return }
    setError(null); store.resolve(conflictId, notes)
  }
  return <form className="conflict-form" onSubmit={submit} noValidate aria-busy={pending}>
    <p className="conflict-notice">حسم التعارض يسجّل الملاحظات وحالة التعارض فقط، ولا يعدّل بيانات القرية.</p>
    <div ref={errorBox} tabIndex={-1}>{error && <p className="conflict-error" role="alert" id={`${prefix}-error`}>{error}</p>}</div>
    <fieldset disabled={pending || disabled}>
      <label htmlFor={`${prefix}-notes`}>ملاحظات الحسم (مطلوبة)<textarea id={`${prefix}-notes`} rows={3} required value={notes}
        aria-invalid={Boolean(error)} aria-describedby={error ? `${prefix}-error` : undefined}
        onChange={event => { setNotes(event.target.value); setError(null) }} /></label>
      <button className="button primary" type="submit">حسم التعارض</button>
    </fieldset>
  </form>
}
