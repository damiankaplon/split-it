import i18n from 'i18next'
import {initReactI18next} from 'react-i18next'
import en from './locales/en'
import pl from './locales/pl'

export const LANGUAGES = ['pl', 'en'] as const
export type Language = (typeof LANGUAGES)[number]

const DEFAULT_LANGUAGE: Language = 'pl'
const STORAGE_KEY = 'splitit.language'

const isLanguage = (value: unknown): value is Language => LANGUAGES.includes(value as Language)

/** The user's explicit choice wins; otherwise Polish, regardless of browser settings. */
function initialLanguage(): Language {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return isLanguage(stored) ? stored : DEFAULT_LANGUAGE
  } catch {
    return DEFAULT_LANGUAGE
  }
}

void i18n.use(initReactI18next).init({
  resources: {
    pl: {translation: pl},
    en: {translation: en},
  },
  lng: initialLanguage(),
  fallbackLng: DEFAULT_LANGUAGE,
  supportedLngs: LANGUAGES,
  // Resources are bundled, so there is nothing to wait for
  initAsync: false,
  interpolation: {
    // React already escapes rendered values
    escapeValue: false,
  },
})

document.documentElement.lang = i18n.language
i18n.on('languageChanged', (language) => {
  document.documentElement.lang = language
  try {
    localStorage.setItem(STORAGE_KEY, language)
  } catch {
    // Storage unavailable (e.g. private mode); the choice just won't persist
  }
})

export function currentLanguage(): Language {
  return isLanguage(i18n.language) ? i18n.language : DEFAULT_LANGUAGE
}

export function changeLanguage(language: Language) {
  return i18n.changeLanguage(language)
}
