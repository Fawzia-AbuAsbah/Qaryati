import { createLatestExplorerRequest } from './explorerRequest.js'
import { createSourceMutation } from './sourceMutation.js'
import { toBackendId } from './villageAdapters.js'
import { VerificationError } from './verificationContracts.js'

const initial = Object.freeze({ historyStatus: 'idle', records: null, historyError: null, mutationStatus: 'idle', mutationError: null, created: null })

export function createVerificationPanelState(service, villageId) {
  toBackendId(villageId)
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
    if (!service.isCurrent() || !service.permissions().read) return null
    active = true
    publish({ historyStatus: 'loading', records: null, historyError: null })
    currentRead = reads.run(signal => service.list(villageId, { signal }), {
      onSuccess: records => publish({ historyStatus: 'ready', records: Object.freeze([...records]), historyError: null }),
      onError: historyError => publish({ historyStatus: 'error', records: null, historyError }),
    })
    return currentRead
  }
  return {
    getSnapshot: () => live() ? state : initial,
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener) },
    load,
    submit(decision, reason) {
      if (!live()) return null
      const access = service.permissions()
      if (!access.create) { publish({ mutationStatus: 'error', mutationError: new VerificationError(access.reason) }); return null }
      try {
        const task = mutations.run(signal => service.create(villageId, decision, reason, { signal }), {
          onSuccess: created => {
            // A history-refresh error must never turn a confirmed POST success
            // into a failed mutation or prompt another automatic POST.
            publish({ mutationStatus: 'success', mutationError: null, created, historyStatus: 'loading', records: null, historyError: null })
            load()
          },
          onError: mutationError => publish({ mutationStatus: 'error', mutationError }),
        })
        publish({ mutationStatus: 'pending', mutationError: null })
        return task
      } catch (error) {
        if (error.code !== 'MUTATION_IN_PROGRESS') publish({ mutationStatus: 'error', mutationError: error })
        return null
      }
    },
    dispose() {
      active = false
      mutations.cancel()
      currentRead?.cancel(); currentRead = null
      state = initial
      for (const listener of listeners) listener()
    },
  }
}
