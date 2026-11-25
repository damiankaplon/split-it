import {type ReactNode, useEffect, useState} from 'react'
import {ArrowDownLeft, ArrowUpRight, CircleCheck} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {type Debt, listDebts, listPendingSettlements, resolveSettlement, type Settlement} from '@/api/debts'
import type {Currency} from '@/api/projects'
import {SettleDialog} from '@/components/SettleDialog'
import {Button} from '@/components/ui/button'
import {Card} from '@/components/ui/card'
import {initials} from '@/lib/initials'
import {amountParts, formatAmount} from '@/lib/money'
import {cn} from '@/lib/utils'

type DebtsState =
  | {status: 'loading'}
  | {status: 'error'}
  | {
      status: 'loaded'
      debts: Debt[]
      pending: Settlement[]
    }

/** One counterpart of the current user: the open debt between them and the settlement awaiting confirmation. */
interface Row {
  debtorId: string
  creditorId: string
  debt: Debt | undefined
  pending: Settlement | undefined
}

interface DebtsPanelProps {
  projectId: string
  currency: Currency
  currentUserId: string
  memberName: (userId: string) => string
  /** Bumped whenever expenses change, as that recalculates the debts. */
  reloadKey: number
}

// Buttons on the dark hero: a solid light one for the main action, an outline for the other
const HERO_PRIMARY =
  'rounded-lg bg-hero-foreground bg-none text-hero shadow-none hover:translate-y-0 hover:bg-hero-foreground/90'
const HERO_OUTLINE =
  'rounded-lg border-hero-line bg-transparent text-hero-foreground shadow-none hover:border-hero-line hover:bg-white/10 hover:text-hero-foreground'

