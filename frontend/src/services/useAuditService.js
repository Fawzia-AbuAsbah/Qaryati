import { useMemo } from 'react'
import { useAuth } from '../auth/useAuth.js'
import { createAuditService } from './auditService.js'

export function useAuditService() {
  const { request, getSnapshot, user, expiresAt } = useAuth()
  // A new verified profile/session creates a new service and invalidates loaders.
  return useMemo(() => {
    if (!user || !expiresAt) return null
    return createAuditService({ request, getSession: getSnapshot })
  }, [request, getSnapshot, user, expiresAt])
}
