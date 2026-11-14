# SplitIt!

SplitIt! lets users create projects and invite others to join. Everyone in a project can add an expense.

- `backend/` - Kotlin / Spring Boot API (Gradle)
- `frontend/` - React / TypeScript / Vite app (npm)

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
