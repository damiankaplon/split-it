import {apiFetch} from './client'

/** `date` is a zone-less ISO local date-time (e.g. 2026-10-03T19:30:00); `amount` is in minor units (grosze). */
export interface Expense {
  id: string
  projectId: string
  title: string
  date: string
  amount: number
  tag: string | null
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
}

/** Newest first. Blank filters are left out so the backend doesn't apply them. */
export const listExpenses = (projectId: string, query: ExpenseQuery = {}) => {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  return apiFetch<ExpensePage>(`/projects/${projectId}/expenses?${params.toString()}`)
}

export const createExpense = (projectId: string, expense: ExpenseRequest) =>
    apiFetch<Expense>(`/projects/${projectId}/expenses`, {method: 'POST', body: JSON.stringify(expense)})

/** Any project member may update any expense. */
export const updateExpense = (projectId: string, expenseId: string, expense: ExpenseRequest) =>
    apiFetch<Expense>(`/projects/${projectId}/expenses/${expenseId}`, {method: 'PUT', body: JSON.stringify(expense)})

/** Tags already used in the project, sorted by name. */
export const listTags = (projectId: string) => apiFetch<string[]>(`/projects/${projectId}/tags`)
