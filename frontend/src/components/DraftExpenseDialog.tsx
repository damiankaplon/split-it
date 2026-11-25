import {type FormEvent, type ReactNode, useState} from 'react'
import {NumericFormat} from 'react-number-format'
import {useTranslation} from 'react-i18next'
import {Button} from '@/components/ui/button'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import type {DraftExpense} from '@/lib/aiImport'
import {decimalSeparator, fitsInMinorUnits, toMinorUnits, zeroAmount} from '@/lib/money'

interface DraftExpenseDialogProps {
  draft: DraftExpense
  tags: string[]
  trigger: ReactNode
  onSaved: (draft: DraftExpense) => void
}

export function DraftExpenseDialog({draft, tags, trigger, onSaved}: DraftExpenseDialogProps) {
  const {t, i18n} = useTranslation()
  const currency = draft.currency
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState<number | undefined>()
  const [date, setDate] = useState('')
  const [tag, setTag] = useState('')

  const amountInMinorUnits = amount === undefined ? 0 : toMinorUnits(amount, currency)
  const canSubmit = title.trim() !== '' && amountInMinorUnits > 0 && date !== ''

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) {
      setTitle(draft.title)
      setAmount(draft.amount === undefined ? undefined : draft.amount / 10 ** currency.minorUnits)
      setDate(draft.date)
      setTag(draft.tag)
    }
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    onSaved({...draft, title: title.trim(), amount: amountInMinorUnits, date, tag: tag.trim()})
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="gap-5 sm:max-w-[480px]">
        <form onSubmit={onSubmit} className="contents">
          <DialogHeader className="gap-1.5">
            <DialogTitle>{t('aiImport.editTitle')}</DialogTitle>
          </DialogHeader>

          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="draft-title">{t('expenseDialog.titleLabel')}</Label>
              <Input
                id="draft-title"
                placeholder={t('expenseDialog.titlePlaceholder')}
                className="h-11 rounded-lg"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                autoFocus
                required
              />
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="flex flex-col gap-2">
                <Label htmlFor="draft-amount">{t('expenseDialog.amountLabel', {currency: currency.code})}</Label>
                <NumericFormat
                  customInput={Input}
                  id="draft-amount"
                  placeholder={zeroAmount(currency, i18n.language)}
                  className="h-11 rounded-lg tabular-nums"
                  decimalSeparator={decimalSeparator(i18n.language)}
                  allowedDecimalSeparators={[',', '.']}
                  decimalScale={currency.minorUnits}
                  allowNegative={false}
                  isAllowed={({floatValue}) => floatValue === undefined || fitsInMinorUnits(floatValue, currency)}
                  value={amount ?? ''}
                  onValueChange={({floatValue}) => setAmount(floatValue)}
                  required
                />
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="draft-date">{t('expenseDialog.dateLabel')}</Label>
                <Input
                  id="draft-date"
                  type="datetime-local"
                  className="h-11 rounded-lg"
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="draft-tag">
                {t('expenseDialog.tagLabel')}
                <span className="font-normal text-muted-foreground">{t('expenseDialog.optional')}</span>
              </Label>
              <Input
                id="draft-tag"
                list="draft-tag-options"
                placeholder={t('expenseDialog.tagPlaceholder')}
                className="h-11 rounded-lg"
                value={tag}
                onChange={(e) => setTag(e.target.value)}
                autoComplete="off"
              />
              <datalist id="draft-tag-options">
                {tags.map((name) => (
                  <option key={name} value={name} />
                ))}
              </datalist>
            </div>
          </div>

          <DialogFooter>
            <DialogClose asChild>
              <Button type="button" variant="outline" size="lg" className="rounded-lg px-4">
                {t('aiImport.cancel')}
              </Button>
            </DialogClose>
            <Button type="submit" size="lg" className="rounded-lg px-4" disabled={!canSubmit}>
              {t('aiImport.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
