import assert from 'node:assert/strict'
import test from 'node:test'
import { authAccessDecision, memberRoutes, safeAuthReturnTo } from './authAccess.js'

const now = 10000
const authenticated = { status: 'authenticated', user: { username: 'reader', roles: null, rolesVerified: false }, expiresAt: now + 1000 }

test('RequireAuth policy waits for profile verification and redirects anonymous/expired sessions', () => {
  assert.equal(authAccessDecision({ status: 'authenticating' }, { now }), 'pending')
  assert.equal(authAccessDecision({ status: 'anonymous' }, { now }), 'login')
  assert.equal(authAccessDecision({ ...authenticated, expiresAt: now }, { now }), 'login')
  assert.equal(authAccessDecision({ ...authenticated, user: null }, { now }), 'login')
  assert.equal(authAccessDecision(authenticated, { now }), 'allow')
})

test('role-dependent requirements fail closed for missing roles and unsupported requirements', () => {
  for (const requiredRoles of [['ADMIN'], ['EDITOR'], ['VIEWER'], ['UNKNOWN_ROLE'], 'ADMIN', null]) {
    assert.equal(authAccessDecision(authenticated, { requiredRoles, now }), 'roles-unavailable')
  }
  for (const requiredRoles of [['UNKNOWN_ROLE'], 'ADMIN', null]) {
    assert.equal(authAccessDecision({ ...authenticated, user: { username: 'admin', roles: ['ADMIN'], rolesVerified: true } }, { requiredRoles, now }), 'roles-unavailable')
  }
})

test('generic guards use explicit OR permissions without implicit role inheritance', () => {
  const session = { ...authenticated, user: { username: 'profile', roles: ['ADMIN'], rolesVerified: true } }
  assert.equal(authAccessDecision(session, { requiredRoles: ['ADMIN'], now }), 'allow')
  assert.equal(authAccessDecision(session, { requiredRoles: ['VIEWER', 'ADMIN'], now }), 'allow')
  assert.equal(authAccessDecision(session, { requiredRoles: ['VIEWER'], now }), 'forbidden')
  assert.equal(authAccessDecision(session, { requiredRoles: ['EDITOR'], now }), 'forbidden')
  assert.equal(authAccessDecision({ ...session, checking: true }, { requiredRoles: ['ADMIN'], now }), 'pending')
  assert.equal(authAccessDecision({ ...session, checking: true }, { now }), 'allow')
  assert.equal(authAccessDecision(session, { requiredRoles: ['ADMIN', 'UNKNOWN'], now }), 'roles-unavailable')
})

test('post-login return URLs stay inside existing routes and preserve query parameters', () => {
  for (const target of ['/', '/villages?q=%D8%A8%D8%AA%D9%8A%D8%B1&page=2', '/villages/42', '/map?village=42&tag=a&tag=b#location', '/population-records?villageId=7&page=0&size=20', '/sources?source=42', '/data-conflicts?status=OPEN', '/audit-logs?mode=actor&actorUserId=5']) {
    assert.equal(safeAuthReturnTo(target), target)
  }
  for (const target of [null, {}, 'https://evil.example', '//evil.example', '/\\evil.example', '/login', '/login?from=/login',
    '/sources/42', 'javascript:alert(1)', 'http://[', '/villages/../login', '/\n/evil.example']) {
    assert.equal(safeAuthReturnTo(target), '/')
  }
})

test('Population Records is an authenticated route for every application role', () => {
  const route = memberRoutes.find(item => item.to === '/population-records')
  assert.ok(route)
  for (const role of ['VIEWER', 'EDITOR', 'ADMIN']) {
    const session = { ...authenticated, user: { username: role, roles: [role], rolesVerified: true } }
    assert.equal(authAccessDecision(session, { requiredRoles: route.requiredRoles, now }), 'allow')
  }
})
