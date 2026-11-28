import assert from 'node:assert/strict'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'
import { publicVillageCapabilities } from './villageMode.js'
import { adaptPublicVillageDetail } from './publicVillageAdapters.js'

// Render the actual JSX with installed Vite/React, without a DOM, HTTP listener,
// dependency additions or backend. Only the existing village-data hook and
// integration descriptor are replaced in this isolated module graph.
let server, components, AuthContext, explorer, details, listing
test.before(async () => {
  server = await createServer({
    root: fileURLToPath(new URL('../../', import.meta.url)), configFile: false, envDir: false,
    cacheDir: 'node_modules/.vite-phase55-tests', logLevel: 'silent',
    plugins: [{ name: 'isolated-source-component-fixtures', enforce: 'pre', load(id) {
      const path = id.replaceAll('\\', '/')
      if (path.endsWith('/src/services/useExplorerData.js')) return `
        let result = { loading: true };
        export function setResult(value) { result = value; }
        export function useExplorerData() { return result; }`
      if (path.endsWith('/src/services/villageDetailsService.js')) return `
        export const villageDetailsService = { mode: 'api', available: false };`
    } }, react()],
    server: { middlewareMode: true, hmr: false, watch: null },
    optimizeDeps: { noDiscovery: true, include: [] },
  })
  const [panel, apiDetails, sources, reverse, context, hook, integration, list, listService] = await Promise.all([
    server.ssrLoadModule('/src/components/sources/VillageSourcesPanel.jsx'),
    server.ssrLoadModule('/src/components/ApiVillageDetails.jsx'),
    server.ssrLoadModule('/src/pages/SourcesPage.jsx'),
    server.ssrLoadModule('/src/components/sources/SourceVillages.jsx'),
    server.ssrLoadModule('/src/auth/authContext.js'),
    server.ssrLoadModule('/src/services/useExplorerData.js'),
    server.ssrLoadModule('/src/services/villageDetailsService.js'),
    server.ssrLoadModule('/src/pages/VillageExplorer.jsx'),
    server.ssrLoadModule('/src/services/villageExplorerService.js'),
  ])
  components = { Panel: panel.default, View: panel.VillageSourcesView, Details: apiDetails.default,
    Sources: sources.default, Reverse: reverse.default, Explorer: list.default }
  AuthContext = context.AuthContext; explorer = hook; details = integration.villageDetailsService
  listing = listService.villageExplorerService
})
test.after(async () => { await server?.close() })
test.beforeEach(() => { details.available = false; listing.mode = 'api'; explorer.setResult({ loading: true, retry() {} }) })

const village = Object.freeze({ id: '42', backendId: 42, name: 'Synthetic village', nameAr: 'Synthetic village', nameEn: null,
  governorateId: 2, localAuthorityId: null, latitude: null, longitude: null, coordinates: null })
const source = Object.freeze({ id: '12', backendId: 12, title: 'Synthetic source' })
const association = Object.freeze({ villageId: '42', sourceId: '12', claimNote: '<script>private note</script>' })
const ready = { linksStatus: 'ready', links: [], linksError: null, catalogStatus: 'ready', catalog: [source],
  catalogError: null, mutationStatus: 'idle', mutationError: null, created: null }
const noRequests = () => assert.fail('Server rendering must not initiate protected HTTP')
function session(roles = ['ADMIN'], patch = {}) {
  return { status: 'authenticated', user: Object.freeze({ username: 'synthetic', roles: Object.freeze(roles), rolesVerified: true }),
    expiresAt: Date.now() + 3600000, checking: false, rolesStale: false, ...patch }
}
function render(Component, props = {}, auth = session(), path = '/villages/42') {
  const value = { ...auth, request: noRequests, getSnapshot: () => auth }
  return renderToStaticMarkup(createElement(AuthContext.Provider, { value },
    createElement(MemoryRouter, { initialEntries: [path] },
      createElement(Routes, null, createElement(Route, { path: '*', element: createElement(Component, props) })))))
}
function view(state = ready, permissions = { read: true, write: true }) {
  return render(components.View, { villageId: '42', state, permissions, store: { loadLinks() {}, loadCatalog() {}, submit() {} } })
}

