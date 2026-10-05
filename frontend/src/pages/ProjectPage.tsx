import {type ReactNode, useEffect, useState} from 'react'
import {AlertCircle, ArrowLeft, Pencil, Plus, Search} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {Link, useParams} from 'react-router'
import {Temporal} from 'temporal-polyfill'
import {ApiError} from '@/api/client'
import {type Expense, listExpenses, listTags} from '@/api/expenses'
import {type Currency, listMembers, listProjects, type Project, type ProjectMember} from '@/api/projects'
import {DebtsPanel} from '@/components/DebtsPanel'
import {ExpenseDialog} from '@/components/ExpenseDialog'
import {InviteDialog} from '@/components/InviteDialog'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Button} from '@/components/ui/button'
import {Card} from '@/components/ui/card'
import {Input} from '@/components/ui/input'
import {initials} from '@/lib/initials'
import {formatAmount} from '@/lib/money'
import {cn} from '@/lib/utils'

const PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300

type ProjectState =
    | { status: 'loading' }
    | { status: 'error'; message: string | null }
    | { status: 'loaded'; project: Project; members: ProjectMember[] }

type ExpensesState =
    | { status: 'loading' }
    | { status: 'error' }
    | { status: 'loaded'; items: Expense[]; page: number; hasNext: boolean; total: number }

// Forbidden and not-found both mean the user can't see this project; other failures show the raw error.
const errorMessage = (e: unknown) =>
    e instanceof ApiError && (e.status === 403 || e.status === 404) ? null : e instanceof Error ? e.message : String(e)

export function ProjectPage({currentUserId}: { currentUserId: string }) {
  const {t} = useTranslation()
  const {projectId = ''} = useParams()
  const [project, setProject] = useState<ProjectState>({status: 'loading'})
  const [tags, setTags] = useState<string[]>([])

  useEffect(() => {
    // There is no single-project endpoint; the list holds only projects the user belongs to.
    Promise.all([listProjects(), listMembers(projectId)])
        .then(([projects, members]) => {
          const found = projects.find((p) => p.id === projectId)
          setProject(found ? {status: 'loaded', project: found, members} : {status: 'error', message: null})
        })
        .catch((e: unknown) => setProject({status: 'error', message: errorMessage(e)}))
    listTags(projectId).then(setTags, () => setTags([]))
  }, [projectId])

  if (project.status === 'loading') {
    return <PageShell><p className="text-muted-foreground">{t('project.loading')}</p></PageShell>
  }

  if (project.status === 'error') {
    return (
        <PageShell>
          <Alert variant="destructive" className="max-w-xl">
            <AlertCircle/>
            <AlertTitle>{t('project.loadError')}</AlertTitle>
            <AlertDescription>{project.message ?? t('project.notFound')}</AlertDescription>
          </Alert>
        </PageShell>
    )
  }

  return (
      <ProjectExpenses
          project={project.project}
          members={project.members}
          currentUserId={currentUserId}
          tags={tags}
          onTagsChanged={() => listTags(projectId).then(setTags, () => undefined)}
      />
  )
}

function PageShell({children}: { children: ReactNode }) {
  const {t} = useTranslation()
  return (
      <main className="mx-auto flex w-full max-w-[1200px] flex-col gap-6 px-6 pt-7 pb-12">
        <Link to="/"
              className="flex w-fit items-center gap-1.5 text-[13px] text-muted-foreground hover:text-foreground">
          <ArrowLeft className="size-3.5"/>
          {t('project.back')}
        </Link>
        {children}
      </main>
  )
}

interface ProjectExpensesProps {
  project: Project
  members: ProjectMember[]
  currentUserId: string
  tags: string[]
  onTagsChanged: () => void
}

