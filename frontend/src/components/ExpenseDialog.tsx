import {type FormEvent, type ReactNode, useState} from 'react'
import {NumericFormat} from 'react-number-format'
import {useTranslation} from 'react-i18next'
import {Temporal} from 'temporal-polyfill'
import {createExpense, type Expense, updateExpense} from '@/api/expenses'
import type {Currency} from '@/api/projects'
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
import {decimalSeparator, fitsInMinorUnits, toMinorUnits, zeroAmount} from '@/lib/money'

interface ExpenseDialogProps {
  projectId: string
  currency: Currency
  /** Existing project tags, offered as suggestions; any new name creates a tag. */
  tags: string[]
  /** The expense to edit; without it the dialog creates a new one. */
  expense?: Expense
  /** Element that opens the dialog. */
  trigger: ReactNode
  onSaved: (expense: Expense) => void
}

// datetime-local inputs take YYYY-MM-DDTHH:mm in local time
const toInputValue = (dateTime: Temporal.PlainDateTime) => dateTime.toString({smallestUnit: 'minute'})

export function ExpenseDialog({projectId, currency, tags, expense, trigger, onSaved}: ExpenseDialogProps) {
  const {t, i18n} = useTranslation()
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState<number | undefined>()
  const [date, setDate] = useState('')
  const [tag, setTag] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const editing = expense !== undefined
  const amountInMinorUnits = amount === undefined ? 0 : toMinorUnits(amount, currency)
  const canSubmit = !submitting && title.trim() !== '' && amountInMinorUnits > 0 && date !== ''

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) {
      setTitle(expense?.title ?? '')
      setAmount(expense && expense.amount / 10 ** currency.minorUnits)
      setDate(toInputValue(expense ? Temporal.PlainDateTime.from(expense.date) : Temporal.Now.plainDateTimeISO()))
      setTag(expense?.tag ?? '')
      setError(null)
    }
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    const request = {
      title: title.trim(),
      amount: amountInMinorUnits,
      date: Temporal.PlainDateTime.from(date).toString({smallestUnit: 'second'}),
      tag: tag.trim() || undefined,
    }
    try {
      onSaved(editing ? await updateExpense(projectId, expense.id, request) : await createExpense(projectId, request))
      setOpen(false)
    } catch {
      setError(editing ? t('expenseDialog.editError') : t('expenseDialog.addError'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogTrigger asChild>{trigger}</DialogTrigger>
        <DialogContent className="gap-5 rounded-xl bg-card shadow-[0_10px_30px_rgba(0,0,0,0.18)] sm:max-w-[480px]">
          <form onSubmit={(e) => void onSubmit(e)} className="contents">
            <DialogHeader className="gap-1.5">
              <DialogTitle className="text-lg">
                {editing ? t('expenseDialog.editTitle') : t('expenseDialog.addTitle')}
              </DialogTitle>
              <DialogDescription>{t('expenseDialog.description')}</DialogDescription>
            </DialogHeader>

            <div className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="expense-title">{t('expenseDialog.titleLabel')}</Label>
                <Input
                    id="expense-title"
                    placeholder={t('expenseDialog.titlePlaceholder')}
                    className="h-10 rounded-lg"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    autoFocus
                    required
                />
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div className="flex flex-col gap-2">
                  <Label htmlFor="expense-amount">{t('expenseDialog.amountLabel', {currency: currency.code})}</Label>
                  {/* Only positive amounts with at most the currency's decimal places can be typed */}
                  <NumericFormat
                      customInput={Input}
                      id="expense-amount"
                      placeholder={zeroAmount(currency, i18n.language)}
                      className="h-10 rounded-lg tabular-nums"
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
                  <Label htmlFor="expense-date">{t('expenseDialog.dateLabel')}</Label>
                  <Input
                      id="expense-date"
                      type="datetime-local"
                      className="h-10 rounded-lg"
                      value={date}
                      onChange={(e) => setDate(e.target.value)}
                      required
                  />
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <Label htmlFor="expense-tag">
                  {t('expenseDialog.tagLabel')}
                  <span className="font-normal text-muted-foreground">{t('expenseDialog.optional')}</span>
                </Label>
                <Input
                    id="expense-tag"
                    list="expense-tag-options"
                    placeholder={t('expenseDialog.tagPlaceholder')}
                    className="h-10 rounded-lg"
                    value={tag}
                    onChange={(e) => setTag(e.target.value)}
                    autoComplete="off"
                />
                <datalist id="expense-tag-options">
                  {tags.map((name) => <option key={name} value={name}/>)}
                </datalist>
              </div>

              {error && <p className="text-[13px] text-destructive" role="alert">{error}</p>}
            </div>

            <DialogFooter>
              <DialogClose asChild>
                <Button type="button" variant="outline" size="lg"
                        className="rounded-lg px-4">{t('expenseDialog.cancel')}</Button>
              </DialogClose>
              <Button type="submit" size="lg" className="rounded-lg px-4" disabled={!canSubmit}>
                {editing ? t('expenseDialog.save') : t('expenseDialog.add')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
  )
}
