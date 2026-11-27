import { useId } from 'react'
import { auditActionLabels } from '../../services/auditContracts.js'
import { StructuredValue } from '../conflicts/StructuredValue.js'

function Meta({ label, value, time = false }) {
  const shown = value === null ? 'غير متاح' : value === '' ? 'نص فارغ'
    : time ? <time dateTime={value}><bdi>{value}</bdi></time> : <bdi className="audit-text">{value}</bdi>
  return <><dt>{label}</dt><dd>{shown}</dd></>
}

function Snapshot({ title, value }) {
  return <section className="audit-snapshot">
    <h4>{title}</h4>
    {value === null ? <p className="conflict-meta">لم تُسجَّل بيانات لهذه الحالة.</p> : <StructuredValue value={value} />}
  </section>
}

export default function AuditLogRecord({ log }) {
  const headingId = useId()
  return <article className="audit-record" aria-labelledby={headingId}>
    <h3 id={headingId}>{auditActionLabels[log.action] ?? <bdi>{log.action}</bdi>} — <bdi>{log.entityType}</bdi> <bdi>#{log.entityId}</bdi></h3>
    <dl className="conflict-metadata">
      <Meta label="معرّف سجل التدقيق" value={log.id} />
      <Meta label="الإجراء" value={log.action} />
      <Meta label="نوع الكيان" value={log.entityType} />
      <Meta label="معرّف الكيان" value={log.entityId} />
      <Meta label="معرّف المستخدم المنفّذ" value={log.actorUserId === null ? null : String(log.actorUserId)} />
      <Meta label="اسم المستخدم المنفّذ كما سُجّل" value={log.actorLabel} />
      <Meta label="وقت التنفيذ" value={log.occurredAt} time />
      <Meta label="السبب" value={log.reason} />
      <Meta label="معرّف الترابط" value={log.correlationId} />
    </dl>
    <div className="audit-snapshots">
      <Snapshot title="البيانات قبل التغيير" value={log.beforeData} />
      <Snapshot title="البيانات بعد التغيير" value={log.afterData} />
    </div>
  </article>
}
