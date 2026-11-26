import { useEffect, useState } from 'react'

export function useVillageData(load) {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState(null)

  useEffect(() => {
    let active = true
    async function request() {
      try {
        const data = await load()
        if (active) setResult({ load, attempt, data, error: false })
      } catch {
        if (active) setResult({ load, attempt, data: null, error: true })
      }
    }
    request()
    return () => { active = false }
  }, [load, attempt])

  const current = result?.load === load && result.attempt === attempt ? result : null
  return { data: current?.data, error: current?.error, loading: !current, retry: () => setAttempt((value) => value + 1) }
}
