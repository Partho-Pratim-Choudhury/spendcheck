# SpendCheck API contract (v1)

Source of truth for `api/` and `app/`. Base path `/api/v1`. JSON camelCase. Money is always
integer **paise** (`…Paise`). Dates `YYYY-MM-DD` in the user's timezone (Asia/Kolkata);
instants ISO-8601 UTC. Errors are RFC 7807 ProblemDetail `{type,title,status,detail,errors?}`.
All endpoints except `POST /auth/login` and `/actuator/health` need `Authorization: Bearer <jwt>`.

Existing (unchanged): `POST /auth/login`, `GET /me`, `GET|POST|PATCH /categories`,
`GET|POST|PATCH|DELETE /transactions`.

Change to existing: `POST /transactions` accepts optional `rawInputId` (number; must belong to the
user, else 400 `errors.rawInputId`).

---

## Plans

A plan covers one **period**: from the salary date to the day before the next salary date.
Salary day `d` in a month shorter than `d` days is clamped to that month's last day.

```
Plan = {
  id, periodStart, periodEnd, status: "ACTIVE"|"CLOSED",
  incomePaise, savingsTargetPaise,
  limits: [{ categoryId, categoryName, kind: "FIXED"|"NEED"|"WANT", limitPaise }],
  fixedTotalPaise,          // Σ limits of FIXED categories (rent, EMI, SIP…)
  variableLimitsPaise,      // Σ limits of NEED + WANT categories
  discretionaryPaise,       // income − fixedTotal − savingsTarget
  unallocatedPaise,         // discretionary − variableLimits (may be negative = over-allocated)
  dailyBudgetPaise          // discretionary / daysTotal (floor)
}
```

- `GET /plans/current` → 200 Plan for the ACTIVE plan; **404** if none (app shows plan setup).
  If the active plan's `periodEnd` is before today, it is rolled over automatically first (see rollover).
- `PUT /plans/current` `{ incomePaise, savingsTargetPaise, limits: [{categoryId, limitPaise}] }`
  → 200 Plan. Creates the plan for the period containing today if none is active, else replaces
  income/savings/limits of the active one. Validation (400): income > 0; savings ≥ 0; each limit ≥ 0;
  categories exist, belong to user, not archived, no duplicates; `discretionaryPaise` must be ≥ 0
  (`errors.savingsTargetPaise`). Over-allocation (unallocated < 0) is allowed but reported.
- `POST /plans/rollover` (no body) → 201 Plan. "Salary came today": closes the active plan (if any)
  and starts a new one at **today**. New `periodEnd` = (scheduled salary date nearest to today,
  moved one month forward) − 1 day. Copies income, savings and limits from the previous plan.
- `GET /plans` → `[{ id, periodStart, periodEnd, status }]` newest first (report month picker).
- Auto rollover: also a daily job at 00:05 user tz — if the active plan ended, close it and create
  the next period (`periodStart = old end + 1`, end by the salary-day rule), copying amounts.

`PUT /users/me/salary-day` `{ salaryDay: 1..31 }` → 200 `{id,email,name,salaryDay}`. Affects future
periods only.

## Goals

```
Goal = { id, name, targetPaise, savedPaise, monthlyContributionPaise, targetDate|null,
         progressPct, onTrackDate|null }   // onTrackDate = projected completion at current contribution
```
- `GET /goals`, `POST /goals` `{name, targetPaise, savedPaise?, monthlyContributionPaise, targetDate?}` → 201,
  `PATCH /goals/{id}` partial → 200, `DELETE /goals/{id}` → 204.
- The **primary goal** is the one with the earliest `targetDate` (nulls last, then lowest id).

## Dashboard

`GET /dashboard` → 404 if no plan.
```
{
  plan: { id, periodStart, periodEnd, daysTotal, daysElapsed, daysLeft },   // elapsed includes today
  today: { date, budgetPaise, spentPaise, leftPaise },   // see engine
  month: { discretionaryPaise, spentPaise, leftPaise, projectedPaise, savingsAtRiskPaise },
  categories: [{ categoryId, name, kind, limitPaise, spentPaise, leftPaise, pct, status }],
                // NEED + WANT only, sorted by status (OVER, WARN, OK) then pct desc
  burn: [{ date, cumulativePaise }],     // every day periodStart..today, variable spend cumulative
  pausesSavedPaise, skippedCount,
  primaryGoal: Goal|null
}
```

