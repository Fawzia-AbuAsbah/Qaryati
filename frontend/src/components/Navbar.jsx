import { Grid2X2, House, Map, Sprout } from 'lucide-react'
import { Link, NavLink } from 'react-router-dom'

const links = [
  { to: '/', label: 'الرئيسية', icon: House },
  { to: '/villages', label: 'استكشاف القرى', icon: Grid2X2 },
  { to: '/map', label: 'الخريطة', icon: Map },
]

export default function Navbar() {
  return (
    <header className="site-header">
      <div className="shell header-inner">
        <Link className="brand" to="/"><span className="brand-icon"><Sprout aria-hidden="true" /></span> قريتي</Link>
        <nav className="site-nav" aria-label="التنقل الرئيسي">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/'}><Icon size={17} aria-hidden="true" />{label}</NavLink>
          ))}
        </nav>
      </div>
    </header>
  )
}
