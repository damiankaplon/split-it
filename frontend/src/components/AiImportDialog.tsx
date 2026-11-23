import {type FormEvent, type ReactNode, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {useNavigate} from 'react-router'
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
import {aiImportPath, type AiImportState} from '@/lib/aiImport'

/** Picks a file and hands it to the review page, which waits for the recognition. */
export function AiImportDialog({projectId, trigger}: {projectId: string; trigger: ReactNode}) {
  const {t} = useTranslation()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [file, setFile] = useState<File | null>(null)

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) setFile(null)
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (file) void navigate(aiImportPath(projectId), {state: {file} satisfies AiImportState})
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="gap-5 rounded-xl bg-card shadow-[0_10px_30px_rgba(0,0,0,0.18)] sm:max-w-[480px]">
        <form onSubmit={onSubmit} className="contents">
          <DialogHeader className="gap-1.5">
            <DialogTitle className="text-lg">{t('aiImport.dialogTitle')}</DialogTitle>
            <DialogDescription>{t('aiImport.dialogDescription')}</DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-2">
            <Label htmlFor="ai-import-file">{t('aiImport.fileLabel')}</Label>
            <Input
              id="ai-import-file"
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp,application/pdf,.pdf,text/csv,.csv"
              className="h-10 rounded-lg"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              required
            />
            <p className="text-[13px] text-muted-foreground">{t('aiImport.fileHint')}</p>
          </div>

          <DialogFooter>
            <DialogClose asChild>
              <Button type="button" variant="outline" size="lg" className="rounded-lg px-4">
                {t('aiImport.cancel')}
              </Button>
            </DialogClose>
            <Button type="submit" size="lg" className="rounded-lg px-4" disabled={!file}>
              {t('aiImport.upload')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