## Spend checks (pre-check)

```
Verdict = {
  checkId,                       // spend_checks row id
  level: "GREEN"|"AMBER"|"RED",
  amountPaise, categoryId, categoryName,
  categoryLimitPaise, categorySpentPaise, categoryLeftPaise,   // before this spend
  overflowPaise,                 // max(0, amount − max(0, categoryLeft))
  savingsHitPaise,               // = overflowPaise (overflow comes out of savings)
  goalName|null, goalDelayDays|null,
  todayBudgetPaise, todaySpentPaise,
  safePerDayBeforePaise, safePerDayAfterPaise,
  reasons: [string],             // short machine-made phrases, e.g. "over category limit"
  message                        // one-sentence template text
}
```
- `POST /checks` `{ amountPaise, categoryId }` → 201 Verdict (also used by the chat pipeline).
  409 if no active plan.
- `POST /checks/{id}/decision` `{ decision: "PROCEEDED"|"SKIPPED"|"DEFERRED", transactionId? }` → 200
  `{ id, decision }`. `transactionId` (must be user's) links the spend when PROCEEDED.

## Text / voice input

```
InputResult = {
  rawInputId, intent: "LOG_EXPENSE"|"PRE_CHECK"|"QUERY"|"UNDO"|"OTHER",
  transcript|null,               // voice only
  draft|null: { amountPaise, categoryId, categoryName, merchant|null, note|null, spentAt,
                needOrWant|null, after: { categorySpentPaise, categoryLimitPaise } },
  verdict|null: Verdict,         // PRE_CHECK; draft also present so "Buy anyway" can create it
  answer|null: { text },         // QUERY, UNDO, OTHER
  undoneTransactionId|null,      // UNDO
  followUp|null: { question, missing: [string] }  // missing/low-confidence fields
}
```
- `POST /inputs/text` `{ text }` (1..500 chars) → 200 InputResult.
- `POST /inputs/voice` multipart field `audio` (m4a/aac/mp4/webm/ogg/wav, ≤ 2 MB) → 200 InputResult
  with `transcript`. 400 if empty/too large/unsupported; 502 ProblemDetail if speech-to-text fails.
- Drafts are **never saved** by `/inputs/*`. The app confirms with `POST /transactions` (source
  TEXT/VOICE, `rawInputId`). Only UNDO writes (cancels the user's most recent CONFIRMED transaction
  created in the last 24 h; if none, answer says so).
- 502 ProblemDetail when the LLM call fails; 503 if `OPENAI_API_KEY` is not configured.
- Query types answered from the engine: category left, total spent this period, safe to spend
  today, primary goal status. No plan → answer explains a plan is needed.

## Reports

`GET /reports/monthly?planId=` (default: latest plan) →
```
{
  plan: { id, periodStart, periodEnd, status },
  totals: { incomePaise, fixedPaise, discretionaryPaise, spentPaise, savedPaise,
            savingsTargetPaise, underPlanPaise },   // saved = savingsTarget + (discretionary − spent)
  byCategory: [{ categoryId, name, kind, limitPaise, spentPaise }],   // NEED+WANT, spent desc
  needsPaise, wantsPaise, untaggedPaise,            // by transaction needOrWant (fallback category kind)
  topWants: [{ label, count, totalPaise }],         // WANT spends grouped by lower(merchant) or category, top 5
  pausesSavedPaise, skippedCount,
  wantsByWeekday: [7 numbers, Mon..Sun, paise],
  trend: [{ planId, periodStart, spentPaise, discretionaryPaise }]   // up to last 6 plans incl. this, oldest first
}
```

## Daily summary

`GET /summary/daily?date=` (default today) →
`{ date, spentPaise, count, budgetPaise, overPaise, month: {spentPaise, discretionaryPaise, projectedPaise, savingsAtRiskPaise}, pausesSavedPaise, text }`
— 404 if no plan covers the date.
