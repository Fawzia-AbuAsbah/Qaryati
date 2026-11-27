import { useCallback } from 'react'
import { useExplorerData } from '../../services/useExplorerData.js'
import SourceState from './SourceState.jsx'

export default function SourceVillages({ service, sourceId }) {
  const load = useCallback(signal => service.sourceVillages(sourceId, { signal }), [service, sourceId])
  const result = useExplorerData(load)
  return <section className="source-panel" aria-label="القرى المرتبطة بالمصدر"><h2>القرى المرتبطة بالمصدر</h2>
    <SourceState {...result} empty={result.data?.length === 0} />
    {result.data && <ul className="source-list">{result.data.map(link => <li key={link.villageId}>
      <span>معرّف القرية: <bdi>{link.villageId}</bdi></span>
      <p>ملاحظة الربط: {link.claimNote ?? 'غير متاحة'}</p>
    </li>)}</ul>}
    <p className="source-notice">هذه ارتباطات تحريرية محمية، ولا تعني أن القرى المرتبطة منشورة أو متاحة للعامة. لا تُربط هذه المعرّفات بقرى البيانات التجريبية.</p>
  </section>
}
