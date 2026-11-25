import type {Currency} from '@/api/projects'

// The backend stores amounts as a Java Int
const MAX_MINOR_UNITS = 2_147_483_647

/** Currencies offered when creating a project; the backend accepts any ISO 4217 code with minor units. */
export const PROJECT_CURRENCIES = ['PLN', 'EUR', 'USD', 'GBP', 'CHF', 'CZK', 'SEK', 'NOK', 'DKK', 'HUF', 'UAH', 'JPY']

/** Minor units come from the browser's ISO 4217 data, matching what the backend reports (e.g. JPY has none). */
export const currencyOf = (code: string): Currency => ({
  code,
  minorUnits:
    new Intl.NumberFormat('en', {
      style: 'currency',
      currency: code,
    }).resolvedOptions().maximumFractionDigits ?? 2,
})

const amountFormat = (currency: Currency, locale: string) =>
  new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currency.code,
    currencyDisplay: 'code',
    minimumFractionDigits: currency.minorUnits,
    maximumFractionDigits: currency.minorUnits,
  })

/** Formats minor units in the locale's number style, labelled with the currency code (e.g. "120,50 PLN"). */
export function formatAmount(minorUnits: number, currency: Currency, locale: string): string {
  return amountFormat(currency, locale).format(minorUnits / 10 ** currency.minorUnits)
}

/** The same amount in parts, so the decimals and the currency code can be styled apart (e.g. "1 065" ",76" "PLN"). */
export function amountParts(minorUnits: number, currency: Currency, locale: string) {
  let whole = ''
  let fraction = ''
  let code = ''
  for (const part of amountFormat(currency, locale).formatToParts(minorUnits / 10 ** currency.minorUnits)) {
    if (part.type === 'currency') code = part.value
    else if (part.type === 'decimal' || part.type === 'fraction') fraction += part.value
    else if (part.type === 'integer' || part.type === 'group' || part.type === 'minusSign') whole += part.value
  }
  return {whole, fraction, code}
}

/** A zero amount in the locale's number style, for input placeholders (e.g. "0,00"). */
export function zeroAmount(currency: Currency, locale: string): string {
  return new Intl.NumberFormat(locale, {minimumFractionDigits: currency.minorUnits}).format(0)
}

/** Rounding absorbs float error, e.g. 0.29 * 100 = 28.999… */
export const toMinorUnits = (value: number, currency: Currency) => Math.round(value * 10 ** currency.minorUnits)

export const fitsInMinorUnits = (value: number, currency: Currency) => toMinorUnits(value, currency) <= MAX_MINOR_UNITS

/** "," for Polish, "." for English. */
export const decimalSeparator = (locale: string) =>
  new Intl.NumberFormat(locale).formatToParts(1.1).find((part) => part.type === 'decimal')?.value ?? '.'
