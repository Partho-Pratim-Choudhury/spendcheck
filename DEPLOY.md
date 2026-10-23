# Deploying SpendCheck

Backend → **Fly.io** (Docker + managed Postgres). App → **EAS** (cloud-built APK/AAB).
CI runs on every push/PR via GitHub Actions; deploys below are run by hand until CD is wired.

## 1. Backend on Fly.io

One-time:

```bash
curl -L https://fly.io/install.sh | sh     # install flyctl
fly auth login
cd api
fly launch --no-deploy                     # creates the app from fly.toml (pick a unique app name)
fly postgres create --name spendcheck-db --region sin --initial-cluster-size 1
fly postgres attach spendcheck-db          # prints a DATABASE_URL (postgres://user:pass@host:5432/db)
```

Set secrets (the app reads a JDBC URL, so convert `postgres://` → `jdbc:postgresql://` and split out
the credentials). Generate strong values; never commit them:

```bash
fly secrets set \
  SPRING_DATASOURCE_URL="jdbc:postgresql://<host>:5432/<db>" \
  SPRING_DATASOURCE_USERNAME="<user>" \
  SPRING_DATASOURCE_PASSWORD="<pass>" \
  JWT_SECRET="$(openssl rand -base64 48)" \
  OPENAI_API_KEY="gsk_...your Groq key..." \
  APP_USER_EMAIL="you@example.com" \
  APP_USER_PASSWORD="<your app password>"
```

Deploy and check:

```bash
fly deploy
curl https://<your-app>.fly.dev/actuator/health   # {"status":"UP"}
```

Notes:
- `fly.toml` already sets `SERVER_PORT=8080`, the Groq base URL and the model ids as plain env.
- Flyway runs the migrations on first boot; the seed user is created from `APP_USER_*`.
- `auto_stop_machines` scales to zero when idle; the first request after idle takes a few seconds.
- Flip the repo to use real OpenAI instead of Groq by removing `SPRING_AI_OPENAI_BASE_URL` and
  setting OpenAI model ids + an OpenAI key.

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

## 4. Later: continuous deploy

- Backend: a `deploy.yml` on push to `main` that runs `fly deploy` using a `FLY_API_TOKEN`
  GitHub Actions secret (`fly tokens create deploy`).
- App: `eas build --profile preview --non-interactive` on a tag, using an `EXPO_TOKEN` secret.

These need the Fly and Expo accounts set up first, so they are left for a follow-up.
