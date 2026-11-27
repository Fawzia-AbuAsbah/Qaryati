import { useEffect, useState } from 'react'

const unavailable = Object.freeze({ status: 'unavailable', items: Object.freeze([]), error: null })
const idle = Object.freeze({ status: 'idle', items: Object.freeze([]), error: null })

function loading() { return { status: 'loading', items: [], error: null } }
function ready(items) { return { status: items.length ? 'ready' : 'empty', items, error: null } }
function failed(error) { return { status: 'error', items: [], error } }

function loadCatalogue(setState, task) {
  const controller = new AbortController()
  let active = true
  setState(loading())
  task(controller.signal).then(items => {
    if (active) setState(ready(items))
  }).catch(error => {
    if (active && !controller.signal.aborted) setState(failed(error))
  })
  return () => { active = false; controller.abort() }
}

// Catalogue requests are independent from village-list requests: a catalogue outage
// must not hide otherwise available public village results.
export function usePublicGeographicCatalogues({ service, governorateId = null }) {
  const [governorates, setGovernorates] = useState(unavailable)
  const [localAuthorities, setLocalAuthorities] = useState(idle)
  const apiAvailable = service?.mode === 'api' && service.available

  useEffect(() => {
    if (!apiAvailable) {
      setGovernorates(unavailable)
      return undefined
    }
    return loadCatalogue(setGovernorates, signal => service.listGovernorates({ signal }))
  }, [apiAvailable, service])

  useEffect(() => {
    if (!apiAvailable || governorateId === null || governorateId === '') {
      setLocalAuthorities(idle)
      return undefined
    }
    return loadCatalogue(setLocalAuthorities, signal => service.listLocalAuthorities({ governorateId, signal }))
  }, [apiAvailable, governorateId, service])

  return { governorates, localAuthorities }
}

export function catalogueName(catalogue, backendId) {
  return catalogue?.items?.find(item => item.backendId === backendId)?.name ?? null
}
