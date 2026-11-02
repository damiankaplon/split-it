import {type ReactNode, useEffect, useRef, useState} from 'react'
import {AlertCircle} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {Alert, AlertTitle} from '@/components/ui/alert'
import {Card} from '@/components/ui/card'
import {InfiniteScrollTrigger} from '@/components/InfiniteScrollTrigger'
import {MonthGroupedList} from '@/components/MonthGroupedList'
import {dateRangeQuery} from '@/lib/dateRange'

const PAGE_SIZE = 20

interface Page<T> {
  items: T[]
  page: number
  hasNext: boolean
}

type ListState<T> =
    | { status: 'loading' }
    | { status: 'error' }
    | { status: 'loaded'; items: T[]; page: number; hasNext: boolean }

interface TagExpensesListProps<T> {
  tag: string
  /** Both dates are ISO (YYYY-MM-DD) and inclusive. */
  from: string
  to: string
  /** Bumped on every expense change, so the list is refetched. */
  reloadKey: number
  load: (query: { page: number; size: number; tag: string; from?: string; to?: string }) => Promise<Page<T>>
  renderItem: (item: T) => ReactNode
}

/** The expenses of one tag within a date range, newest first, loaded page by page. */
export function TagExpensesList<T extends { id: string; date: string }>(
    {tag, from, to, reloadKey, load, renderItem}: TagExpensesListProps<T>) {
  const {t, i18n} = useTranslation()
  const [state, setState] = useState<ListState<T>>({status: 'loading'})
  const [loadingMore, setLoadingMore] = useState(false)
  const loadRef = useRef(load)

  // Kept current without re-running the fetch effects whenever the parent re-renders
  useEffect(() => {
    loadRef.current = load
  })

  useEffect(() => {
    let cancelled = false
    loadRef.current({page: 0, size: PAGE_SIZE, tag, ...dateRangeQuery({from, to})}).then(
        (page) => {
          if (!cancelled) setState({status: 'loaded', items: page.items, page: page.page, hasNext: page.hasNext})
        },
        () => {
          if (!cancelled) setState({status: 'error'})
        })
    return () => {
      cancelled = true
    }
  }, [tag, from, to, reloadKey])

  const loadMore = async () => {
    if (state.status !== 'loaded') return
    setLoadingMore(true)
    try {
      const next = await loadRef.current({page: state.page + 1, size: PAGE_SIZE, tag, ...dateRangeQuery({from, to})})
      setState((s) => s.status === 'loaded'
          ? {status: 'loaded', items: [...s.items, ...next.items], page: next.page, hasNext: next.hasNext}
          : s)
    } catch {
      setState({status: 'error'})
    } finally {
      setLoadingMore(false)
    }
  }

  return (
      <section className="flex flex-col gap-3" aria-label={t('charts.expensesOf', {tag})}>
        <h2 className="font-semibold">{t('charts.expensesOf', {tag})}</h2>

        {state.status === 'loading' && <p className="text-muted-foreground">{t('project.loadingExpenses')}</p>}

        {state.status === 'error' && (
            <Alert variant="destructive" className="max-w-xl">
              <AlertCircle/>
              <AlertTitle>{t('project.expensesError')}</AlertTitle>
            </Alert>
        )}

        {state.status === 'loaded' && state.items.length === 0 && (
            <Card className="items-center px-6 py-12 text-center shadow-xs">
              <span className="font-medium">{t('project.noMatches')}</span>
            </Card>
        )}

        {state.status === 'loaded' && state.items.length > 0 && (
            <div className="flex flex-col gap-5">
              <Card className="gap-0 py-0 shadow-xs">
                <MonthGroupedList items={state.items} locale={i18n.language} renderItem={renderItem}/>
              </Card>
              {state.hasNext && <InfiniteScrollTrigger loading={loadingMore} onLoadMore={() => void loadMore()}/>}
            </div>
        )}
      </section>
  )
}
