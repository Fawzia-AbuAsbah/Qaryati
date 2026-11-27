import { createElement as h } from 'react'

export const structuredValueMaxDepth = 8

// Renders arbitrary JSON from conflictingValues as React text nodes only. Keys and
// values are never parsed as markup, URLs or styles, and no HTML injection API is used.
export function StructuredValue({ value, depth = 0 }) {
  if (depth > structuredValueMaxDepth) return h('span', { className: 'conflict-meta' }, 'بنية متداخلة أعمق من المعروض')
  if (value === null) return h('span', { className: 'conflict-meta' }, 'قيمة فارغة (null)')
  if (typeof value === 'string') return value === '' ? h('span', { className: 'conflict-meta' }, 'نص فارغ') : h('bdi', { className: 'conflict-text' }, value)
  if (typeof value === 'number' || typeof value === 'boolean') return h('bdi', null, String(value))
  if (Array.isArray(value)) {
    if (!value.length) return h('span', { className: 'conflict-meta' }, 'قائمة فارغة')
    return h('ol', { className: 'conflict-structure' }, value.map((item, index) => h('li', { key: index }, h(StructuredValue, { value: item, depth: depth + 1 }))))
  }
  if (value && typeof value === 'object') {
    const entries = Object.entries(value)
    if (!entries.length) return h('span', { className: 'conflict-meta' }, 'كائن فارغ')
    return h('dl', { className: 'conflict-structure' }, entries.map(([key, item], index) => [
      h('dt', { key: `k${index}` }, h('bdi', null, key)),
      h('dd', { key: `v${index}` }, h(StructuredValue, { value: item, depth: depth + 1 })),
    ]))
  }
  return h('span', { className: 'conflict-meta' }, 'قيمة غير مدعومة')
}
