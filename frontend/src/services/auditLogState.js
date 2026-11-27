import { createLatestExplorerRequest } from './explorerRequest.js'

const initial = Object.freeze({ status: 'idle', items: null, error: null })

// One read-only controller per (service, query). A filter change is a new controller;
// disposing it cancels its request and redacts protected audit data.
export function createAuditLogState(service, query) {
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
      publish({ status: 'loading', items: null, error: null })
      current = reads.run(signal => service.search(query, { signal }), {
        onSuccess: items => publish({ status: 'ready', items: Object.freeze([...items]), error: null }),
        onError: error => publish({ status: 'error', items: null, error }),
      })
      return current
    },
    dispose() {
      active = false
      current?.cancel(); current = null
      state = initial
      for (const listener of listeners) listener()
    },
  }
}
