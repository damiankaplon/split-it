/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_KEYCLOAK_URL?: string
  readonly VITE_KEYCLOAK_REALM?: string
  readonly VITE_KEYCLOAK_CLIENT_ID?: string
  readonly VITE_API_BASE_URL?: string
  /** Public URL of the web app, used in invitation links. Defaults to the current origin. */
  readonly VITE_WEB_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
