export function readExplorerQuery(params) {
  const requested = Number(params.get('page'))
  const sortBy = params.get('sortBy') ?? 'id'
  return {
    query: params.get('q') ?? '', governorate: params.get('governorate') ?? '',
    governorateId: params.get('governorateId') ?? '',
    localAuthorityId: params.get('localAuthorityId') ?? '',
    page: Number.isSafeInteger(requested) && requested > 0 ? requested : 1,
    sortBy: ['createdAt', 'updatedAt'].includes(sortBy) ? 'id' : sortBy, sortDir: params.get('sortDir')?.toLowerCase() ?? 'asc',
  }
}

export function updateExplorerQuery(params, name, value) {
  const next = new URLSearchParams(params)
  if (value === '' || value == null) next.delete(name)
  else next.set(name, String(value))
  if (name === 'governorateId') {
    next.delete('governorate')
    next.delete('localAuthorityId')
  }
  if (name === 'governorate') next.delete('governorateId')
  if (name !== 'page') next.delete('page')
  return next
}

export function clearExplorerQuery(params) {
  const next = new URLSearchParams(params)
  for (const name of ['q', 'governorate', 'governorateId', 'localAuthorityId', 'page', 'sortBy', 'sortDir']) next.delete(name)
  return next
}
