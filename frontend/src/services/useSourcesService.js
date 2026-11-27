import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../auth/useAuth.js'
import { createSourcesService } from './sourcesService.js'
import { createSourceMutation } from './sourceMutation.js'

export function useSourcesService() {
  const { request, getSnapshot, user, expiresAt } = useAuth()
  // A new verified profile/session creates a new service and invalidates loaders.
  return useMemo(() => {
    if (!user || !expiresAt) return null
    return createSourcesService({ request, getSession: getSnapshot })
  }, [request, getSnapshot, user, expiresAt])
}

export function useSourceMutation(service) {
  const runner = useMemo(() => createSourceMutation(service.isCurrent), [service])
  const [result, setResult] = useState(null)
  useEffect(() => () => runner.cancel(), [runner])
  const current = result?.runner === runner ? result : { pending: false, error: null }
  return {
    ...current,
    submit(load, onSuccess) {
      try {
        runner.run(load, {
          onSuccess,
          onError: error => setResult({ runner, pending: false, error }),
          onSettled: () => setResult(value => value?.runner === runner ? { ...value, pending: false } : value),
        })
        setResult({ runner, pending: true, error: null })
      } catch (error) {
        if (error.code !== 'MUTATION_IN_PROGRESS') setResult({ runner, pending: false, error })
      }
    },
  }
}
