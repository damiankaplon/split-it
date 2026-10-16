import {type ReactNode, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {deleteIncome, type Income} from '@/api/personal'
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

interface DeleteIncomeDialogProps {
  income: Income
  /** Element that opens the dialog. */
  trigger: ReactNode
  onDeleted: (income: Income) => void
}

export function DeleteIncomeDialog({income, trigger, onDeleted}: DeleteIncomeDialogProps) {
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
      await deleteIncome(income.id)
      setOpen(false)
      onDeleted(income)
    } catch {
      setError(t('incomeDeleteDialog.error'))
    } finally {
      setDeleting(false)
    }
  }

  return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogTrigger asChild>{trigger}</DialogTrigger>
        <DialogContent className="gap-5 rounded-xl bg-card shadow-[0_10px_30px_rgba(0,0,0,0.18)] sm:max-w-[420px]">
          <DialogHeader className="gap-1.5">
            <DialogTitle className="text-lg">{t('incomeDeleteDialog.title')}</DialogTitle>
            <DialogDescription>{t('incomeDeleteDialog.description', {title: income.title})}</DialogDescription>
          </DialogHeader>
          {error && <p className="text-[13px] text-destructive" role="alert">{error}</p>}
          <DialogFooter>
            <DialogClose asChild>
              <Button type="button" variant="outline" size="lg"
                      className="rounded-lg px-4">{t('incomeDeleteDialog.cancel')}</Button>
            </DialogClose>
            <Button type="button" variant="destructive" size="lg" className="rounded-lg px-4" disabled={deleting}
                    onClick={() => void onConfirm()}>
              {t('incomeDeleteDialog.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
  )
}
