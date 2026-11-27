export function readExplorerQuery(params) {
  const requested = Number(params.get('page'))
  return {
    query: params.get('q') ?? '', governorate: params.get('governorate') ?? '',
    governorateId: params.get('governorateId') ?? '',
    page: Number.isSafeInteger(requested) && requested > 0 ? requested : 1,
    sortBy: params.get('sortBy') ?? 'id', sortDir: params.get('sortDir')?.toLowerCase() ?? 'asc',
  }
}

export function updateExplorerQuery(params, name, value) {
  const next = new URLSearchParams(params)
  if (value === '' || value == null) next.delete(name)
  else next.set(name, String(value))
  if (name === 'governorateId') next.delete('governorate')
  if (name === 'governorate') next.delete('governorateId')
  if (name !== 'page') next.delete('page')
  return next
}

export function clearExplorerQuery(params) {
  const next = new URLSearchParams(params)
  for (const name of ['q', 'governorate', 'governorateId', 'page', 'sortBy', 'sortDir']) next.delete(name)
  return next
}
