import {TagBadge} from '@/components/TagBadge'
import {type ReactNode, useEffect, useState} from 'react'
import {AlertCircle, Pencil, Plus, Search, Trash2} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {Temporal} from 'temporal-polyfill'
import {listPersonalExpenses, listPersonalTags, type PersonalExpense} from '@/api/personal'
import {DeletePersonalExpenseDialog} from '@/components/DeletePersonalExpenseDialog'
import {PersonalExpenseDialog} from '@/components/PersonalExpenseDialog'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Button} from '@/components/ui/button'
import {Card} from '@/components/ui/card'
import {Input} from '@/components/ui/input'
import {formatAmount} from '@/lib/money'

const PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300
const DEFAULT_CURRENCY = 'PLN'

type ExpensesState =
    | { status: 'loading' }
    | { status: 'error' }
    | { status: 'loaded'; items: PersonalExpense[]; page: number; hasNext: boolean; total: number }

/** The signed-in user's own expenses, unrelated to any project. */
export function PersonalExpensesPage() {
  const {t, i18n} = useTranslation()
  const [tags, setTags] = useState<string[]>([])
  const [searchInput, setSearchInput] = useState('')
  const [title, setTitle] = useState('')
  const [tag, setTag] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [expenses, setExpenses] = useState<ExpensesState>({status: 'loading'})
  const [loadingMore, setLoadingMore] = useState(false)
  // New expenses default to the currency used last
  const [lastCurrency, setLastCurrency] = useState<string | null>(null)

  const reloadTags = () => listPersonalTags().then(setTags, () => undefined)

  useEffect(() => {
    listPersonalTags().then(setTags, () => setTags([]))
  }, [])

  useEffect(() => {
    const timeout = setTimeout(() => setTitle(searchInput.trim()), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timeout)
  }, [searchInput])

  // First page; refetched whenever a filter changes or an expense is added or removed. Stale results are dropped.
  useEffect(() => {
    let cancelled = false
    listPersonalExpenses({page: 0, size: PAGE_SIZE, title, tag: tag ?? undefined})
        .then((page) => {
          if (cancelled) return
          setExpenses({
            status: 'loaded',
            items: page.items,
            page: page.page,
            hasNext: page.hasNext,
            total: page.totalElements,
          })
          setLastCurrency((current) => current ?? page.items[0]?.currency.code ?? null)
        })
        .catch(() => {
          if (!cancelled) setExpenses({status: 'error'})
        })
    return () => {
      cancelled = true
    }
  }, [title, tag, reloadKey])

  const loadMore = async () => {
    if (expenses.status !== 'loaded') return
    setLoadingMore(true)
    try {
      const next = await listPersonalExpenses({page: expenses.page + 1, size: PAGE_SIZE, title, tag: tag ?? undefined})
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

  const refreshTagsIfNew = (expense: PersonalExpense) => {
    if (expense.tag && !tags.some((name) => name.toLowerCase() === expense.tag?.toLowerCase())) void reloadTags()
  }

  const onCreated = (expense: PersonalExpense) => {
    // The list is ordered by date, so the new expense may belong anywhere; reload from the top.
    setReloadKey((k) => k + 1)
    setLastCurrency(expense.currency.code)
    refreshTagsIfNew(expense)
  }

  const onUpdated = (before: PersonalExpense, after: PersonalExpense) => {
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

  const onDeleted = (expense: PersonalExpense) => {
    setExpenses((s) => s.status === 'loaded'
        ? {...s, items: s.items.filter((item) => item.id !== expense.id), total: s.total - 1}
        : s)
  }

  return (
      <main className="mx-auto flex w-full max-w-[1200px] flex-col gap-6 px-6 pt-7 pb-12">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="flex flex-col gap-1">
            <h1 className="text-[26px] font-semibold tracking-[-0.02em]">{t('personal.title')}</h1>
            <p className="text-muted-foreground">
              {t('personal.subtitle')}
              {expenses.status === 'loaded' && !filtered && ` · ${t('project.expenseCount', {count: expenses.total})}`}
            </p>
          </div>
          <PersonalExpenseDialog
              defaultCurrency={lastCurrency ?? DEFAULT_CURRENCY}
              tags={tags}
              onSaved={onCreated}
              trigger={
                <Button className="rounded-lg">
                  <Plus/>
                  {t('personalExpenseDialog.add')}
                </Button>
              }
          />
        </div>

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
              <select
                  aria-label={t('project.filterByTag')}
                  className="h-10 max-w-sm rounded-lg border border-input bg-card px-3 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 md:text-sm"
                  value={tag ?? ''}
                  onChange={(e) => setTag(e.target.value === '' ? null : e.target.value)}
              >
                <option value="">{t('project.allTags')}</option>
                {tags.map((name) => (
                    <option key={name} value={name}>{name}</option>
                ))}
              </select>
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
              {!filtered && <span className="text-muted-foreground">{t('personal.noExpensesHint')}</span>}
            </Card>
        )}

        {expenses.status === 'loaded' && expenses.items.length > 0 && (
            <div className="flex flex-col gap-5">
              <Card className="gap-0 py-0 shadow-xs">
                <ul className="divide-y">
                  {expenses.items.map((expense) => (
                      <PersonalExpenseRow
                          key={expense.id}
                          expense={expense}
                          locale={i18n.language}
                          actions={
                            <>
                              <PersonalExpenseDialog
                                  defaultCurrency={expense.currency.code}
                                  tags={tags}
                                  expense={expense}
                                  onSaved={(updated) => onUpdated(expense, updated)}
                                  trigger={
                                    <Button variant="ghost" size="icon-sm" className="rounded-lg text-muted-foreground"
                                            aria-label={t('personalExpenseDialog.edit', {title: expense.title})}>
                                      <Pencil/>
                                    </Button>
                                  }
                              />
                              <DeletePersonalExpenseDialog
                                  expense={expense}
                                  onDeleted={onDeleted}
                                  trigger={
                                    <Button variant="ghost" size="icon-sm"
                                            className="rounded-lg text-muted-foreground hover:text-destructive"
                                            aria-label={t('personalDeleteDialog.trigger', {title: expense.title})}>
                                      <Trash2/>
                                    </Button>
                                  }
                              />
                            </>
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
      </main>
  )
}

function PersonalExpenseRow({expense, locale, actions}: {
  expense: PersonalExpense
  locale: string
  actions: ReactNode
}) {
  // The backend sends a zone-less LocalDateTime, which is exactly a PlainDateTime
  const date = Temporal.PlainDateTime.from(expense.date).toLocaleString(locale, {
    dateStyle: 'medium',
    timeStyle: 'short'
  })
  return (
      <li className="flex items-center gap-3 px-4 py-3">
        <div className="flex min-w-0 grow flex-col gap-0.5">
          <span className="truncate font-medium">{expense.title}</span>
          <span className="flex flex-wrap items-center gap-x-1.5 text-[13px] text-muted-foreground">
            <span>{date}</span>
            {expense.tag && (
                <TagBadge name={expense.tag} color={expense.tagColor}/>
            )}
          </span>
        </div>
        <span className="shrink-0 font-semibold tabular-nums">
          {formatAmount(expense.amount, expense.currency, locale)}
        </span>
        <div className="flex shrink-0 items-center">{actions}</div>
      </li>
  )
}
