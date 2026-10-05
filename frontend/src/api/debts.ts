import {apiFetch} from './client'

/** `debtorId` owes `creditorId` the `amount` in minor units, under an equal split of all expenses. */
export interface Debt {
  debtorId: string
  creditorId: string
  amount: number
}

/**
 * A debtor's declaration that they paid `amount` outside the app. The debt goes down only once
 * the creditor confirms receiving it.
 */
export interface Settlement {
  id: string
  debtorId: string
  creditorId: string
  amount: number
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'
  createdAt: string
}

/** Every open debt in the project, biggest first. */
export const listDebts = (projectId: string) => apiFetch<Debt[]>(`/projects/${projectId}/debts`)

/** Pending settlements the current user requested or has to confirm. */
export const listPendingSettlements = (projectId: string) =>
    apiFetch<Settlement[]>(`/projects/${projectId}/settlements`)

/**
 * The current user asks `creditorId` to confirm receiving `amount`. The backend doesn't check it against
 * the debt (confirming does), so callers keep it at most the debt.
 * Fails with 409 when a request to that creditor already awaits confirmation.
 */
export const requestSettlement = (projectId: string, creditorId: string, amount: number) =>
    apiFetch<Settlement>(`/projects/${projectId}/settlements`, {
      method: 'POST',
      body: JSON.stringify({creditorId, amount}),
    })

/**
 * `confirm` and `reject` are for the creditor, `cancel` for the debtor; anyone else gets 404.
 * Confirming fails with 409 when the debt has meanwhile become smaller than the declared amount.
 */
export const resolveSettlement = (settlementId: string, action: 'confirm' | 'reject' | 'cancel') =>
    apiFetch<Settlement>(`/settlements/${settlementId}/${action}`, {method: 'POST'})
