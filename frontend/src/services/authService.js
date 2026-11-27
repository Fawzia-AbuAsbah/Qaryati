import { apiClient } from './apiClient.js'
import { validRoles } from './authRoles.js'

export class AuthError extends Error {
  constructor(code) {
    super(code)
    this.name = 'AuthError'
    this.code = code
  }
}

export function loginValidation(credentials) {
  const { username, password } = credentials ?? {}
  const errors = {}
  if (typeof username !== 'string' || !username.trim()) errors.username = 'أدخل اسم المستخدم.'
  if (typeof password !== 'string' || !password.trim()) errors.password = 'أدخل كلمة المرور.'
  return errors
}

function bearer(token) {
  if (typeof token !== 'string' || !token || /\s/.test(token)) throw new AuthError('INVALID_AUTH_RESPONSE')
  return token
}

export function createAuthService({ request = apiClient } = {}) {
  return {
    async login(credentials, { signal } = {}) {
      if (Object.keys(loginValidation(credentials)).length) throw new AuthError('LOGIN_VALIDATION')
      const { username, password } = credentials
      const dto = await request('/auth/login', { method: 'POST', body: { username, password }, signal })
      signal?.throwIfAborted()
      if (!dto || dto.tokenType !== 'Bearer' || !Number.isSafeInteger(dto.expiresIn) || dto.expiresIn <= 0) {
        throw new AuthError('INVALID_AUTH_RESPONSE')
      }
      return { accessToken: bearer(dto.accessToken), tokenType: 'Bearer', expiresIn: dto.expiresIn }
    },
    async getProfile(token, { signal } = {}) {
      const dto = await request('/auth/me', { token: bearer(token), signal })
      signal?.throwIfAborted()
      if (!dto || typeof dto.username !== 'string' || !dto.username.trim()) throw new AuthError('INVALID_AUTH_RESPONSE')
      // Invalid role data closes role-dependent actions without inventing a role
      // or removing the backend's explicitly authenticated-only read access.
      const rolesVerified = validRoles(dto.roles)
      const roles = rolesVerified ? Object.freeze([...dto.roles]) : null
      return Object.freeze({ username: dto.username, roles, rolesVerified })
    },
    requestWithToken(token, path, options = {}) {
      return request(path, { ...options, token: bearer(token) })
    },
  }
}

export const authService = createAuthService()

export function retryDeadline(value, now = Date.now()) {
  if (typeof value !== 'string' || !value.trim()) return null
  const header = value.trim()
  if (/^\d+$/.test(header)) {
    const seconds = Number(header), deadline = now + seconds * 1000
    return Number.isSafeInteger(seconds) && Number.isSafeInteger(deadline) ? deadline : null
  }
  if (!/^[A-Za-z]{3}, \d{2} [A-Za-z]{3} \d{4} \d{2}:\d{2}:\d{2} GMT$/.test(header)) return null
  const date = Date.parse(header)
  return Number.isFinite(date) ? Math.max(now, date) : null
}

export function authErrorMessage(error, remainingSeconds = 0) {
  if (error?.status === 429 || error?.code === 'LOGIN_THROTTLED') {
    return remainingSeconds > 0 ? `محاولات كثيرة. حاول مجددًا بعد ${new Intl.NumberFormat('ar').format(remainingSeconds)} ثانية.` : 'محاولات كثيرة. يرجى المحاولة لاحقًا.'
  }
  if (error?.status === 401) return 'تعذّر تسجيل الدخول أو التحقق من الجلسة. تحقق من بيانات الدخول وحاول مجددًا.'
  if (error?.status === 403) return 'لا تتوفر صلاحية لتنفيذ هذا الطلب.'
  if (error?.status === 400 || error?.code === 'LOGIN_VALIDATION') return 'تحقق من اسم المستخدم وكلمة المرور.'
  if (error?.code === 'SESSION_EXPIRED') return 'انتهت صلاحية الجلسة. سجّل الدخول مجددًا.'
  if (error?.code === 'LOGIN_IN_PROGRESS') return 'جارٍ تسجيل الدخول. انتظر اكتمال الطلب.'
  if (error?.kind === 'network') return 'تعذّر الاتصال بالخدمة. تحقق من الاتصال وحاول مجددًا.'
  return 'تعذّر إكمال طلب تسجيل الدخول أو التحقق من الجلسة. حاول لاحقًا.'
}
