import {Fragment, type ReactNode} from 'react'
import {formatInstantMonth, instantMonthKey} from '@/lib/instants'

/** Expense rows with a quiet eyebrow wherever a new month begins; items must already be ordered by date. */
export function MonthGroupedList<T extends {id: string; date: string}>({
  items,
  locale,
  renderItem,
}: {
  items: T[]
  locale: string
  renderItem: (item: T) => ReactNode
}) {
  return (
    <ul className="p-2">
      {items.map((item, index) => {
        const month = instantMonthKey(item.date)
        const startsMonth = index === 0 || instantMonthKey(items[index - 1].date) !== month
        return (
          <Fragment key={item.id}>
            {startsMonth && (
              <li role="presentation" className="flex items-center gap-3 px-3 pt-4 pb-2 first:pt-2">
                <span className="font-mono text-[11px] leading-4 font-medium tracking-[0.1em] text-muted-foreground uppercase">
                  {formatInstantMonth(item.date, locale)}
                </span>
                <span aria-hidden className="h-px flex-1 bg-border" />
              </li>
            )}
            {renderItem(item)}
          </Fragment>
        )
      })}
    </ul>
  )
}
