import { useEffect, useRef, useState } from 'react'
import { Eye, EyeOff, LogIn } from 'lucide-react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.js'
import { authErrorMessage, loginValidation } from '../services/authService.js'
import { safeAuthReturnTo } from '../services/authAccess.js'

export default function LoginPage() {
  const auth = useAuth()
  const location = useLocation()
  const [username, setUsername] = useState(''), [password, setPassword] = useState('')
  const [visible, setVisible] = useState(false), [fields, setFields] = useState({})
  const [clock, setClock] = useState(() => Date.now())
  const submitting = useRef(false), errorBox = useRef(null)
  const { cancelLogin, retryAt, error, reason } = auth
  const remaining = Math.max(0, Math.ceil(((retryAt ?? 0) - clock) / 1000))
  const pending = auth.status === 'authenticating'
  const fieldErrors = Boolean(fields.username || fields.password)
  useEffect(() => () => cancelLogin(), [cancelLogin])
  useEffect(() => { if (!pending) submitting.current = false }, [pending])
  useEffect(() => {
    if (!retryAt) return
    const timer = setInterval(() => setClock(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [retryAt])
  useEffect(() => { if (error || fieldErrors) errorBox.current?.focus() }, [error, fieldErrors])

  async function submit(event) {
    event.preventDefault()
    if (submitting.current || pending || remaining > 0) return
    const credentials = { username, password }, errors = loginValidation(credentials)
    setFields(errors)
    if (errors.username || errors.password) return
    const attempt = Symbol('login attempt')
    submitting.current = attempt
    setPassword('')
    setVisible(false)
    try { await auth.login(credentials) } catch {
      if (submitting.current === attempt) setClock(Date.now())
    } finally {
      if (submitting.current === attempt) submitting.current = false
    }
  }

  if (auth.status === 'authenticated') return <Navigate to={safeAuthReturnTo(location.state?.from)} replace />
  const message = error ? authErrorMessage(error, remaining) : reason === 'expired' ? authErrorMessage({ code: 'SESSION_EXPIRED' }) : null
  return <>
    <div className="page-banner"><div className="shell"><h1>تسجيل الدخول</h1><p>أدخل بيانات حسابك للوصول إلى الخدمات المتاحة لك.</p></div></div>
    <section className="shell section auth-section" aria-label="تسجيل الدخول">
      <form className="detail-panel auth-form" onSubmit={submit} noValidate aria-busy={pending}>
        {(message || fieldErrors) && <div className="auth-error" role="alert" tabIndex={-1} ref={errorBox} id="login-error">
          {message ?? 'تحقق من الحقول المطلوبة أدناه.'}
        </div>}
        <label htmlFor="login-username">اسم المستخدم</label>
        <span className="field"><input id="login-username" name="username" dir="auto" value={username} autoComplete="username" autoCapitalize="none" spellCheck={false} required disabled={pending}
          aria-invalid={Boolean(fields.username)} aria-describedby={fields.username ? 'login-username-error' : undefined}
          onChange={event => { setUsername(event.target.value); setFields({}) }} /></span>
        {fields.username && <p className="auth-field-error" id="login-username-error">{fields.username}</p>}
        <label htmlFor="login-password">كلمة المرور</label>
        <span className="field"><input id="login-password" name="password" dir="ltr" type={visible ? 'text' : 'password'} value={password} autoComplete="current-password" required disabled={pending}
          aria-invalid={Boolean(fields.password)} aria-describedby={fields.password ? 'login-password-error' : undefined}
          onChange={event => { setPassword(event.target.value); setFields({}) }} />
          <button className="auth-visibility" type="button" disabled={pending} aria-label={visible ? 'إخفاء كلمة المرور' : 'إظهار كلمة المرور'} aria-pressed={visible} onClick={() => setVisible(value => !value)}>
            {visible ? <EyeOff size={20} aria-hidden="true" /> : <Eye size={20} aria-hidden="true" />}
          </button>
        </span>
        {fields.password && <p className="auth-field-error" id="login-password-error">{fields.password}</p>}
        <button className="button primary" type="submit" disabled={pending || remaining > 0}><LogIn size={18} aria-hidden="true" />{pending ? 'جارٍ تسجيل الدخول…' : 'تسجيل الدخول'}</button>
        {pending && <p role="status">جارٍ التحقق من بيانات الدخول…</p>}
        {remaining > 0 && <p role="status">{authErrorMessage({ code: 'LOGIN_THROTTLED' }, remaining)}</p>}
        {pending && <button className="button outline" type="button" onClick={() => auth.cancelLogin()}>إلغاء</button>}
        <Link to="/">العودة إلى الرئيسية</Link>
      </form>
    </section>
  </>
}
