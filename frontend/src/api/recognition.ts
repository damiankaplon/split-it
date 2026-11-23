import {apiFetch} from './client'

/** Any detail may be null, but not all of them. */
export interface RecognizedExpense {
  title: string | null
  /** ISO date (YYYY-MM-DD) */
  date: string | null
  /** Positive decimal with a dot (e.g. "1234.56"), not minor units: the currency may be unknown. */
  amount: string | null
  currency: {code: string; minorUnits: number} | null
}

/** Images, PDFs and CSVs are supported. The call can take a while; nothing is saved. */
export const recognizeExpenses = (file: File) => {
  const body = new FormData()
  body.append('file', file)
  return apiFetch<RecognizedExpense[]>('/api/recognition', {method: 'POST', body})
}
