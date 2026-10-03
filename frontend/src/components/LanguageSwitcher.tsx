import {useTranslation} from 'react-i18next'
import {changeLanguage, currentLanguage, LANGUAGES} from '@/i18n'
import {Button} from '@/components/ui/button'
import {cn} from '@/lib/utils'

/** Inline "Polski · English" toggle for screens without the account menu. */
export function LanguageSwitcher({className}: { className?: string }) {
  const {t} = useTranslation()
  const active = currentLanguage()

  return (
      <nav aria-label={t('language.label')} className={cn('flex items-center gap-1', className)}>
        {LANGUAGES.map((language) => (
            <Button
                key={language}
                variant="ghost"
                size="sm"
                lang={language}
                aria-current={language === active}
                className="h-8 px-2 text-[13px] text-muted-foreground aria-[current=true]:font-semibold aria-[current=true]:text-foreground"
                onClick={() => void changeLanguage(language)}
            >
              {t(`language.${language}`)}
            </Button>
        ))}
      </nav>
  )
}
