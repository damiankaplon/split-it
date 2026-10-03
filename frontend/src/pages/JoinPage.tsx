import {useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {Link, useNavigate, useParams} from 'react-router'
import {acceptInvitation, type Invitation, previewInvitation} from '@/api/projects'
import {Button} from '@/components/ui/button'
import {Card} from '@/components/ui/card'

type PreviewState =
    | { status: 'loading' }
    | { status: 'invalid' }
    | { status: 'ready'; projectName: string }

export function JoinPage({username}: { username: string }) {
  const {t} = useTranslation()
  const {projectId = '', token = ''} = useParams()
  const navigate = useNavigate()
  const [preview, setPreview] = useState<PreviewState>({status: 'loading'})
  const [joining, setJoining] = useState(false)
  const [joinFailed, setJoinFailed] = useState(false)

  useEffect(() => {
    previewInvitation({projectId, token})
        .then((p) => setPreview({status: 'ready', projectName: p.projectName}))
        .catch(() => setPreview({status: 'invalid'}))
  }, [projectId, token])

  const join = async (invitation: Invitation) => {
    setJoining(true)
    setJoinFailed(false)
    try {
      await acceptInvitation(invitation)
      navigate(`/projects/${invitation.projectId}`, {replace: true})
    } catch {
      setJoinFailed(true)
      setJoining(false)
    }
  }

  return (
      <div className="flex grow items-center justify-center p-6">
        <Card className="w-full max-w-[440px] gap-6 p-8 shadow-xs">
          {preview.status === 'loading' && <p className="text-muted-foreground">{t('join.checking')}</p>}

          {preview.status === 'invalid' && (
              <>
                <div className="flex flex-col gap-2">
                  <h1 className="text-[26px] font-semibold tracking-[-0.02em]">{t('join.unavailableTitle')}</h1>
                  <p className="text-muted-foreground">{t('join.invalid')} {t('join.askOwner')}</p>
                </div>
                <div className="flex justify-end">
                  <Button asChild variant="outline" size="lg" className="rounded-lg px-4">
                    <Link to="/">{t('join.goToProjects')}</Link>
                  </Button>
                </div>
              </>
          )}

          {preview.status === 'ready' && (
              <>
                <div className="flex flex-col gap-2">
                  <span className="text-[13px] text-muted-foreground">{t('join.invitedTo')}</span>
                  <h1 className="text-[26px] font-semibold tracking-[-0.02em]">{preview.projectName}</h1>
                </div>
                <p className="text-[13px] text-muted-foreground">
                  {t('join.signedInInfo', {username})}
                </p>
                {joinFailed && <p className="text-[13px] text-destructive" role="alert">{t('join.invalid')}</p>}
                <div className="flex justify-end gap-2">
                  <Button asChild variant="outline" size="lg" className="rounded-lg px-4">
                    <Link to="/">{t('join.notNow')}</Link>
                  </Button>
                  <Button
                      size="lg"
                      className="rounded-lg px-4"
                      disabled={joining}
                      onClick={() => void join({projectId, token})}
                  >
                    {t('join.join')}
                  </Button>
                </div>
              </>
          )}
        </Card>
      </div>
  )
}
