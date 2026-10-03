import {Key} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {Button} from '@/components/ui/button'
import {Card} from '@/components/ui/card'
import {LanguageSwitcher} from '@/components/LanguageSwitcher'
import {LogoMark} from '@/components/Logo'

interface SignInPageProps {
  onSignIn: () => void
  keycloakUnavailable?: boolean
}

export function SignInPage({onSignIn, keycloakUnavailable}: SignInPageProps) {
  const {t} = useTranslation()

  return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-6">
        <Card className="w-full max-w-[400px] gap-6 p-8 shadow-xs">
          <div className="flex flex-col items-center gap-3 text-center">
            <LogoMark size="lg"/>
            <h1 className="text-[22px] font-semibold tracking-[-0.01em]">{t('signIn.title')}</h1>
            <p className="text-muted-foreground">{t('signIn.subtitle')}</p>
          </div>
          <Button className="h-11 rounded-lg" onClick={onSignIn}>
            <Key/>
            {t('signIn.button')}
          </Button>
          {keycloakUnavailable ? (
              <p className="text-center text-[13px] text-destructive" role="alert">{t('signIn.keycloakUnavailable')}</p>
          ) : (
              <p className="text-center text-[13px] text-muted-foreground">{t('signIn.hint')}</p>
          )}
        </Card>
        <LanguageSwitcher/>
      </div>
  )
}
