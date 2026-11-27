import assert from 'node:assert/strict'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'
import { createVillageHomeService } from './villageHomeService.js'

// Actual JSX render tests. Replace only the asynchronous data hooks and Leaflet's
// DOM-dependent bindings; no browser, backend, listener or dependencies are added.
let server, Home, MapPage, TileStatus, hook, homeService, mapService, leaflet
test.before(async () => {
  server = await createServer({ root: fileURLToPath(new URL('../../', import.meta.url)), configFile: false, envDir: false,
    cacheDir: 'node_modules/.vite-phase57-tests', logLevel: 'silent', ssr: { noExternal: ['leaflet', 'react-leaflet'] },
    plugins: [{ name: 'home-map-render-fixtures', enforce: 'pre', load(id) {
      const path = id.replaceAll('\\', '/')
      if (path.endsWith('/src/services/useExplorerData.js')) return `
        let result = { loading: true, retry() {} };
        export function setResult(value) { result = value; }
        export function useExplorerData() { return result; }`
      if (path.endsWith('/src/services/useVillageData.js')) return `export { useExplorerData as useVillageData } from './useExplorerData.js';`
      if (path.includes('/node_modules/leaflet/') && path.endsWith('.js')) return `
        export class Icon { constructor(options) { this.options = options; } }
        export const control = { zoom() { return { addTo() { return { remove() {} }; } }; } };`
      if (path.endsWith('/react-leaflet/lib/index.js')) return `
        import { createElement } from 'react';
        export const received = { markers: [], maps: [], tiles: [] };
        export function reset() { received.markers.length = 0; received.maps.length = 0; received.tiles.length = 0; }
        export function MapContainer(props) { received.maps.push(props); return createElement('div', { id: props.id, 'data-test-map': true }, props.children); }
        export function Marker(props) { received.markers.push(props); return createElement('div', { 'data-test-marker': JSON.stringify(props.position) }, props.children); }
        export function Popup({ children }) { return createElement('div', { 'data-test-popup': true }, children); }
        export function TileLayer(props) { received.tiles.push(props); return null; }
        export function useMap() { return {}; }`
    } }, react()], server: { middlewareMode: true, hmr: false, watch: null }, optimizeDeps: { noDiscovery: true, include: [] } })
  const modules = await Promise.all([
    server.ssrLoadModule('/src/pages/HomePage.jsx'), server.ssrLoadModule('/src/pages/MapPage.jsx'),
    server.ssrLoadModule('/src/services/useExplorerData.js'), server.ssrLoadModule('/src/services/villageHomeService.js'),
    server.ssrLoadModule('/src/services/villageMapService.js'), server.ssrLoadModule('/node_modules/react-leaflet/lib/index.js'),
    server.ssrLoadModule('/src/services/villageDetailsService.js'),
  ])
  Home = modules[0].default; MapPage = modules[1].default; TileStatus = modules[1].MapTileStatus
  hook = modules[2]; homeService = modules[3].villageHomeService; mapService = modules[4].villageMapService; leaflet = modules[5]
  modules[6].villageDetailsService.mode = 'api'; modules[6].villageDetailsService.available = true
})
test.after(async () => { await server?.close() })
test.beforeEach(() => {
  homeService.mode = 'api'; mapService.mode = 'api'; mapService.available = true
  hook.setResult({ loading: true, retry() {} }); leaflet.reset()
})
const village = (id, coordinates = [31.9, 35.2], name = `Synthetic ${id}`) => ({ id: String(id), backendId: id, name, coordinates })
function render(Component, path = '/map?village=42&tag=a&tag=b', props = {}) {
  return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [path] }, createElement(Component, props)))
}
function mapData(items) { hook.setResult({ data: { items, total: items.length, complete: true }, loading: false, retry() {} }) }

