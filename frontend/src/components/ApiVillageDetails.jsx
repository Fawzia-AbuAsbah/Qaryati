import { useCallback, useLayoutEffect } from 'react'
import { MapPin } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'
import PageState from './PageState.jsx'
import { villageDetailsService } from '../services/villageDetailsService.js'
import { detailsErrorMessage, detailsPageTitle, detailsReturnNavigation } from '../services/villageDetailsState.js'
import { useExplorerData } from '../services/useExplorerData.js'
import { villageMapService } from '../services/villageMapService.js'
import { villageMapHref } from '../services/villageMapState.js'
import { useAuth } from '../auth/useAuth.js'
import { sourcePermissions, sourceVillageIdentity } from '../services/sourceAccess.js'
import { verificationPermissions } from '../services/verificationAccess.js'
import { conflictPermissions } from '../services/conflictAccess.js'
import VerificationPanel from './VerificationPanel.jsx'
import VillageConflictsPanel from './VillageConflictsPanel.jsx'
import VillageSourcesPanel from './sources/VillageSourcesPanel.jsx'
import { catalogueName, usePublicGeographicCatalogues } from '../services/usePublicGeographicCatalogues.js'

function Field({ label, value }) {
  return <p><strong>{label}: </strong><bdi>{value ?? 'غير متاح'}</bdi></p>
}

function ProtectedVillagePanels({ village }) {
  const auth = useAuth()
  if (!sourceVillageIdentity(village, villageDetailsService)) return null
  const sources = sourcePermissions(auth), verification = verificationPermissions(auth), conflicts = conflictPermissions(auth)
  if (!sources.read && !verification.read && !conflicts.read) return null
  return <section className="detail-panel" aria-label="المعلومات التحريرية المحمية" data-protected-village-id={village.id}>
    <h2>المعلومات التحريرية المحمية</h2>
    {sources.read && <VillageSourcesPanel village={village} integration={villageDetailsService} />}
    {verification.read && <VerificationPanel village={village} integration={villageDetailsService} />}
    {conflicts.read && <VillageConflictsPanel village={village} integration={villageDetailsService} />}
  </section>
}

function ApiVillageContent({ village, returnTo, returnLabel, catalogues }) {
  const mapHref = villageMapHref(village, villageDetailsService, villageMapService)
  const governorate = catalogueName(catalogues.governorates, village.governorateId)
  const localAuthority = catalogueName(catalogues.localAuthorities, village.localAuthorityId)
  return <>
    <div className="page-banner"><div className="shell">
      <nav aria-label="مسار الصفحة"><Link to={returnTo}>{returnLabel}</Link> / <span aria-current="page">{village.name}</span></nav>
      <h1>{village.name}</h1>
      <p className="location"><MapPin size={18} aria-hidden="true" />{governorate ?? 'اسم المحافظة غير متاح'}</p>
    </div></div>
    <div className="shell section details">
      <section className="detail-panel">
        <h2>معلومات القرية الأساسية</h2>
        <Field label="الاسم الإنجليزي" value={village.nameEn} />
        <Field label="أصل الاسم" value={village.nameOrigin} />
        <Field label="وصف الموقع" value={village.locationDescription} />
        <Field label="الارتفاع (متر)" value={village.elevationM} />
        <Field label="المساحة (كم²)" value={village.areaKm2} />
        <Field label="معرّف المحافظة" value={village.governorateId} />
        <Field label="معرّف الهيئة المحلية" value={village.localAuthorityId} />
        <Field label="المحافظة" value={governorate} />
        <Field label="الهيئة المحلية" value={localAuthority} />
        {catalogues.governorates.status === 'loading' && <p role="status">جارٍ تحميل اسم المحافظة…</p>}
        {catalogues.governorates.status === 'error' && <p role="alert">تعذّر تحميل اسم المحافظة.</p>}
        {village.governorateId && catalogues.localAuthorities.status === 'loading' && <p role="status">جارٍ تحميل اسم الهيئة المحلية…</p>}
        {village.governorateId && catalogues.localAuthorities.status === 'error' && <p role="alert">تعذّر تحميل اسم الهيئة المحلية.</p>}
        <Field label="خط العرض" value={village.latitude} /><Field label="خط الطول" value={village.longitude} />
        {mapHref ? <Link className="button primary" to={mapHref}><MapPin size={18} aria-hidden="true" />عرض الموقع على الخريطة</Link>
          : <p>موقع القرية على الخريطة غير متاح حاليًا.</p>}
      </section>
      <section className="detail-panel"><h2>معلومات إضافية</h2>
        <p>بيانات السكان والتاريخ والتراث والزراعة والمرافق والأنشطة الاقتصادية غير متاحة من الخدمة العامة حاليًا.</p>
      </section>
      <ProtectedVillagePanels village={village} />
    </div>
  </>
}

export default function ApiVillageDetails() {
  const { id } = useParams()
  const location = useLocation()
  const { href: returnTo, label: returnLabel } = detailsReturnNavigation(location.state)
  const load = useCallback(signal => villageDetailsService.getVillage(id, { signal }), [id])
  const { data: village, ...state } = useExplorerData(load)
  const catalogues = usePublicGeographicCatalogues({ service: villageDetailsService, governorateId: village?.governorateId ?? null })
  const title = detailsPageTitle({ ...state, village })
  useLayoutEffect(() => { document.title = title }, [title])
  if (!village) return <div className="shell section">
    <PageState {...state} title="لم نعثر على هذه القرية" message={state.error ? detailsErrorMessage(state.error) : undefined}>
      <Link className="button primary" to={returnTo}>العودة إلى {returnLabel}</Link>
    </PageState>
  </div>
  return <ApiVillageContent key={village.id} village={village} returnTo={returnTo} returnLabel={returnLabel} catalogues={catalogues} />
}
