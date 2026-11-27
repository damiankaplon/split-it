import {App} from '@capacitor/app'
import {Browser} from '@capacitor/browser'
import {Preferences} from '@capacitor/preferences'
import type Keycloak from 'keycloak-js'
import type {KeycloakAdapter} from 'keycloak-js'

/*
 * Sign-in for the Android and iOS apps.
 *
 * The web app redirects the whole page to Keycloak and back. In the app that would load Keycloak inside the
 * WebView, so the login page opens in the system browser instead (Custom Tabs / SFSafariViewController), where
 * password managers and passkeys work. Keycloak then redirects to REDIRECT_URI, a URL scheme registered by this
 * app only (AndroidManifest.xml, Info.plist), and the app reloads itself with the code and state from that URL,
 * so keycloak-js finishes the PKCE code exchange in init() exactly like it does on the web. keycloak-js has no
 * public API that takes a callback URL, see https://github.com/keycloak/keycloak-js/issues/27.
 *
 * The tokens are kept in Preferences, so the app stays signed in between launches. It asks for offline_access:
 * a regular refresh token ends with the Keycloak SSO session (30 minutes idle by default), while an offline one
 * lasts until the realm's offline session idle timeout.
 */

const REDIRECT_URI = 'pl.damiankaplon.splitit://auth'
const TOKENS_KEY = 'auth.tokens'
/** Set on sign-out, so the next login asks for credentials even if the browser still has a Keycloak session. */
const SIGNED_OUT_KEY = 'auth.signedOut'
/** Marks that this app process has already looked at the URL it was launched with. */
const LAUNCH_URL_SEEN_KEY = 'auth.launchUrlSeen'

interface StoredTokens {
  token: string
  refreshToken: string
  idToken?: string
}

/** Initializes keycloak-js for the native app. Resolves to whether the user is signed in. */
export async function initNativeKeycloak(keycloak: Keycloak): Promise<boolean> {
  void App.addListener('appUrlOpen', ({url}) => {
    if (!isAuthCallback(url)) return
    // SFSafariViewController stays open on iOS; on Android, opening the app already closed the tab
    void Browser.close().catch(() => undefined)
    window.location.replace(appUrlWithCallback(url))
  })
  await applyLaunchUrl()

  const stored = await loadTokens()
  try {
    return await keycloak.init({
      adapter: nativeAdapter(keycloak),
      pkceMethod: 'S256',
      // Keycloak's session cookie lives in the system browser, so the WebView cannot check it
      checkLoginIframe: false,
      // Custom URL schemes carry the query more reliably than the fragment
      responseMode: 'query',
      scope: 'openid offline_access',
      ...stored,
    })
  } catch (error) {
    if (!stored) throw error
    // Keycloak rejected the stored refresh token (invalid_grant: the session ended or was revoked)
    if (responseStatus(error) === 400) {
      await keycloak.clearToken()
      return false
    }
    // Offline or Keycloak unreachable: keep the session, API calls refresh the token once Keycloak is back
    return Boolean(keycloak.authenticated)
  }
}

/** Stores the current tokens, or forgets them after sign-out. Call whenever keycloak-js gets or drops tokens. */
export function rememberNativeSession(keycloak: Keycloak) {
  const {token, refreshToken, idToken} = keycloak
  if (token && refreshToken) {
    localStorage.removeItem(SIGNED_OUT_KEY)
    const tokens: StoredTokens = {token, refreshToken, idToken}
    void Preferences.set({key: TOKENS_KEY, value: JSON.stringify(tokens)})
  } else {
    void Preferences.remove({key: TOKENS_KEY})
  }
}

function nativeAdapter(keycloak: Keycloak): KeycloakAdapter {
  return {
    login: async (options) => {
      const prompt = options?.prompt ?? (localStorage.getItem(SIGNED_OUT_KEY) ? 'login' : undefined)
      await Browser.open({url: await keycloak.createLoginUrl({...options, prompt})})
    },
    register: async (options) => {
      await Browser.open({url: await keycloak.createRegisterUrl(options)})
    },
    logout: async () => {
      await endSession(keycloak)
    },
    accountManagement: async () => {
      await Browser.open({url: keycloak.createAccountUrl()})
    },
    redirectUri: (options) => options?.redirectUri ?? REDIRECT_URI,
  }
}

/**
 * Ends the Keycloak session from the app, without a browser round trip: POSTing the refresh token to the
 * logout endpoint revokes it, offline session included.
 */
async function endSession(keycloak: Keycloak) {
  const {refreshToken, clientId} = keycloak
  if (refreshToken && clientId) {
    const realmUrl = `${keycloak.authServerUrl?.replace(/\/$/, '')}/realms/${encodeURIComponent(keycloak.realm ?? '')}`
    await fetch(`${realmUrl}/protocol/openid-connect/logout`, {
      method: 'POST',
      body: new URLSearchParams({client_id: clientId, refresh_token: refreshToken}),
    }).catch(() => undefined) // Offline: the user is still signed out of the app below
  }
  localStorage.setItem(SIGNED_OUT_KEY, 'true')
  // Fires onAuthLogout, which forgets the stored tokens
  await keycloak.clearToken()
}

async function loadTokens(): Promise<StoredTokens | undefined> {
  const {value} = await Preferences.get({key: TOKENS_KEY})
  return value ? (JSON.parse(value) as StoredTokens) : undefined
}

/**
 * Android may kill the app while the login page is open in the browser; the redirect then starts it again with
 * the callback as its launch URL. Puts the callback into the current URL so init() picks it up.
 */
async function applyLaunchUrl() {
  // sessionStorage lives as long as the WebView: after the app reloads itself, the launch URL is not new
  if (sessionStorage.getItem(LAUNCH_URL_SEEN_KEY)) return
  sessionStorage.setItem(LAUNCH_URL_SEEN_KEY, 'true')
  const launch = await App.getLaunchUrl()
  if (launch && isAuthCallback(launch.url)) {
    window.history.replaceState(window.history.state, '', appUrlWithCallback(launch.url))
  }
}

/** keycloak-js throws its (untyped) NetworkError, carrying the response, when Keycloak answers with an error. */
function responseStatus(error: unknown): number | undefined {
  if (error instanceof Error && 'response' in error && error.response instanceof Response) return error.response.status
  return undefined
}

function isAuthCallback(url: string) {
  return url.startsWith(`${REDIRECT_URI}?`)
}

/** The page the app is on, with the code and state from Keycloak's redirect as its query. */
function appUrlWithCallback(callbackUrl: string) {
  return `${window.location.pathname}${new URL(callbackUrl).search}`
}
