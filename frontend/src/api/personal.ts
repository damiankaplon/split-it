import {apiFetch} from './client'
import type {TagTotal} from './expenses'
import type {Currency} from './projects'
import {dayRangeParams} from '@/lib/instants'

/**
 * An expense only its owner can see. `date` is an exact ISO instant;
 * `amount` is in the minor units of the expense's own currency.
 */
export interface PersonalExpense {
  id: string
  title: string
  date: string
  amount: number
  currency: Currency
  tag: string | null
  tagColor: string | null
}

export interface PersonalExpenseRequest {
  title: string
  date: string
  amount: number
  /** ISO 4217 code */
  currency: string
  tag?: string
}

export interface PersonalExpensePage {
  items: PersonalExpense[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface PersonalExpenseQuery {
  page?: number
  size?: number
  tag?: string
  title?: string
  /** Zoned ISO date-times (e.g. 2026-10-03T00:00:00+02:00), both inclusive */
  from?: string
  to?: string
}

/** Blank filters are left out so the backend doesn't apply them. */
const toParams = (query: PersonalExpenseQuery) => {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  return params.toString()
}

/** Newest first. */
export const listPersonalExpenses = (query: PersonalExpenseQuery = {}) =>
    apiFetch<PersonalExpensePage>(`/personal/expenses?${toParams(query)}`)

export interface PersonalExpenseSum {
  currency: Currency
  /** In the currency's minor units */
  total: number
}

/** Per-currency sums of the expenses matching the filters; paging is ignored. */
export const getPersonalExpensesTotal = (query: Omit<PersonalExpenseQuery, 'page' | 'size'> = {}) =>
    apiFetch<PersonalExpenseSum[]>(`/personal/expenses/total?${toParams(query)}`)

export const createPersonalExpense = (expense: PersonalExpenseRequest) =>
    apiFetch<PersonalExpense>('/personal/expenses', {method: 'POST', body: JSON.stringify(expense)})

export const updatePersonalExpense = (expenseId: string, expense: PersonalExpenseRequest) =>
    apiFetch<PersonalExpense>(`/personal/expenses/${expenseId}`, {method: 'PUT', body: JSON.stringify(expense)})

export const deletePersonalExpense = (expenseId: string) =>
    apiFetch<void>(`/personal/expenses/${expenseId}`, {method: 'DELETE'})

/** Sum of a tag's expenses in one currency; a null tag stands for the untagged ones. */
export interface PersonalTagTotal extends TagTotal {
  currency: Currency
}

/**
 * Per-currency and per-tag sums of the user's expenses between two ISO dates (YYYY-MM-DD), both inclusive;
 * ordered by currency code, then largest first.
 */
export const getPersonalTotalsByTag = (from: string, to: string) =>
    apiFetch<PersonalTagTotal[]>(`/personal/expenses/totals-by-tag?${dayRangeParams(from, to)}`)

/** Tags the user has already used on personal expenses, sorted by name. */
export const listPersonalTags = () => apiFetch<string[]>('/personal/tags')

/**
 * Money the user received. `date` is an exact instant (ISO, UTC); a delimiting income starts a new savings
 * period in its currency.
 */
export interface Income {
  id: string
  title: string
  date: string
  delimiting: boolean
  amount: number
  currency: Currency
}

export interface IncomeRequest {
  title: string
  /** ISO instant, e.g. 2026-01-10T08:00:00Z */
  date: string
  delimiting: boolean
  amount: number
  /** ISO 4217 code */
  currency: string
}

/** Newest first. */
export const listIncomes = () => apiFetch<Income[]>('/personal/incomes')

export const createIncome = (income: IncomeRequest) =>
    apiFetch<Income>('/personal/incomes', {method: 'POST', body: JSON.stringify(income)})

export const updateIncome = (incomeId: string, income: IncomeRequest) =>
    apiFetch<Income>(`/personal/incomes/${incomeId}`, {method: 'PUT', body: JSON.stringify(income)})

export const deleteIncome = (incomeId: string) =>
    apiFetch<void>(`/personal/incomes/${incomeId}`, {method: 'DELETE'})

/** What was saved in one currency from a delimiting income until the next one; amounts are in minor units. */
export interface Savings {
  currency: Currency
  /** Instant of the delimiting income starting the period */
  start: string
  /** Start of the next period (exclusive); null while the period is running */
  end: string | null
  income: number
  expenses: number
  saved: number
}

/**
 * Periods overlapping `from`..`to`, both inclusive zoned ISO date-times (e.g. 2026-01-10T00:00:00+01:00). Unzoned
 * expenses are read in the zone of `to`, so send the user's own zone. Ordered by currency code, then newest first.
 */
export const getSavings = (from: string, to: string) => {
  const params = new URLSearchParams({from, to})
  return apiFetch<Savings[]>(`/personal/savings?${params.toString()}`)
}
