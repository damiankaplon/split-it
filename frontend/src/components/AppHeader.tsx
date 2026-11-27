import {Languages, LogOut, Menu, X} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {type ReactNode, useState} from 'react'
import {Link, useLocation} from 'react-router'
import {Dialog as DialogPrimitive} from 'radix-ui'
import {Avatar, AvatarFallback} from '@/components/ui/avatar'
import {Dialog, DialogOverlay, DialogPortal, DialogTrigger} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {LogoMark, Wordmark} from '@/components/Logo'
import {changeLanguage, currentLanguage, type Language, LANGUAGES} from '@/i18n'
import {initials} from '@/lib/initials'
import {cn} from '@/lib/utils'

interface AppHeaderProps {
  username: string
  onSignOut: () => void
}

export function AppHeader({username, onSignOut}: AppHeaderProps) {
  const {t} = useTranslation()
  // Project pages belong to the Projects tab too
  const {pathname} = useLocation()
  const isPersonal = pathname.startsWith('/personal')
  const isSavings = pathname.startsWith('/savings')
  const [drawerOpen, setDrawerOpen] = useState(false)

  return (
    // A floating glass capsule, 12px below the top (or the phone's status bar), rather than a full-width bar
    <header className="sticky top-[calc(env(safe-area-inset-top)_+_0.75rem)] z-30 mt-[calc(env(safe-area-inset-top)_+_0.75rem)] px-4">
      <div className="mx-auto flex h-14 max-w-[1200px] items-center gap-3 rounded-full border bg-card/70 pr-2 pl-3 shadow-card backdrop-blur-xl">
        <Dialog open={drawerOpen} onOpenChange={setDrawerOpen}>
          <DialogTrigger asChild>
            <button
              type="button"
              aria-label={t('header.openMenu')}
              className="-ml-2 cursor-pointer rounded-lg p-2 outline-none hover:bg-secondary focus-visible:ring-4 focus-visible:ring-ring/20 md:hidden"
            >
              <Menu className="size-5" />
            </button>
          </DialogTrigger>
          <DialogPortal>
            <DialogOverlay className="md:hidden" />
            <DialogPrimitive.Content
              aria-describedby={undefined}
              className="fixed inset-y-0 left-0 z-50 flex w-72 max-w-[85vw] flex-col gap-4 border-r bg-card p-4 pt-[calc(env(safe-area-inset-top)_+_1rem)] pb-[calc(env(safe-area-inset-bottom)_+_1rem)] pl-[calc(env(safe-area-inset-left)_+_1rem)] shadow-pop outline-none duration-200 data-[state=closed]:animate-out data-[state=closed]:slide-out-to-left data-[state=open]:animate-in data-[state=open]:slide-in-from-left md:hidden"
            >
              <DialogPrimitive.Title className="sr-only">{t('header.navigation')}</DialogPrimitive.Title>
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-2.5">
                  <LogoMark />
                  <Wordmark />
                </span>
                <DialogPrimitive.Close
                  aria-label={t('header.closeMenu')}
                  className="cursor-pointer rounded-lg p-2 outline-none hover:bg-secondary focus-visible:ring-4 focus-visible:ring-ring/20"
                >
                  <X className="size-5" />
                </DialogPrimitive.Close>
              </div>
              <nav aria-label={t('header.navigation')} className="flex flex-col gap-1">
                <HeaderTab
                  to="/"
                  active={!isPersonal && !isSavings}
                  onNavigate={() => setDrawerOpen(false)}
                  className="py-2.5"
                >
                  {t('header.projects')}
                </HeaderTab>
                <HeaderTab
                  to="/personal"
                  active={isPersonal}
                  onNavigate={() => setDrawerOpen(false)}
                  className="py-2.5"
                >
                  {t('header.personal')}
                </HeaderTab>
                <HeaderTab to="/savings" active={isSavings} onNavigate={() => setDrawerOpen(false)} className="py-2.5">
                  {t('header.savings')}
                </HeaderTab>
              </nav>
            </DialogPrimitive.Content>
          </DialogPortal>
        </Dialog>
        <Link
          to="/"
          className="flex items-center gap-2.5 rounded-lg outline-none focus-visible:ring-4 focus-visible:ring-ring/20"
        >
          <LogoMark />
          <Wordmark />
        </Link>
        <nav aria-label={t('header.navigation')} className="ml-4 hidden items-center gap-1 md:flex">
          <HeaderTab to="/" active={!isPersonal && !isSavings}>
            {t('header.projects')}
          </HeaderTab>
          <HeaderTab to="/personal" active={isPersonal}>
            {t('header.personal')}
          </HeaderTab>
          <HeaderTab to="/savings" active={isSavings}>
            {t('header.savings')}
          </HeaderTab>
        </nav>
        <div className="grow" />
        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label={t('header.accountMenu')}
            className="cursor-pointer rounded-full outline-none focus-visible:ring-4 focus-visible:ring-ring/20"
          >
            <Avatar className="size-10">
              <AvatarFallback className="bg-ink text-[13px] font-semibold text-on-ink">
                {initials(username)}
              </AvatarFallback>
            </Avatar>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="min-w-48">
            <DropdownMenuLabel className="font-normal">
              <span className="text-muted-foreground">{t('header.signedInAs')}</span>
              <div className="truncate font-medium">{username}</div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuSub>
              <DropdownMenuSubTrigger>
                <Languages />
                {t('language.label')}
              </DropdownMenuSubTrigger>
              <DropdownMenuSubContent>
                <DropdownMenuRadioGroup
                  value={currentLanguage()}
                  onValueChange={(language) => void changeLanguage(language as Language)}
                >
                  {LANGUAGES.map((language) => (
                    <DropdownMenuRadioItem key={language} value={language} lang={language}>
                      {t(`language.${language}`)}
                    </DropdownMenuRadioItem>
                  ))}
                </DropdownMenuRadioGroup>
              </DropdownMenuSubContent>
            </DropdownMenuSub>
            <DropdownMenuItem onSelect={onSignOut}>
              <LogOut />
              {t('header.signOut')}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}

function HeaderTab({
  to,
  active,
  children,
  onNavigate,
  className,
}: {
  to: string
  active: boolean
  children: ReactNode
  onNavigate?: () => void
  className?: string
}) {
  return (
    <Link
      to={to}
      onClick={onNavigate}
      aria-current={active ? 'page' : undefined}
      className={cn(
        'rounded-full px-4 py-2 text-sm font-medium outline-none transition-colors focus-visible:ring-4 focus-visible:ring-ring/20',
        active ? 'bg-ink text-on-ink' : 'text-muted-foreground hover:bg-foreground/5 hover:text-foreground',
        className,
      )}
    >
      {children}
    </Link>
  )
}
