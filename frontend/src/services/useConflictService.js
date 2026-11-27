import { useMemo } from 'react'
import { useAuth } from '../auth/useAuth.js'
import { createConflictService } from './conflictService.js'

export function useConflictService() {
  const { request, getSnapshot, user, expiresAt } = useAuth()
  // A new verified profile/session creates a new service and invalidates loaders.
  return useMemo(() => {
    if (!user || !expiresAt) return null
    return createConflictService({ request, getSession: getSnapshot })
  }, [request, getSnapshot, user, expiresAt])
}
