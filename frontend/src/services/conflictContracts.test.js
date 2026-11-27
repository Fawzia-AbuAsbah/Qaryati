import assert from 'node:assert/strict'
import test from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import {
  adaptConflict, conflictCreateBody, conflictErrorMessage, conflictNextAction, conflictResolveBody, conflictStatuses,
  conflictValuesError, conflictValuesFromDraft,
} from './conflictContracts.js'
import { StructuredValue, structuredValueMaxDepth } from '../components/conflicts/StructuredValue.js'
import { safeAuthReturnTo } from './authAccess.js'

const render = value => renderToStaticMarkup(createElement(StructuredValue, { value }))

test('lifecycle exposes only OPEN -> IN_REVIEW -> RESOLVED actions', () => {
  assert.deepEqual(conflictStatuses, ['OPEN', 'IN_REVIEW', 'RESOLVED'])
  assert.equal(conflictNextAction('OPEN'), 'review')
  assert.equal(conflictNextAction('IN_REVIEW'), 'resolve')
  assert.equal(conflictNextAction('RESOLVED'), null)
  assert.equal(conflictNextAction('CLOSED'), null)
})

test('competing-value validation mirrors @NotEmpty maps, @NotBlank keys, @NotNull values and the two-value minimum', () => {
  assert.equal(conflictValuesError([{ a: '1' }, { a: '2' }]), null)
  assert.equal(conflictValuesError([{ a: '' }, { b: { c: null } }]), null) // Empty strings and nested nulls are not rejected by the backend.
  for (const bad of [undefined, [], [{ a: '1' }], [{ a: '1' }, {}], [{ a: '1' }, { '': 'x' }], [{ a: '1' }, { '\t': 'x' }],
    [{ a: '1' }, { a: null }], [{ a: '1' }, [['a', '1']]], [{ a: '1' }, Object.create({ inherited: true })], [{ a: '1' }, { a: Infinity }]]) {
    assert.ok(conflictValuesError(bad), JSON.stringify(bad))
  }
})

test('form drafts keep typed text exactly, reject duplicate/blank keys and keep "__proto__" as plain data', () => {
  const values = conflictValuesFromDraft([[{ key: 'value', value: '  1200 ' }, { key: 'source', value: '' }], [{ key: '__proto__', value: 'x' }]])
  assert.deepEqual(JSON.parse(JSON.stringify(values)), [{ value: '  1200 ', source: '' }, { ['__proto__']: 'x' }])
  assert.equal(Object.getPrototypeOf(values[1]), Object.prototype)
  assert.equal(Object.hasOwn(values[1], '__proto__'), true)
  assert.equal(conflictValuesError(values), null)
  assert.equal(JSON.stringify(conflictCreateBody('42', 'population', values).conflictingValues[1]), '{"__proto__":"x"}')
  for (const draft of [[], [[{ key: 'a', value: '1' }]], [[{ key: 'a', value: '1' }], []], [[{ key: 'a', value: '1' }], [{ key: ' ', value: '1' }]],
    [[{ key: 'a', value: '1' }, { key: 'a', value: '2' }], [{ key: 'a', value: '1' }]], [[{ key: 'a', value: 1 }], [{ key: 'a', value: '1' }]]]) {
    assert.throws(() => conflictValuesFromDraft(draft), { code: 'CONFLICT_VALIDATION' })
  }
})

test('create body sends raw field name within the 100-character limit and only VILLAGE numeric IDs', () => {
  const values = [{ a: '1' }, { a: '2' }]
  assert.deepEqual(conflictCreateBody('42', ' name ', values), { entityType: 'VILLAGE', entityId: 42, fieldName: ' name ', conflictingValues: values })
  assert.equal(conflictCreateBody('42', 'ب'.repeat(100), values).fieldName.length, 100)
  assert.throws(() => conflictCreateBody('42', 'ب'.repeat(101), values), error => Boolean(error.fields.fieldName))
  assert.throws(() => conflictCreateBody('battir', 'name', values))
  assert.throws(() => conflictCreateBody('42', 'name', values, 'SOURCE'), { code: 'CONFLICT_VALIDATION' })
})