test('actual API Home keeps the hero, search form and navigation without mock featured cards or statistics', () => {
  hook.setResult({ data: { status: 'unavailable', items: [] }, loading: false, retry() {} })
  const html = render(Home, '/')
  assert.match(html, /class="hero"/)
  assert.match(html, /role="search"/)
  assert.match(html, /name="q"/)
  assert.match(html, /dir="auto"/)
  assert.match(html, /maxLength="180"/i)
  assert.match(html, /href="\/villages"/)
  assert.match(html, /href="\/map"/)
  assert.match(html, /القرى المختارة غير متاحة/)
  assert.equal(html.includes('village-card'), false)
  assert.equal(html.includes('/villages/battir'), false)
  assert.equal(html.includes('عدد القرى'), false)
})
test('actual mock Home keeps the three original featured slug cards without the API input limit', async () => {
  homeService.mode = 'mock'
  hook.setResult({ data: await createVillageHomeService().getFeatured(), loading: false, retry() {} })
  const html = render(Home, '/')
  for (const id of ['battir', 'lifta', 'deir-ghassaneh']) assert.match(html, new RegExp(`href="/villages/${id}"`))
  assert.equal(html.includes('maxLength="180"'), false)
  assert.equal(html.includes('القرى المختارة غير متاحة'), false)
})
test('actual API Map passes only valid latitude/longitude pairs to Leaflet, preserves zero and counts villages separately', () => {
  mapData([village(42, [0, 0]), village(43, null), village(44, [31.9, 35.2])])
  const html = render(MapPage)
  assert.deepEqual(leaflet.received.markers.map(props => props.position), [[0, 0], [31.9, 35.2]])
  assert.equal(leaflet.received.maps.length, 1)
  assert.deepEqual(leaflet.received.maps[0].center, [0, 0])
  assert.equal(leaflet.received.maps[0].keyboard, true)
  assert.ok(leaflet.received.markers.every(props => props.keyboard === true))
  assert.match(html, /data-village-total="3" data-marker-count="2"/)
  assert.match(html, /href="\/villages\/43"/)
  assert.match(html, /الموقع غير متاح/)
  assert.match(html, /aria-pressed="true"/)
  assert.equal(leaflet.received.markers.length, 2)
})
test('actual Map exposes safe text popups, numeric Details links, keyboard alternatives and tile attribution', () => {
  mapData([village(42, [31, 35], '<img src=x onerror=alert(1)>')])
  const html = render(MapPage)
  assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/)
  assert.equal(html.includes('<img src=x'), false)
  assert.match(html, /data-test-popup/)
  assert.match(html, /href="\/villages\/42"/)
  assert.match(html, /href="#village-map-index"/)
  assert.match(html, /aria-controls="village-map"/)
  assert.match(html, /href="https:\/\/www.openstreetmap.org\/copyright"/)
  assert.match(html, /dir="rtl"/)
  assert.equal(html.includes('/villages/battir'), false)
  assert.equal(html.includes('بيت لحم'), false)
})
for (const items of [[], [village(42, null)], [village(42, [91, 0])], [village(42, [0, 0, 0])]]) {
  test(`actual Map with no valid locations never mounts Leaflet; villages remain available in the index (${JSON.stringify(items)})`, () => {
    mapData(items)
    const html = render(MapPage)
    assert.equal(leaflet.received.maps.length, 0)
    assert.equal(leaflet.received.markers.length, 0)
    assert.match(html, /data-marker-count="0"/)
    if (items.length) assert.match(html, /href="\/villages\/42"/)
    assert.match(html, /لا توجد/)
  })
}
for (const error of [{ status: 503 }, { kind: 'network' }, { code: 'INCOMPLETE_MAP' }]) {
  test(`actual API Map failure ${JSON.stringify(error)} exposes retry without stale counts, partial markers or mock cards`, () => {
    hook.setResult({ error, data: null, loading: false, retry() {} })
    const html = render(MapPage)
    assert.match(html, /role="alert"/)
    assert.match(html, /إعادة المحاولة/)
    assert.equal(leaflet.received.maps.length, 0)
    assert.equal(html.includes('data-village-total='), false)
    assert.equal(html.includes('/villages/battir'), false)
  })
}
test('actual Map loading state never presents village counts or mounts Leaflet', () => {
  const html = render(MapPage)
  assert.match(html, /جارٍ تحميل بيانات القرى/)
  assert.match(html, /role="status"/)
  assert.equal(html.includes('data-village-total='), false)
  assert.equal(leaflet.received.maps.length, 0)
})
test('actual Map handles incomplete controller data as an explicit error instead of a render crash', () => {
  hook.setResult({ data: { items: [village(42)], total: 2, complete: false }, loading: false, retry() {} })
  const html = render(MapPage)
  assert.match(html, /role="alert"/)
  assert.equal(leaflet.received.markers.length, 0)
})
test('tile failures have a separate retry and retain the distinction from Village API failures', () => {
  const html = render(TileStatus, '/map', { status: 'error', retry() {} })
  assert.match(html, /بيانات القرى وروابطها ما زالت متاحة/)
  assert.match(html, /إعادة تحميل أجزاء الخريطة/)
  assert.equal(html.includes('تعذّر تحميل البيانات'), false)
  assert.match(render(TileStatus, '/map', { status: 'loading' }), /جارٍ تحميل أجزاء الخريطة/)
  assert.equal(render(TileStatus, '/map', { status: 'ready' }), '')
})
test('mock Map retains slug navigation and its demo location disclaimer', () => {
  mapService.mode = 'mock'
  hook.setResult({ data: [{ id: 'battir', name: 'بتير', coordinates: [31, 35] }, { id: 'beita', name: 'بيتا', coordinates: null }], loading: false, retry() {} })
  const html = render(MapPage, '/map?village=battir')
  assert.match(html, /href="\/villages\/battir"/)
  assert.match(html, /href="\/villages\/beita"/)
  assert.match(html, /مخصّصة للعرض التجريبي/)
  assert.equal(leaflet.received.markers.length, 1)
})
