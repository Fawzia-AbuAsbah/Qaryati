import { createLatestExplorerRequest } from './explorerRequest.js'
import { createSourceMutation } from './sourceMutation.js'
import { SourceError } from './sourceContracts.js'
import { toBackendId } from './villageAdapters.js'

const initial = Object.freeze({ linksStatus: 'idle', links: null, linksError: null,
  catalogStatus: 'idle', catalog: null, catalogError: null,
  mutationStatus: 'idle', mutationError: null, created: null })

export function createVillageSourcesState(service, villageId) {
  toBackendId(villageId)
  const linksReads = createLatestExplorerRequest(), catalogReads = createLatestExplorerRequest(), listeners = new Set()
  let state = initial, active = false, linksTask = null, catalogTask = null
  const live = () => active && service.isCurrent() && service.permissions().read
  const mutations = createSourceMutation(live)
  function publish(patch) {
    if (!live()) return
    state = Object.freeze({ ...state, ...patch })
    for (const listener of listeners) listener()
  }
  function loadLinks() {
    if (!service.isCurrent() || !service.permissions().read) return null
    active = true
    publish({ linksStatus: 'loading', links: null, linksError: null })
    linksTask = linksReads.run(signal => service.villageSources(villageId, { signal }), {
      onSuccess: links => publish({ linksStatus: 'ready', links: Object.freeze([...links]), linksError: null }),
      onError: linksError => publish({ linksStatus: 'error', links: null, linksError }),
    })
    return linksTask
  }
  function loadCatalog() {
    if (!service.isCurrent() || !service.permissions().read) return null
    active = true
    publish({ catalogStatus: 'loading', catalog: null, catalogError: null })
    catalogTask = catalogReads.run(signal => service.list({ signal }), {
      onSuccess: catalog => publish({ catalogStatus: 'ready', catalog: Object.freeze([...catalog]), catalogError: null }),
      onError: catalogError => publish({ catalogStatus: 'error', catalog: null, catalogError }),
    })
    return catalogTask
  }
  return {
    getSnapshot: () => live() ? state : initial,
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener) },
    load() {
      const links = loadLinks(), catalog = loadCatalog()
      return { done: Promise.all([links?.done, catalog?.done]) }
    },
    loadLinks, loadCatalog,
    submit(sourceId, claimNote) {
      if (!live()) return null
      const access = service.permissions()
      if (!access.write) { publish({ mutationStatus: 'error', mutationError: new SourceError(access.reason ?? 'SOURCE_FORBIDDEN') }); return null }
      try {
        try { toBackendId(sourceId) } catch { throw new SourceError('SOURCE_VALIDATION', { sourceId: 'اختر معرّف مصدر صالحًا من القائمة.' }) }
        if (state.linksStatus !== 'ready' || state.catalogStatus !== 'ready'
          || !state.catalog.some(source => source.id === sourceId)
          || (claimNote !== null && typeof claimNote !== 'string')) {
          throw new SourceError('SOURCE_VALIDATION', { sourceId: 'اختر مصدرًا من القائمة المحمّلة.' })
        }
        if (state.links.some(link => link.sourceId === sourceId)) throw new SourceError('SOURCE_ALREADY_LINKED')
        const task = mutations.run(signal => service.link(villageId, sourceId, claimNote, { signal }), {
          onSuccess: created => {
            // Confirmed creation is separate from the following GET. A refresh
            // failure must never become a POST failure or trigger another POST.
            publish({ mutationStatus: 'success', mutationError: null, created,
              linksStatus: 'loading', links: null, linksError: null })
            loadLinks()
          },
          onError: mutationError => publish({ mutationStatus: 'error', mutationError }),
        })
        publish({ mutationStatus: 'pending', mutationError: null })
        return task
      } catch (mutationError) {
        if (mutationError.code !== 'MUTATION_IN_PROGRESS') publish({ mutationStatus: 'error', mutationError })
        return null
      }
    },
    dispose() {
      active = false
      mutations.cancel()
      linksTask?.cancel(); catalogTask?.cancel()
      linksTask = catalogTask = null
      state = initial
      for (const listener of listeners) listener()
    },
  }
}
