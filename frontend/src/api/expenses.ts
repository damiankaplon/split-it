import {dayRangeParams} from '@/lib/instants'
import {apiFetch} from './client'

/** `date` is an exact ISO instant (e.g. 2026-10-03T17:30:00Z); `amount` is in minor units (grosze). */
export interface Expense {
  id: string
  projectId: string
  title: string
  date: string
  amount: number
  tag: string | null
  tagColor: string | null
  createdBy: string
}

export interface ExpenseRequest {
  title: string
  date: string
  amount: number
  tag?: string
}

export interface ExpensePage {
  items: Expense[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface ExpenseQuery {
  page?: number
  size?: number
  tag?: string
  title?: string
  createdBy?: string
  /** Zoned ISO date-times (e.g. 2026-10-03T00:00:00+02:00), both inclusive */
  from?: string
  to?: string
}

/** Blank filters are left out so the backend doesn't apply them. */
const toParams = (query: object) => {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  return params.toString()
}

/** Newest first. */
export const listExpenses = (projectId: string, query: ExpenseQuery = {}) =>
    apiFetch<ExpensePage>(`/projects/${projectId}/expenses?${toParams(query)}`)

export const createExpense = (projectId: string, expense: ExpenseRequest) =>
    apiFetch<Expense>(`/projects/${projectId}/expenses`, {method: 'POST', body: JSON.stringify(expense)})

/** Any project member may update any expense. */
export const updateExpense = (projectId: string, expenseId: string, expense: ExpenseRequest) =>
    apiFetch<Expense>(`/projects/${projectId}/expenses/${expenseId}`, {method: 'PUT', body: JSON.stringify(expense)})

/** Sum in minor units of the project's expenses matching the filters; paging is ignored. */
export const getExpensesTotal = (projectId: string, query: Omit<ExpenseQuery, 'page' | 'size'> = {}) =>
    apiFetch<{ total: number }>(`/projects/${projectId}/expenses/total?${toParams(query)}`).then((r) => r.total)

/** Sum of a tag's expenses in minor units; a null tag stands for the untagged ones. */
export interface TagTotal {
  tag: string | null
  tagColor: string | null
  total: number
}

/** Per-tag sums of the expenses between two ISO dates (YYYY-MM-DD), both inclusive; largest first. */
export const getTotalsByTag = (projectId: string, from: string, to: string) =>
    apiFetch<TagTotal[]>(`/projects/${projectId}/expenses/totals-by-tag?${dayRangeParams(from, to)}`)

/** Tags already used in the project, sorted by name. */
export const listTags = (projectId: string) => apiFetch<string[]>(`/projects/${projectId}/tags`)
