import { useEffect, useRef, useState } from 'react'
import { Icon, control } from 'leaflet'
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet'
import { Link, useSearchParams } from 'react-router-dom'
import markerUrl from 'leaflet/dist/images/marker-icon.png'
import markerRetinaUrl from 'leaflet/dist/images/marker-icon-2x.png'
import markerShadowUrl from 'leaflet/dist/images/marker-shadow.png'
import 'leaflet/dist/leaflet.css'
import PageState from '../components/PageState.jsx'
import { hasCoordinates, listMapVillages } from '../services/villageService.js'
import { useVillageData } from '../services/useVillageData.js'

const villageIcon = new Icon({
  iconUrl: markerUrl,
  iconRetinaUrl: markerRetinaUrl,
  shadowUrl: markerShadowUrl,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
  shadowAnchor: [12, 41],
})

const markerEvents = {
  popupopen({ popup }) {
    popup.getElement()?.querySelector('.leaflet-popup-close-button')?.setAttribute('aria-label', 'إغلاق النافذة')
  },
}

function MapControls({ villages, selected }) {
  const map = useMap()
  useEffect(() => {
    const zoom = control.zoom({ zoomInTitle: 'تكبير الخريطة', zoomOutTitle: 'تصغير الخريطة' }).addTo(map)
    return () => { zoom.remove() }
  }, [map])

  useEffect(() => {
    if (selected) map.setView(selected.coordinates, 14)
    else map.fitBounds(villages.map((village) => village.coordinates), { padding: [30, 30], maxZoom: 11 })
  }, [map, selected, villages])
  return null
}

function VillageMarker({ village, selected }) {
  const marker = useRef(null)

  useEffect(() => {
    if (selected) marker.current?.openPopup()
  }, [selected])

  return (
    <Marker ref={marker} position={village.coordinates} icon={villageIcon} title={village.name} alt={village.name} eventHandlers={markerEvents}>
      <Popup>
        <div dir="rtl">
          <strong>{village.name}</strong><br />
          <Link to={`/villages/${village.id}`}>تفاصيل القرية</Link>
        </div>
      </Popup>
    </Marker>
  )
}

function VillageMap({ villages, selected }) {
  const [tileError, setTileError] = useState(false)
  return (
    <>
      {tileError && <p className="notice" role="status">تعذّر تحميل بعض أجزاء الخريطة. يمكنك متابعة تصفّح روابط القرى أدناه.</p>}
      <div className="map-frame" dir="ltr" role="region" aria-label="خريطة القرى التفاعلية">
        <MapContainer center={villages[0].coordinates} zoom={9} scrollWheelZoom={false} zoomControl={false} attributionControl={false}>
          <TileLayer url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" maxZoom={19} eventHandlers={{ tileerror: () => setTileError(true) }} />
          <MapControls villages={villages} selected={selected} />
          {villages.map((village) => <VillageMarker key={village.id} village={village} selected={selected?.id === village.id} />)}
        </MapContainer>
        <div className="map-attribution" dir="rtl">© مساهمو <a href="https://www.openstreetmap.org/copyright">أوبن ستريت ماب</a></div>
      </div>
    </>
  )
}

export default function MapPage() {
  const [params] = useSearchParams()
  const { data, ...state } = useVillageData(listMapVillages)
  const id = params.get('village')
  const villages = data?.filter(hasCoordinates) ?? []
  const selected = villages.find((village) => village.id === id)

  return (
    <>
      <div className="page-banner"><div className="shell"><h1>خريطة القرى</h1><p>اختر علامة على الخريطة لعرض اسم القرية والانتقال إلى تفاصيلها.</p></div></div>
      <section className="shell section" aria-label="مواقع القرى">
        {data && id && !selected && <p className="notice" role="status">الموقع المطلوب غير متاح على الخريطة.</p>}
        {villages.length ? <VillageMap villages={villages} selected={selected} /> : <PageState {...state} title="لا توجد مواقع متاحة للعرض" />}
        {data?.length > 0 && (
          <div className="map-index">
            <h2>روابط القرى</h2>
            <ul>{data.map((village) => (
              <li key={village.id}><Link to={`/villages/${village.id}`}>{village.name}</Link>{!hasCoordinates(village) && <span> · الموقع غير متاح</span>}</li>
            ))}</ul>
            <p>المواقع المعروضة تقريبية ومخصّصة للعرض التجريبي.</p>
          </div>
        )}
      </section>
    </>
  )
}
