import {endOfDay, startOfDay, toZonedParam} from '@/lib/instants'

/** Inclusive range of calendar days (`YYYY-MM-DD`); an empty string means unbounded. */
export interface DateRange {
  from: string
  to: string
}

export const EMPTY_DATE_RANGE: DateRange = {from: '', to: ''}

export const isDateRangeSet = (range: DateRange) => range.from !== '' || range.to !== ''

/** Backend filters take zoned date-times, so the range covers its first day from midnight and its last day until the end, in the user's zone. */
export const dateRangeQuery = (range: DateRange) => ({
  from: range.from === '' ? undefined : toZonedParam(startOfDay(range.from)),
  to: range.to === '' ? undefined : toZonedParam(endOfDay(range.to)),
})
