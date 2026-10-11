import {type FormEvent, type ReactNode, useState} from 'react'
import {NumericFormat} from 'react-number-format'
import {useTranslation} from 'react-i18next'
import {Temporal} from 'temporal-polyfill'
import {createPersonalExpense, type PersonalExpense, updatePersonalExpense} from '@/api/personal'
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
import {currencyOf, decimalSeparator, fitsInMinorUnits, PROJECT_CURRENCIES, toMinorUnits, zeroAmount} from '@/lib/money'

interface PersonalExpenseDialogProps {
  /** Currency preselected for a new expense. */
  defaultCurrency: string
  /** The user's existing tags, offered as suggestions; any new name creates a tag. */
  tags: string[]
  /** The expense to edit; without it the dialog creates a new one. */
  expense?: PersonalExpense
  /** Element that opens the dialog. */
  trigger: ReactNode
  onSaved: (expense: PersonalExpense) => void
}

// datetime-local inputs take YYYY-MM-DDTHH:mm in local time
const toInputValue = (dateTime: Temporal.PlainDateTime) => dateTime.toString({smallestUnit: 'minute'})

export function PersonalExpenseDialog({defaultCurrency, tags, expense, trigger, onSaved}: PersonalExpenseDialogProps) {
  const {t, i18n} = useTranslation()
  const currencyNames = new Intl.DisplayNames(i18n.language, {type: 'currency'})
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState<number | undefined>()
  const [currencyCode, setCurrencyCode] = useState(defaultCurrency)
  const [date, setDate] = useState('')
  const [tag, setTag] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const editing = expense !== undefined
  const currency = currencyOf(currencyCode)
  // An edited expense may use a currency outside the offered list
  const currencyOptions = PROJECT_CURRENCIES.includes(currencyCode) ? PROJECT_CURRENCIES : [currencyCode, ...PROJECT_CURRENCIES]
  const amountInMinorUnits = amount === undefined ? 0 : toMinorUnits(amount, currency)
  const canSubmit = !submitting && title.trim() !== '' && amountInMinorUnits > 0
      && fitsInMinorUnits(amount ?? 0, currency) && date !== ''

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) {
      setTitle(expense?.title ?? '')
      setAmount(expense && expense.amount / 10 ** expense.currency.minorUnits)
      setCurrencyCode(expense?.currency.code ?? defaultCurrency)
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
      currency: currencyCode,
      date: Temporal.PlainDateTime.from(date).toString({smallestUnit: 'second'}),
      tag: tag.trim() || undefined,
    }
    try {
      onSaved(editing ? await updatePersonalExpense(expense.id, request) : await createPersonalExpense(request))
      setOpen(false)
    } catch {
      setError(editing ? t('personalExpenseDialog.editError') : t('personalExpenseDialog.addError'))
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
                {editing ? t('personalExpenseDialog.editTitle') : t('personalExpenseDialog.addTitle')}
              </DialogTitle>
              <DialogDescription>{t('personalExpenseDialog.description')}</DialogDescription>
            </DialogHeader>

            <div className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="personal-expense-title">{t('personalExpenseDialog.titleLabel')}</Label>
                <Input
                    id="personal-expense-title"
                    placeholder={t('personalExpenseDialog.titlePlaceholder')}
                    className="h-10 rounded-lg"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    autoFocus
                    required
                />
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div className="flex flex-col gap-2">
                  <Label htmlFor="personal-expense-amount">{t('personalExpenseDialog.amountLabel')}</Label>
                  {/* Only positive amounts with at most the currency's decimal places can be typed */}
                  <NumericFormat
                      customInput={Input}
                      id="personal-expense-amount"
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
                  <Label htmlFor="personal-expense-currency">{t('personalExpenseDialog.currencyLabel')}</Label>
                  <select
                      id="personal-expense-currency"
                      className="h-10 w-full rounded-lg border border-input bg-transparent px-3 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 md:text-sm dark:bg-input/30"
                      value={currencyCode}
                      onChange={(e) => setCurrencyCode(e.target.value)}
                  >
                    {currencyOptions.map((code) => (
                        <option key={code} value={code}>{currencyNames.of(code)} ({code})</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <Label htmlFor="personal-expense-date">{t('personalExpenseDialog.dateLabel')}</Label>
                <Input
                    id="personal-expense-date"
                    type="datetime-local"
                    className="h-10 rounded-lg"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    required
                />
              </div>

              <div className="flex flex-col gap-2">
                <Label htmlFor="personal-expense-tag">
                  {t('personalExpenseDialog.tagLabel')}
                  <span className="font-normal text-muted-foreground">{t('personalExpenseDialog.optional')}</span>
                </Label>
                <Input
                    id="personal-expense-tag"
                    list="personal-expense-tag-options"
                    placeholder={t('personalExpenseDialog.tagPlaceholder')}
                    className="h-10 rounded-lg"
                    value={tag}
                    onChange={(e) => setTag(e.target.value)}
                    autoComplete="off"
                />
                <datalist id="personal-expense-tag-options">
                  {tags.map((name) => <option key={name} value={name}/>)}
                </datalist>
              </div>

              {error && <p className="text-[13px] text-destructive" role="alert">{error}</p>}
            </div>

            <DialogFooter>
              <DialogClose asChild>
                <Button type="button" variant="outline" size="lg"
                        className="rounded-lg px-4">{t('personalExpenseDialog.cancel')}</Button>
              </DialogClose>
              <Button type="submit" size="lg" className="rounded-lg px-4" disabled={!canSubmit}>
                {editing ? t('personalExpenseDialog.save') : t('personalExpenseDialog.add')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
  )
}
