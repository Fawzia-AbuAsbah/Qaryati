import { lazy, Suspense, useEffect, useLayoutEffect } from 'react'
import { BrowserRouter, Link, matchPath, Route, Routes, useLocation } from 'react-router-dom'
import Footer from './components/Footer.jsx'
import Navbar from './components/Navbar.jsx'
import PageState from './components/PageState.jsx'
import HomePage from './pages/HomePage.jsx'
import VillageExplorer from './pages/VillageExplorer.jsx'
import VillageDetails from './pages/VillageDetails.jsx'
import LoginPage from './pages/LoginPage.jsx'
import { AuthProvider } from './auth/AuthProvider.jsx'
import { RequireAuth } from './auth/RequireAuth.jsx'
import SourcesPage from './pages/SourcesPage.jsx'
import DataConflictsPage from './pages/DataConflictsPage.jsx'
import './styles/site.css'
import './styles/auth.css'
import './styles/sources.css'

const MapPage = lazy(() => import('./pages/MapPage.jsx'))

const pageTitles = [
  ['/', 'الرئيسية'],
  ['/villages', 'استكشاف القرى'],
  ['/villages/:id', 'تفاصيل القرية'],
  ['/map', 'خريطة القرى'],
  ['/login', 'تسجيل الدخول'],
  ['/sources', 'المصادر'],
  ['/data-conflicts', 'تعارضات البيانات'],
]

function Layout() {
  const { pathname } = useLocation()
  const pageTitle = pageTitles.find(([path]) => matchPath(path, pathname))?.[1] ?? 'الصفحة غير موجودة'

  useLayoutEffect(() => {
    document.title = `${pageTitle} | قريتي`
  }, [pageTitle])

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
            <Route path="/login" element={<LoginPage />} />
            <Route path="/sources" element={<RequireAuth><SourcesPage /></RequireAuth>} />
            <Route path="/data-conflicts" element={<RequireAuth><DataConflictsPage /></RequireAuth>} />
            <Route path="*" element={<PageState title="الصفحة غير موجودة"><Link className="button primary" to="/">العودة إلى الرئيسية</Link></PageState>} />
          </Routes>
        </Suspense>
      </main>
      <Footer />
    </>
  )
}

export default function App() {
  return <AuthProvider><BrowserRouter><Layout /></BrowserRouter></AuthProvider>
}
