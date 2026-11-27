import { createLatestExplorerRequest } from './explorerRequest.js'
import { createSourceMutation } from './sourceMutation.js'
import { ConflictError, conflictNextAction } from './conflictContracts.js'

const initial = Object.freeze({
  listStatus: 'idle', items: null, listError: null,
  mutationStatus: 'idle', mutation: null, targetId: null, mutationError: null, result: null, createdId: null,
})

// One controller per (service, scope). `scope.load(signal)` is the only read, so a
// village change or status-filter change is a new controller and the old one is
// disposed. All mutations share one runner: no concurrent or duplicate actions.
export function createConflictListState(service, scope) {
  if (typeof scope?.load !== 'function' || typeof scope.operation !== 'string') throw new TypeError('Conflict scope required')
  const reads = createLatestExplorerRequest(), listeners = new Set()
  let state = initial, active = false, currentRead = null
  const live = () => active && service.isCurrent()
  const mutations = createSourceMutation(live)
  function publish(patch) {
    if (!live()) return
    state = Object.freeze({ ...state, ...patch })
    for (const listener of listeners) listener()
  }
  function load() {
    if (!service.isCurrent() || !service.permissions()[scope.operation]) return null
    active = true
    publish({ listStatus: 'loading', items: null, listError: null })
    currentRead = reads.run(signal => scope.load(signal), {
      onSuccess: items => publish({ listStatus: 'ready', items: Object.freeze([...items]), listError: null }),
      onError: listError => publish({ listStatus: 'error', items: null, listError }),
    })
    return currentRead
  }
  function mutate(mutation, targetId, run) {
    if (!live()) return null
    const access = service.permissions()
    if (!access[mutation]) { publish({ mutationStatus: 'error', mutation, targetId, mutationError: new ConflictError(access.reason ?? 'CONFLICT_FORBIDDEN'), result: null }); return null }
    if (targetId !== null) {
      // Only the action valid for the latest server-confirmed status is accepted.
      const current = state.items?.find(item => item.id === targetId)
      if (!current || conflictNextAction(current.status) !== mutation) return null
    }
    try {
      const task = mutations.run(run, {
        onSuccess: result => {
          // A list-refresh error must never turn a confirmed mutation into a
          // failure or prompt another automatic mutation.
          // createdId changes only after a confirmed creation, so later review or
          // resolve results never reset an unrelated draft in the creation form.
          publish({ mutationStatus: 'success', mutationError: null, result, listStatus: 'loading', items: null, listError: null,
            ...(mutation === 'create' ? { createdId: result.id } : {}) })
          load()
        },
        onError: mutationError => publish({ mutationStatus: 'error', mutationError }),
      })
      publish({ mutationStatus: 'pending', mutation, targetId, mutationError: null, result: null })
      return task
    } catch (error) {
      if (error.code !== 'MUTATION_IN_PROGRESS') publish({ mutationStatus: 'error', mutation, targetId, mutationError: error, result: null })
      return null
    }
  }
  return {
    getSnapshot: () => live() ? state : initial,
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener) },
    load,
    create(villageId, fieldName, values) {
      return mutate('create', null, signal => service.create(villageId, fieldName, values, { signal }))
    },
    review(id) { return mutate('review', id, signal => service.review(id, { signal })) },
    resolve(id, notes) { return mutate('resolve', id, signal => service.resolve(id, notes, { signal })) },
    dispose() {
      active = false
      mutations.cancel()
      currentRead?.cancel(); currentRead = null
      state = initial
      for (const listener of listeners) listener()
    },
  }
}
