import Keycloak from 'keycloak-js'

// Defaults match backend/docker-compose.yaml and the imported split_it realm.
export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'split_it',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'localhost',
})

let initPromise: Promise<boolean> | undefined

/**
 * Initializes the adapter exactly once (keycloak-js throws on a second init, which
 * React StrictMode would otherwise trigger). Resolves to whether the user is signed in.
 */
export function initKeycloak(): Promise<boolean> {
  initPromise ??= keycloak.init({
    // Authorization Code flow with PKCE; the realm's client is public and requires S256.
    pkceMethod: 'S256',
    // Picks up an existing Keycloak session without forcing the login page.
    onLoad: 'check-sso',
    checkLoginIframe: false,
  })
  return initPromise
}

/** Returns a valid access token, refreshing it if it expires within `minValidity` seconds. */
export async function getAccessToken(minValidity = 30): Promise<string> {
  try {
    await keycloak.updateToken(minValidity)
  } catch {
    await keycloak.login()
  }
  if (!keycloak.token) throw new Error('Not authenticated')
  return keycloak.token
}
