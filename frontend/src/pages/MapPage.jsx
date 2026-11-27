import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Icon, control } from 'leaflet'
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import markerUrl from 'leaflet/dist/images/marker-icon.png'
import markerRetinaUrl from 'leaflet/dist/images/marker-icon-2x.png'
import markerShadowUrl from 'leaflet/dist/images/marker-shadow.png'
import 'leaflet/dist/leaflet.css'
import PageState from '../components/PageState.jsx'
import { hasCoordinates, listMapVillages } from '../services/villageService.js'
import { useVillageData } from '../services/useVillageData.js'
import { useExplorerData } from '../services/useExplorerData.js'
import { villageMapService } from '../services/villageMapService.js'
import { hasMapCoordinates, mapPresentation, mapSelectionMessage, mapSelectionQuery, mapViewport, mapErrorMessage } from '../services/villageMapState.js'
import { villageDetailsService } from '../services/villageDetailsService.js'
import { villageDetailsHref } from '../services/villageDetailsState.js'

const number = new Intl.NumberFormat('ar')

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

function MapControls({ viewport }) {
  const map = useMap()
  useEffect(() => {
    const zoom = control.zoom({ zoomInTitle: 'تكبير الخريطة', zoomOutTitle: 'تصغير الخريطة' }).addTo(map)
    return () => { zoom.remove() }
  }, [map])

  useEffect(() => {
    if (viewport?.center) map.setView(viewport.center, viewport.zoom)
    else if (viewport?.bounds) map.fitBounds(viewport.bounds, viewport.options)
  }, [map, viewport])
  useEffect(() => {
    const container = map.getContainer()
    container.setAttribute('role', 'group')
    container.setAttribute('aria-label', 'خريطة القرى؛ استخدم الأسهم للتحريك ومفاتيح الجمع والطرح للتكبير والتصغير')
    container.setAttribute('aria-describedby', 'village-map-help')
    if (typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(() => map.invalidateSize({ pan: false }))
    observer.observe(container)
    return () => observer.disconnect()
  }, [map])
  return null
}

function VillageMarker({ village, selected, apiMode = false, returnTo }) {
  const marker = useRef(null)
  const name = apiMode ? village.name ?? 'اسم القرية غير متاح' : village.name
  const detailsHref = apiMode ? villageDetailsHref(village.id, villageMapService, villageDetailsService) : `/villages/${village.id}`

  useEffect(() => {
    if (selected) marker.current?.openPopup()
    else marker.current?.closePopup()
  }, [selected])

  return (
    <Marker ref={marker} position={village.coordinates} icon={villageIcon} title={name} alt={name} keyboard eventHandlers={markerEvents}>
      <Popup>
        <div dir="rtl">
          <strong>{name}</strong><br />
          {detailsHref ? <Link to={detailsHref} state={apiMode ? { mapReturnTo: returnTo } : undefined}>تفاصيل القرية</Link> : <p>تفاصيل القرية غير متاحة حاليًا.</p>}
        </div>
      </Popup>
    </Marker>
  )
}

export function MapTileStatus({ status, retry }) {
  if (status === 'loading') return <p role="status">جارٍ تحميل أجزاء الخريطة… بيانات القرى متاحة.</p>
  if (status !== 'error') return null
  return <div className="notice" role="status">
    <p>تعذّر تحميل بعض أجزاء الخريطة. بيانات القرى وروابطها ما زالت متاحة.</p>
    <button className="button outline" type="button" onClick={retry}>إعادة تحميل أجزاء الخريطة</button>
  </div>
}

function VillageMap({ villages, selected, apiMode = false, returnTo }) {
  const [tileStatus, setTileStatus] = useState('loading')
  const tiles = useRef(null)
  const markers = useMemo(() => villages.filter(hasMapCoordinates), [villages])
  const viewport = useMemo(() => mapViewport(markers, selected), [markers, selected])
  if (!viewport) return null
  function retryTiles() { setTileStatus('loading'); tiles.current?.redraw() }
  return (
    <>
      <p id="village-map-help" className="sr-only">يمكن التنقل بين العلامات بزر Tab وفتح النافذة بزر Enter. روابط تفاصيل القرى متاحة أيضًا في القائمة بعد الخريطة.</p>
      <MapTileStatus status={tileStatus} retry={retryTiles} />
      <div className="map-frame" dir="ltr" role="region" aria-label="خريطة القرى التفاعلية">
        <MapContainer id="village-map" center={viewport.center ?? markers[0].coordinates} zoom={viewport.zoom ?? 9} keyboard scrollWheelZoom={false} zoomControl={false} attributionControl={false}>
          <TileLayer ref={tiles} url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" maxZoom={19} eventHandlers={{
            loading: () => setTileStatus(status => status === 'error' ? status : 'loading'),
            tileerror: () => setTileStatus('error'),
            load: () => setTileStatus(status => status === 'error' ? status : 'ready'),
          }} />
          <MapControls viewport={viewport} />
          {markers.map((village) => <VillageMarker key={village.id} village={village} selected={selected?.id === village.id} apiMode={apiMode} returnTo={returnTo} />)}
        </MapContainer>
        <div className="map-attribution" dir="rtl">© مساهمو <a href="https://www.openstreetmap.org/copyright">أوبن ستريت ماب</a></div>
      </div>
    </>
  )
}

