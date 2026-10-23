# Deploying SpendCheck

Backend → **Render** (Docker + managed Postgres, free, no card). App → **EAS** (cloud-built
APK/AAB). CI runs on every push/PR via GitHub Actions; deploys below are run by hand.

## 1. Backend on Render

`render.yaml` at the repo root defines the API and a Postgres database.

1. Sign in at dashboard.render.com (GitHub login, no card).
2. **New → Blueprint** → pick the `spendcheck` repo → Apply. Render creates the web service and the
   database from `render.yaml`, wiring the `DB_*` vars and generating `JWT_SECRET` automatically.
3. It pauses for the secret vars marked `sync:false`. Set them on the service → Environment:
   - `OPENAI_API_KEY` = your Groq key (`gsk_...`)
   - `APP_USER_EMAIL` = your login email
   - `APP_USER_PASSWORD` = your login password
4. Save → it builds the Docker image and deploys. Check:
   ```
   curl https://<your-service>.onrender.com/actuator/health   # {"status":"UP"}
   ```

Notes:
- The app reads Render's `PORT`; the Groq base URL and model ids are plain env in `render.yaml`.
- Flyway runs the migrations on first boot; the seed user is created from `APP_USER_*`.
- **Free tier sleeps** after ~15 min idle — the first request then takes 30–60s to wake.
- **Render's free Postgres expires after 90 days.** For a permanent DB, create a free one at
  neon.tech (no card) and set `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` from its connection
  details (append `?sslmode=require` is handled by Neon's host — use the pooled connection string's
  parts); then remove the `databases:` block from `render.yaml`.
- Use real OpenAI instead of Groq by clearing `SPRING_AI_OPENAI_BASE_URL` and setting OpenAI model
  ids + an OpenAI key.

## 2. App on EAS

One-time:

```bash
npm i -g eas-cli
eas login
cd app
eas build:configure        # links the project (writes the EAS project id into app.json)
```

Point `eas.json`'s `EXPO_PUBLIC_API_URL` at your Fly URL (or leave it — the app lets you set the
server on the sign-in screen at runtime). Build:

```bash
eas build --profile preview --platform android     # cloud-built APK, returns a download/QR link
```

The build runs in Expo's cloud, so it is unaffected by the local TLS interception that breaks
Gradle on this laptop. For the Play Store internal track: `eas build --profile production` then
`eas submit --profile production --platform android` (needs a $25 Play Console account).

## 3. CI (already wired: `.github/workflows/ci.yml`)

Every push to `main` and every PR runs three jobs:
- **backend** — `./mvnw -B verify` (unit + Testcontainers integration tests).
- **mobile** — `tsc --noEmit`, `expo lint`, `jest`.
- **secret-scan** — gitleaks over the full history.

Turn the gates on in GitHub → Settings → Branches → add a rule for `main` → require the three
checks to pass before merging.

## 4. Continuous deploy

- Backend: `render.yaml` sets `autoDeploy: true`, so Render redeploys on every push to `main` once
  the blueprint is linked — no GitHub Actions needed.
- App: a workflow can run `eas build --profile preview --non-interactive` on a tag using an
  `EXPO_TOKEN` GitHub Actions secret. Left for a follow-up until the Expo account is set up.
