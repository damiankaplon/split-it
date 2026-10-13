import {useEffect, useState} from 'react'
import {AlertCircle} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {Temporal} from 'temporal-polyfill'
import type {TagTotal} from '@/api/expenses'
import type {Currency} from '@/api/projects'
import {TagBadge} from '@/components/TagBadge'
import {Alert, AlertTitle} from '@/components/ui/alert'
import {Card} from '@/components/ui/card'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {formatAmount} from '@/lib/money'

const CHART_HEIGHT_PX = 240
const TICK_COUNT = 4

export interface CurrencyTagTotal extends TagTotal {
  currency: Currency
}

type TotalsState =
    | { status: 'loading' }
    | { status: 'error' }
    | { status: 'loaded'; totals: CurrencyTagTotal[] }

interface TagTotalsChartsProps {
  /** Both dates are ISO (YYYY-MM-DD) and inclusive. */
  load: (from: string, to: string) => Promise<CurrencyTagTotal[]>
  /** Bumped on every expense change, so the sums are refetched. */
  reloadKey: number
}

/** Bar charts of the expense sums per tag in a date range, one per currency; the last month by default. */
export function TagTotalsCharts({load, reloadKey}: TagTotalsChartsProps) {
  const {t, i18n} = useTranslation()
  const [to, setTo] = useState(() => Temporal.Now.plainDateISO().toString())
  const [from, setFrom] = useState(() => Temporal.Now.plainDateISO().subtract({months: 1}).toString())
  const [totals, setTotals] = useState<TotalsState>({status: 'loading'})
  // Cleared date inputs report an empty value
  const validRange = from !== '' && to !== '' && Temporal.PlainDate.compare(from, to) <= 0

  useEffect(() => {
    if (!validRange) return
    // The previous range's chart stays until the new sums arrive; stale results are dropped
    let cancelled = false
    load(from, to).then(
        (result) => {
          if (!cancelled) setTotals({status: 'loaded', totals: result})
        },
        () => {
          if (!cancelled) setTotals({status: 'error'})
        })
    return () => {
      cancelled = true
    }
  }, [load, from, to, validRange, reloadKey])

  const byCurrency = new Map<string, CurrencyTagTotal[]>()
  if (totals.status === 'loaded') {
    for (const total of totals.totals) {
      byCurrency.set(total.currency.code, [...(byCurrency.get(total.currency.code) ?? []), total])
    }
  }

  return (
      <div className="flex flex-col gap-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="charts-from">{t('charts.from')}</Label>
            <Input id="charts-from" type="date" className="h-10 w-44 rounded-lg bg-card" value={from} max={to}
                   aria-invalid={!validRange} onChange={(e) => setFrom(e.target.value)}/>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="charts-to">{t('charts.to')}</Label>
            <Input id="charts-to" type="date" className="h-10 w-44 rounded-lg bg-card" value={to} min={from}
                   aria-invalid={!validRange} onChange={(e) => setTo(e.target.value)}/>
          </div>
        </div>

        {!validRange && <p className="text-sm text-destructive">{t('charts.invalidRange')}</p>}

        {validRange && totals.status === 'loading' && <p className="text-muted-foreground">{t('charts.loading')}</p>}

        {validRange && totals.status === 'error' && (
            <Alert variant="destructive" className="max-w-xl">
              <AlertCircle/>
              <AlertTitle>{t('charts.error')}</AlertTitle>
            </Alert>
        )}

        {validRange && totals.status === 'loaded' && byCurrency.size === 0 && (
            <Card className="items-center px-6 py-12 text-center shadow-xs">
              <span className="font-medium">{t('charts.empty')}</span>
            </Card>
        )}

        {validRange && [...byCurrency.values()].map((items) => (
            <TagTotalsChart
                key={items[0].currency.code}
                // The currency only needs naming when there are several charts
                title={byCurrency.size > 1 ? `${t('charts.title')} · ${items[0].currency.code}` : t('charts.title')}
                totals={items}
                currency={items[0].currency}
                locale={i18n.language}
            />
        ))}
      </div>
  )
}

/** Round step (1, 2 or 5 times a power of ten) giving about [TICK_COUNT] gridlines up to [max]. */
function niceStep(max: number): number {
  const raw = max / TICK_COUNT
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 5, 10].find((m) => m * magnitude >= raw) ?? 10
  return step * magnitude
}

