import { useCallback, useLayoutEffect, useState } from 'react'
import { BookOpen, Building2, Landmark, MapPin, Trees, Users } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'
import PageState from './PageState.jsx'
import { villageDetailsService } from '../services/villageDetailsService.js'
import { detailsErrorMessage, detailsPageTitle, detailsReturnNavigation } from '../services/villageDetailsState.js'
import { useExplorerData } from '../services/useExplorerData.js'
import { villageMapService } from '../services/villageMapService.js'
import { villageMapHref } from '../services/villageMapState.js'

const statusLabels = { DRAFT: 'مسودة', UNDER_REVIEW: 'قيد المراجعة', VERIFIED: 'موثّق', REJECTED: 'مرفوض', DISPUTED: 'محل خلاف' }
const precisionLabels = { EXACT: 'تاريخ محدد', YEAR: 'سنة', PERIOD: 'فترة', UNKNOWN: 'غير معروف' }
const cultureLabels = { CRAFT: 'حرفة', FOOD: 'طعام', TRADITION: 'تقليد', PRACTICE: 'ممارسة', OTHER: 'أخرى' }
const siteLabels = { ARCHAEOLOGICAL: 'أثري', TRADITIONAL_BUILDING: 'مبنى تقليدي', SPRING: 'نبع', RELIGIOUS: 'ديني', VIEWPOINT: 'مطلّ', OTHER: 'أخرى' }
const number = new Intl.NumberFormat('ar')

function Field({ label, value }) {
  return <p><strong>{label}: </strong><bdi>{value ?? 'غير متاح'}</bdi></p>
}

function DomainRecord({ item, section }) {
  return (
    <article className="heritage-record">
      {section === 'overviews' ? <>
        <p>{item.overview ?? 'النص التاريخي غير متاح.'}</p>
        <Field label="آخر تحديث" value={item.updatedAt} />
      </> : <>
        <h4>{(section === 'events' ? item.title : item.name) ?? 'الاسم غير متاح'}</h4>
        <p>{item.description ?? 'الوصف غير متاح.'}</p>
      </>}
      {section === 'events' && <>
        <Field label="التاريخ" value={item.eventDate} />
        <Field label="من سنة" value={item.yearFrom} /><Field label="إلى سنة" value={item.yearTo} />
        <Field label="دقة التاريخ" value={precisionLabels[item.datePrecision]} />
        <Field label="تاريخ الإضافة" value={item.createdAt} />
      </>}
      {section === 'culture' && <Field label="النوع" value={cultureLabels[item.heritageType]} />}
      {section === 'sites' && <>
        <Field label="نوع الموقع" value={siteLabels[item.siteType]} />
        <Field label="خط العرض" value={item.latitude} /><Field label="خط الطول" value={item.longitude} />
        <Field label="ملاحظات الحالة" value={item.conditionNotes} />
      </>}
      <Field label="حالة التحقق" value={statusLabels[item.verificationStatus]} />
      <Field label="معرّفات المصادر" value={item.sourceIds === null ? null : item.sourceIds.length ? item.sourceIds.join('، ') : 'لا توجد معرّفات مصادر مرفقة'} />
    </article>
  )
}

function RecordSection({ villageId, section, title }) {
  const [page, setPage] = useState(1)
  const load = useCallback(signal => villageDetailsService.listSection(villageId, section, { page, signal }), [villageId, section, page])
  const { data, ...state } = useExplorerData(load)
  const headingId = `village-section-${section}`
  return (
    <section className="heritage-section" aria-labelledby={headingId}>
      <h3 id={headingId}>{title}</h3>
      {data?.items.length ? data.items.map(item => <DomainRecord key={item.id} item={item} section={section} />)
        : <PageState {...state} title="لا توجد سجلات لهذا القسم" message={state.error ? detailsErrorMessage(state.error) : undefined} />}
      {data?.totalPages > 1 && <nav className="pagination" aria-label={`صفحات ${title}`}>
        <button className="button outline" disabled={data.page <= 1} onClick={() => setPage(data.page - 1)}>السابق</button>
        <span>صفحة {number.format(data.page)} من {number.format(data.totalPages)}</span>
        <button className="button outline" disabled={data.page >= data.totalPages} onClick={() => setPage(data.page + 1)}>التالي</button>
      </nav>}
    </section>
  )
}