test('resolution notes are required non-blank text with no invented length limit and are sent unmodified', () => {
  assert.deepEqual(conflictResolveBody('  ملاحظات  '), { resolutionNotes: '  ملاحظات  ' })
  assert.equal(conflictResolveBody('x'.repeat(20000)).resolutionNotes.length, 20000)
  for (const notes of ['', ' ', '\n', null, 1]) assert.throws(() => conflictResolveBody(notes), error => Boolean(error.fields.resolutionNotes))
})

test('adapters keep non-village management rows but scope village reads strictly', () => {
  const row = { id: 4, entityType: 'VILLAGE', entityId: 42, fieldName: 'f', conflictingValues: [{ a: 1 }, { a: 2 }], status: 'OPEN' }
  const adapted = adaptConflict(row)
  assert.equal(adapted.resolvedBy, null)
  assert.equal(adapted.createdAt, null)
  assert.equal(adapted.resolvedAt, null)
  assert.equal(Object.isFrozen(adapted), true)
  assert.throws(() => adaptConflict(row, { villageId: '43' }))
  assert.throws(() => adaptConflict(row, { status: 'IN_REVIEW' }))
  assert.throws(() => adaptConflict({ ...row, resolutionNotes: 5 }))
})

test('structured values render as escaped text with no injected markup or attributes', () => {
  const html = render({ '<script>alert(1)</script>': '<img src=x onerror=alert(1)>', link: 'javascript:alert(1)', nested: [{ style: 'color:red' }] })
  assert.equal(html.includes('<script>'), false)
  assert.equal(html.includes('<img'), false)
  // Every real tag is a known structural element with at most a fixed class attribute.
  const tags = html.match(/<[^>]*>/g)
  assert.ok(tags.every(tag => /^<\/?(dl|dt|dd|ol|li|bdi|span)( class="conflict-(structure|text|meta)")?>$/.test(tag)), tags.join(' '))
  assert.ok(html.includes('&lt;script&gt;alert(1)&lt;/script&gt;'))
  assert.ok(html.includes('&lt;img src=x onerror=alert(1)&gt;'))
  assert.ok(html.includes('javascript:alert(1)'))
})

test('structured values distinguish null, empty values, booleans, numbers and depth limits', () => {
  assert.ok(render(null).includes('null'))
  assert.ok(render('').includes('نص فارغ'))
  assert.ok(render([]).includes('قائمة فارغة'))
  assert.ok(render({}).includes('كائن فارغ'))
  assert.ok(render(false).includes('false'))
  assert.ok(render(0).includes('>0<'))
  assert.ok(render({ __proto__: null, a: 'b' }).includes('<dt><bdi>a</bdi></dt>'))
  let deep = 'leaf'
  for (let i = 0; i <= structuredValueMaxDepth + 1; i++) deep = [deep]
  const html = render(deep)
  assert.equal(html.includes('leaf'), false)
  assert.ok(html.includes('بنية متداخلة'))
})

test('error messages separate auth, roles, validation, missing records, invalid transitions and uncertain mutations', () => {
  assert.match(conflictErrorMessage({ status: 401 }), /سجّل الدخول/)
  assert.match(conflictErrorMessage({ code: 'CONFLICT_ROLES_UNAVAILABLE' }, 'create'), /لا يمكن التحقق/)
  assert.match(conflictErrorMessage({ status: 403 }, 'review'), /صلاحية/)
  assert.match(conflictErrorMessage({ status: 400 }, 'create'), /تحقق/)
  assert.match(conflictErrorMessage({ status: 404 }, 'create'), /القرية/)
  assert.match(conflictErrorMessage({ status: 404 }, 'resolve'), /التعارض المطلوب/)
  assert.match(conflictErrorMessage({ status: 409 }, 'resolve'), /حالة التعارض الحالية/)
  assert.match(conflictErrorMessage({ kind: 'network' }, 'review'), /قد يكون الطلب قد نُفّذ/)
  assert.match(conflictErrorMessage({ kind: 'network' }), /تعذّر الاتصال/)
})

test('login return URLs accept the protected conflicts page and its status filter only', () => {
  assert.equal(safeAuthReturnTo('/data-conflicts?status=IN_REVIEW'), '/data-conflicts?status=IN_REVIEW')
  assert.equal(safeAuthReturnTo('/data-conflicts/9'), '/')
})