function TagTotalsChart({title, totals, currency, locale}: {
  title: string
  totals: CurrencyTagTotal[]
  currency: Currency
  locale: string
}) {
  const {t} = useTranslation()
  const toMajor = (minor: number) => minor / 10 ** currency.minorUnits
  // Expense amounts are positive, so the largest sum is too
  const largest = toMajor(Math.max(...totals.map((total) => total.total)))
  const step = niceStep(largest)
  const axisMax = Math.ceil(largest / step) * step
  const ticks = Array.from({length: Math.round(axisMax / step) + 1}, (_, i) => i * step)
  const tickFormat = new Intl.NumberFormat(locale, {notation: 'compact', maximumFractionDigits: 1})
  const tagName = (total: TagTotal) => total.tag ?? t('charts.untagged')

  return (
      <Card className="gap-4 px-4 py-5 shadow-xs sm:px-6">
        <h2 className="font-semibold">{title}</h2>
        <div className="flex gap-2">
          {/* Y axis labels, in major units of the currency */}
          <div className="relative w-10 shrink-0 text-right text-xs text-muted-foreground tabular-nums"
               style={{height: CHART_HEIGHT_PX}} aria-hidden>
            {ticks.map((tick) => (
                <span key={tick} className="absolute right-0 translate-y-1/2"
                      style={{bottom: `${(tick / axisMax) * 100}%`}}>
                  {tickFormat.format(tick)}
                </span>
            ))}
          </div>
          <div className="min-w-0 grow overflow-x-auto">
            <div className="flex min-w-fit flex-col">
              <div className="relative" style={{height: CHART_HEIGHT_PX}}>
                {ticks.map((tick) => (
                    <div key={tick}
                         className={`absolute inset-x-0 border-t ${tick === 0 ? 'border-border' : 'border-dashed border-border/60'}`}
                         style={{bottom: `${(tick / axisMax) * 100}%`}} aria-hidden/>
                ))}
                <ul className="absolute inset-0 flex items-end gap-0.5 px-1" aria-label={title}>
                  {totals.map((total) => (
                      <li key={total.tag ?? ''}
                          className="group relative flex h-full min-w-12 flex-1 items-end justify-center"
                          tabIndex={0}
                          aria-label={`${tagName(total)}: ${formatAmount(total.total, currency, locale)}`}>
                        <div
                            className="w-full max-w-14 rounded-t-[4px] transition-opacity group-hover:opacity-80 group-focus:opacity-80"
                            style={{
                              height: `${(toMajor(total.total) / axisMax) * 100}%`,
                              backgroundColor: total.tagColor ?? 'var(--muted-foreground)',
                            }}
                        />
                        <span
                            className="pointer-events-none absolute z-10 mb-1 hidden rounded-md border bg-popover px-2 py-1 text-xs whitespace-nowrap text-popover-foreground shadow-md group-hover:block group-focus:block"
                            style={{bottom: `${(toMajor(total.total) / axisMax) * 100}%`}}
                            aria-hidden>
                          <span className="font-medium">{tagName(total)}</span>
                          {' · '}
                          <span className="tabular-nums">{formatAmount(total.total, currency, locale)}</span>
                        </span>
                      </li>
                  ))}
                </ul>
              </div>
              {/* X axis labels */}
              <div className="flex gap-0.5 px-1 pt-2" aria-hidden>
                {totals.map((total) => (
                    <span key={total.tag ?? ''}
                          className="min-w-12 flex-1 truncate text-center text-xs text-muted-foreground"
                          title={tagName(total)}>
                      {tagName(total)}
                    </span>
                ))}
              </div>
            </div>
          </div>
        </div>
        {/* The exact sums, which the bars only approximate */}
        <ul className="flex flex-wrap gap-x-4 gap-y-1.5 border-t pt-4 text-sm">
          {totals.map((total) => (
              <li key={total.tag ?? ''} className="flex items-center gap-1.5">
                {total.tag
                    ? <TagBadge name={total.tag} color={total.tagColor}/>
                    : <span className="text-muted-foreground">{t('charts.untagged')}</span>}
                <span className="font-medium tabular-nums">{formatAmount(total.total, currency, locale)}</span>
              </li>
          ))}
        </ul>
      </Card>
  )
}
