import {Fragment, type ReactNode, useEffect, useState} from 'react'
import {AlertCircle, ArrowLeft, ArrowRight, Info} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {Temporal} from 'temporal-polyfill'
import type {TagTotal} from '@/api/expenses'
import type {Currency} from '@/api/projects'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Card} from '@/components/ui/card'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {type DateRange, EMPTY_DATE_RANGE} from '@/lib/dateRange'
import {formatAmount} from '@/lib/money'

export interface CurrencyTagTotal extends TagTotal {
  currency: Currency
}

type TotalsState = {status: 'loading'} | {status: 'error'} | {status: 'loaded'; totals: CurrencyTagTotal[]}

interface TagTotalsChartsProps {
  /** Both dates are ISO (YYYY-MM-DD) and inclusive. */
  load: (from: string, to: string) => Promise<CurrencyTagTotal[]>
  /** Bumped on every expense change, so the sums are refetched. */
  reloadKey: number
  /** Feed date filters carried over when opening the charts; an empty bound falls back to the default. */
  initialRange?: DateRange
  /** Rendered below the charts for the selected tag and the charted range (both ISO, inclusive). */
  renderTagExpenses?: (selection: {tag: string; from: string; to: string}) => ReactNode
  /** Moves all expenses of the `from` tag to the `to` tag; the merge buttons are only shown when given. */
  onMergeTags?: (from: string, to: string) => Promise<void>
}

/** Pie charts of the expense sums per tag in a date range, one per currency; the last month by default. */
export function TagTotalsCharts({
  load,
  reloadKey,
  initialRange = EMPTY_DATE_RANGE,
  renderTagExpenses,
  onMergeTags,
}: TagTotalsChartsProps) {
  const {t, i18n} = useTranslation()
  const [to, setTo] = useState(() => initialRange.to || Temporal.Now.plainDateISO().toString())
  const [from, setFrom] = useState(
    () => initialRange.from || Temporal.PlainDate.from(to).subtract({months: 1}).toString(),
  )
  const [totals, setTotals] = useState<TotalsState>({status: 'loading'})
  const [selectedTag, setSelectedTag] = useState<string | null>(null)
  // The tag whose expenses the next picked tag takes over
  const [mergeSource, setMergeSource] = useState<string | null>(null)
  const [mergeFailed, setMergeFailed] = useState(false)
  const [mergeHintDismissed, setMergeHintDismissed] = useState(false)
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
      },
    )
    return () => {
      cancelled = true
    }
  }, [load, from, to, validRange, reloadKey])

  const onPickMergeTag = (name: string) => {
    setMergeFailed(false)
    if (mergeSource === null) {
      setMergeHintDismissed(false)
      setMergeSource(name)
      return
    }
    const source = mergeSource
    setMergeSource(null)
    // Picking the same tag again only cancels the merge
    if (!onMergeTags || source === name) return
    onMergeTags(source, name).then(
      () => setSelectedTag((current) => (current === source ? name : current)),
      () => setMergeFailed(true),
    )
  }

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
          <Input
            id="charts-from"
            type="date"
            className="h-11 w-44 rounded-lg bg-card"
            value={from}
            max={to}
            aria-invalid={!validRange}
            onChange={(e) => setFrom(e.target.value)}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="charts-to">{t('charts.to')}</Label>
          <Input
            id="charts-to"
            type="date"
            className="h-11 w-44 rounded-lg bg-card"
            value={to}
            min={from}
            aria-invalid={!validRange}
            onChange={(e) => setTo(e.target.value)}
          />
        </div>
      </div>

      {!validRange && <p className="text-sm text-destructive">{t('charts.invalidRange')}</p>}

      {mergeSource !== null && !mergeHintDismissed && (
        // Floating popup: clicking it dismisses it, and picking any tag closes it too
        <Alert
          role="alert"
          className="fixed inset-x-4 bottom-4 z-50 mx-auto w-auto max-w-md cursor-pointer shadow-lg"
          onClick={() => setMergeHintDismissed(true)}
        >
          <Info />
          <AlertTitle>{t('charts.merge.title', {tag: mergeSource})}</AlertTitle>
          <AlertDescription>{t('charts.merge.hint', {tag: mergeSource})}</AlertDescription>
        </Alert>
      )}

      {mergeFailed && (
        <Alert variant="destructive" className="max-w-xl">
          <AlertCircle />
          <AlertTitle>{t('charts.merge.error')}</AlertTitle>
        </Alert>
      )}

      {validRange && totals.status === 'loading' && <p className="text-muted-foreground">{t('charts.loading')}</p>}

      {validRange && totals.status === 'error' && (
        <Alert variant="destructive" className="max-w-xl">
          <AlertCircle />
          <AlertTitle>{t('charts.error')}</AlertTitle>
        </Alert>
      )}

      {validRange && totals.status === 'loaded' && byCurrency.size === 0 && (
        <Card className="items-center px-6 py-12 text-center">
          <span className="font-medium">{t('charts.empty')}</span>
        </Card>
      )}

      {validRange &&
        [...byCurrency.values()].map((items) => (
          <TagTotalsChart
            key={items[0].currency.code}
            // The currency only needs naming when there are several charts
            title={byCurrency.size > 1 ? `${t('charts.title')} · ${items[0].currency.code}` : t('charts.title')}
            totals={items}
            currency={items[0].currency}
            locale={i18n.language}
            selectedTag={selectedTag}
            onSelectTag={
              renderTagExpenses ? (name) => setSelectedTag((current) => (current === name ? null : name)) : undefined
            }
            mergeSource={mergeSource}
            onMergeTag={onMergeTags ? onPickMergeTag : undefined}
          />
        ))}

      {/* Keyed by tag, so a newly selected tag starts from the loading state */}
      {validRange && selectedTag !== null && (
        <Fragment key={selectedTag}>{renderTagExpenses?.({tag: selectedTag, from, to})}</Fragment>
      )}
    </div>
  )
}

