import { sourceErrorMessage } from '../../services/sourceContracts.js'

export default function SourceState({ loading, error, retry, empty, mutation = false }) {
  if (loading) return <p className="source-notice" role="status">جارٍ تحميل المصادر…</p>
  if (error) return <div className="source-notice source-error" role="alert"><p>{sourceErrorMessage(error, mutation)}</p>
    {retry && !mutation && <button type="button" className="button outline" onClick={retry}>إعادة المحاولة</button>}
  </div>
  if (empty) return <p className="source-notice">لا توجد بيانات مصادر في هذه القائمة.</p>
  return null
}
