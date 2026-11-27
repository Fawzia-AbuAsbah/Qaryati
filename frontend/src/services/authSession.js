import { authService, AuthError, loginValidation, retryDeadline } from './authService.js'

export function createAuthSession({ service = authService, now = Date.now, schedule = setTimeout, cancelSchedule = clearTimeout } = {}) {
  let token = null, generation = 0, timer = null, loginOperation = null, profileOperation = null
  let snapshot = Object.freeze({ status: 'anonymous', user: null, expiresAt: null, error: null, reason: null, checking: false, rolesStale: false, retryAt: null })
  const listeners = new Set(), operations = new Set()

  function publish(patch) {
    snapshot = Object.freeze({ ...snapshot, ...patch })
    for (const listener of listeners) listener()
  }
  function clearTimer() {
    if (timer !== null) cancelSchedule(timer)
    timer = null
  }
  function terminate(reason = null, error = null) {
    generation++
    clearTimer()
    token = null
    const code = reason === 'expired' ? 'SESSION_EXPIRED' : 'AUTH_CANCELLED'
    for (const operation of operations) {
      operation.controller.abort(new AuthError(code))
      operation.finish()
    }
    operations.clear()
    loginOperation = profileOperation = null
    publish({ status: 'anonymous', user: null, expiresAt: null, error, reason, checking: false, rolesStale: false })
  }
  function checkExpiration() {
    if (token && snapshot.expiresAt <= now()) { terminate('expired'); return true }
    return false
  }
  function armExpiration() {
    clearTimer()
    if (!token || checkExpiration()) return
    // Browsers cap timeout delays; long verified durations are scheduled in chunks.
    timer = schedule(() => { timer = null; if (!checkExpiration()) armExpiration() }, Math.min(snapshot.expiresAt - now(), 2147483647))
  }
  function operation(signal) {
    const controller = new AbortController()
    const abort = () => controller.abort(signal.reason)
    if (signal?.aborted) abort()
    else signal?.addEventListener('abort', abort, { once: true })
    const current = {
      controller, generation,
      finish() { signal?.removeEventListener('abort', abort); operations.delete(current) },
    }
    operations.add(current)
    return current
  }
  function ensureCurrent(current) {
    checkExpiration()
    current.controller.signal.throwIfAborted()
    if (current.generation !== generation) throw new AuthError('AUTH_CANCELLED')
  }
  function requireSession() {
    checkExpiration()
    if (!token || snapshot.status !== 'authenticated') throw new AuthError('AUTH_REQUIRED')
  }

  async function login(credentials, { signal } = {}) {
    signal?.throwIfAborted()
    if (loginOperation) throw new AuthError('LOGIN_IN_PROGRESS')
    if (snapshot.retryAt > now()) throw new AuthError('LOGIN_THROTTLED')
    if (Object.keys(loginValidation(credentials)).length) {
      const error = new AuthError('LOGIN_VALIDATION')
      publish({ error })
      throw error
    }
    terminate()
    const current = operation(signal)
    loginOperation = current
    publish({ status: 'authenticating', error: null, reason: null, retryAt: null })
    const startedAt = now()
    try {
      const response = await service.login(credentials, { signal: current.controller.signal })
      ensureCurrent(current)
      const expiresAt = startedAt + response.expiresIn * 1000
      if (!Number.isSafeInteger(expiresAt)) throw new AuthError('INVALID_AUTH_RESPONSE')
      if (expiresAt <= now()) throw new AuthError('SESSION_EXPIRED')
      token = response.accessToken
      publish({ expiresAt })
      armExpiration()
      ensureCurrent(current)
      const user = await service.getProfile(token, { signal: current.controller.signal })
      ensureCurrent(current)
      publish({ status: 'authenticated', user, error: null, checking: false, rolesStale: false })
      return user
    } catch (error) {
      if (current.generation === generation && loginOperation === current) {
        const aborted = current.controller.signal.aborted
        const retryAt = error?.status === 429 ? retryDeadline(error.retryAfter, now()) : snapshot.retryAt
        terminate(aborted ? 'cancelled' : error?.code === 'SESSION_EXPIRED' ? 'expired' : null, aborted ? null : error)
        publish({ retryAt })
      }
      throw error
    } finally {
      current.finish()
      if (loginOperation === current) loginOperation = null
    }
  }

  async function refreshProfile({ signal } = {}) {
    requireSession()
    profileOperation?.controller.abort(new AuthError('AUTH_CANCELLED'))
    const current = operation(signal), capturedToken = token
    profileOperation = current
    publish({ checking: true, error: null })
    try {
      current.controller.signal.throwIfAborted()
      const user = await service.getProfile(capturedToken, { signal: current.controller.signal })
      ensureCurrent(current)
      publish({ user, checking: false, error: null, rolesStale: false })
      return user
    } catch (error) {
      if (current.generation === generation && profileOperation === current) {
        if (!current.controller.signal.aborted && error?.status === 401) terminate('unauthorized', error)
        // Preserve identity on transient failure, but do not treat the retained
        // role snapshot as current. Cancellation cannot clear a previous failure.
        else publish({ checking: false, rolesStale: snapshot.rolesStale || !current.controller.signal.aborted,
          error: current.controller.signal.aborted ? null : error })
      }
      throw error
    } finally {
      current.finish()
      if (profileOperation === current) profileOperation = null
    }
  }

  async function request(path, options = {}) {
    requireSession()
    const current = operation(options.signal), capturedToken = token
    try {
      current.controller.signal.throwIfAborted()
      const data = await service.requestWithToken(capturedToken, path, { ...options, signal: current.controller.signal })
      ensureCurrent(current)
      return data
    } catch (error) {
      // Only an authentication failure for this generation invalidates this session.
      if (current.generation === generation && !current.controller.signal.aborted && error?.status === 401) terminate('unauthorized', error)
      throw error
    } finally { current.finish() }
  }

  return {
    getSnapshot: () => snapshot,
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener) },
    login, refreshProfile, request, checkExpiration,
    logout() { terminate('logout') },
    cancelLogin() { if (loginOperation && snapshot.status === 'authenticating') terminate('cancelled') },
    dispose() { terminate(); publish({ retryAt: null }) },
  }
}
