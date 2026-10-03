// Polish is the default language and the source of truth for translation keys.
const pl = {
  language: {
    label: 'Język',
    pl: 'Polski',
    en: 'English',
  },
  signIn: {
    title: 'Zaloguj się do SplitIt!',
    subtitle: 'Śledź wspólne wydatki i sprawdzaj, kto ile komu jest winien.',
    button: 'Kontynuuj przez Keycloak',
    hint: 'Przekierujemy Cię na stronę logowania, a potem z powrotem tutaj.',
    keycloakUnavailable: 'Nie można połączyć się z Keycloak. Czy jest uruchomiony?',
  },
  header: {
    accountMenu: 'Menu konta',
    signedInAs: 'Zalogowano jako',
    signOut: 'Wyloguj się',
  },
  projects: {
    title: 'Projekty',
    subtitle: 'Twoje projekty i te, do których należysz.',
    loading: 'Wczytywanie projektów…',
    loadError: 'Nie udało się wczytać projektów',
    empty: 'Nie należysz jeszcze do żadnego projektu. Utwórz pierwszy, aby zacząć.',
    owner: 'Właściciel',
    member: 'Uczestnik',
  },
  newProject: {
    button: 'Nowy projekt',
    title: 'Nowy projekt',
    description: 'Osoby możesz zaprosić po utworzeniu projektu.',
    nameLabel: 'Nazwa',
    namePlaceholder: 'np. Wyjazd do Zakopanego',
    cancel: 'Anuluj',
    submit: 'Utwórz projekt',
    error: 'Nie udało się utworzyć projektu. Spróbuj ponownie.',
  },
  invite: {
    button: 'Zaproś',
    title: 'Zaproś do projektu {{project}}',
    description: 'Każdy link pozwala dołączyć jednej osobie po zalogowaniu.',
    linkLabel: 'Link z zaproszeniem',
    creating: 'Tworzenie linku…',
    copy: 'Kopiuj',
    copied: 'Skopiowano',
    singleUse: 'Po użyciu link przestaje działać.',
    error: 'Nie udało się utworzyć linku z zaproszeniem.',
    newLink: 'Nowy link',
    members: 'Uczestnicy',
    loadingMembers: 'Wczytywanie…',
    you: '(Ty)',
    done: 'Gotowe',
  },
  join: {
    checking: 'Sprawdzanie zaproszenia…',
    unavailableTitle: 'Zaproszenie niedostępne',
    invalid: 'Ten link z zaproszeniem jest nieprawidłowy, wygasł lub został już użyty.',
    askOwner: 'Poproś właściciela projektu o nowy.',
    goToProjects: 'Przejdź do projektów',
    invitedTo: 'Zaproszono Cię do projektu',
    signedInInfo:
        'Zalogowano jako {{username}}. Po dołączeniu zobaczysz wszystkie wydatki w tym projekcie i będziesz dodawać nowe.',
    notNow: 'Nie teraz',
    join: 'Dołącz do projektu',
  },
}

export type Translations = typeof pl

export default pl
