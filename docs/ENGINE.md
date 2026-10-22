# Budget engine rules

Pure Java (`api/.../engine`, no Spring, no I/O). All amounts `long` paise; divisions floor.
Inputs: the plan (income, savings target, per-category limits with kind), the period's CONFIRMED
transactions, goals, SKIPPED spend checks, and `today` (user tz). Only NEED and WANT categories
are "variable"; spends in FIXED categories (paying rent) never touch discretionary numbers.

## Period

- `scheduledSalaryDate(year, month, salaryDay)` = `min(salaryDay, lengthOfMonth)` of that month.
- `periodContaining(date, salaryDay)`: start = latest scheduled salary date ≤ date; end = next
  scheduled salary date − 1 day.
- Rollover on day `t`: `nearest` = the scheduled salary date (previous, current or next month)
  closest to `t` (ties → earlier). New period = `[t, scheduled(nearest's month + 1) − 1]`.
- `daysTotal = end − start + 1`, `daysElapsed = today − start + 1` (clamped to 1..daysTotal),
  `daysLeft = end − today + 1` (min 1).

## Month numbers

```
fixedTotal          = Σ limit(c) for FIXED c
discretionary       = income − fixedTotal − savingsTarget
spent               = Σ variable spends in period
spentBeforeToday    = Σ variable spends with date < today
spentToday          = Σ variable spends dated today
left                = discretionary − spent
todayBudget         = max(0, (discretionary − spentBeforeToday) / daysLeft)
todayLeft           = todayBudget − spentToday             (may be negative)
projected           = spent × daysTotal / daysElapsed
savingsAtRisk       = max(0, projected − discretionary)
pausesSaved         = Σ amount of SKIPPED checks in period
```

## Category status

`spent(c)`, `left(c) = limit(c) − spent(c)`, `pct = spent × 100 / limit` (limit 0 → 100 if spent>0
else 0). Status: `OVER` if spent > limit; `WARN` if pct ≥ 75; else `OK`. A NEED/WANT category with
no limit row counts as limit 0.

## Verdict for "spend X in category c"

```
catLeft        = left(c)                       (before the spend)
overflow       = max(0, X − max(0, catLeft))
safePerDayBefore = max(0, (left + spentToday) / daysLeft)        // = todayBudget when nothing else changed
safePerDayAfter  = max(0, (left + spentToday − X) / daysLeft)
RED   if overflow > 0
AMBER if not RED and (spentToday + X > todayBudget  or  X × 2 > catLeft)
GREEN otherwise
savingsHit     = overflow
goalDelayDays  = primary goal with monthlyContribution > 0 and overflow > 0:
                 ceil(overflow × 30 / monthlyContribution)  else null
```
`reasons` (in this order, only those that apply): "over category limit", "over today's safe
spend", "uses more than half of what's left in category".

Message templates (₹ with Indian grouping, whole rupees). Rounding never flatters the user:
amounts left / safe to spend round **down**, the RED overflow rounds **up** (a 1-paise overflow
never reads "₹0"), everything else (spent, projected, limits, overages) rounds half-up:
- GREEN: "Fits. {category} left after: ₹{catLeft − X}."
- AMBER: "Careful. {first reason, capitalised}. {category} left after: ₹{max(0,catLeft − X)}."
- RED:   "Over plan. ₹{overflow} more than {category} has left — it comes out of savings."
  plus " {goal} delayed ~{n} days." when goalDelayDays present.
