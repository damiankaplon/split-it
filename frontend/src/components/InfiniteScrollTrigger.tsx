import {useEffect, useRef} from 'react'
import {useTranslation} from 'react-i18next'

const PRELOAD_MARGIN = '300px'

/** Calls onLoadMore whenever it scrolls into view and nothing is loading; re-checks after each load finishes. */
export function InfiniteScrollTrigger({loading, onLoadMore}: { loading: boolean; onLoadMore: () => void }) {
  const {t} = useTranslation()
  const ref = useRef<HTMLDivElement>(null)
  const onLoadMoreRef = useRef(onLoadMore)

  // Keeps the observer callback calling the latest handler; written after commit, not during render.
  useEffect(() => {
    onLoadMoreRef.current = onLoadMore
  })

  useEffect(() => {
    const element = ref.current
    if (!element || loading) return
    // A fresh observer reports the current state at once, so a sentinel still in view after a load fetches again.
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) onLoadMoreRef.current()
    }, {rootMargin: PRELOAD_MARGIN})
    observer.observe(element)
    return () => observer.disconnect()
  }, [loading])

  return (
      <div ref={ref} className="min-h-6 self-center text-[13px] text-muted-foreground">
        {loading && t('project.loadingExpenses')}
      </div>
  )
}
