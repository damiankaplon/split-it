import {type ReactNode, useEffect, useState} from 'react'
import {AlertCircle, ChevronUp, Pencil, Plus, SlidersHorizontal, Trash2} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {getSavings, type Income, listIncomes, type Savings} from '@/api/personal'
import {DateRangeFilter} from '@/components/DateRangeFilter'
import {DeleteIncomeDialog} from '@/components/DeleteIncomeDialog'
import {IncomeDialog} from '@/components/IncomeDialog'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Button} from '@/components/ui/button'
import {Card} from '@/components/ui/card'
import {type DateRange, EMPTY_DATE_RANGE, isDateRangeSet} from '@/lib/dateRange'
import {endOfDay, formatInstant, nowZoned, startOfDay, toZonedParam} from '@/lib/instants'
import {formatAmount} from '@/lib/money'
import {cn} from '@/lib/utils'

const DEFAULT_CURRENCY = 'PLN'

type LoadState<T> =
    | { status: 'loading' }
    | { status: 'error' }
    | { status: 'loaded'; items: T[] }

const formatDate = (instant: string, locale: string) => formatInstant(instant, locale, {dateStyle: 'medium'})

/** The picked days in the user's zone, as zoned ISO date-times; without a range, the last year up to now. */
function zonedRange(range: DateRange): { from: string; to: string } {
  const to = range.to === '' ? nowZoned() : endOfDay(range.to)
  const from = range.from === '' ? to.subtract({years: 1}) : startOfDay(range.from)
  return {from: toZonedParam(from), to: toZonedParam(to)}
}

