import {type ReactNode, useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {type Debt, listDebts, listPendingSettlements, resolveSettlement, type Settlement} from '@/api/debts'
import type {Currency} from '@/api/projects'
import {SettleDialog} from '@/components/SettleDialog'
import {Button} from '@/components/ui/button'
import {Card} from '@/components/ui/card'
import {initials} from '@/lib/initials'
import {formatAmount} from '@/lib/money'
import {cn} from '@/lib/utils'

type DebtsState =
    | { status: 'loading' }
    | { status: 'error' }
    | { status: 'loaded'; debts: Debt[]; pending: Settlement[] }

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
    return <PanelShell><p className="px-4 py-3 text-muted-foreground">{t('debts.loading')}</p></PanelShell>
  }

  if (state.status === 'error') {
    return (
        <PanelShell>
          <p className="flex flex-wrap items-center gap-2 px-4 py-3 text-destructive">
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
  const mine = (d: { debtorId: string; creditorId: string }) =>
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
          <div className="flex flex-col gap-0.5 px-4 py-3">
            <span className="font-medium">{t('debts.settled')}</span>
            <span className="text-[13px] text-muted-foreground">{t('debts.settledHint')}</span>
          </div>
        </PanelShell>
    )
  }

  return (
      <PanelShell
          summary={
            <>
              {totalOwed > 0 && (
                  <span className="text-destructive">{t('debts.youOweTotal', {amount: format(totalOwed)})}</span>
              )}
              {totalOwedToMe > 0 && (
                  <span className="text-emerald-700 dark:text-emerald-400">
                    {t('debts.owedToYouTotal', {amount: format(totalOwedToMe)})}
                  </span>
              )}
              {toConfirm > 0 && <span className="text-primary">{t('debts.toConfirm', {count: toConfirm})}</span>}
            </>
          }
      >
        {actionError && (
            <p className="border-b px-4 py-2 text-[13px] text-destructive" role="alert">{actionError}</p>
        )}
        <ul className="divide-y">
          {iOwe.map(({creditorId, debt, pending}) => {
            const name = memberName(creditorId)
            return (
                <DebtRow key={creditorId} name={name} label={t('debts.youOwe', {name})}
                         amount={debt && format(debt.amount)} owing
                         note={pending && t('debts.awaitingConfirmation', {name, amount: format(pending.amount)})}>
                  {pending ? (
                      <Button size="sm" variant="outline" className="rounded-lg" disabled={resolving === pending.id}
                              onClick={() => void resolve(pending, 'cancel')}>
                        {t('debts.cancelRequest')}
                      </Button>
                  ) : debt && (
                      <SettleDialog
                          projectId={projectId} currency={currency} debt={debt} creditorName={name}
                          onChanged={reload}
                          trigger={<Button size="sm" className="rounded-lg">{t('debts.settleUp')}</Button>}
                      />
                  )}
                </DebtRow>
            )
          })}
          {owedToMe.map(({debtorId, debt, pending}) => {
            const name = memberName(debtorId)
            return (
                <DebtRow key={debtorId} name={name} label={t('debts.owesYou', {name})}
                         amount={debt && format(debt.amount)} owing={false}
                         note={pending && t('debts.confirmPrompt', {name, amount: format(pending.amount)})}>
                  {pending && (
                      <>
                        <Button size="sm" variant="outline" className="rounded-lg" disabled={resolving === pending.id}
                                onClick={() => void resolve(pending, 'reject')}>
                          {t('debts.reject')}
                        </Button>
                        <Button size="sm" className="rounded-lg" disabled={resolving === pending.id}
                                onClick={() => void resolve(pending, 'confirm')}>
                          {t('debts.confirm')}
                        </Button>
                      </>
                  )}
                </DebtRow>
            )
          })}
        </ul>
      </PanelShell>
  )
}

function PanelShell({summary, children}: { summary?: ReactNode; children: ReactNode }) {
  const {t} = useTranslation()
  return (
      <section className="flex flex-col gap-2" aria-labelledby="debts-heading">
        <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <h2 id="debts-heading" className="text-base font-semibold">{t('debts.title')}</h2>
          {summary && <div className="flex flex-wrap gap-x-3 text-[13px] font-medium">{summary}</div>}
        </div>
        <Card className="gap-0 py-0 shadow-xs">{children}</Card>
      </section>
  )
}

function DebtRow({name, label, amount, owing, note, children}: {
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
      <li className="flex flex-wrap items-center gap-3 px-4 py-3">
        <span
            className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-secondary text-[11px] font-semibold"
            title={name}>
          {initials(name)}
        </span>
        <span className="flex min-w-0 grow flex-col gap-0.5">
          <span className="truncate">{label}</span>
          {note && <span className="text-[13px] text-muted-foreground">{note}</span>}
        </span>
        {amount && (
            <span className={cn(
                'shrink-0 font-semibold tabular-nums',
                owing ? 'text-destructive' : 'text-emerald-700 dark:text-emerald-400',
            )}>
              {amount}
            </span>
        )}
        <span className="flex shrink-0 gap-2">{children}</span>
      </li>
  )
}