test('actual API Details mounts the real panel with the loaded village identity and integration descriptor', () => {
  details.available = true // Test-only mutable integration descriptor.
  explorer.setResult({ data: { ...village, namesById: new Map(), items: [] }, loading: false, retry() {} })
  const html = render(components.Details)
  assert.match(html, /data-village-id="42"/)
  assert.match(html, /aria-label="مصادر القرية"/)
  assert.equal(html.includes('ربط مصدر موجود'), false) // No form before authenticated lists are loaded.
  assert.deepEqual(publicVillageCapabilities, { browsing: true, identity: true, geographicCatalogues: true, richSections: false })
})

test('actual Details wiring respects the closed integration even with a numeric village and ADMIN', () => {
  explorer.setResult({ data: { ...village, namesById: new Map(), items: [] }, loading: false, retry() {} })
  assert.equal(render(components.Details).includes('data-village-id='), false)
  assert.deepEqual(publicVillageCapabilities, { browsing: true, identity: true, geographicCatalogues: true, richSections: false })
})

test('actual public Details renders only verified basic fields with honest unsupported-section and name states', () => {
  details.available = true
  explorer.setResult({ data: village, loading: false, retry() {} })
  const html = render(components.Details, {}, session([], { status: 'anonymous', user: null }))
  assert.match(html, /Synthetic village/)
  assert.match(html, /معرّف المحافظة/)
  assert.match(html, /اسم المحافظة غير متاح/)
  assert.match(html, /غير متاحة من الخدمة العامة/)
  assert.equal(html.includes('data-protected-village-id='), false)
  assert.equal(html.includes('data-village-id='), false)
  assert.equal(html.includes('verification-panel'), false)
  assert.equal(html.includes('conflict-panel'), false)
  assert.equal(html.includes('DRAFT'), false)
  assert.equal(html.includes('<img'), false)
})

function publicDetailHtml(patch = {}) {
  details.available = true
  const data = adaptPublicVillageDetail({ id: 42, nameAr: 'Synthetic village', nameEn: null,
    governorateId: 2, localAuthorityId: null, latitude: 0, longitude: 0,
    nameOrigin: 'Synthetic origin', locationDescription: 'Synthetic location', elevationM: -430.25,
    areaKm2: 1.125, ...patch })
  explorer.setResult({ data, loading: false, retry() {} })
  return render(components.Details, {}, session([], { status: 'anonymous', user: null }))
}

test('actual public Details renders the four approved fields with units, decimal values and zero coordinates', () => {
  const html = publicDetailHtml()
  for (const [label, value] of [['أصل الاسم', 'Synthetic origin'], ['وصف الموقع', 'Synthetic location'],
    ['الارتفاع (متر)', '-430.25'], ['المساحة (كم²)', '1.125'], ['خط العرض', '0'], ['خط الطول', '0']]) {
    assert.ok(html.includes(`<strong>${label}: </strong><bdi>${value}</bdi>`))
  }
  assert.equal(html.includes('data-protected-village-id='), false)
  assert.equal(html.includes('data-village-id='), false)
  assert.equal(html.includes('verification-panel'), false)
  assert.equal(html.includes('conflict-panel'), false)
})

test('actual public Details keeps zero elevation and displays unavailable optional values without invented data', () => {
  const html = publicDetailHtml({ nameOrigin: null, elevationM: 0, areaKm2: null,
    latitude: null, longitude: null })
  for (const label of ['أصل الاسم', 'المساحة (كم²)', 'خط العرض', 'خط الطول', 'الاسم الإنجليزي', 'معرّف الهيئة المحلية']) {
    assert.ok(html.includes(`<strong>${label}: </strong><bdi>غير متاح</bdi>`))
  }
  assert.ok(html.includes('<strong>الارتفاع (متر): </strong><bdi>0</bdi>'))
  const nullElevation = publicDetailHtml({ elevationM: null })
  assert.ok(nullElevation.includes('<strong>الارتفاع (متر): </strong><bdi>غير متاح</bdi>'))
})

test('actual public Details escapes descriptive strings as text and creates no HTML from their contents', () => {
  const html = publicDetailHtml({ nameOrigin: '<img src=x onerror=alert(1)>',
    locationDescription: '<script>alert(2)</script>' })
  assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/)
  assert.match(html, /&lt;script&gt;alert\(2\)&lt;\/script&gt;/)
  assert.equal(html.includes('<img'), false)
  assert.equal(html.includes('<script'), false)
})