function ApiVillageContent({ village, returnTo, returnLabel }) {
  const load = useCallback(signal => villageDetailsService.getGovernorates({ signal }), [])
  const { data: catalog, ...catalogState } = useExplorerData(load)
  const governorate = catalog?.namesById.get(village.governorateId)
  const name = village.name ?? 'اسم القرية غير متاح'
  const mapHref = villageMapHref(village, villageDetailsService, villageMapService)
  return <>
    <div className="page-banner"><div className="shell">
      <nav aria-label="مسار الصفحة"><Link to={returnTo}>{returnLabel}</Link> / <span aria-current="page">{name}</span></nav>
      <h1>{name}</h1>
      <p className="location"><MapPin size={18} aria-hidden="true" />{catalogState.loading ? 'جارٍ تحميل المحافظة…' : governorate ? `محافظة ${governorate}` : 'المحافظة غير متاحة'}</p>
    </div></div>
    <div className="shell section details">
      <section className="detail-panel">
        <h2>نبذة عن القرية</h2>
        <Field label="الاسم الإنجليزي" value={village.nameEn} />
        <Field label="أصل الاسم" value={village.nameOrigin} />
        <Field label="وصف الموقع" value={village.locationDescription} />
        <Field label="خط العرض" value={village.latitude} /><Field label="خط الطول" value={village.longitude} />
        <Field label="الارتفاع بالمتر" value={village.elevationM} /><Field label="المساحة بالكيلومتر المربع" value={village.areaKm2} />
        {mapHref ? <Link className="button primary" to={mapHref}><MapPin size={18} aria-hidden="true" />عرض الموقع على الخريطة</Link>
          : <p>{village.coordinates ? 'إحداثيات القرية متاحة. عرض الخريطة غير متاح حاليًا.' : 'موقع القرية على الخريطة غير متاح حاليًا.'}</p>}
        {catalogState.error && <PageState error={catalogState.error} retry={catalogState.retry} message={`تعذّر تحميل أسماء المحافظات. ${detailsErrorMessage(catalogState.error)}`} />}
      </section>
      <section className="detail-panel"><h2><Users size={22} aria-hidden="true" />السكان</h2><p>بيانات السكان لهذه القرية غير متاحة من الخدمة حاليًا.</p></section>
      <section className="detail-panel"><h2><BookOpen size={22} aria-hidden="true" />التاريخ</h2>
        <RecordSection villageId={village.id} section="overviews" title="النبذة التاريخية" />
        <RecordSection villageId={village.id} section="events" title="الأحداث التاريخية" />
      </section>
      <section className="detail-panel"><h2><Landmark size={22} aria-hidden="true" />التراث الثقافي</h2>
        <RecordSection villageId={village.id} section="culture" title="موضوعات التراث الثقافي" />
        <RecordSection villageId={village.id} section="sites" title="المواقع التراثية" />
      </section>
      <section className="detail-panel"><h2><Trees size={22} aria-hidden="true" />الزراعة</h2><p>بيانات الزراعة لهذه القرية غير متاحة من الخدمة حاليًا.</p></section>
      <section className="detail-panel"><h2><Building2 size={22} aria-hidden="true" />المرافق والمجتمع</h2><p>بيانات المرافق والمجتمع لهذه القرية غير متاحة من الخدمة حاليًا.</p></section>
    </div>
  </>
}

export default function ApiVillageDetails() {
  const { id } = useParams()
  const location = useLocation()
  const { href: returnTo, label: returnLabel } = detailsReturnNavigation(location.state)
  const load = useCallback(signal => villageDetailsService.getVillage(id, { signal }), [id])
  const { data: village, ...state } = useExplorerData(load)
  const title = detailsPageTitle({ ...state, village })
  useLayoutEffect(() => { document.title = title }, [title])
  if (!village) return <div className="shell section">
    <PageState {...state} title="لم نعثر على هذه القرية" message={state.error ? detailsErrorMessage(state.error) : undefined}>
      <Link className="button primary" to={returnTo}>العودة إلى {returnLabel}</Link>
    </PageState>
  </div>
  // A village change remounts section pagination and cancels all previous reads.
  return <ApiVillageContent key={village.id} village={village} returnTo={returnTo} returnLabel={returnLabel} />
}
