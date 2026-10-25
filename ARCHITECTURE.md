# SpendCheck — Architecture

How the app is put together, why it's built this way, and where to look for each piece.

SpendCheck is a budgeting app that checks a spend **before** you make it. You enter a spend by
text or voice ("swiggy 450", "can I buy shoes for 3500?") and it answers with the effect on your
category limit, today's safe-to-spend, and your savings goal. It's a monorepo: a Spring Boot API
and an Expo/React Native Android app.

```
┌────────────────────────────┐        HTTPS + JWT         ┌────────────────────────────┐
│  app/  (React Native, Expo) │ ─────────────────────────▶ │  api/  (Spring Boot, Java 21)│
│  chat · dashboard · spends  │                            │  auth · ledger · plan ·      │
│  reports · plan · summary   │ ◀───────────────────────── │  engine · check · report ·   │
└────────────────────────────┘      JSON (RFC 7807 errors) │  input                       │
                                                           └──────────┬─────────┬─────────┘
                                                                      │         │
                                                            ┌─────────▼──┐  ┌───▼────────────────┐
                                                            │ PostgreSQL │  │ OpenAI-compatible   │
                                                            │ (Flyway)   │  │ LLM + speech-to-text│
                                                            └────────────┘  │ (Groq free / OpenAI)│
                                                                            └────────────────────┘
```

## Core design principles

1. **The LLM is untrusted input, not the brain.** It only turns words into a structured draft and
   phrases replies. Every rupee of arithmetic is done by a deterministic, unit-tested Java engine.
   A model mistake can mis-read a number; it can never mis-calculate a budget or move money.
2. **Nothing the AI produces is saved until the user confirms it.** `/inputs/*` return a *draft* or
   a *verdict*; the app shows a card; only an explicit Confirm writes a transaction.
3. **The AI provider is a plug.** The backend talks to any OpenAI-compatible endpoint, selected by
   one environment variable, so it runs on Groq's free tier or on OpenAI unchanged.
4. **Multi-user by construction.** Every table is scoped by `user_id`; a user can only ever read or
   write their own rows.

---

## Backend (`api/`) — Spring Boot 3.5, Java 21, Spring AI 1.1

Package-by-feature under `com.parthopratim.spendcheck`:

| Package | Responsibility |
|---|---|
| `auth` | Login, invite-gated registration, JWT issue/validate, BCrypt, the current-user helper, `SecurityConfig` |
| `ledger` | Categories and transactions (CRUD, soft delete, idempotent create), spend aggregation SQL |
| `plan` | Budget plans, per-category limits, goals, salary-day periods, rollover (manual + nightly job) |
| `engine` | **Pure** budget math — no Spring, no I/O, no database. The heart of the app |
| `check` | Pre-spend verdicts (GREEN/AMBER/RED) and skip/proceed/defer decisions |
| `report` | Dashboard, monthly report, daily summary — composed from the engine |
| `input` | The NLU pipeline: `/inputs/text`, `/inputs/voice` → speech-to-text → LLM extraction → draft/verdict/answer |
| `common` | RFC 7807 error handling, rate limiter, shared exceptions |
| `config` | `AppProperties` (typed config), AI client wiring, category seeding, scheduling |

### The engine (`engine/`) — why it's separate

Plain Java records and functions, no framework. It takes a plan, the period's confirmed
transactions, goals and "today", and returns month numbers, per-category status, a spend verdict,
and goal projections. Because it's pure, it's exhaustively unit-tested (every formula, every
boundary — exactly-at-limit, 1 paise over, salary day 31 in February, leap years, early/late
rollover). Rules live in [`docs/ENGINE.md`](docs/ENGINE.md). Key types: `PeriodCalculator`,
`BudgetEngine`, `Verdict`, `MonthNumbers`, `CategoryStatus`, `Inr` (money formatting).

Money is **integer paise** everywhere (`long`), never floating point.

### Data model (PostgreSQL, Flyway migrations)

`V1__init.sql` → `V3__raw_inputs_retention.sql`. Tables: `users`, `categories`, `budget_plans`,
`plan_limits`, `goals`, `transactions`, `spend_checks`, `raw_inputs`, `push_tokens`. A partial
unique index enforces one ACTIVE plan per user; transactions carry a client-generated UUID so
`POST /transactions` is idempotent (safe retries). `raw_inputs` logs each NL input (text,
transcript, model output, latency) and is purged after 90 days.

### Security

- Stateless **JWT** (HS256), subject = user id; issuer checked; BCrypt password hashes.
- Login doesn't reveal whether an email exists (constant-time dummy-hash path).
- **Invite-gated sign-up**: `POST /auth/register` needs a shared code (`SIGNUP_INVITE_CODE`); blank
  code ⇒ sign-ups closed. Prevents strangers creating accounts on the public URL.
- **Per-user AI rate limit** (default 15/min) on the Groq-calling endpoints, so one user can't
  exhaust the shared free quota; over the limit → 429 with `Retry-After`.
- Secrets (AI key, JWT secret, DB password) live only in the host environment, never in the repo.
- Errors are RFC 7807 `application/problem+json`; stack traces are never returned.

### AI integration (`input/` + `config/AiConfig`)

```
text  ────────────────────────────────────┐
                                           ▼
voice ─► speech-to-text (Whisper) ─► transcript ─► LLM extraction (strict JSON schema)
                                                        │  {intent, amount, category, date, …}
                                                        ▼
                                          deterministic post-processing
                                   (resolve category by name/alias, bound the
                                    amount & date, ask a follow-up if unsure)
                                                        │
                        ┌───────────────────────────────┼───────────────────────────┐
                        ▼                               ▼                             ▼
                  LOG_EXPENSE → draft           PRE_CHECK → engine verdict      QUERY → engine answer
                  (user confirms)               (+ draft to act on)            UNDO → propose removal
```

