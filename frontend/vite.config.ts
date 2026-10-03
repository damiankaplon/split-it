import path from 'node:path'
import react, {reactCompilerPreset} from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'
import {defineConfig, loadEnv} from 'vite'

// https://vite.dev/config/
export default defineConfig(({mode}) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendUrl = env.BACKEND_URL ?? 'http://localhost:8080'

  return {
    plugins: [
      react(),
      babel({presets: [reactCompilerPreset()]}),
      tailwindcss(),
    ],
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname, './src'),
      },
    },
    server: {
      // The Keycloak client only allows redirects back to http://localhost:5173
      port: 5173,
      strictPort: true,
      // In dev the browser only talks to the Vite origin; /api/* is forwarded to the
      // Spring backend server-side, so no CORS configuration is needed on the backend.
      proxy: {
        '/api': {
          target: backendUrl,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  }
})
