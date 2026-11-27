import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth.js'
import { authAccessDecision, safeAuthReturnTo } from '../services/authAccess.js'

export function RequireAuth({ children, requiredRoles = [] }) {
  const auth = useAuth()
  const location = useLocation()
  const decision = authAccessDecision(auth, { requiredRoles })
  if (decision === 'pending') return <p className="state" role="status">جارٍ التحقق من الجلسة…</p>
  if (decision === 'login') return <Navigate to="/login" replace state={{ from: safeAuthReturnTo(`${location.pathname}${location.search}${location.hash}`) }} />
  if (decision === 'roles-unavailable') return <div className="state" role="alert"><h2>هذه الوظيفة غير متاحة حاليًا</h2><p>لا يمكن التحقق من صلاحيات الوصول المطلوبة.</p></div>
  return children
}
