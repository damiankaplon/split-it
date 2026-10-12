import {apiFetch} from './client'
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
}

/** Newest first. Blank filters are left out so the backend doesn't apply them. */
export const listPersonalExpenses = (query: PersonalExpenseQuery = {}) => {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  return apiFetch<PersonalExpensePage>(`/personal/expenses?${params.toString()}`)
}

export const createPersonalExpense = (expense: PersonalExpenseRequest) =>
    apiFetch<PersonalExpense>('/personal/expenses', {method: 'POST', body: JSON.stringify(expense)})

export const updatePersonalExpense = (expenseId: string, expense: PersonalExpenseRequest) =>
    apiFetch<PersonalExpense>(`/personal/expenses/${expenseId}`, {method: 'PUT', body: JSON.stringify(expense)})

export const deletePersonalExpense = (expenseId: string) =>
    apiFetch<void>(`/personal/expenses/${expenseId}`, {method: 'DELETE'})

/** Tags the user has already used on personal expenses, sorted by name. */
export const listPersonalTags = () => apiFetch<string[]>('/personal/tags')