function TagDot({color}: {color: string | null | undefined}) {
  return (
    <span
      aria-hidden
      className="size-2 shrink-0 rounded-full"
      style={{backgroundColor: color ?? 'var(--muted-foreground)'}}
    />
  )
}

const PIE_SIZE = 200
const PIE_RADIUS = 88
// How far the selected slice slides out; the radius leaves it room inside the viewBox
const PIE_POP = 8

/** The point `radius` away from the centre; angles run clockwise from 12 o'clock, in radians. */
const pieAt = (angle: number, radius: number) => ({
  x: PIE_SIZE / 2 + radius * Math.sin(angle),
  y: PIE_SIZE / 2 - radius * Math.cos(angle),
})

interface PieSlice {
  key: string
  color: string
  /** Both in radians. */
  start: number
  end: number
  selected: boolean
  /** Shown on hover. */
  label: string
}

/** Decorative: the legend next to it is the accessible version of the same data. */
function TagPie({slices}: {slices: PieSlice[]}) {
  // Everything else fades while one tag is selected
  const hasSelection = slices.some((slice) => slice.selected)
  return (
    <svg viewBox={`0 0 ${PIE_SIZE} ${PIE_SIZE}`} aria-hidden className="size-56 shrink-0">
      {slices.map((slice) => {
        const middle = (slice.start + slice.end) / 2
        const pop = slice.selected && slices.length > 1 ? PIE_POP : 0
        const className =
          'stroke-card stroke-2 transition-[transform,opacity] duration-200 motion-reduce:transition-none'
        const style = {
          fill: slice.color,
          opacity: hasSelection && !slice.selected ? 0.45 : 1,
          transform: `translate(${pop * Math.sin(middle)}px, ${-pop * Math.cos(middle)}px)`,
        }
        if (slices.length === 1) {
          // A single slice is a whole circle, which an arc can not draw
          return (
            <circle
              key={slice.key}
              cx={PIE_SIZE / 2}
              cy={PIE_SIZE / 2}
              r={PIE_RADIUS}
              className={className}
              style={style}
            >
              <title>{slice.label}</title>
            </circle>
          )
        }
        const from = pieAt(slice.start, PIE_RADIUS)
        const to = pieAt(slice.end, PIE_RADIUS)
        const largeArc = slice.end - slice.start > Math.PI ? 1 : 0
        return (
          <path
            key={slice.key}
            d={`M ${PIE_SIZE / 2} ${PIE_SIZE / 2} L ${from.x} ${from.y} A ${PIE_RADIUS} ${PIE_RADIUS} 0 ${largeArc} 1 ${to.x} ${to.y} Z`}
            className={className}
            style={style}
          >
            <title>{slice.label}</title>
          </path>
        )
      })}
    </svg>
  )
}

