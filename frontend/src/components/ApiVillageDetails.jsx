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

function ApiVillageContent({ village, returnTo, returnLabel }) {
  const mapHref = villageMapHref(village, villageDetailsService, villageMapService)
  return <>
    <div className="page-banner"><div className="shell">
      <nav aria-label="مسار الصفحة"><Link to={returnTo}>{returnLabel}</Link> / <span aria-current="page">{village.name}</span></nav>
      <h1>{village.name}</h1>
      <p className="location"><MapPin size={18} aria-hidden="true" />اسم المحافظة غير متاح</p>
    </div></div>
    <div className="shell section details">
      <section className="detail-panel">
        <h2>معلومات القرية الأساسية</h2>
        <Field label="الاسم الإنجليزي" value={village.nameEn} />
        <Field label="معرّف المحافظة" value={village.governorateId} />
        <Field label="معرّف الهيئة المحلية" value={village.localAuthorityId} />
        <p>أسماء المحافظات والهيئات المحلية غير متاحة من الخدمة العامة حاليًا.</p>
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
  const title = detailsPageTitle({ ...state, village })
  useLayoutEffect(() => { document.title = title }, [title])
  if (!village) return <div className="shell section">
    <PageState {...state} title="لم نعثر على هذه القرية" message={state.error ? detailsErrorMessage(state.error) : undefined}>
      <Link className="button primary" to={returnTo}>العودة إلى {returnLabel}</Link>
    </PageState>
  </div>
  return <ApiVillageContent key={village.id} village={village} returnTo={returnTo} returnLabel={returnLabel} />
}
