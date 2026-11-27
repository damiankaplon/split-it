import {useTranslation} from 'react-i18next'
import {changeLanguage, LANGUAGES, useCurrentLanguage} from '@/i18n'
import {Button} from '@/components/ui/button'
import {cn} from '@/lib/utils'

/** Inline "Polski · English" toggle for screens without the account menu. */
export function LanguageSwitcher({className}: {className?: string}) {
  const {t} = useTranslation()
  const active = useCurrentLanguage()

  return (
    <nav
      aria-label={t('language.label')}
      className={cn(
        'inline-flex items-center gap-0.5 rounded-full bg-muted p-1 shadow-[inset_0_1px_2px_rgb(0_0_0/0.06)]',
        className,
      )}
    >
      {LANGUAGES.map((language) => (
        <Button
          key={language}
          variant="ghost"
          size="sm"
          lang={language}
          aria-current={language === active}
          className="h-8 rounded-full px-4 text-[13px] text-muted-foreground hover:bg-transparent hover:text-foreground aria-[current=true]:bg-card aria-[current=true]:text-foreground aria-[current=true]:shadow-card"
          onClick={() => void changeLanguage(language)}
        >
          {t(`language.${language}`)}
        </Button>
      ))}
    </nav>
  )
}
