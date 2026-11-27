// Match the checked-out backend's Java String.strip(), including control spaces
// and excluding non-breaking spaces, which remain literal substring characters.
export function publicVillageSearch(value) {
  if (typeof value !== 'string') throw new TypeError('Unsupported search')
  // eslint-disable-next-line no-control-regex -- Java whitespace includes these control characters.
  const term = value.replace(/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu, '')
  if (term.length > 180) throw new TypeError('Unsupported search')
  return term
}
