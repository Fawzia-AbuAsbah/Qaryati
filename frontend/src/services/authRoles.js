// Verified against LoginController and the database-backed JWT converter at 4e2d137.
// This capability is independent of the still-closed public Village capability.
export const effectiveRolesContractVerified = true
export const applicationRoles = Object.freeze(['VIEWER', 'EDITOR', 'ADMIN'])

export function validRoles(roles) {
  return Array.isArray(roles) && Array.from(roles).every(role => applicationRoles.includes(role))
}

export function verifiedProfileRoles(user) {
  return user?.rolesVerified === true && validRoles(user.roles)
}
