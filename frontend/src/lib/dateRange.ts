/** Inclusive range of calendar days (`YYYY-MM-DD`); an empty string means unbounded. */
export interface DateRange {
  from: string
  to: string
}

export const EMPTY_DATE_RANGE: DateRange = {from: '', to: ''}

export const isDateRangeSet = (range: DateRange) => range.from !== '' || range.to !== ''

/** Backend filters take date-times, so the range covers its first day from midnight and its last day until the end. */
export const dateRangeQuery = (range: DateRange) => ({
  from: range.from === '' ? undefined : `${range.from}T00:00:00`,
  to: range.to === '' ? undefined : `${range.to}T23:59:59.999`,
})
