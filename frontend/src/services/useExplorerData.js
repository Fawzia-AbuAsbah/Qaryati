import { useEffect, useMemo, useState } from 'react'
import { createLatestExplorerRequest } from './explorerRequest.js'

export function useExplorerData(load) {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState(null)
  const runner = useMemo(() => createLatestExplorerRequest(), [])
  useEffect(() => {
    const task = runner.run(load, {
      onSuccess: data => setResult({ load, attempt, data, error: null }),
      onError: error => setResult({ load, attempt, data: null, error }),
    })
    return task.cancel
  }, [load, attempt, runner])
  const current = result?.load === load && result.attempt === attempt ? result : null
  return { data: current?.data, error: current?.error, loading: !current, retry: () => setAttempt(value => value + 1) }
}
