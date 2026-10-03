import 'i18next'
import type {Translations} from './locales/pl'

// Makes t() keys and interpolation variables type-checked against the Polish resources.
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation'
    resources: {
      translation: Translations
    }
  }
}
