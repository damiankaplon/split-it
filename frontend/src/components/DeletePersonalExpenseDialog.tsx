import {type ReactNode, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {deletePersonalExpense, type PersonalExpense} from '@/api/personal'
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

interface DeletePersonalExpenseDialogProps {
  expense: PersonalExpense
  /** Element that opens the dialog. */
  trigger: ReactNode
  onDeleted: (expense: PersonalExpense) => void
}

export function DeletePersonalExpenseDialog({expense, trigger, onDeleted}: DeletePersonalExpenseDialogProps) {
  const {t} = useTranslation()
  const [open, setOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) setError(null)
  }

  const onConfirm = async () => {
    setDeleting(true)
    setError(null)
    try {
      await deletePersonalExpense(expense.id)
      setOpen(false)
      onDeleted(expense)
    } catch {
      setError(t('personalDeleteDialog.error'))
    } finally {
      setDeleting(false)
    }
  }

  return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogTrigger asChild>{trigger}</DialogTrigger>
        <DialogContent className="gap-5 rounded-xl bg-card shadow-[0_10px_30px_rgba(0,0,0,0.18)] sm:max-w-[420px]">
          <DialogHeader className="gap-1.5">
            <DialogTitle className="text-lg">{t('personalDeleteDialog.title')}</DialogTitle>
            <DialogDescription>{t('personalDeleteDialog.description', {title: expense.title})}</DialogDescription>
          </DialogHeader>
          {error && <p className="text-[13px] text-destructive" role="alert">{error}</p>}
          <DialogFooter>
            <DialogClose asChild>
              <Button type="button" variant="outline" size="lg"
                      className="rounded-lg px-4">{t('personalDeleteDialog.cancel')}</Button>
            </DialogClose>
            <Button type="button" variant="destructive" size="lg" className="rounded-lg px-4" disabled={deleting}
                    onClick={() => void onConfirm()}>
              {t('personalDeleteDialog.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
  )
}
