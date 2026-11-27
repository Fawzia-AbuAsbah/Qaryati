import { Grid2X2, House, Map, Sprout } from 'lucide-react'
import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.js'

const links = [
  { to: '/', label: 'الرئيسية', icon: House },
  { to: '/villages', label: 'استكشاف القرى', icon: Grid2X2 },
  { to: '/map', label: 'الخريطة', icon: Map },
]

export default function Navbar() {
  const auth = useAuth()
  return (
    <header className="site-header">
      <div className="shell header-inner">
        <Link className="brand" to="/"><span className="brand-icon"><Sprout aria-hidden="true" /></span> قريتي</Link>
        <nav className="site-nav" aria-label="التنقل الرئيسي">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/'}><Icon size={17} aria-hidden="true" />{label}</NavLink>
          ))}
        </nav>
        <div className="auth-controls">
          {auth.status === 'authenticated' ? <>
            <NavLink className="button outline" to="/sources">المصادر</NavLink>
            <NavLink className="button outline" to="/data-conflicts">تعارضات البيانات</NavLink>
            <NavLink className="button outline" to="/audit-logs">سجل التدقيق</NavLink>
            <span>مرحبًا، <bdi>{auth.user.username}</bdi></span>
            <button className="button outline" type="button" onClick={() => auth.logout()}>تسجيل الخروج</button>
          </> : <NavLink className="button outline" to="/login">تسجيل الدخول</NavLink>}
        </div>
      </div>
    </header>
  )
}
