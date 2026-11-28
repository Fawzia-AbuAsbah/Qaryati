import { useMemo } from 'react'
import { useAuth } from '../auth/useAuth.js'
import { createPopulationService } from './populationService.js'

export function usePopulationService() {
  const { request, getSnapshot, user, expiresAt } = useAuth()
  return useMemo(() => {
    if (!user || !expiresAt) return null
    return createPopulationService({ request, getSession: getSnapshot })
  }, [request, getSnapshot, user, expiresAt])
}
