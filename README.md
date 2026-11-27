# SplitIt!

SplitIt! lets users create projects and invite others to join. Everyone in a project can add an expense.

- `backend/` - Kotlin / Spring Boot API (Gradle)
- `frontend/` - React / TypeScript / Vite app (npm), also wrapped as the Android and iOS app with
  [Capacitor](https://capacitorjs.com) (`frontend/android`, `frontend/ios`)

## Mobile app (Android & iOS)

The mobile app is the same React app running in a native WebView. `frontend/capacitor.config.ts` configures it (app id
`pl.damiankaplon.splitit`), and `frontend/.env.mobile` holds the absolute production URLs it uses.

### Building

You need Android Studio for Android, and a Mac with Xcode for iOS. Run these in `frontend/`:

```sh
npm run build:mobile   # build with .env.mobile and copy it into android/ and ios/ (cap sync)
npm run open:android   # open the project in Android Studio, then run it on a device or emulator
npm run open:ios       # open the project in Xcode (set the signing team once), then run it
```

Always use `npm run build:mobile`. A plain `npm run build` produces the web build, which calls `/api` on its own origin;
that does not work inside the app. To point the app at another backend, override values in `.env.mobile.local`
(git-ignored).

The icons and splash screens are generated from `frontend/assets/`: after changing the logo, run
`node assets/render-sources.mjs` and then `npm run assets`.

### Sign-in

The app opens the Keycloak login page in the system browser (Chrome Custom Tabs, SFSafariViewController). Keycloak then
redirects to `pl.damiankaplon.splitit://auth`, a URL scheme registered by the app, and keycloak-js finishes the PKCE code
exchange. The app requests `offline_access` and keeps the tokens in Capacitor Preferences, so users stay signed in
between launches. Signing out revokes the session at Keycloak. See `frontend/src/auth/native.ts`.

### One-time server setup

- **Keycloak**: in the `split_it` realm, create the client `splitit-mobile`: OpenID Connect, client authentication off,
  only the standard flow, PKCE method `S256`, valid redirect URI `pl.damiankaplon.splitit://auth`, and web origins
  `https://localhost` and `capacitor://localhost`. Keep `offline_access` among its optional client scopes (the default).
  The local realm import (`backend/src/test/resources/keycloak/split_it-realm.json`) already has this client, but
  `--import-realm` skips a realm that already exists. On an existing local database, add the client by hand.
- **Backend**: the app calls the API from the WebView origin, `https://localhost` on Android or `capacitor://localhost`
  on iOS. `splitit.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`) allows both by default.

## Code style

Formatting is configured in the repository, so IntelliJ and the tools below agree:

- `.editorconfig` - indentation, line endings and Kotlin import rules (read by IntelliJ and ktlint)
- `frontend/.prettierrc.json` - Prettier settings for the frontend
- `backend/build.gradle.kts` - Spotless with ktlint for the backend

### Frontend

Run these in `frontend/`:

```sh
npm run lint            # ESLint
npm run format          # format with Prettier
npm run format:check    # check formatting without changing files
```

### Backend

Run these in `backend/`:

```sh
./gradlew spotlessCheck   # check formatting
./gradlew spotlessApply   # format the code
```

Gradle needs `JAVA_HOME` to point to a JDK 25.

### Git hook (one-time setup)

A pre-commit hook in `.githooks/` blocks the commit if formatting is wrong:

- staged Kotlin files under `backend/` - runs `./gradlew spotlessCheck`
- staged files under `frontend/` - runs `prettier --check` on those files (needs `npm install` in `frontend/` first)

Git does not enable hooks from a clone automatically, so run this once per clone:

```sh
git config core.hooksPath .githooks
```

If the hook blocks a commit, run `./gradlew spotlessApply` in `backend/` or `npm run format` in `frontend/`, stage the
changes and commit again.

### IntelliJ

- Settings → Languages & Frameworks → JavaScript → Prettier: select the `frontend/node_modules/prettier` package and
  tick **Run on save**.
- IntelliJ reads `.editorconfig` automatically, including the Kotlin import settings.