- Clients are built in `AiConfig` against `spring.ai.openai.base-url` (empty = OpenAI; set to
  `https://api.groq.com/openai` for Groq). Models come from `LLM_MODEL` / `STT_MODEL`.
- Extraction uses a **strict JSON schema** so the model must return well-formed fields; the prompt
  carries today's date, the user's categories/aliases, Hinglish rules and few-shot examples.
- **Deterministic post-processing** (`Interpreter`) decides the final category/amount/date — the
  model's free text never directly picks protected values. Low confidence or missing fields → a
  follow-up question instead of a guess.
- Graceful failure: no key → 503 "not configured"; no credits → 503 with a billing message; provider
  error/rate limit → 502; bad audio → 400.
- Accuracy is measured by an **eval harness** over a 147-case golden set (English, Hinglish, typos,
  spoken amounts, relative dates), run with `./mvnw -Peval test`.

API contract: [`docs/API.md`](docs/API.md).

---

## App (`app/`) — Expo SDK 57, React Native, TypeScript

Routes (`src/app/`, Expo Router):

```
_layout.tsx          providers + signed-in/out route guard
index.tsx            entry redirect
(auth)/login.tsx     email + password + "create account"
(auth)/register.tsx  invite-gated sign-up
(tabs)/chat.tsx      text + hold-to-talk voice; draft / verdict / answer / undo cards
(tabs)/dashboard.tsx safe-to-spend, burn chart vs plan, category status, goal   (tab: Home)
(tabs)/transactions  filterable spends, edit/delete                              (tab: Spends)
(tabs)/reports.tsx   donut, needs vs wants, top wants, weekday, trend
plan.tsx             income, salary day, fixed costs, savings, goals, limits
summary.tsx          daily summary (also opened by the 21:30 reminder)
```

Supporting code (`src/lib/`): `api.ts` (typed fetch, timeouts, 401 handling, server URL),
`auth.tsx` (session context, SecureStore token), `queries.ts` (TanStack Query hooks + cache
invalidation), `calc.ts` (the amount-field expression evaluator), `money.ts`, `dates.ts`,
`chart.ts`, `insights.ts`, `notifications.ts`, `serverUrl.ts`, `rollover.ts`, `types.ts`.
Charts are inline `react-native-svg`; there's no UI-kit dependency — components in `src/components/`
are built on a small theme-token system.

Notable client behaviour:
- **Money math stays on the server.** The app only formats and displays; the one exception is the
  amount field's live calculator (`calc.ts`, a safe parser — no `eval`), which just previews the
  total the server will store.
- Token in **SecureStore**; a 401 clears the session; requests name the server in error messages.
- Voice recording is uploaded as a file part and deleted after; a daily **local** reminder
  (no push tokens) opens the summary.

---

## A request end to end — "can I buy shoes for 3500?"

1. Chat screen `POST /inputs/text`.
2. Backend: LLM extracts `{intent: PRE_CHECK, amount: 3500, category: Shopping}`; post-processing
   resolves Shopping and bounds the amount.
3. `CheckService` → `BudgetEngine`: category left, overflow into savings, goal delay, safe-per-day
   before/after → a RED/AMBER/GREEN `Verdict`, persisted in `spend_checks`.
4. Response = verdict **+** a draft (so "Buy anyway" can create the transaction).
5. App shows a verdict card: "Over plan. ₹2,300 more than Shopping has left — Goa trip delayed ~8
   days." Buttons: Skip (records the pause), Buy anyway (creates the transaction + records
   proceeded), Ask in 3 days (schedules a reminder).

---

## Deployment & delivery

- **Backend → Render** (Docker, free tier). `render.yaml` provisions the web service + managed
  Postgres, wires `DB_*`, generates the JWT secret; `autoDeploy` redeploys on push to `main`.
  The free instance sleeps when idle (~60s cold start), so auth calls use a long timeout and the
  app restores sessions optimistically.
- **App → EAS** cloud build (APK/AAB), or a local Gradle APK. The server URL is baked from
  `EXPO_PUBLIC_API_URL` (the Render URL); switchable at runtime from the Plan screen for dev.
- **CI** (`.github/workflows/ci.yml`): on every push/PR — backend `mvnw verify` (unit +
  Testcontainers integration tests), app `tsc`/`lint`/`jest`, and a gitleaks secret scan.

Deploy steps: [`DEPLOY.md`](DEPLOY.md).

---

## Testing

- **Engine**: dense table-driven unit tests (pure functions, every boundary).
- **API**: `@SpringBootTest` + Testcontainers against real Postgres; AI is stubbed, so no network
  or keys in the default build. Covers auth, registration, isolation, plans/rollover, checks,
  reports, rate limiting, and the input pipeline.
- **App**: Jest unit tests for the pure helpers (calc, dates, chart, money, insights, server URL,
  chat state reducers).
- **NLU accuracy**: the `-Peval` golden-set harness (real provider calls, off by default).

---

## Key decisions, in one place

| Decision | Why |
|---|---|
| LLM drafts, engine computes | A non-deterministic model must never own money math or writes |
| Confirm-before-save; undo proposes | A mis-read never silently changes data |
| Money as integer paise | No floating-point rounding errors |
| Provider behind one base-URL env | Run free on Groq, or on OpenAI, with no code change |
| Invite code + per-user rate limit | A public URL on a shared free AI quota needs gating |
| Optimistic session restore | A free host's cold start shouldn't block the splash |
| Monorepo, package-by-feature | Contract (`docs/API.md`) shared; features stay cohesive |