for (const auth of [session(['VIEWER']), session(['EDITOR']), session(['ADMIN']), session(['VIEWER', 'ADMIN']),
  session([]), session([], { user: { username: 'synthetic', roles: null, rolesVerified: false } }),
  session(['ADMIN'], { rolesStale: true })]) {
  test(`actual Details preserves authenticated history/conflict reads and isolates role-based Sources for ${JSON.stringify(auth.user.roles)}/${auth.rolesStale}`, () => {
    details.available = true
    explorer.setResult({ data: village, loading: false, retry() {} })
    const html = render(components.Details, {}, auth)
    assert.match(html, /data-protected-village-id="42"/)
    assert.match(html, /aria-label="سجل تحقق القرية"/)
    assert.match(html, /تعارضات بيانات القرية/)
    const sourceAllowed = !auth.rolesStale && auth.user.roles?.length > 0
    assert.equal(html.includes('data-village-id="42"'), sourceAllowed)
  })
}
for (const auth of [session([], { status: 'anonymous', user: null }), session(['ADMIN'], { expiresAt: 0 })]) {
  test(`actual Details removes protected panels for ${auth.status}/${auth.expiresAt === 0 ? 'expired' : 'logout'}`, () => {
    details.available = true
    explorer.setResult({ data: village, loading: false, retry() {} })
    const html = render(components.Details, {}, auth)
    assert.match(html, /معلومات القرية الأساسية/)
    assert.equal(html.includes('data-protected-village-id='), false)
  })
}

test('actual public Explorer exposes confirmed geographic filters, preserves unknown selected IDs and only public sorts', () => {
  details.available = true
  explorer.setResult({ data: { items: [village], governorates: [], total: 1, page: 1, totalPages: 1 }, loading: false, retry() {} })
  const html = render(components.Explorer, {}, session([], { status: 'anonymous', user: null }), '/villages?governorateId=2&localAuthorityId=7&sortBy=updatedAt')
  assert.match(html, /href="\/villages\/42"/)
  assert.match(html, /id="governorate"/)
  assert.match(html, /id="local-authority"/)
  assert.match(html, /معرّف المحافظة 2/)
  assert.match(html, /معرّف الهيئة المحلية 7/)
  assert.match(html, /استُبدل ترتيب زمني قديم/)
  assert.equal(html.includes('value="updatedAt"'), false)
  assert.equal(html.includes('value="createdAt"'), false)
  assert.equal(html.includes('DRAFT'), false)
})

test('actual mock Explorer preserves its name dropdown and slug cards', () => {
  listing.mode = 'mock'
  explorer.setResult({ data: { items: [{ id: 'battir', name: 'بتير', governorate: 'بيت لحم', images: [] }],
    governorates: [{ id: 'بيت لحم', name: 'بيت لحم' }], total: 1, page: 1, totalPages: 1 }, loading: false, retry() {} })
  const html = render(components.Explorer)
  assert.match(html, /id="governorate"/)
  assert.match(html, /href="\/villages\/battir"/)
  assert.equal(html.includes('قائمة التصفية غير متاحة'), false)
})

test('actual Sources page has guidance instead of an identity-less village-linking panel or form', () => {
  explorer.setResult({ data: [], loading: false, retry() {} })
  const html = render(components.Sources, {}, session(), '/sources')
  assert.match(html, /استخدم قسم مصادر القرية في تفاصيل قرية حقيقية/)
  assert.equal(html.includes('data-village-id='), false)
  assert.equal(html.includes('ربط مصدر موجود'), false)
})

for (const identity of [null, { id: 'battir' }, { id: '042', backendId: 42 }, { id: '42', backendId: 43 },
  { id: '0', backendId: 0 }, { id: '9007199254740992', backendId: 9007199254740992 }]) {
  test(`actual panel rejects unsafe/mock/mismatched identity ${JSON.stringify(identity)} without rendering a form`, () => {
    assert.equal(render(components.Panel, { village: identity, integration: { mode: 'api', available: true } }), '')
  })
}

for (const integration of [{ mode: 'mock', available: true }, { mode: 'api', available: false }, null]) {
  test(`actual panel respects integration ${JSON.stringify(integration)}`, () => {
    assert.equal(render(components.Panel, { village, integration }), '')
  })
}

