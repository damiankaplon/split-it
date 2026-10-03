import {Navigate, Route, Routes} from 'react-router'
import {useAuth} from '@/auth/AuthContext'
import {AppHeader} from '@/components/AppHeader'
import {JoinPage} from '@/pages/JoinPage'
import {ProjectPage} from '@/pages/ProjectPage'
import {ProjectsPage} from '@/pages/ProjectsPage'
import {SignInPage} from '@/pages/SignInPage'

function App() {
  const {state, login, logout} = useAuth()

  if (state.status === 'loading') return null

  if (state.status !== 'authenticated') {
    // Keycloak redirects back to the current URL, so e.g. an invite link survives sign-in.
    return <SignInPage onSignIn={() => void login()} keycloakUnavailable={state.status === 'error'}/>
  }

  return (
      <div className="flex min-h-screen flex-col">
        <AppHeader username={state.username} onSignOut={() => void logout()}/>
        <Routes>
          <Route path="/" element={<ProjectsPage currentUserId={state.userId}/>}/>
          <Route path="/projects/:projectId" element={<ProjectPage currentUserId={state.userId}/>}/>
          <Route path="/join/:projectId/:token" element={<JoinPage username={state.username}/>}/>
          <Route path="*" element={<Navigate to="/" replace/>}/>
        </Routes>
      </div>
  )
}

export default App
