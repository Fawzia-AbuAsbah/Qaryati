import { createLatestExplorerRequest } from './explorerRequest.js'

const initial = Object.freeze({ status: 'idle', result: null, error: null })

export function createPopulationState(service, query) {
  const reads = createLatestExplorerRequest(), listeners = new Set()
  let state = initial, active = false, current = null
  const live = () => active && service.isCurrent()
  function publish(patch) {
    if (!live()) return
    state = Object.freeze({ ...state, ...patch })
    for (const listener of listeners) listener()
  }
  return {
    getSnapshot: () => live() ? state : initial,
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener) },
    load() {
      if (!service.isCurrent() || !service.permissions().read) return null
      active = true
      publish({ status: 'loading', result: null, error: null })
      current = reads.run(signal => service.list(query, { signal }), {
        onSuccess: result => publish({ status: 'ready', result, error: null }),
        onError: error => publish({ status: 'error', result: null, error }),
      })
      return current
    },
    dispose() {
      active = false
      current?.cancel(); current = null; state = initial
      for (const listener of listeners) listener()
    },
  }
}
