import {useEffect, useState} from 'react'
import {AlertCircle, ArrowLeft, Loader2, Pencil, Sparkles, Trash2, TriangleAlert} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {Link, Navigate, useLocation, useNavigate, useParams} from 'react-router'
import {ApiError} from '@/api/client'
import {createExpense, listTags} from '@/api/expenses'
import {createPersonalExpense, listPersonalTags} from '@/api/personal'
import {type Currency, getProject} from '@/api/projects'
import {type RecognizedExpense, recognizeExpenses} from '@/api/recognition'
import {DraftExpenseDialog} from '@/components/DraftExpenseDialog'
import {TagBadge} from '@/components/TagBadge'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Button} from '@/components/ui/button'
import {Card} from '@/components/ui/card'
import {type AiImportState, type DraftExpense, isComplete} from '@/lib/aiImport'
import {inputValueToInstant} from '@/lib/instants'
import {currencyOf, formatAmount, toMinorUnits} from '@/lib/money'

/** Where the confirmed expenses go: a project, or (without a name) the user's personal expenses. */
interface Destination {
  name?: string
  /** The currency assumed when the file's was not recognized */
  currency: Currency
}

type Recognition =
  | {status: 'loading'}
  | {status: 'error'; error: unknown}
  | {
      status: 'loaded'
      drafts: DraftExpense[]
    }

// StrictMode runs effects twice in development; the upload (and its cost) must happen once per file.
const recognitions = new WeakMap<File, Promise<RecognizedExpense[]>>()
const recognizeOnce = (file: File) => {
  let promise = recognitions.get(file)
  if (!promise) {
    promise = recognizeExpenses(file)
    recognitions.set(file, promise)
    // A failed call may be retried by navigating here again
    promise.catch(() => recognitions.delete(file))
  }
  return promise
}

const toDraft = (recognized: RecognizedExpense, index: number, destination: Destination): DraftExpense => {
  // The file's currency when it was recognized, the destination's otherwise
  const currency: Currency = recognized.currency ?? destination.currency
  const amount = recognized.amount === null ? undefined : toMinorUnits(Number(recognized.amount), currency)
  return {
    key: `${index}`,
    title: recognized.title ?? '',
    amount: amount !== undefined && amount > 0 ? amount : undefined,
    currency,
    date: recognized.date === null ? '' : `${recognized.date}T00:00`,
    tag: '',
  }
}

export function AiImportPage() {
  const {projectId} = useParams()
  const {state} = useLocation() as {state: Partial<AiImportState> | null}
  const file = state?.file

  // The file lives only in the navigation state; after a reload there is nothing to upload.
  if (!file) return <Navigate to={projectId === undefined ? '/personal' : `/projects/${projectId}`} replace />
  return <AiImport projectId={projectId} currency={state?.currency} file={file} />
}

