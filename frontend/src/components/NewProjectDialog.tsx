import {type FormEvent, useState} from 'react'
import {Plus} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {createProject, type Project} from '@/api/projects'
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

interface NewProjectDialogProps {
  onCreated: (project: Project) => void
}

export function NewProjectDialog({onCreated}: NewProjectDialogProps) {
  const {t} = useTranslation()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (!next) {
      setName('')
      setError(null)
    }
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      onCreated(await createProject(name.trim()))
      onOpenChange(false)
    } catch {
      setError(t('newProject.error'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogTrigger asChild>
          <Button size="lg" className="rounded-lg px-4">
            <Plus/>
            {t('newProject.button')}
          </Button>
        </DialogTrigger>
        <DialogContent className="gap-5 rounded-xl bg-card shadow-[0_10px_30px_rgba(0,0,0,0.18)] sm:max-w-[480px]">
          <form onSubmit={(e) => void onSubmit(e)} className="contents">
            <DialogHeader className="gap-1.5">
              <DialogTitle className="text-lg">{t('newProject.title')}</DialogTitle>
              <DialogDescription>{t('newProject.description')}</DialogDescription>
            </DialogHeader>
            <div className="flex flex-col gap-2">
              <Label htmlFor="new-project-name">{t('newProject.nameLabel')}</Label>
              <Input
                  id="new-project-name"
                  placeholder={t('newProject.namePlaceholder')}
                  className="h-10 rounded-lg"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  autoFocus
                  required
              />
              {error && <p className="text-[13px] text-destructive" role="alert">{error}</p>}
            </div>
            <DialogFooter>
              <DialogClose asChild>
                <Button type="button" variant="outline" size="lg"
                        className="rounded-lg px-4">{t('newProject.cancel')}</Button>
              </DialogClose>
              <Button type="submit" size="lg" className="rounded-lg px-4" disabled={submitting || !name.trim()}>
                {t('newProject.submit')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
  )
}