/** The current user's side of the project balance: whom they owe, who owes them, and settlements to confirm. */
export function DebtsPanel({projectId, currency, currentUserId, memberName, reloadKey}: DebtsPanelProps) {
  const {t, i18n} = useTranslation()
  const [state, setState] = useState<DebtsState>({status: 'loading'})
  const [localReloadKey, setLocalReloadKey] = useState(0)
  const [resolving, setResolving] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([listDebts(projectId), listPendingSettlements(projectId)])
      .then(([debts, pending]) => {
        if (!cancelled) setState({status: 'loaded', debts, pending})
      })
      .catch(() => {
        if (!cancelled) setState({status: 'error'})
      })
    return () => {
      cancelled = true
    }
  }, [projectId, reloadKey, localReloadKey])

  const reload = () => setLocalReloadKey((k) => k + 1)

  const resolve = async (settlement: Settlement, action: 'confirm' | 'reject' | 'cancel') => {
    setResolving(settlement.id)
    setActionError(null)
    try {
      await resolveSettlement(settlement.id, action)
    } catch {
      setActionError(action === 'confirm' ? t('debts.confirmError') : t('debts.actionError'))
    } finally {
      setResolving(null)
      reload()
    }
  }

  const format = (amount: number) => formatAmount(amount, currency, i18n.language)

  if (state.status === 'loading') {
    return (
      <PanelShell>
        <p className="px-5 py-4 text-muted-foreground">{t('debts.loading')}</p>
      </PanelShell>
    )
  }

  if (state.status === 'error') {
    return (
      <PanelShell>
        <p className="flex flex-wrap items-center gap-2 px-5 py-4 text-destructive">
          {t('debts.error')}
          <Button variant="link" className="h-auto p-0 text-destructive underline" onClick={reload}>
            {t('project.retry')}
          </Button>
        </p>
      </PanelShell>
    )
  }

  // A pending settlement may outlive its debt (e.g. expenses changed), so it still gets a row to be resolved
  const rows = new Map<string, Row>()
  const rowOf = (debtorId: string, creditorId: string) => {
    const key = `${debtorId}\n${creditorId}`
    const row = rows.get(key) ?? {debtorId, creditorId, debt: undefined, pending: undefined}
    rows.set(key, row)
    return row
  }
  const mine = (d: {debtorId: string; creditorId: string}) =>
    d.debtorId === currentUserId || d.creditorId === currentUserId
  state.debts.filter(mine).forEach((debt) => (rowOf(debt.debtorId, debt.creditorId).debt = debt))
  state.pending.filter(mine).forEach((s) => (rowOf(s.debtorId, s.creditorId).pending = s))
  const iOwe = [...rows.values()].filter((r) => r.debtorId === currentUserId)
  const owedToMe = [...rows.values()].filter((r) => r.creditorId === currentUserId)

  const totalOwed = iOwe.reduce((sum, r) => sum + (r.debt?.amount ?? 0), 0)
  const totalOwedToMe = owedToMe.reduce((sum, r) => sum + (r.debt?.amount ?? 0), 0)
  const toConfirm = owedToMe.filter((r) => r.pending).length

  if (rows.size === 0) {
    return (
      <PanelShell>
        <div className="flex items-center gap-3 px-5 py-4">
          <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-success-muted text-owed">
            <CircleCheck className="size-5" aria-hidden />
          </span>
          <div className="flex flex-col gap-0.5">
            <span className="font-medium">{t('debts.settled')}</span>
            <span className="text-[13px] text-muted-foreground">{t('debts.settledHint')}</span>
          </div>
        </div>
      </PanelShell>
    )
  }

  // The bigger of the two totals leads; a total of zero is left out
  const stats = [
    {tone: 'credit' as const, amount: totalOwedToMe, label: t('debts.owedLabel')},
    {tone: 'debt' as const, amount: totalOwed, label: t('debts.oweLabel')},
  ].filter((s) => s.amount > 0)

  return (
    <section
      aria-labelledby="debts-heading"
      className="hero-panel flex flex-col gap-6 overflow-hidden rounded-2xl border border-hero-line p-5 text-hero-foreground shadow-pop sm:p-7"
    >
      <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
        <div className="flex flex-col gap-3">
          <h2
            id="debts-heading"
            className="font-mono text-[11px] leading-4 font-medium tracking-[0.1em] text-hero-muted uppercase"
          >
            {t('debts.title')}
          </h2>
          <div className="flex flex-wrap items-end gap-x-10 gap-y-4">
            {stats.map((stat, index) => (
              <Stat
                key={stat.tone}
                tone={stat.tone}
                label={stat.label}
                parts={amountParts(stat.amount, currency, i18n.language)}
                large={index === 0}
              />
            ))}
          </div>
        </div>
        {toConfirm > 0 && (
          <span className="rounded-full border border-hero-line bg-white/10 px-3 py-1 text-xs font-medium">
            {t('debts.toConfirm', {count: toConfirm})}
          </span>
        )}
      </div>

      {stats.length > 0 && (
        <div
          role="img"
          aria-label={[
            totalOwedToMe > 0 && t('debts.owedToYouTotal', {amount: format(totalOwedToMe)}),
            totalOwed > 0 && t('debts.youOweTotal', {amount: format(totalOwed)}),
          ]
            .filter(Boolean)
            .join(', ')}
          className="flex h-3 w-full max-w-xs gap-1"
        >
          {/* Two halves with slanted facing edges: the "split" in SplitIt */}
          {stats.map((stat) => (
            <span
              key={stat.tone}
              style={{flex: stat.amount}}
              className={cn(
                stat.tone === 'credit' ? 'bg-cyan' : 'bg-flare',
                stats.length === 1
                  ? 'rounded-full'
                  : stat.tone === 'credit'
                    ? 'rounded-l-full [clip-path:polygon(0_0,100%_0,calc(100%-8px)_100%,0_100%)]'
                    : 'rounded-r-full [clip-path:polygon(8px_0,100%_0,100%_100%,0_100%)]',
              )}
            />
          ))}
        </div>
      )}

      {actionError && (
        <p className="text-[13px] text-hero-debt" role="alert">
          {actionError}
        </p>
      )}
      <ul className="flex flex-col gap-2">
        {iOwe.map(({creditorId, debt, pending}) => {
          const name = memberName(creditorId)
          return (
            <DebtRow
              key={creditorId}
              name={name}
              label={t('debts.youOwe', {name})}
              amount={debt && format(debt.amount)}
              owing
              note={pending && t('debts.awaitingConfirmation', {name, amount: format(pending.amount)})}
            >
              {pending ? (
                <Button
                  size="sm"
                  variant="outline"
                  className={HERO_OUTLINE}
                  disabled={resolving === pending.id}
                  onClick={() => void resolve(pending, 'cancel')}
                >
                  {t('debts.cancelRequest')}
                </Button>
              ) : (
                debt && (
                  <SettleDialog
                    projectId={projectId}
                    currency={currency}
                    debt={debt}
                    creditorName={name}
                    onChanged={reload}
                    trigger={
                      <Button size="sm" className={HERO_PRIMARY}>
                        {t('debts.settleUp')}
                      </Button>
                    }
                  />
                )
              )}
            </DebtRow>
          )
        })}
        {owedToMe.map(({debtorId, debt, pending}) => {
          const name = memberName(debtorId)
          return (
            <DebtRow
              key={debtorId}
              name={name}
              label={t('debts.owesYou', {name})}
              amount={debt && format(debt.amount)}
              owing={false}
              note={pending && t('debts.confirmPrompt', {name, amount: format(pending.amount)})}
            >
              {pending && (
                <>
                  <Button
                    size="sm"
                    variant="outline"
                    className={HERO_OUTLINE}
                    disabled={resolving === pending.id}
                    onClick={() => void resolve(pending, 'reject')}
                  >
                    {t('debts.reject')}
                  </Button>
                  <Button
                    size="sm"
                    className={HERO_PRIMARY}
                    disabled={resolving === pending.id}
                    onClick={() => void resolve(pending, 'confirm')}
                  >
                    {t('debts.confirm')}
                  </Button>
                </>
              )}
            </DebtRow>
          )
        })}
      </ul>
    </section>
  )
}

