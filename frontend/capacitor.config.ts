import type {CapacitorConfig} from '@capacitor/cli'

// Wraps the Vite build (dist/) into the native Android and iOS apps in android/ and ios/.
// The app talks to the backend and Keycloak over absolute URLs, see .env.mobile.
const config: CapacitorConfig = {
  appId: 'pl.damiankaplon.splitit',
  appName: 'SplitIt!',
  webDir: 'dist',
  plugins: {
    SystemBars: {
      // The app has a light theme only, so the status and navigation bars use dark icons
      style: 'LIGHT',
      // index.html declares viewport-fit=cover; telling Android up front avoids a layout jump on start
      initialViewportFitValueHint: 'cover',
    },
  },
}

export default config
