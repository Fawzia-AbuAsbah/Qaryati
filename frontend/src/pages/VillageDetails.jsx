import { useCallback, useLayoutEffect } from 'react'
import { BookOpen, Building2, Landmark, MapPin, Trees, Users } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import PageState from '../components/PageState.jsx'
import { getVillage, hasCoordinates } from '../services/villageService.js'
import { useVillageData } from '../services/useVillageData.js'

const sections = [
  { key: 'population', title: 'السكان', icon: Users },
  { key: 'history', title: 'التاريخ', icon: BookOpen },
  { key: 'heritage', title: 'التراث الثقافي', icon: Landmark },
  { key: 'agriculture', title: 'الزراعة', icon: Trees },
  { key: 'community', title: 'المرافق والمجتمع', icon: Building2 },
]

export default function VillageDetails() {
  const { id } = useParams()
  const load = useCallback(() => getVillage(id), [id])
  const { data: village, ...state } = useVillageData(load)
  const pageTitle = state.loading ? 'جارٍ تحميل القرية'
    : state.error ? 'تعذّر تحميل القرية'
      : village ? village.name : 'القرية غير موجودة'

  useLayoutEffect(() => {
    document.title = `${pageTitle} | قريتي`
  }, [pageTitle])

  if (!village) {
    return <div className="shell section"><PageState {...state} title="لم نعثر على هذه القرية">{!state.error && <Link className="button primary" to="/villages">العودة إلى دليل القرى</Link>}</PageState></div>
  }

  return (
    <>
      <div className="page-banner">
        <div className="shell">
          <nav aria-label="مسار الصفحة"><Link to="/villages">دليل القرى</Link> / <span aria-current="page">{village.name}</span></nav>
          <h1>{village.name}</h1>
          <p className="location"><MapPin size={18} aria-hidden="true" /> محافظة {village.governorate}</p>
        </div>
      </div>
      <div className="shell section details">
        <section className="detail-panel">
          <h2>نبذة عن القرية</h2>
          <p>{village.overview}</p>
          {village.contentStatus === 'demo' && <p className="notice">هذا الملف تجريبي. تُعرض فيه موضوعات مقترحة للتوثيق، وتظهر الحقول التي تنتظر مصادر موثوقة على أنها غير متاحة.</p>}
          {hasCoordinates(village) ? <Link className="button primary" to={`/map?village=${encodeURIComponent(village.id)}`}><MapPin size={18} aria-hidden="true" /> عرض الموقع على الخريطة</Link> : <p>موقع القرية على الخريطة غير متاح حاليًا.</p>}
        </section>
        {sections.map(({ key, title, icon: Icon }) => (
          <section className="detail-panel" key={key}>
            <h2><Icon size={22} aria-hidden="true" />{title}</h2>
            <p>{village[key] ?? 'لا تتوفر معلومات موثّقة لهذا القسم حاليًا.'}</p>
          </section>
        ))}
      </div>
    </>
  )
}
