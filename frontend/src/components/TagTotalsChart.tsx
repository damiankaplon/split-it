import {Fragment, type ReactNode, useEffect, useState} from 'react'
import {AlertCircle} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {Temporal} from 'temporal-polyfill'
import type {TagTotal} from '@/api/expenses'
import type {Currency} from '@/api/projects'
import {Alert, AlertTitle} from '@/components/ui/alert'
import {Card} from '@/components/ui/card'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {type DateRange, EMPTY_DATE_RANGE} from '@/lib/dateRange'
import {formatAmount} from '@/lib/money'

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
  /** Feed date filters carried over when opening the charts; an empty bound falls back to the default. */
  initialRange?: DateRange
  /** Rendered below the charts for the selected tag and the charted range (both ISO, inclusive). */
  renderTagExpenses?: (selection: { tag: string; from: string; to: string }) => ReactNode
}

/** Bar charts of the expense sums per tag in a date range, one per currency; the last month by default. */
export function TagTotalsCharts({
                                  load,
                                  reloadKey,
                                  initialRange = EMPTY_DATE_RANGE,
                                  renderTagExpenses
                                }: TagTotalsChartsProps) {
  const {t, i18n} = useTranslation()
  const [to, setTo] = useState(() => initialRange.to || Temporal.Now.plainDateISO().toString())
  const [from, setFrom] = useState(() =>
      initialRange.from || Temporal.PlainDate.from(to).subtract({months: 1}).toString())
  const [totals, setTotals] = useState<TotalsState>({status: 'loading'})
  const [selectedTag, setSelectedTag] = useState<string | null>(null)
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
                selectedTag={selectedTag}
                onSelectTag={renderTagExpenses
                    ? (name) => setSelectedTag((current) => (current === name ? null : name))
                    : undefined}
            />
        ))}

        {/* Keyed by tag, so a newly selected tag starts from the loading state */}
        {validRange && selectedTag !== null && (
            <Fragment key={selectedTag}>{renderTagExpenses?.({tag: selectedTag, from, to})}</Fragment>
        )}
      </div>
  )
}

function TagTotalsChart({title, totals, currency, locale, selectedTag, onSelectTag}: {
  title: string
  totals: CurrencyTagTotal[]
  currency: Currency
  locale: string
  selectedTag: string | null
  /** Absent when tags can't be selected; untagged expenses never can. */
  onSelectTag?: (tag: string) => void
}) {
  const {t} = useTranslation()
  const tagName = (total: TagTotal) => total.tag ?? t('charts.untagged')
  // Biggest first; expense amounts are positive, so the first one is the longest bar
  const sorted = [...totals].sort((a, b) => b.total - a.total)
  const largest = sorted[0].total

  return (
      <Card className="gap-4 px-4 py-5 shadow-xs sm:px-6">
        <h2 className="font-semibold">{title}</h2>
        <ul className="flex flex-col gap-2.5" aria-label={title}>
          {sorted.map((total) => {
            const selectable = onSelectTag !== undefined && total.tag !== null
            const selected = total.tag !== null && total.tag === selectedTag
            return (
              <li key={total.tag ?? ''}
                  className={`grid grid-cols-[minmax(4rem,7rem)_1fr_auto] items-center gap-3 rounded-md text-sm ${selected ? 'bg-accent' : ''}`}>
                {selectable ? (
                    <button type="button" aria-pressed={selected} title={tagName(total)}
                            className="truncate rounded-md text-left underline-offset-2 hover:underline focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none"
                            onClick={() => onSelectTag(total.tag as string)}>
                      {tagName(total)}
                    </button>
                ) : (
                    <span className="truncate" title={tagName(total)}>{tagName(total)}</span>
                )}
                <div className="h-5 rounded-[4px] bg-muted/50" aria-hidden>
                  <div className="h-full rounded-[4px]"
                       style={{
                         width: `${(total.total / largest) * 100}%`,
                         backgroundColor: total.tagColor ?? 'var(--muted-foreground)',
                       }}/>
                </div>
                <span className="min-w-20 text-right font-medium tabular-nums">
                  {formatAmount(total.total, currency, locale)}
                </span>
              </li>
            )
          })}
        </ul>
      </Card>
  )
}