function ProjectExpenses({project, members, currentUserId, tags, onTagsChanged}: ProjectExpensesProps) {
  const {t, i18n} = useTranslation()
  const [searchInput, setSearchInput] = useState('')
  const [title, setTitle] = useState('')
  const [tag, setTag] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  // Bumped on every expense change, since any amount change recalculates the debts
  const [debtsReloadKey, setDebtsReloadKey] = useState(0)
  const [expenses, setExpenses] = useState<ExpensesState>({status: 'loading'})
  const [loadingMore, setLoadingMore] = useState(false)

  useEffect(() => {
    const timeout = setTimeout(() => setTitle(searchInput.trim()), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timeout)
  }, [searchInput])

  // First page; refetched whenever a filter changes or an expense is added. Stale results are dropped.
  useEffect(() => {
    let cancelled = false
    listExpenses(project.id, {page: 0, size: PAGE_SIZE, title, tag: tag ?? undefined})
        .then((page) => {
          if (!cancelled) {
            setExpenses({
              status: 'loaded',
              items: page.items,
              page: page.page,
              hasNext: page.hasNext,
              total: page.totalElements
            })
          }
        })
        .catch(() => {
          if (!cancelled) setExpenses({status: 'error'})
        })
    return () => {
      cancelled = true
    }
  }, [project.id, title, tag, reloadKey])

  const loadMore = async () => {
    if (expenses.status !== 'loaded') return
    setLoadingMore(true)
    try {
      const next = await listExpenses(project.id, {
        page: expenses.page + 1,
        size: PAGE_SIZE,
        title,
        tag: tag ?? undefined
      })
      setExpenses((s) => s.status === 'loaded'
          ? {
            ...s,
            items: [...s.items, ...next.items],
            page: next.page,
            hasNext: next.hasNext,
            total: next.totalElements
          }
          : s)
    } catch {
      setExpenses({status: 'error'})
    } finally {
      setLoadingMore(false)
    }
  }

  const filtered = title !== '' || tag !== null

  const refreshTagsIfNew = (expense: Expense) => {
    if (expense.tag && !tags.some((name) => name.toLowerCase() === expense.tag?.toLowerCase())) onTagsChanged()
  }

  const onCreated = (expense: Expense) => {
    // The list is ordered by date, so the new expense may belong anywhere; reload from the top.
    setReloadKey((k) => k + 1)
    setDebtsReloadKey((k) => k + 1)
    refreshTagsIfNew(expense)
  }

  const onUpdated = (before: Expense, after: Expense) => {
    if (after.amount !== before.amount) setDebtsReloadKey((k) => k + 1)
    // A new date may move it elsewhere in the list, and new values may no longer match the filters
    if (after.date !== before.date || filtered) {
      setReloadKey((k) => k + 1)
    } else {
      setExpenses((s) => s.status === 'loaded'
          ? {...s, items: s.items.map((item) => (item.id === after.id ? after : item))}
          : s)
    }
    refreshTagsIfNew(after)
  }

  const memberNames = new Map(members.map((m) => [m.memberId, m.name]))
  const memberName = (userId: string) => memberNames.get(userId) ?? t('project.formerMember')
  const isOwner = project.ownerId === currentUserId

  return (
      <PageShell>
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="flex flex-col gap-1">
            <h1 className="text-[26px] font-semibold tracking-[-0.02em]">{project.name}</h1>
            <p className="text-muted-foreground">
              {t('project.memberCount', {count: members.length})}
              {expenses.status === 'loaded' && !filtered && ` · ${t('project.expenseCount', {count: expenses.total})}`}
            </p>
          </div>
          <div className="flex gap-2">
            {isOwner && <InviteDialog project={project} currentUserId={currentUserId}/>}
            <ExpenseDialog
                projectId={project.id}
                currency={project.currency}
                tags={tags}
                onSaved={onCreated}
                trigger={
                  <Button size="lg" className="rounded-lg px-4">
                    <Plus/>
                    {t('expenseDialog.add')}
                  </Button>
                }
            />
          </div>
        </div>

        <DebtsPanel
            projectId={project.id}
            currency={project.currency}
            currentUserId={currentUserId}
            memberName={memberName}
            reloadKey={debtsReloadKey}
        />

        <div className="flex flex-col gap-3">
          <div className="relative max-w-sm">
            <Search
                className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"/>
            <Input
                type="search"
                aria-label={t('project.search')}
                placeholder={t('project.search')}
                className="h-10 rounded-lg bg-card pl-9"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
            />
          </div>
          {tags.length > 0 && (
              <div className="flex flex-wrap gap-1.5" role="group" aria-label={t('project.filterByTag')}>
                <TagChip selected={tag === null} onClick={() => setTag(null)}>{t('project.allTags')}</TagChip>
                {tags.map((name) => (
                    <TagChip key={name} selected={tag === name} onClick={() => setTag(tag === name ? null : name)}>
                      {name}
                    </TagChip>
                ))}
              </div>
          )}
        </div>

        {expenses.status === 'loading' && <p className="text-muted-foreground">{t('project.loadingExpenses')}</p>}

        {expenses.status === 'error' && (
            <Alert variant="destructive" className="max-w-xl">
              <AlertCircle/>
              <AlertTitle>{t('project.expensesError')}</AlertTitle>
              <AlertDescription>
                <Button variant="link" className="h-auto p-0 text-destructive underline"
                        onClick={() => setReloadKey((k) => k + 1)}>
                  {t('project.retry')}
                </Button>
              </AlertDescription>
            </Alert>
        )}

        {expenses.status === 'loaded' && expenses.items.length === 0 && (
            <Card className="items-center gap-2 px-6 py-12 text-center shadow-xs">
              <span className="font-medium">{filtered ? t('project.noMatches') : t('project.noExpenses')}</span>
              {!filtered && <span className="text-muted-foreground">{t('project.noExpensesHint')}</span>}
            </Card>
        )}

        {expenses.status === 'loaded' && expenses.items.length > 0 && (
            <div className="flex flex-col gap-5">
              <Card className="gap-0 py-0 shadow-xs">
                <ul className="divide-y">
                  {expenses.items.map((expense) => (
                      <ExpenseRow
                          key={expense.id}
                          expense={expense}
                          author={memberName(expense.createdBy)}
                          isMine={expense.createdBy === currentUserId}
                          currency={project.currency}
                          locale={i18n.language}
                          action={
                            <ExpenseDialog
                                projectId={project.id}
                                currency={project.currency}
                                tags={tags}
                                expense={expense}
                                onSaved={(updated) => onUpdated(expense, updated)}
                                trigger={
                                  <Button variant="ghost" size="icon-sm" className="rounded-lg text-muted-foreground"
                                          aria-label={t('expenseDialog.edit', {title: expense.title})}>
                                    <Pencil/>
                                  </Button>
                                }
                            />
                          }
                      />
                  ))}
                </ul>
              </Card>
              {expenses.hasNext && (
                  <Button variant="outline" size="lg" className="self-center rounded-lg px-4" disabled={loadingMore}
                          onClick={() => void loadMore()}>
                    {loadingMore ? t('project.loadingExpenses') : t('project.loadMore')}
                  </Button>
              )}
            </div>
        )}
      </PageShell>
  )
}

