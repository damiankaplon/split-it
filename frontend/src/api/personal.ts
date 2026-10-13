import {apiFetch} from './client'
import type {TagTotal} from './expenses'
import type {Currency} from './projects'

/**
 * An expense only its owner can see. `date` is a zone-less ISO local date-time;
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
  /** Zone-less ISO local date-times, both inclusive */
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
    apiFetch<PersonalTagTotal[]>(`/personal/expenses/totals-by-tag?${new URLSearchParams({from, to}).toString()}`)

/** Tags the user has already used on personal expenses, sorted by name. */
export const listPersonalTags = () => apiFetch<string[]>('/personal/tags')
