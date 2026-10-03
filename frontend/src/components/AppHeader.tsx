import {Languages, LogOut} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {Link} from 'react-router'
import {Avatar, AvatarFallback} from '@/components/ui/avatar'
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
import {LogoMark} from '@/components/Logo'
import {changeLanguage, currentLanguage, type Language, LANGUAGES} from '@/i18n'
import {initials} from '@/lib/initials'

interface AppHeaderProps {
  username: string
  onSignOut: () => void
}

export function AppHeader({username, onSignOut}: AppHeaderProps) {
  const {t} = useTranslation()

  return (
      <header className="border-b bg-card">
        <div className="mx-auto flex h-[60px] max-w-[1200px] items-center gap-3 px-6">
          <Link to="/" className="flex items-center gap-2 text-[15px] font-semibold">
            <LogoMark/>
            SplitIt!
          </Link>
          <div className="grow"/>
          <DropdownMenu>
            <DropdownMenuTrigger
                aria-label={t('header.accountMenu')}
                className="cursor-pointer rounded-full outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
            >
              <Avatar className="size-9 border">
                <AvatarFallback className="bg-secondary text-[13px] font-semibold text-foreground">
                  {initials(username)}
                </AvatarFallback>
              </Avatar>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="min-w-48">
              <DropdownMenuLabel className="font-normal">
                <span className="text-muted-foreground">{t('header.signedInAs')}</span>
                <div className="truncate font-medium">{username}</div>
              </DropdownMenuLabel>
              <DropdownMenuSeparator/>
              <DropdownMenuSub>
                <DropdownMenuSubTrigger>
                  <Languages/>
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
                <LogOut/>
                {t('header.signOut')}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </header>
  )
}
