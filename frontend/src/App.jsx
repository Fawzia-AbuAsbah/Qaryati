import { lazy, Suspense, useEffect } from 'react'
import { BrowserRouter, Link, Route, Routes, useLocation } from 'react-router-dom'
import Footer from './components/Footer.jsx'
import Navbar from './components/Navbar.jsx'
import PageState from './components/PageState.jsx'
import HomePage from './pages/HomePage.jsx'
import VillageExplorer from './pages/VillageExplorer.jsx'
import VillageDetails from './pages/VillageDetails.jsx'
import './styles/site.css'

const MapPage = lazy(() => import('./pages/MapPage.jsx'))

function Layout() {
  const { pathname } = useLocation()

  useEffect(() => {
    window.scrollTo(0, 0)
    document.getElementById('main-content')?.focus({ preventScroll: true })
  }, [pathname])

  return (
    <>
      <a className="skip-link" href="#main-content">انتقل إلى المحتوى</a>
      <Navbar />
      <main id="main-content" tabIndex={-1}>
        <Suspense fallback={<PageState loading />}>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/villages" element={<VillageExplorer />} />
            <Route path="/villages/:id" element={<VillageDetails />} />
            <Route path="/map" element={<MapPage />} />
            <Route path="*" element={<PageState title="الصفحة غير موجودة"><Link className="button primary" to="/">العودة إلى الرئيسية</Link></PageState>} />
          </Routes>
        </Suspense>
      </main>
      <Footer />
    </>
  )
}

export default function App() {
  return <BrowserRouter><Layout /></BrowserRouter>
}
