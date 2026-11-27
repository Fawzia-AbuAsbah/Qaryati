import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { useExplorerData } from '../../services/useExplorerData.js'
import { villageDetailsService } from '../../services/villageDetailsService.js'
import SourceState from './SourceState.jsx'

export default function SourceVillages({ service, sourceId }) {
  const load = useCallback(signal => service.sourceVillages(sourceId, { signal }), [service, sourceId])
  const result = useExplorerData(load)
  const canNavigate = villageDetailsService.mode === 'api' && villageDetailsService.available
  return <section className="source-panel" aria-label="القرى المرتبطة بالمصدر"><h2>القرى المرتبطة بالمصدر</h2>
    <SourceState {...result} empty={result.data?.length === 0} />
    {result.data && <ul className="source-list">{result.data.map(link => <li key={link.villageId}>
      {canNavigate ? <Link to={`/villages/${link.villageId}`}>القرية رقم {link.villageId}</Link> : <span>معرّف القرية: <bdi>{link.villageId}</bdi></span>}
      <p>ملاحظة الربط: {link.claimNote ?? 'غير متاحة'}</p>
    </li>)}</ul>}
    {!canNavigate && <p className="source-notice">الانتقال إلى القرى الحقيقية غير متاح حاليًا. لا تُربط هذه المعرّفات بقرى البيانات التجريبية.</p>}
  </section>
}