for (const roles of [['VIEWER'], ['EDITOR'], ['ADMIN'], ['VIEWER', 'ADMIN']]) {
  test(`actual panel accepts real identity for ${roles.join('/')} and waits for loaded data before offering linking`, () => {
    const html = render(components.Panel, { village, integration: { mode: 'api', available: true } }, session(roles))
    assert.match(html, /data-village-id="42"/)
    assert.equal(html.includes('ربط مصدر موجود'), false)
  })
}

for (const auth of [session([], { status: 'anonymous', user: null }), session([]), session(['ADMIN', 'UNKNOWN']),
  session(['ADMIN'], { rolesStale: true }), session(['ADMIN'], { checking: true }),
  session(['ADMIN'], { expiresAt: 0 }), session([], { user: { username: 'ADMIN', roles: null, rolesVerified: false } })]) {
  test(`actual panel exposes no editorial data or form to denied session ${JSON.stringify(auth.user?.roles)}/${auth.status}/${auth.rolesStale}`, () => {
    const html = render(components.Panel, { village, integration: { mode: 'api', available: true } }, auth)
    assert.equal(html.includes('data-village-id='), false)
    assert.equal(html.includes('ربط مصدر موجود'), false)
    assert.equal(html.includes('private note'), false)
  })
}

test('actual form offers safe existing sources and an optional note, with no unlink/delete controls', () => {
  const html = view()
  assert.match(html, /<option value="12">Synthetic source<\/option>/)
  assert.match(html, /ملاحظة الربط \(اختياري\)/)
  assert.match(html, /تحديث قائمة ارتباطات القرية/)
  assert.match(html, /تحديث فهرس المصادر المتاحة/)
  assert.equal(html.includes('maxlength='), false)
  assert.equal(html.includes('حذف'), false)
  assert.equal(html.includes('إلغاء الربط'), false)
  const pending = view({ ...ready, mutationStatus: 'pending' })
  assert.match(pending, /aria-busy="true"/)
  assert.match(pending, /<fieldset disabled=""/)
})

test('actual read-only view exposes associations as escaped text without a misleading disabled form', () => {
  const html = view({ ...ready, links: [association] }, { read: true, write: false })
  assert.match(html, /Synthetic source/)
  assert.match(html, /&lt;script&gt;private note&lt;\/script&gt;/)
  assert.match(html, /قراءة فقط/)
  assert.equal(html.includes('<form'), false)
  assert.equal(view({ ...ready, links: [association] }, { read: false, write: false }), '')
})

test('actual panel has honest empty and fully-linked states instead of unusable forms', () => {
  const empty = view({ ...ready, catalog: [] })
  assert.match(empty, /لا توجد مصادر للاختيار/)
  assert.equal(empty.includes('<form'), false)
  const linked = view({ ...ready, links: [association] })
  assert.match(linked, /جميع المصادر المتاحة مرتبطة/)
  assert.equal(linked.includes('<form'), false)
})

test('actual view distinguishes saved link success from a failed GET refresh and offers only a list retry', () => {
  const html = view({ ...ready, linksStatus: 'error', links: null, linksError: { status: 503 },
    mutationStatus: 'success', created: association })
  assert.match(html, /تم ربط المصدر رقم/)
  assert.match(html, /تم الربط، لكن تعذّر تحديث القائمة/)
  assert.match(html, /إعادة المحاولة/)
  assert.equal(html.includes('<form'), false)
})

test('actual form shows duplicate 409 without claiming a successful link or changing association notes', () => {
  const html = view({ ...ready, mutationStatus: 'error', mutationError: { status: 409 } })
  assert.match(html, /لم تُستبدل ملاحظة الربط الموجودة/)
  assert.equal(html.includes('تم ربط المصدر رقم'), false)
  assert.match(html, /<form/)
})

test('actual reverse associations never imply public detail availability or link editorial IDs to mock villages', () => {
  explorer.setResult({ data: [association], loading: false, retry() {} })
  const html = render(components.Reverse, { sourceId: '12', service: { sourceVillages: noRequests } })
  assert.match(html, /معرّف القرية/)
  assert.match(html, /لا تعني أن القرى المرتبطة منشورة/)
  assert.equal(html.includes('/villages/42'), false)
  assert.match(html, /&lt;script&gt;private note&lt;\/script&gt;/)
})
