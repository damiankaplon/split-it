import {Temporal} from 'temporal-polyfill'

/**
 * The backend deals in exact instants (ISO, UTC); the user sees and enters them in their own time zone.
 */
const zone = () => Temporal.Now.timeZoneId()

/** An instant as the `YYYY-MM-DDTHH:mm` a datetime-local input takes, in the user's zone. */
export const instantToInputValue = (instant: string) =>
    Temporal.Instant.from(instant).toZonedDateTimeISO(zone()).toPlainDateTime().toString({smallestUnit: 'minute'})

/** The current moment as a datetime-local input value. */
export const nowInputValue = () => Temporal.Now.plainDateTimeISO(zone()).toString({smallestUnit: 'minute'})

/** A datetime-local input value, read in the user's zone, as an ISO instant. */
export const inputValueToInstant = (value: string) =>
    Temporal.PlainDateTime.from(value).toZonedDateTime(zone()).toInstant().toString()

export const formatInstant = (instant: string, locale: string, options: Intl.DateTimeFormatOptions) =>
    Temporal.Instant.from(instant).toZonedDateTimeISO(zone()).toLocaleString(locale, options)

/** Zoned ISO date-time with its offset, e.g. `2026-01-10T00:00:00+01:00`, as the backend's range parameters take. */
export const toZonedParam = (dateTime: Temporal.ZonedDateTime) => dateTime.toString({timeZoneName: 'never'})

/** The first moment of a day (`YYYY-MM-DD`) in the user's zone. */
export const startOfDay = (date: string) => Temporal.PlainDate.from(date).toZonedDateTime(zone())

/** The last millisecond of a day (`YYYY-MM-DD`) in the user's zone. */
export const endOfDay = (date: string) =>
    Temporal.PlainDate.from(date).add({days: 1}).toZonedDateTime(zone()).subtract({milliseconds: 1})

export const nowZoned = () => Temporal.Now.zonedDateTimeISO(zone())

/** Query parameters covering the days `from`..`to` (`YYYY-MM-DD`, both inclusive) in the user's zone. */
export const dayRangeParams = (from: string, to: string) =>
    new URLSearchParams({from: toZonedParam(startOfDay(from)), to: toZonedParam(endOfDay(to))}).toString()