function ExpenseRow({expense, author, isMine, currency, locale, action}: {
  expense: Expense
  currency: Currency
  author: string
  isMine: boolean
  locale: string
  action: ReactNode
}) {
  const {t} = useTranslation()
  // The backend sends a zone-less LocalDateTime, which is exactly a PlainDateTime
  const date = Temporal.PlainDateTime.from(expense.date).toLocaleString(locale, {
    dateStyle: 'medium',
    timeStyle: 'short'
  })
  return (
      <li className="flex items-center gap-3 px-4 py-3">
        <span
            className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-secondary text-[11px] font-semibold"
            title={author}>
          {initials(author)}
        </span>
        <div className="flex min-w-0 grow flex-col gap-0.5">
          <span className="truncate font-medium">{expense.title}</span>
          <span className="flex flex-wrap items-center gap-x-1.5 text-[13px] text-muted-foreground">
            <span>{isMine ? t('project.addedByYou') : t('project.addedBy', {name: author})}</span>
            <span aria-hidden>·</span>
            <span>{date}</span>
            {expense.tag && (
                <span className="rounded-full border bg-background px-2 py-px text-xs">{expense.tag}</span>
            )}
          </span>
        </div>
        <span className="shrink-0 font-semibold tabular-nums">{formatAmount(expense.amount, currency, locale)}</span>
        {action}
      </li>
  )
}

function TagChip({selected, onClick, children}: {
  selected: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
      <button
          type="button"
          aria-pressed={selected}
          onClick={onClick}
          className={cn(
              'h-7 rounded-full border px-3 text-[13px] transition-colors outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50',
              selected ? 'border-primary bg-primary text-primary-foreground' : 'bg-card hover:bg-accent',
          )}
      >
        {children}
      </button>
  )
}
