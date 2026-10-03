import {createContext, type ReactNode, use, useEffect, useState} from 'react'
import {initKeycloak, keycloak} from './keycloak'
import {currentLanguage} from '@/i18n'

type AuthState =
    | { status: 'loading' }
    | { status: 'error'; error: unknown }
    | { status: 'anonymous' }
    | { status: 'authenticated'; userId: string; username: string }

interface Auth {
  state: AuthState
  login: () => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<Auth | null>(null)

function currentState(): AuthState {
  if (!keycloak.authenticated) return {status: 'anonymous'}
  const token = keycloak.tokenParsed
  return {status: 'authenticated', userId: token?.sub ?? '', username: token?.preferred_username ?? ''}
}

export function AuthProvider({children}: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({status: 'loading'})

  useEffect(() => {
    keycloak.onAuthSuccess = () => setState(currentState())
    keycloak.onAuthLogout = () => setState({status: 'anonymous'})
    keycloak.onAuthRefreshError = () => setState({status: 'anonymous'})
    // Refresh proactively so the session survives idle periods, not just API calls.
    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).catch(() => setState({status: 'anonymous'}))
    }

    initKeycloak()
        .then(() => setState(currentState()))
        .catch((error: unknown) => setState({status: 'error', error}))
  }, [])

  const auth: Auth = {
    state,
    // Asks Keycloak to render its login page in the app's language (ui_locales)
    login: () => keycloak.login({locale: currentLanguage()}),
    logout: () => keycloak.logout({redirectUri: window.location.origin}),
  }

  return <AuthContext value={auth}>{children}</AuthContext>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): Auth {
  const auth = use(AuthContext)
  if (!auth) throw new Error('useAuth must be used within AuthProvider')
  return auth
}
