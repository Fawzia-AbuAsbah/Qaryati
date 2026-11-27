import assert from 'node:assert/strict'
import test from 'node:test'
import { authAccessDecision, safeAuthReturnTo } from './authAccess.js'

const now = 10000
const authenticated = { status: 'authenticated', user: { username: 'reader', roles: null, rolesVerified: false }, expiresAt: now + 1000 }

test('RequireAuth policy waits for profile verification and redirects anonymous/expired sessions', () => {
  assert.equal(authAccessDecision({ status: 'authenticating' }, { now }), 'pending')
  assert.equal(authAccessDecision({ status: 'anonymous' }, { now }), 'login')
  assert.equal(authAccessDecision({ ...authenticated, expiresAt: now }, { now }), 'login')
  assert.equal(authAccessDecision({ ...authenticated, user: null }, { now }), 'login')
  assert.equal(authAccessDecision(authenticated, { now }), 'allow')
})

test('every role-dependent requirement fails closed, including unsupported and fabricated caller claims', () => {
  for (const requiredRoles of [['ADMIN'], ['EDITOR'], ['VIEWER'], ['UNKNOWN_ROLE'], 'ADMIN', null]) {
    assert.equal(authAccessDecision(authenticated, { requiredRoles, now }), 'roles-unavailable')
    assert.equal(authAccessDecision({ ...authenticated, user: { username: 'admin', roles: ['ADMIN'], rolesVerified: true } }, { requiredRoles, now }), 'roles-unavailable')
  }
})

test('post-login return URLs stay inside existing routes and preserve query parameters', () => {
  for (const target of ['/', '/villages?q=%D8%A8%D8%AA%D9%8A%D8%B1&page=2', '/villages/42', '/map?village=42&tag=a&tag=b#location', '/sources?source=42']) {
    assert.equal(safeAuthReturnTo(target), target)
  }
  for (const target of [null, {}, 'https://evil.example', '//evil.example', '/\\evil.example', '/login', '/login?from=/login',
    '/sources/42', 'javascript:alert(1)', 'http://[', '/villages/../login', '/\n/evil.example']) {
    assert.equal(safeAuthReturnTo(target), '/')
  }
})