function TagTotalsChart({
  title,
  totals,
  currency,
  locale,
  selectedTag,
  onSelectTag,
  mergeSource,
  onMergeTag,
}: {
  title: string
  totals: CurrencyTagTotal[]
  currency: Currency
  locale: string
  selectedTag: string | null
  /** Absent when tags can't be selected; untagged expenses never can. */
  onSelectTag?: (tag: string) => void
  /** The tag picked to be merged into another one, if any. */
  mergeSource: string | null
  /** Absent when tags can't be merged; untagged expenses never can. */
  onMergeTag?: (tag: string) => void
}) {
  const {t} = useTranslation()
  const tagName = (total: TagTotal) => total.tag ?? t('charts.untagged')
  // Untagged expenses have no colour of their own
  const tagColor = (total: TagTotal) => total.tagColor ?? 'var(--muted-foreground)'
  // Biggest first, running clockwise from the top; expense amounts are positive, so the sum is too
  const sorted = [...totals].sort((a, b) => b.total - a.total)
  const sum = sorted.reduce((acc, total) => acc + total.total, 0)
  const percent = new Intl.NumberFormat(locale, {style: 'percent', maximumFractionDigits: 1})
  const mergeLabel = (tag: string) =>
    mergeSource === null ? t('charts.merge.start', {tag}) : t('charts.merge.into', {tag})

  const slices: PieSlice[] = []
  let angle = 0
  for (const total of sorted) {
    const end = angle + (total.total / sum) * 2 * Math.PI
    slices.push({
      key: total.tag ?? '',
      color: tagColor(total),
      start: angle,
      end,
      selected: total.tag !== null && total.tag === selectedTag,
      label: `${tagName(total)}: ${formatAmount(total.total, currency, locale)} (${percent.format(total.total / sum)})`,
    })
    angle = end
  }

  return (
    <Card className="gap-4 px-4 py-5 sm:px-6">
      <h2 className="font-semibold">{title}</h2>
      <div className="flex flex-col items-center gap-6 md:flex-row md:gap-10">
        <TagPie slices={slices} />
        {/* The legend: the columns are shared by all rows, so the amounts and shares line up. On narrow screens the share moves under the amount, leaving the names room. */}
        <ul
          className={`grid w-full min-w-0 flex-1 gap-x-3 gap-y-1 ${onMergeTag ? 'grid-cols-[auto_minmax(0,1fr)_auto] sm:grid-cols-[auto_minmax(0,1fr)_auto_auto]' : 'grid-cols-[minmax(0,1fr)_auto] sm:grid-cols-[minmax(0,1fr)_auto_auto]'}`}
          aria-label={title}
        >
          {sorted.map((total, index) => {
            const selectable = onSelectTag !== undefined && total.tag !== null
            const selected = slices[index].selected
            return (
              <li
                key={total.tag ?? ''}
                className={`grid ${onMergeTag ? 'col-span-3 sm:col-span-4' : 'col-span-2 sm:col-span-3'} grid-cols-subgrid items-center rounded-xl px-2.5 py-2 text-sm transition-colors hover:bg-surface-2 ${selected ? 'bg-accent ring-1 ring-primary/35 ring-inset hover:bg-accent' : ''}`}
              >
                {onMergeTag &&
                  (total.tag !== null ? (
                    <button
                      type="button"
                      aria-label={mergeLabel(total.tag)}
                      title={mergeLabel(total.tag)}
                      className="rounded-md p-1 text-muted-foreground hover:bg-accent hover:text-foreground focus-visible:ring-4 focus-visible:ring-ring/20 focus-visible:outline-none"
                      onClick={() => onMergeTag(total.tag as string)}
                    >
                      {mergeSource === null ? <ArrowRight className="size-4" /> : <ArrowLeft className="size-4" />}
                    </button>
                  ) : (
                    // Takes the button's place, as an empty cell would shift the name into the button's column
                    <span aria-hidden />
                  ))}
                {selectable ? (
                  <button
                    type="button"
                    aria-pressed={selected}
                    title={tagName(total)}
                    className="flex min-w-0 items-center gap-2 rounded-md text-left font-medium underline-offset-2 hover:underline focus-visible:ring-4 focus-visible:ring-ring/20 focus-visible:outline-none"
                    onClick={() => onSelectTag(total.tag as string)}
                  >
                    <TagDot color={total.tagColor} />
                    <span className="truncate">{tagName(total)}</span>
                  </button>
                ) : (
                  <span className="flex min-w-0 items-center gap-2 font-medium" title={tagName(total)}>
                    <TagDot color={total.tagColor} />
                    <span className="truncate">{tagName(total)}</span>
                  </span>
                )}
                <span className="flex flex-col items-end">
                  <span className="font-mono text-[15px] font-medium tracking-[-0.01em] tabular-nums">
                    {formatAmount(total.total, currency, locale)}
                  </span>
                  <span className="text-xs text-muted-foreground tabular-nums sm:hidden">
                    {percent.format(total.total / sum)}
                  </span>
                </span>
                <span className="hidden min-w-12 text-right text-muted-foreground tabular-nums sm:block">
                  {percent.format(total.total / sum)}
                </span>
              </li>
            )
          })}
        </ul>
      </div>
    </Card>
  )
}
