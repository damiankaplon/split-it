import {type FormEvent, type ReactNode, useState} from 'react'
import {NumericFormat} from 'react-number-format'
import {useTranslation} from 'react-i18next'
import {ApiError} from '@/api/client'
import {type Debt, requestSettlement} from '@/api/debts'
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
import {decimalSeparator, formatAmount, toMinorUnits, zeroAmount} from '@/lib/money'

interface SettleDialogProps {
  projectId: string
  currency: Currency
  /** A debt of the current user. */
  debt: Debt
  creditorName: string
  /** Element that opens the dialog. */
  trigger: ReactNode
  /** Called after the request was sent, or when the debts must be reloaded because they changed. */
  onChanged: () => void
}

/** The debtor declares a payment; the creditor has to confirm it before the debt goes down. */
export function SettleDialog({projectId, currency, debt, creditorName, trigger, onChanged}: SettleDialogProps) {
  const {t, i18n} = useTranslation()
  const [open, setOpen] = useState(false)
  const [amount, setAmount] = useState<number | undefined>()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const amountInMinorUnits = amount === undefined ? 0 : toMinorUnits(amount, currency)
  const tooMuch = amountInMinorUnits > debt.amount
  const canSubmit = !submitting && amountInMinorUnits > 0 && !tooMuch
  const fullAmount = formatAmount(debt.amount, currency, i18n.language)

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) {
      // Paying everything back is the common case
      setAmount(debt.amount / 10 ** currency.minorUnits)
      setError(null)
    }
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await requestSettlement(projectId, debt.creditorId, amountInMinorUnits)
      onChanged()
      setOpen(false)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setError(t('settle.pendingError', {name: creditorName}))
        onChanged()
      } else {
        setError(t('settle.error'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogTrigger asChild>{trigger}</DialogTrigger>
        <DialogContent className="gap-5 rounded-xl bg-card shadow-[0_10px_30px_rgba(0,0,0,0.18)] sm:max-w-[420px]">
          <form onSubmit={(e) => void onSubmit(e)} className="contents">
            <DialogHeader className="gap-1.5">
              <DialogTitle className="text-lg">{t('settle.title', {name: creditorName})}</DialogTitle>
              <DialogDescription>
                {t('settle.description', {name: creditorName, amount: fullAmount})}
              </DialogDescription>
            </DialogHeader>

            <div className="flex flex-col gap-2">
              <Label htmlFor="settle-amount">{t('settle.amountLabel', {currency: currency.code})}</Label>
              <NumericFormat
                  customInput={Input}
                  id="settle-amount"
                  placeholder={zeroAmount(currency, i18n.language)}
                  className="h-10 rounded-lg tabular-nums"
                  decimalSeparator={decimalSeparator(i18n.language)}
                  allowedDecimalSeparators={[',', '.']}
                  decimalScale={currency.minorUnits}
                  allowNegative={false}
                  value={amount ?? ''}
                  onValueChange={({floatValue}) => setAmount(floatValue)}
                  aria-invalid={tooMuch}
                  autoFocus
                  required
              />
              <p className={tooMuch ? 'text-[13px] text-destructive' : 'text-[13px] text-muted-foreground'}>
                {tooMuch ? t('settle.tooMuch', {amount: fullAmount}) : t('settle.partialHint')}
              </p>
              {error && <p className="text-[13px] text-destructive" role="alert">{error}</p>}
            </div>

            <DialogFooter>
              <DialogClose asChild>
                <Button type="button" variant="outline" size="lg" className="rounded-lg px-4">
                  {t('settle.cancel')}
                </Button>
              </DialogClose>
              <Button type="submit" size="lg" className="rounded-lg px-4" disabled={!canSubmit}>
                {t('settle.submit')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
  )
}
