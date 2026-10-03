import {useEffect, useState} from 'react'
import {AlertCircle} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {listProjects, type Project} from '@/api/projects'
import {InviteDialog} from '@/components/InviteDialog'
import {NewProjectDialog} from '@/components/NewProjectDialog'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Card} from '@/components/ui/card'

type ProjectsState =
    | { status: 'loading' }
    | { status: 'error'; message: string }
    | { status: 'loaded'; projects: Project[] }

export function ProjectsPage({currentUserId}: { currentUserId: string }) {
  const {t} = useTranslation()
  const [state, setState] = useState<ProjectsState>({status: 'loading'})

  useEffect(() => {
    listProjects()
        .then((projects) => setState({status: 'loaded', projects}))
        .catch((e: unknown) => setState({status: 'error', message: e instanceof Error ? e.message : String(e)}))
  }, [])

  const onCreated = (project: Project) =>
      setState((s) => (s.status === 'loaded' ? {status: 'loaded', projects: [...s.projects, project]} : s))

  return (
      <main className="mx-auto flex max-w-[1200px] flex-col gap-6 px-6 pt-7 pb-12">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex flex-col gap-1">
            <h1 className="text-[26px] font-semibold tracking-[-0.02em]">{t('projects.title')}</h1>
            <p className="text-muted-foreground">{t('projects.subtitle')}</p>
          </div>
          <NewProjectDialog onCreated={onCreated}/>
        </div>

        {state.status === 'loading' && <p className="text-muted-foreground">{t('projects.loading')}</p>}

        {state.status === 'error' && (
            <Alert variant="destructive" className="max-w-xl">
              <AlertCircle/>
              <AlertTitle>{t('projects.loadError')}</AlertTitle>
              <AlertDescription>{state.message}</AlertDescription>
            </Alert>
        )}

        {state.status === 'loaded' && state.projects.length === 0 && (
            <p className="text-muted-foreground">{t('projects.empty')}</p>
        )}

        {state.status === 'loaded' && state.projects.length > 0 && (
            <div className="grid grid-cols-[repeat(auto-fit,minmax(min(320px,100%),1fr))] gap-4">
              {state.projects.map((project) => {
                const isOwner = project.ownerId === currentUserId
                return (
                    <Card key={project.id} className="gap-5 p-5 shadow-xs">
                      <div className="flex flex-col gap-1">
                        <span className="text-base font-semibold">{project.name}</span>
                        <span
                            className="text-[13px] text-muted-foreground">{isOwner ? t('projects.owner') : t('projects.member')}</span>
                      </div>
                      {isOwner && (
                          <div className="flex justify-end border-t pt-3.5">
                            <InviteDialog project={project} currentUserId={currentUserId}/>
                          </div>
                      )}
                    </Card>
                )
              })}
            </div>
        )}
      </main>
  )
}
