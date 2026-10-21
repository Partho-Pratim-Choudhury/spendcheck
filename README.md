# SpendCheck

Personal budgeting app that checks a spend against your monthly plan *before* you make it.
Log spends by chat or voice; the app tells you what each one does to your category limits,
your savings target and your goals.

- **api/** — Spring Boot 3 (Java 21), Postgres, Flyway, JWT auth, Spring AI (OpenAI)
- **app/** — Expo / React Native (TypeScript, expo-router), Android first

## Design principles

- The LLM only extracts structure from what you say and phrases replies. All money math
  is deterministic Java in the budget engine.
- Nothing the LLM produces is saved until you confirm it.
- The OpenAI key lives only in the backend environment, never in the app.

## Run locally

Prerequisites: Docker, JDK 21+, Node 20+, the Expo Go app on an Android phone.

```bash
cp .env.example .env          # fill in DB_PASSWORD, JWT_SECRET, APP_USER_*
docker compose up -d postgres # Postgres 16 on localhost:5433

cd api && ./mvnw spring-boot:run      # API on :8077

cd app && cp .env.example .env        # set EXPO_PUBLIC_API_URL=http://<laptop-ip>:8077
npm install && npx expo start         # scan QR with Expo Go
```

If the phone can't reach the laptop (Wi-Fi client isolation), use `npx expo start --tunnel`
for the bundle and a Tailscale IP for `EXPO_PUBLIC_API_URL`.

## Tests

```bash
cd api && ./mvnw verify       # unit + Testcontainers integration tests (needs Docker)
cd app && npx tsc --noEmit
```

## Roadmap

| Week | Scope |
|---|---|
| 1 | Skeleton, schema, auth, categories + transactions CRUD, app shell + login |
| 2 | Monthly plan, period calculator, budget engine, plan form |
| 3 | Text input → LLM extraction → confirm card, pre-check verdicts |
| 4 | Voice input (speech-to-text) |
| 5 | Dashboard, spends list |
| 6 | Reports + charts, daily summary push |
| 7 | NLU eval set, APK release, daily use |
