import { CircleAlert, Search } from 'lucide-react'

export default function PageState({ loading, error, retry, title, message, children }) {
  if (loading) return <p className="state" role="status">جارٍ التحميل…</p>

  return (
    <div className="state" role={error ? 'alert' : undefined}>
      {error ? <CircleAlert aria-hidden="true" /> : <Search aria-hidden="true" />}
      <h2>{error ? 'تعذّر تحميل البيانات' : title}</h2>
      {message && <p>{message}</p>}
      {error && <button className="button primary" onClick={retry}>إعادة المحاولة</button>}
      {children}
    </div>
  )
}
