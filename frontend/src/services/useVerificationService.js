import { useMemo } from 'react'
import { useAuth } from '../auth/useAuth.js'
import { createVerificationService } from './verificationService.js'

export function useVerificationService() {
  const { request, getSnapshot, user, expiresAt } = useAuth()
  return useMemo(() => {
    if (!user || !expiresAt) return null
    return createVerificationService({ request, getSession: getSnapshot })
  }, [request, getSnapshot, user, expiresAt])
}
