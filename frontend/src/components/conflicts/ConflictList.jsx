import { conflictErrorMessage } from '../../services/conflictContracts.js'
import ConflictRecord from './ConflictRecord.jsx'

const successMessages = {
  create: id => <>تم تسجيل التعارض بنجاح. معرّف التعارض: <bdi>{id}</bdi>.</>,
  review: id => <>تم نقل التعارض <bdi>{id}</bdi> إلى المراجعة.</>,
  resolve: id => <>تم حسم التعارض <bdi>{id}</bdi>.</>,
}

export default function ConflictList({ store, state, permissions, emptyText, showEntity = false }) {
  const confirmed = state.mutationStatus === 'success' && state.result
  return <div className="conflict-list" aria-busy={state.listStatus === 'loading'}>
    {confirmed && <p className="conflict-notice" role="status">{successMessages[state.mutation](state.result.id)}</p>}
    {['idle', 'loading'].includes(state.listStatus) && <p role="status">جارٍ تحميل التعارضات…</p>}
    {state.listError && <div className="conflict-error" role="alert">
      {confirmed && <p>تمت العملية بنجاح، لكن تعذّر تحديث القائمة.</p>}
      <p>{conflictErrorMessage(state.listError)}</p>
      <button className="button outline" type="button" onClick={() => store.load()}>إعادة تحميل التعارضات</button>
    </div>}
    {state.listStatus === 'ready' && state.items.length === 0 && <p>{emptyText}</p>}
    {state.items?.map(conflict => <ConflictRecord key={conflict.id} conflict={conflict} store={store} state={state} permissions={permissions} showEntity={showEntity} />)}
  </div>
}
