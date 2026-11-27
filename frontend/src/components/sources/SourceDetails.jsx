import { sourceTypeLabels } from '../../services/sourceContracts.js'

export default function SourceDetails({ source }) {
  const values = [['معرّف المصدر', source.id], ['العنوان', source.title], ['نوع المصدر', sourceTypeLabels[source.sourceType]], ['الناشر', source.publisher], ['المؤلف', source.author],
    ['الرابط', source.url], ['تاريخ النشر', source.publicationDate], ['تاريخ الاطلاع', source.accessedAt], ['نص الاقتباس', source.citation], ['تاريخ الإنشاء', source.createdAt]]
  return <section aria-label="تفاصيل المصدر"><h2>تفاصيل المصدر</h2><dl className="source-metadata">
    {values.map(([label, value]) => <div key={label}><dt>{label}</dt><dd dir="auto">{value === null ? 'غير متاح' : value === '' ? 'نص فارغ' : value}</dd></div>)}
  </dl></section>
}