/** What the user has saved between their delimiting incomes, one period per currency and delimiting income. */
export function SavingsPage() {
  const {t, i18n} = useTranslation()
  // Empty means the backend's default: the last year
  const [dates, setDates] = useState<DateRange>(EMPTY_DATE_RANGE)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [savings, setSavings] = useState<LoadState<Savings>>({status: 'loading'})
  const [incomes, setIncomes] = useState<LoadState<Income>>({status: 'loading'})
  const [lastCurrency, setLastCurrency] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const {from, to} = zonedRange(dates)
    getSavings(from, to).then(
        (items) => {
          if (!cancelled) setSavings({status: 'loaded', items})
        },
        () => {
          if (!cancelled) setSavings({status: 'error'})
        })
    return () => {
      cancelled = true
    }
  }, [dates, reloadKey])

  // The income list isn't affected by the date range
  useEffect(() => {
    let cancelled = false
    listIncomes().then(
        (items) => {
          if (cancelled) return
          setIncomes({status: 'loaded', items})
          setLastCurrency((current) => current ?? items[0]?.currency.code ?? null)
        },
        () => {
          if (!cancelled) setIncomes({status: 'error'})
        })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  const reload = () => setReloadKey((k) => k + 1)

  const onCreated = (income: Income) => {
    setLastCurrency(income.currency.code)
    reload()
  }

  return (
      <main className="mx-auto flex w-full max-w-[1200px] flex-col gap-6 px-6 pt-7 pb-12">
        <div className="flex flex-col gap-1">
          <div className="flex items-center justify-between gap-3">
            <h1 className="text-[26px] font-semibold tracking-[-0.02em]">{t('savings.title')}</h1>
            <IncomeDialog
                defaultCurrency={lastCurrency ?? DEFAULT_CURRENCY}
                onSaved={onCreated}
                trigger={
                  <Button size="icon" className="rounded-lg" aria-label={t('incomeDialog.add')}
                          title={t('incomeDialog.add')}>
                    <Plus/>
                  </Button>
                }
            />
          </div>
          <p className="text-muted-foreground">{t('savings.subtitle')}</p>
        </div>

        <section className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <Button variant="outline" size="icon" className="size-10 shrink-0 rounded-lg bg-card"
                    aria-expanded={filtersOpen}
                    aria-label={filtersOpen ? t('project.hideFilters') : t('project.showFilters')}
                    title={filtersOpen ? t('project.hideFilters') : t('project.showFilters')}
                    onClick={() => setFiltersOpen((open) => !open)}>
              {filtersOpen ? <ChevronUp/> : <SlidersHorizontal/>}
            </Button>
            {filtersOpen && <DateRangeFilter value={dates} onChange={setDates}/>}
            {!isDateRangeSet(dates) &&
                <span className="text-[13px] text-muted-foreground">{t('savings.lastYear')}</span>}
          </div>

          {savings.status === 'loading' && <p className="text-muted-foreground">{t('savings.loading')}</p>}

          {savings.status === 'error' && <LoadError title={t('savings.loadError')} onRetry={reload}/>}

          {savings.status === 'loaded' && savings.items.length === 0 && (
              <Card className="items-center gap-2 px-6 py-12 text-center shadow-xs">
                <span className="font-medium">{t('savings.empty')}</span>
                <span className="text-muted-foreground">{t('savings.emptyHint')}</span>
              </Card>
          )}

          {savings.status === 'loaded' && savings.items.length > 0 && (
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {savings.items.map((period) => (
                    <SavingsCard key={`${period.currency.code}-${period.start}`} period={period}
                                 locale={i18n.language}/>
                ))}
              </div>
          )}
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="text-lg font-semibold">{t('savings.incomes')}</h2>

          {incomes.status === 'loading' && <p className="text-muted-foreground">{t('savings.loadingIncomes')}</p>}

          {incomes.status === 'error' && <LoadError title={t('savings.incomesError')} onRetry={reload}/>}

          {incomes.status === 'loaded' && incomes.items.length === 0 && (
              <Card className="items-center px-6 py-8 text-center text-muted-foreground shadow-xs">
                {t('savings.noIncomes')}
              </Card>
          )}

          {incomes.status === 'loaded' && incomes.items.length > 0 && (
              <Card className="gap-0 py-0 shadow-xs">
                <ul className="divide-y">
                  {incomes.items.map((income) => (
                      <IncomeRow
                          key={income.id}
                          income={income}
                          locale={i18n.language}
                          actions={
                            <>
                              <IncomeDialog
                                  defaultCurrency={income.currency.code}
                                  income={income}
                                  onSaved={reload}
                                  trigger={
                                    <Button variant="ghost" size="icon-sm" className="rounded-lg text-muted-foreground"
                                            aria-label={t('incomeDialog.edit', {title: income.title})}>
                                      <Pencil/>
                                    </Button>
                                  }
                              />
                              <DeleteIncomeDialog
                                  income={income}
                                  onDeleted={reload}
                                  trigger={
                                    <Button variant="ghost" size="icon-sm"
                                            className="rounded-lg text-muted-foreground hover:text-destructive"
                                            aria-label={t('incomeDeleteDialog.trigger', {title: income.title})}>
                                      <Trash2/>
                                    </Button>
                                  }
                              />
                            </>
                          }
                      />
                  ))}
                </ul>
              </Card>
          )}
        </section>
      </main>
  )
}

function LoadError({title, onRetry}: { title: string; onRetry: () => void }) {
  const {t} = useTranslation()
  return (
      <Alert variant="destructive" className="max-w-xl">
        <AlertCircle/>
        <AlertTitle>{title}</AlertTitle>
        <AlertDescription>
          <Button variant="link" className="h-auto p-0 text-destructive underline" onClick={onRetry}>
            {t('project.retry')}
          </Button>
        </AlertDescription>
      </Alert>
  )
}

function SavingsCard({period, locale}: { period: Savings; locale: string }) {
  const {t} = useTranslation()
  const {currency} = period
  return (
      <Card className="gap-3 px-5 py-4 shadow-xs">
        <div className="flex flex-col gap-0.5">
          <span className="text-[13px] text-muted-foreground">
            {formatDate(period.start, locale)} – {period.end ? formatDate(period.end, locale) : t('savings.now')}
          </span>
          <span
              className={cn('text-2xl font-semibold tabular-nums', period.saved < 0 && 'text-destructive')}>
            {formatAmount(period.saved, currency, locale)}
          </span>
        </div>
        <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 text-sm">
          <dt className="text-muted-foreground">{t('savings.income')}</dt>
          <dd className="tabular-nums">{formatAmount(period.income, currency, locale)}</dd>
          <dt className="text-muted-foreground">{t('savings.expenses')}</dt>
          <dd className="tabular-nums">{formatAmount(period.expenses, currency, locale)}</dd>
        </dl>
      </Card>
  )
}

function IncomeRow({income, locale, actions}: { income: Income; locale: string; actions: ReactNode }) {
  const {t} = useTranslation()
  return (
      <li className="flex items-center gap-3 px-4 py-3">
        <div className="flex min-w-0 grow flex-col gap-0.5">
          <span className="truncate font-medium">{income.title}</span>
          <span className="flex flex-wrap items-center gap-x-2 text-[13px] text-muted-foreground">
            <span>{formatDate(income.date, locale)}</span>
            <span>{income.delimiting ? t('incomeDialog.delimiting') : t('incomeDialog.nonDelimiting')}</span>
          </span>
        </div>
        <span className="shrink-0 font-semibold tabular-nums">
          {formatAmount(income.amount, income.currency, locale)}
        </span>
        <div className="flex shrink-0 items-center">{actions}</div>
      </li>
  )
}
