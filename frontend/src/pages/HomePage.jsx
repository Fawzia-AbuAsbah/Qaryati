import { Compass, Map, Search } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import PageState from '../components/PageState.jsx'
import VillageCard from '../components/VillageCard.jsx'
import { homeSearchLocation, villageHomeService } from '../services/villageHomeService.js'
import { useExplorerData } from '../services/useExplorerData.js'
import { detailsErrorMessage } from '../services/villageDetailsState.js'

const loadFeatured = signal => villageHomeService.getFeatured({ signal })

export default function HomePage() {
  const navigate = useNavigate()
  const { data, ...state } = useExplorerData(loadFeatured)

  function search(event) {
    event.preventDefault()
    navigate(homeSearchLocation(new FormData(event.currentTarget).get('q')))
  }

  return (
    <>
      <section className="hero">
        <div className="shell hero-inner">
          <div>
            <p className="eyebrow"><Compass size={18} aria-hidden="true" /> دليلك إلى القرى الفلسطينية</p>
            <h1>لكل قرية <span>حكاية.</span></h1>
            <p className="hero-description">اكتشف قرى فلسطين وتعرّف إلى أماكنها وتراثها، من البيوت الحجرية إلى مدرّجات الزيتون.</p>
            <form className="hero-search" role="search" onSubmit={search}>
              <label className="sr-only" htmlFor="home-search">اسم القرية</label>
              <Search size={21} aria-hidden="true" />
              <input id="home-search" name="q" type="search" maxLength={villageHomeService.mode === 'api' ? 180 : undefined} placeholder="ابحث عن قرية…" />
              <button className="button primary" type="submit">بحث</button>
            </form>
            <div className="actions">
              <Link className="button primary" to="/villages"><Compass size={18} aria-hidden="true" /> استكشف القرى</Link>
              <Link className="button outline" to="/map"><Map size={18} aria-hidden="true" /> تصفّح الخريطة</Link>
            </div>
          </div>
        </div>
        <span className="hero-credit">مشهد توضيحي</span>
      </section>
      <section className="shell section" aria-labelledby="featured-title">
        <div className="section-heading">
          <div><h2 id="featured-title">قرى مختارة</h2><p>ابدأ رحلتك من إحدى هذه القرى.</p></div>
          <Link className="button outline" to="/villages">عرض جميع القرى</Link>
        </div>
        {data?.status === 'unavailable' ? <p className="notice" role="status">القرى المختارة غير متاحة من الخدمة حاليًا.</p> : data?.items.length ? (
          <div className="village-grid">{data.items.map((village) => <VillageCard key={village.id} village={village} />)}</div>
        ) : <PageState {...state} title="لا توجد قرى مختارة حاليًا" message={state.error ? detailsErrorMessage(state.error) : undefined} />}
      </section>
    </>
  )
}