function MockMapPage() {
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

function ApiMapPage() {
  const [params, setParams] = useSearchParams()
  const { pathname, search } = useLocation()
  const load = useCallback(signal => villageMapService.listVillages({ signal }), [])
  const { data, ...state } = useExplorerData(load)
  let presentation = { markers: [], selected: null, selection: 'pending' }, error = state.error
  try { presentation = mapPresentation(data, params.get('village')) } catch (failure) { error = failure }
  const { markers, selected, selection } = presentation
  const ready = data && !error
  const selectionMessage = mapSelectionMessage(selection)
  const returnTo = `${pathname}${search}`
  return <>
    <div className="page-banner"><div className="shell"><h1>خريطة القرى</h1><p>اختر علامة على الخريطة لعرض اسم القرية والانتقال إلى تفاصيلها.</p></div></div>
    <section className="shell section" aria-label="مواقع القرى">
      {ready && <p className="results" role="status" data-village-total={data.total} data-marker-count={markers.length}>
        عدد القرى المتاحة للعامة: {number.format(data.total)} · العلامات ذات الإحداثيات المتاحة: {number.format(markers.length)}
      </p>}
      {selectionMessage && <p className="notice" role="status">{selectionMessage}</p>}
      {ready && params.has('village') && <button className="button outline" type="button" onClick={() => setParams(mapSelectionQuery(params, null))}>عرض جميع المواقع</button>}
      {ready && data.items.length > 0 && <p><a className="card-link" href="#village-map-index">الانتقال إلى قائمة القرى وروابط التفاصيل</a></p>}
      {ready && markers.length ? <VillageMap villages={markers} selected={selected} apiMode returnTo={returnTo} />
        : state.loading ? <p className="state" role="status">جارٍ تحميل بيانات القرى للخريطة…</p>
          : <PageState {...state} error={error} title={ready && data.items.length ? 'لا توجد مواقع متاحة للعرض' : 'لا توجد قرى متاحة للعرض'} message={error ? mapErrorMessage(error) : undefined} />}
      {ready && data.items.length > 0 && <div className="map-index" id="village-map-index" tabIndex={-1}>
        <h2>روابط القرى</h2>
        <ul>{data.items.map(village => {
          const href = villageDetailsHref(village.id, villageMapService, villageDetailsService)
          const name = village.name ?? 'اسم القرية غير متاح'
          return <li key={village.id} className="map-index-actions">
            {href ? <Link to={href} state={{ mapReturnTo: returnTo }}>{name}</Link> : <span>{name}</span>}
            {hasMapCoordinates(village) ? <button className="button outline" type="button" aria-label={`تحديد ${name} على الخريطة`} aria-controls="village-map"
              aria-pressed={selected?.id === village.id} onClick={() => setParams(mapSelectionQuery(params, village.id))}>تحديد على الخريطة</button>
              : <span> · الموقع غير متاح</span>}
          </li>
        })}</ul>
        <p>القرى التي لا تتوفر إحداثياتها مدرجة دون علامات على الخريطة.</p>
      </div>}
    </section>
  </>
}

export default function MapPage() {
  return villageMapService.mode === 'mock' ? <MockMapPage /> : <ApiMapPage />
}