/** The plain panel for the loading, error and all-settled states: the hero is only for open balances. */
function PanelShell({children}: {children: ReactNode}) {
  const {t} = useTranslation()
  return (
    <section className="flex flex-col gap-2" aria-labelledby="debts-heading">
      <h2 id="debts-heading" className="text-base font-semibold">
        {t('debts.title')}
      </h2>
      <Card className="gap-0 py-0">{children}</Card>
    </section>
  )
}

/** One headline total: the label with an arrow, then the amount with its decimals and currency code dimmed. */
function Stat({
  tone,
  label,
  parts,
  large,
}: {
  tone: 'credit' | 'debt'
  label: string
  parts: {whole: string; fraction: string; code: string}
  large: boolean
}) {
  const Icon = tone === 'credit' ? ArrowDownLeft : ArrowUpRight
  return (
    <div className="flex flex-col gap-1.5">
      <span className="flex items-center gap-1.5 font-mono text-[11px] leading-4 font-medium tracking-[0.1em] text-hero-muted uppercase">
        <Icon className={cn('size-3.5', tone === 'credit' ? 'text-hero-credit' : 'text-hero-debt')} aria-hidden />
        {label}
      </span>
      <p
        className={cn(
          'font-display font-semibold tracking-[-0.035em] tabular-nums',
          large
            ? 'text-[44px] leading-[48px] sm:text-[56px] sm:leading-[60px]'
            : 'text-[32px] leading-9 sm:text-[40px] sm:leading-[44px]',
        )}
      >
        {parts.whole}
        <span className="text-[0.5em] text-hero-muted">{parts.fraction}</span>
        <span className="ml-2.5 font-mono text-sm font-medium tracking-normal text-hero-muted">{parts.code}</span>
      </p>
    </div>
  )
}

function DebtRow({
  name,
  label,
  amount,
  owing,
  note,
  children,
}: {
  name: string
  label: string
  /** Formatted debt; missing when only a pending settlement is left. */
  amount: string | undefined
  /** The current user owes this amount (as opposed to being owed it). */
  owing: boolean
  /** Status of a pending settlement. */
  note: string | undefined
  children: ReactNode
}) {
  return (
    <li className="flex flex-wrap items-center gap-3 rounded-xl border border-hero-line bg-white/5 px-3.5 py-3">
      <span
        className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-white/15 text-[11px] font-semibold"
        title={name}
      >
        {initials(name)}
      </span>
      <span className="flex min-w-0 grow basis-44 flex-col gap-0.5">
        <span className="truncate font-medium">{label}</span>
        {note && <span className="text-[13px] text-hero-muted">{note}</span>}
      </span>
      {amount && (
        <span
          className={cn(
            'shrink-0 font-mono text-lg font-medium tracking-[-0.01em] tabular-nums',
            owing ? 'text-hero-debt' : 'text-hero-credit',
          )}
        >
          {amount}
        </span>
      )}
      <span className="ml-auto flex shrink-0 gap-2">{children}</span>
    </li>
  )
}
