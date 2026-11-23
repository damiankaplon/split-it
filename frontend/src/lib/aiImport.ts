import type {Currency} from '@/api/projects'

/** What the review page gets through the router: the file is uploaded there, not in the dialog. */
export interface AiImportState {
  file: File
}

export const aiImportPath = (projectId: string) => `/projects/${projectId}/ai-import`

/** A recognized expense awaiting confirmation; empty details have to be filled in before it can be added. */
export interface DraftExpense {
  key: string
  title: string
  /** Minor units of [currency]; undefined when not recognized */
  amount: number | undefined
  /** The file's currency, or the project's when the file's was not recognized */
  currency: Currency
  /** datetime-local value; empty when not recognized */
  date: string
  tag: string
}

export const isComplete = (draft: DraftExpense) =>
  draft.title.trim() !== '' && draft.amount !== undefined && draft.amount > 0 && draft.date !== ''
