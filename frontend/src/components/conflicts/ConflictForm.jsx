import { useEffect, useId, useRef, useState } from 'react'
import { conflictCreateBody, conflictErrorMessage, conflictFieldNameMax, conflictValuesFromDraft } from '../../services/conflictContracts.js'

const emptyRow = id => ({ id, key: '', value: '' })

export default function ConflictForm({ villageId, store, pending, error, disabled }) {
  const prefix = useId(), errorBox = useRef(null), nextId = useRef(4)
  const [fieldName, setFieldName] = useState('')
  const [values, setValues] = useState(() => [{ id: 0, rows: [emptyRow(1)] }, { id: 2, rows: [emptyRow(3)] }])
  const [fields, setFields] = useState({})
  useEffect(() => { if (error || Object.keys(fields).length) errorBox.current?.focus() }, [error, fields])
  const id = () => nextId.current++
  function update(change) { setValues(change); setFields({}) }
  function setRow(valueId, rowId, patch) {
    update(list => list.map(value => value.id !== valueId ? value : { ...value, rows: value.rows.map(row => row.id === rowId ? { ...row, ...patch } : row) }))
  }
  function submit(event) {
    event.preventDefault()
    if (pending || disabled) return
    let conflictingValues
    try {
      conflictingValues = conflictValuesFromDraft(values.map(value => value.rows.map(({ key, value: text }) => ({ key, value: text }))))
      conflictCreateBody(villageId, fieldName, conflictingValues)
    } catch (validation) { setFields(validation.fields ?? {}); return }
    setFields({}); store.create(villageId, fieldName, conflictingValues)
  }
  return <form className="conflict-form" onSubmit={submit} noValidate aria-busy={pending}>
    <h3>تسجيل تعارض بيانات</h3>
    <p className="conflict-notice">يسجّل التعارض القيم المتنافسة كما أُدخلت نصوصًا، ولا يغيّر بيانات القرية.</p>
    <div ref={errorBox} tabIndex={-1}>{(error || Object.keys(fields).length > 0) && <p className="conflict-error" role="alert">
      {error ? conflictErrorMessage(error, 'create') : 'تحقق من الحقول المحددة.'}</p>}</div>
    <fieldset disabled={pending || disabled}>
      <label htmlFor={`${prefix}-field`}>اسم الحقل المتنازع عليه (مطلوب)<input id={`${prefix}-field`} required maxLength={conflictFieldNameMax}
        value={fieldName} aria-invalid={Boolean(fields.fieldName)} aria-describedby={fields.fieldName ? `${prefix}-field-error` : undefined}
        onChange={event => { setFieldName(event.target.value); setFields({}) }} /></label>
      {fields.fieldName && <p className="conflict-error" id={`${prefix}-field-error`}>{fields.fieldName}</p>}
      {fields.conflictingValues && <p className="conflict-error">{fields.conflictingValues}</p>}
      {values.map((value, index) => <fieldset key={value.id} className="conflict-value-editor" aria-describedby={fields[`value-${index}`] ? `${prefix}-value-${index}-error` : undefined}>
        <legend>القيمة المتنافسة {index + 1}</legend>
        {value.rows.map((row, rowIndex) => <div key={row.id} className="conflict-entry">
          <label htmlFor={`${prefix}-${row.id}-key`}>اسم الحقل {rowIndex + 1}<input id={`${prefix}-${row.id}-key`} value={row.key} placeholder="مثال: القيمة"
            onChange={event => setRow(value.id, row.id, { key: event.target.value })} /></label>
          <label htmlFor={`${prefix}-${row.id}-value`}>القيمة {rowIndex + 1}<input id={`${prefix}-${row.id}-value`} value={row.value}
            onChange={event => setRow(value.id, row.id, { value: event.target.value })} /></label>
          {value.rows.length > 1 && <button className="button outline" type="button"
            onClick={() => update(list => list.map(item => item.id !== value.id ? item : { ...item, rows: item.rows.filter(entry => entry.id !== row.id) }))}>إزالة الحقل</button>}
        </div>)}
        {fields[`value-${index}`] && <p className="conflict-error" id={`${prefix}-value-${index}-error`}>{fields[`value-${index}`]}</p>}
        <div className="conflict-actions">
          <button className="button outline" type="button" onClick={() => update(list => list.map(item => item.id !== value.id ? item : { ...item, rows: [...item.rows, emptyRow(id())] }))}>إضافة حقل</button>
          {values.length > 2 && <button className="button outline" type="button" onClick={() => update(list => list.filter(item => item.id !== value.id))}>إزالة القيمة</button>}
        </div>
      </fieldset>)}
      <div className="conflict-actions">
        <button className="button outline" type="button" onClick={() => update(list => [...list, { id: id(), rows: [emptyRow(id())] }])}>إضافة قيمة متنافسة</button>
        <button className="button primary" type="submit">{pending ? 'جارٍ تسجيل التعارض…' : 'تسجيل التعارض'}</button>
      </div>
    </fieldset>
    {pending && <p role="status">جارٍ تسجيل التعارض. لا تغلق الصفحة حتى تظهر النتيجة.</p>}
  </form>
}
