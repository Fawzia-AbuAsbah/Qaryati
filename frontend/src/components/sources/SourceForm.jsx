import { useEffect, useId, useRef, useState } from 'react'
import { sourceBody, sourceFields, sourceReplacement, sourceTypeLabels, sourceTypes } from '../../services/sourceContracts.js'
import { useSourceMutation } from '../../services/useSourcesService.js'
import SourceState from './SourceState.jsx'

const labels = { title: 'العنوان', publisher: 'الناشر', author: 'المؤلف', sourceType: 'نوع المصدر', url: 'الرابط', publicationDate: 'تاريخ النشر', accessedAt: 'تاريخ الاطلاع', citation: 'نص الاقتباس' }

export default function SourceForm({ service, original = null, onSaved, onPendingChange }) {
  const prefix = useId(), errorBox = useRef(null)
  const [values, setValues] = useState(() => Object.fromEntries(sourceFields.map(field => [field, original?.[field] ?? ''])))
  const [dirty, setDirty] = useState({}), [fields, setFields] = useState({})
  const mutation = useSourceMutation(service)
  const allowed = service.permissions().write
  useEffect(() => { onPendingChange?.(mutation.pending) }, [mutation.pending, onPendingChange])
  useEffect(() => () => onPendingChange?.(false), [onPendingChange])
  useEffect(() => { if (Object.keys(fields).length || mutation.error) errorBox.current?.focus() }, [fields, mutation.error])
  function submit(event) {
    event.preventDefault()
    if (!allowed || mutation.pending) return
    const changes = Object.fromEntries(sourceFields.filter(field => !original || dirty[field]).map(field => [field,
      ['title', 'sourceType'].includes(field) ? values[field] : values[field] === '' ? null : values[field]]))
    try { if (original) sourceReplacement(original, changes); else sourceBody(changes) } catch (error) { setFields(error.fields ?? {}); return }
    setFields({})
    mutation.submit(signal => original ? service.update(original, changes, { signal }) : service.create(changes, { signal }), onSaved)
  }
  return <form className="source-form" onSubmit={submit} noValidate aria-busy={mutation.pending}>
    <h2>{original ? 'تعديل المصدر' : 'إضافة مصدر'}</h2>
    {!allowed && <p className="source-notice">الإنشاء والتعديل غير متاحين دون صلاحيات محرر أو مدير يمكن التحقق منها.</p>}
    <div tabIndex={-1} ref={errorBox}>
      {Object.keys(fields).length > 0 && <p role="alert" className="source-error">تحقق من الحقول المحددة أدناه.</p>}
      <SourceState error={mutation.error} mutation />
    </div>
    <fieldset disabled={!allowed || mutation.pending}>
      {sourceFields.map(field => <label key={field} htmlFor={`${prefix}-${field}`}>{labels[field]}{['title', 'sourceType'].includes(field) ? ' (مطلوب)' : ' (اختياري)'}
        {field === 'sourceType' ? <select id={`${prefix}-${field}`} value={values[field]} required aria-invalid={Boolean(fields[field])} aria-describedby={fields[field] ? `${prefix}-${field}-error` : undefined}
          onChange={event => { setValues({ ...values, [field]: event.target.value }); setDirty({ ...dirty, [field]: true }); setFields({}) }}>
          <option value="">اختر النوع</option>{sourceTypes.map(type => <option key={type} value={type}>{sourceTypeLabels[type]}</option>)}
        </select> : field === 'citation' ? <textarea id={`${prefix}-${field}`} value={values[field]} rows={4} aria-invalid={Boolean(fields[field])} aria-describedby={fields[field] ? `${prefix}-${field}-error` : undefined}
          onChange={event => { setValues({ ...values, [field]: event.target.value }); setDirty({ ...dirty, [field]: true }); setFields({}) }} />
          : <input id={`${prefix}-${field}`} value={values[field]} dir={['url', 'publicationDate', 'accessedAt'].includes(field) ? 'ltr' : 'auto'} type={field === 'url' ? 'url' : 'text'} required={field === 'title'} maxLength={field === 'title' ? 350 : ['author', 'publisher'].includes(field) ? 200 : undefined}
            placeholder={['publicationDate', 'accessedAt'].includes(field) ? 'YYYY-MM-DD' : undefined} aria-invalid={Boolean(fields[field])} aria-describedby={fields[field] ? `${prefix}-${field}-error` : undefined}
            onChange={event => { setValues({ ...values, [field]: event.target.value }); setDirty({ ...dirty, [field]: true }); setFields({}) }} />}
        {fields[field] && <span id={`${prefix}-${field}-error`} className="source-error">{fields[field]}</span>}
      </label>)}
      <button type="submit" className="button primary">{mutation.pending ? 'جارٍ الحفظ…' : 'حفظ المصدر'}</button>
    </fieldset>
    {mutation.pending && <p role="status">جارٍ حفظ المصدر. لا تغلق الصفحة حتى تظهر النتيجة.</p>}
  </form>
}
