import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'
import {defineConfig, globalIgnores} from 'eslint/config'

export default defineConfig([
  // android/ and ios/ are the Capacitor native projects; they hold a copy of dist/
  globalIgnores(['dist', 'android', 'ios']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
      prettier,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    // shadcn/ui components are generated and export their variant helpers alongside components
    files: ['src/components/ui/**/*.tsx'],
    rules: {'react-refresh/only-export-components': 'off'},
  },
])
