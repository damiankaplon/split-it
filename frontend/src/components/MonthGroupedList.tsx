import {Fragment, type ReactNode} from 'react'
import {formatInstantMonth, instantMonthKey} from '@/lib/instants'

/** Expense rows with a header wherever a new month begins; items must already be ordered by date. */
export function MonthGroupedList<T extends { id: string; date: string }>({items, locale, renderItem}: {
  items: T[]
  locale: string
  renderItem: (item: T) => ReactNode
}) {
  return (
      <ul className="divide-y">
        {items.map((item, index) => {
          const month = instantMonthKey(item.date)
          const startsMonth = index === 0 || instantMonthKey(items[index - 1].date) !== month
          return (
              <Fragment key={item.id}>
                {startsMonth && (
                    <li role="presentation"
                        className={`bg-primary px-4 py-2 text-xs font-semibold tracking-wide text-primary-foreground uppercase ${index === 0 ? 'rounded-t-xl' : ''}`}>
                      {formatInstantMonth(item.date, locale)}
                    </li>
                )}
                {renderItem(item)}
              </Fragment>
          )
        })}
      </ul>
  )
}
