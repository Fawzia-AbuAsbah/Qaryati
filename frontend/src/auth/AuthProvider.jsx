import { useEffect, useMemo, useState, useSyncExternalStore } from 'react'
import { createAuthSession } from '../services/authSession.js'
import { AuthContext } from './authContext.js'

export function AuthProvider({ children }) {
  const [store] = useState(() => createAuthSession())
  const snapshot = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  useEffect(() => {
    const check = () => { if (document.visibilityState !== 'hidden') store.checkExpiration() }
    window.addEventListener('focus', check)
    document.addEventListener('visibilitychange', check)
    return () => {
      window.removeEventListener('focus', check)
      document.removeEventListener('visibilitychange', check)
      store.dispose()
    }
  }, [store])
  const value = useMemo(() => ({
    ...snapshot, login: store.login, logout: store.logout, cancelLogin: store.cancelLogin,
    refreshProfile: store.refreshProfile, request: store.request, getSnapshot: store.getSnapshot,
  }), [snapshot, store])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