function AiImport({projectId, currency, file}: {projectId?: string; currency?: string; file: File}) {
  const {t, i18n} = useTranslation()
  const navigate = useNavigate()
  const [project, setProject] = useState<Destination | null | undefined>(undefined)
  const [tags, setTags] = useState<string[]>([])
  const [recognition, setRecognition] = useState<Recognition>({status: 'loading'})
  const [confirming, setConfirming] = useState(false)
  const [failedCount, setFailedCount] = useState(0)

  useEffect(() => {
    let cancelled = false
    // Forbidden and not-found both mean the user can't use this project
    const projectOrNull: Promise<Destination | null> =
      projectId === undefined
        ? Promise.resolve({currency: currencyOf(currency ?? 'PLN')})
        : getProject(projectId).catch((e: unknown) => {
            if (e instanceof ApiError && (e.status === 403 || e.status === 404)) return null
            throw e
          })
    Promise.all([projectOrNull, recognizeOnce(file)]).then(
      ([found, recognized]) => {
        if (cancelled) return
        setProject(found)
        if (found) setRecognition({status: 'loaded', drafts: recognized.map((r, i) => toDraft(r, i, found))})
      },
      (error: unknown) => {
        if (!cancelled) setRecognition({status: 'error', error})
      },
    )
    const loadTags = projectId === undefined ? listPersonalTags() : listTags(projectId)
    loadTags.then(setTags, () => setTags([]))
    return () => {
      cancelled = true
    }
  }, [file, projectId, currency])

  const backPath = projectId === undefined ? '/personal' : `/projects/${projectId}`
  const back = (
    <Link
      to={backPath}
      className="flex w-fit items-center gap-1.5 text-[13px] text-muted-foreground hover:text-foreground"
    >
      <ArrowLeft className="size-3.5" />
      {projectId === undefined ? t('aiImport.backToPersonal') : t('aiImport.backToProject')}
    </Link>
  )

  const shell = (children: React.ReactNode) => (
    <main className="mx-auto flex w-full max-w-[900px] flex-col gap-6 px-6 pt-7 pb-12">
      {back}
      {children}
    </main>
  )

  if (recognition.status === 'loading') {
    return shell(
      <Card className="items-center gap-4 px-6 py-16 text-center" role="status">
        <Loader2 className="size-8 animate-spin text-muted-foreground" />
        <span className="text-lg font-medium">{t('aiImport.waiting')}</span>
      </Card>,
    )
  }

  if (recognition.status === 'error') {
    const status = recognition.error instanceof ApiError ? recognition.error.status : null
    const message =
      status === 415
        ? t('aiImport.errorUnsupported')
        : status === 413
          ? t('aiImport.errorTooLarge')
          : t('aiImport.errorGeneric')
    return shell(
      <Alert variant="destructive" className="max-w-xl">
        <AlertCircle />
        <AlertTitle>{t('aiImport.error')}</AlertTitle>
        <AlertDescription>{message}</AlertDescription>
      </Alert>,
    )
  }

  if (project === null || project === undefined) {
    return <Navigate to="/" replace />
  }

  const {drafts} = recognition
  const setDrafts = (update: (drafts: DraftExpense[]) => DraftExpense[]) =>
    setRecognition((r) => (r.status === 'loaded' ? {...r, drafts: update(r.drafts)} : r))
  const allComplete = drafts.length > 0 && drafts.every(isComplete)

  const onConfirm = async () => {
    setConfirming(true)
    setFailedCount(0)
    const failed: DraftExpense[] = []
    // One by one, so the failed ones can stay on the list
    for (const draft of drafts) {
      try {
        const title = draft.title.trim()
        const date = inputValueToInstant(draft.date)
        const tag = draft.tag.trim() || undefined
        if (projectId === undefined) {
          // Personal expenses keep their own currency
          await createPersonalExpense({title, date, amount: draft.amount ?? 0, currency: draft.currency.code, tag})
        } else {
          // The backend knows only the project's currency; there is no conversion, the number is kept
          await createExpense(projectId, {
            title,
            amount: toMinorUnits((draft.amount ?? 0) / 10 ** draft.currency.minorUnits, project.currency),
            date,
            tag,
          })
        }
      } catch {
        failed.push(draft)
      }
    }
    if (failed.length === 0) {
      void navigate(backPath)
      return
    }
    setDrafts(() => failed)
    setFailedCount(failed.length)
    setConfirming(false)
  }

  return shell(
    <>
      <div className="flex flex-col gap-1">
        <h1 className="flex items-center gap-2 text-[28px] leading-[34px] font-semibold tracking-[-0.025em]">
          <Sparkles className="size-6" />
          {t('aiImport.title')}
        </h1>
        <p className="text-muted-foreground">
          {project.name === undefined
            ? t('aiImport.subtitlePersonal', {file: file.name})
            : t('aiImport.subtitle', {file: file.name, project: project.name})}
        </p>
      </div>

      {failedCount > 0 && (
        <Alert variant="destructive">
          <AlertCircle />
          <AlertTitle>{t('aiImport.confirmError', {count: failedCount})}</AlertTitle>
        </Alert>
      )}

      {drafts.length === 0 ? (
        <Card className="items-center gap-2 px-6 py-12 text-center">
          <span className="font-medium">{t('aiImport.empty')}</span>
        </Card>
      ) : (
        <>
          <Card className="gap-0 py-0">
            <ul className="divide-y">
              {drafts.map((draft) => (
                <DraftRow
                  key={draft.key}
                  draft={draft}
                  currency={projectId === undefined ? undefined : project.currency}
                  tags={tags}
                  locale={i18n.language}
                  disabled={confirming}
                  onChange={(updated) => setDrafts((all) => all.map((d) => (d.key === updated.key ? updated : d)))}
                  onRemove={() => setDrafts((all) => all.filter((d) => d.key !== draft.key))}
                />
              ))}
            </ul>
          </Card>
          <div className="flex flex-wrap items-center justify-end gap-3">
            {!allComplete && <span className="text-[13px] text-muted-foreground">{t('aiImport.incomplete')}</span>}
            <Button
              size="lg"
              className="rounded-lg px-4"
              disabled={!allComplete || confirming}
              onClick={() => void onConfirm()}
            >
              {confirming ? t('aiImport.confirming') : t('aiImport.confirm', {count: drafts.length})}
            </Button>
          </div>
        </>
      )}
    </>,
  )
}

function DraftRow({
  draft,
  currency,
  tags,
  locale,
  disabled,
  onChange,
  onRemove,
}: {
  draft: DraftExpense
  /** The project's, to flag a draft in another one; none for personal expenses, which keep theirs */
  currency?: Currency
  tags: string[]
  locale: string
  disabled: boolean
  onChange: (draft: DraftExpense) => void
  onRemove: () => void
}) {
  const {t} = useTranslation()
  const title = draft.title.trim() || t('aiImport.untitled')
  const missing = <span className="text-destructive">{t('aiImport.missing')}</span>
  const date = draft.date === '' ? null : new Date(draft.date).toLocaleDateString(locale, {dateStyle: 'medium'})
  return (
    <li className="flex items-center gap-3 px-4 py-3">
      <div className="flex min-w-0 grow flex-col gap-0.5">
        <span className="truncate font-medium">{draft.title.trim() === '' ? missing : draft.title}</span>
        <span className="flex flex-wrap items-center gap-x-1.5 text-[13px] text-muted-foreground">
          <span>{date ?? missing}</span>
          {draft.tag && <TagBadge name={draft.tag} color={null} />}
          {currency && draft.currency.code !== currency.code && (
            <span className="flex items-center gap-1 text-amber-600">
              <TriangleAlert className="size-3.5" />
              {t('aiImport.otherCurrency', {currency: draft.currency.code})}
            </span>
          )}
        </span>
      </div>
      <span className="shrink-0 font-semibold tabular-nums">
        {draft.amount === undefined ? missing : formatAmount(draft.amount, draft.currency, locale)}
      </span>
      <DraftExpenseDialog
        draft={draft}
        tags={tags}
        onSaved={onChange}
        trigger={
          <Button
            variant="ghost"
            size="icon-sm"
            className="rounded-lg text-muted-foreground"
            aria-label={t('aiImport.edit', {title})}
            disabled={disabled}
          >
            <Pencil />
          </Button>
        }
      />
      <Button
        variant="ghost"
        size="icon-sm"
        className="rounded-lg text-muted-foreground"
        aria-label={t('aiImport.remove', {title})}
        disabled={disabled}
        onClick={onRemove}
      >
        <Trash2 />
      </Button>
    </li>
  )
}
