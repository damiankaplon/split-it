import {useEffect, useState} from 'react'
import {Check, Copy, UserPlus} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {createInvitation, invitationUrl, listMembers, type Project, type ProjectMember,} from '@/api/projects'
import {Button} from '@/components/ui/button'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {initials} from '@/lib/initials'

type LinkState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; url: string }

interface InviteDialogProps {
  project: Project
  currentUserId: string
}

export function InviteDialog({project, currentUserId}: InviteDialogProps) {
  const {t} = useTranslation()
  const [open, setOpen] = useState(false)
  const [link, setLink] = useState<LinkState>({status: 'loading'})
  const [copied, setCopied] = useState(false)

  // Created on user action rather than in an effect: every call mints a new single-use token.
  const generateLink = async () => {
    setLink({status: 'loading'})
    setCopied(false)
    try {
      setLink({status: 'ready', url: invitationUrl(await createInvitation(project.id))})
    } catch {
      setLink({status: 'error'})
    }
  }

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) void generateLink()
  }

  const copy = async (url: string) => {
    await navigator.clipboard.writeText(url)
    setCopied(true)
  }

  return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogTrigger asChild>
          <Button variant="outline" className="rounded-lg">
            <UserPlus/>
            {t('invite.button')}
          </Button>
        </DialogTrigger>
        <DialogContent className="gap-5 rounded-xl bg-card shadow-[0_10px_30px_rgba(0,0,0,0.18)] sm:max-w-[480px]">
          <DialogHeader className="gap-1.5">
            <DialogTitle className="text-lg">{t('invite.title', {project: project.name})}</DialogTitle>
            <DialogDescription>{t('invite.description')}</DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-2">
            <Label htmlFor="invite-link">{t('invite.linkLabel')}</Label>
            <div className="flex gap-2">
              <Input
                  id="invite-link"
                  readOnly
                  className="h-10 grow rounded-lg bg-background text-zinc-700"
                  value={link.status === 'ready' ? link.url : link.status === 'loading' ? t('invite.creating') : ''}
                  onFocus={(e) => e.currentTarget.select()}
              />
              <Button
                  size="lg"
                  className="rounded-lg px-3.5"
                  disabled={link.status !== 'ready'}
                  onClick={() => link.status === 'ready' && void copy(link.url)}
              >
                {copied ? <Check/> : <Copy/>}
                {copied ? t('invite.copied') : t('invite.copy')}
              </Button>
            </div>
            <div className="flex items-center justify-between text-[13px] text-muted-foreground">
              {link.status === 'error' ? (
                  <span className="text-destructive" role="alert">{t('invite.error')}</span>
              ) : (
                  <span>{t('invite.singleUse')}</span>
              )}
              <Button
                  variant="ghost"
                  size="sm"
                  className="h-8 px-2 text-[13px]"
                  disabled={link.status === 'loading'}
                  onClick={() => void generateLink()}
              >
                {t('invite.newLink')}
              </Button>
            </div>
          </div>

          <MemberList project={project} currentUserId={currentUserId}/>

          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline" size="lg" className="rounded-lg px-4">{t('invite.done')}</Button>
            </DialogClose>
          </DialogFooter>
        </DialogContent>
      </Dialog>
  )
}

function MemberList({project, currentUserId}: InviteDialogProps) {
  const {t} = useTranslation()
  const [members, setMembers] = useState<ProjectMember[] | null>(null)

  useEffect(() => {
    listMembers(project.id).then(setMembers, () => setMembers([]))
  }, [project.id])

  return (
      <div className="flex flex-col gap-2.5 border-t pt-4">
        <h3 className="font-medium">{t('invite.members')}</h3>
        {members === null ? (
            <p className="text-muted-foreground">{t('invite.loadingMembers')}</p>
        ) : (
            <ul className="flex flex-col gap-2.5">
              {members.map((member) => (
                  <li key={member.memberId} className="flex items-center gap-2.5">
              <span
                  className="inline-flex size-7 items-center justify-center rounded-full bg-secondary text-[11px] font-semibold">
                {initials(member.name)}
              </span>
                    <span className="grow">
                {member.name}
                      {member.memberId === currentUserId && ` ${t('invite.you')}`}
              </span>
                    <span className="text-[13px] text-muted-foreground">
                {member.memberId === project.ownerId ? t('projects.owner') : t('projects.member')}
              </span>
                  </li>
              ))}
            </ul>
        )}
      </div>
  )
}
