import {type FormEvent, type ReactNode, useState} from 'react'
import {NumericFormat} from 'react-number-format'
import {useTranslation} from 'react-i18next'
import {createIncome, type Income, updateIncome} from '@/api/personal'
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
import {inputValueToInstant, instantToInputValue, nowInputValue} from '@/lib/instants'
import {currencyOf, decimalSeparator, fitsInMinorUnits, PROJECT_CURRENCIES, toMinorUnits, zeroAmount} from '@/lib/money'

interface IncomeDialogProps {
  /** Currency preselected for a new income. */
  defaultCurrency: string
  /** The income to edit; without it the dialog creates a new one. */
  income?: Income
  /** Element that opens the dialog. */
  trigger: ReactNode
  onSaved: (income: Income) => void
}

const SELECT_CLASS = 'h-10 w-full rounded-lg border border-input bg-transparent px-3 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 md:text-sm dark:bg-input/30'

export function IncomeDialog({defaultCurrency, income, trigger, onSaved}: IncomeDialogProps) {
  const {t, i18n} = useTranslation()
  const currencyNames = new Intl.DisplayNames(i18n.language, {type: 'currency'})
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState<number | undefined>()
  const [currencyCode, setCurrencyCode] = useState(defaultCurrency)
  const [date, setDate] = useState('')
  const [delimiting, setDelimiting] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const editing = income !== undefined
  const currency = currencyOf(currencyCode)
  // An edited income may use a currency outside the offered list
  const currencyOptions = PROJECT_CURRENCIES.includes(currencyCode) ? PROJECT_CURRENCIES : [currencyCode, ...PROJECT_CURRENCIES]
  const amountInMinorUnits = amount === undefined ? 0 : toMinorUnits(amount, currency)
  const canSubmit = !submitting && title.trim() !== '' && amountInMinorUnits > 0
      && fitsInMinorUnits(amount ?? 0, currency) && date !== ''

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) {
      setTitle(income?.title ?? '')
      setAmount(income && income.amount / 10 ** income.currency.minorUnits)
      setCurrencyCode(income?.currency.code ?? defaultCurrency)
      setDate(income ? instantToInputValue(income.date) : nowInputValue())
      setDelimiting(income?.delimiting ?? true)
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
      date: inputValueToInstant(date),
      delimiting,
    }
    try {
      onSaved(editing ? await updateIncome(income.id, request) : await createIncome(request))
      setOpen(false)
    } catch {
      setError(editing ? t('incomeDialog.editError') : t('incomeDialog.addError'))
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
                {editing ? t('incomeDialog.editTitle') : t('incomeDialog.addTitle')}
              </DialogTitle>
              <DialogDescription>{t('incomeDialog.description')}</DialogDescription>
            </DialogHeader>

            <div className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="income-title">{t('incomeDialog.titleLabel')}</Label>
                <Input
                    id="income-title"
                    placeholder={t('incomeDialog.titlePlaceholder')}
                    className="h-10 rounded-lg"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    autoFocus
                    required
                />
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div className="flex flex-col gap-2">
                  <Label htmlFor="income-amount">{t('incomeDialog.amountLabel')}</Label>
                  <NumericFormat
                      customInput={Input}
                      id="income-amount"
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
                  <Label htmlFor="income-currency">{t('incomeDialog.currencyLabel')}</Label>
                  <select
                      id="income-currency"
                      className={SELECT_CLASS}
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
                <Label htmlFor="income-date">{t('incomeDialog.dateLabel')}</Label>
                <Input
                    id="income-date"
                    type="datetime-local"
                    className="h-10 rounded-lg"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    required
                />
              </div>

              <div className="flex flex-col gap-2">
                <Label htmlFor="income-type">{t('incomeDialog.typeLabel')}</Label>
                <select
                    id="income-type"
                    className={SELECT_CLASS}
                    value={delimiting ? 'delimiting' : 'other'}
                    onChange={(e) => setDelimiting(e.target.value === 'delimiting')}
                >
                  <option value="delimiting">{t('incomeDialog.delimiting')}</option>
                  <option value="other">{t('incomeDialog.nonDelimiting')}</option>
                </select>
                <p className="text-[13px] text-muted-foreground">{t('incomeDialog.typeHint')}</p>
              </div>

              {error && <p className="text-[13px] text-destructive" role="alert">{error}</p>}
            </div>

            <DialogFooter>
              <DialogClose asChild>
                <Button type="button" variant="outline" size="lg"
                        className="rounded-lg px-4">{t('incomeDialog.cancel')}</Button>
              </DialogClose>
              <Button type="submit" size="lg" className="rounded-lg px-4" disabled={!canSubmit}>
                {editing ? t('incomeDialog.save') : t('incomeDialog.add')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
  )
}
