import { Sprout } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="shell footer-inner">
        <Link to="/" className="brand"><span className="brand-icon"><Sprout aria-hidden="true" /></span> قريتي</Link>
        <p>دليل رقمي للقرى الفلسطينية · نسخة تجريبية</p>
        <Link to="/map">خريطة القرى</Link>
      </div>
    </footer>
  )
}
