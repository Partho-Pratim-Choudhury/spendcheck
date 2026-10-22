/** API types — mirror the SpendCheck backend contract (/api/v1). */

export type CategoryKind = 'FIXED' | 'NEED' | 'WANT';
export type NeedOrWant = 'NEED' | 'WANT';
export type TransactionSource = 'TEXT' | 'VOICE' | 'MANUAL';

/** RFC 7807 ProblemDetail as returned by the backend on errors. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}

export interface User {
  id: number | string;
  email: string;
  name: string;
  /** Day of month salary is credited (1–31); defines the budget cycle. */
  salaryDay: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  /** ISO-8601 timestamp. */
  expiresAt: string;
  user: User;
}

export interface Category {
  id: number | string;
  name: string;
  kind: CategoryKind;
  aliases: string[];
  archived: boolean;
}

export interface Transaction {
  id: string;
  amountPaise: number;
  categoryId: Category['id'];
  categoryName: string;
  merchant: string | null;
  note: string | null;
  /** ISO-8601 timestamp. */
  spentAt: string;
  needOrWant: NeedOrWant | null;
  source: TransactionSource;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionQuery {
  from?: string;
  to?: string;
  categoryId?: Category['id'];
  needOrWant?: NeedOrWant;
}

/** POST /transactions — idempotent on client-generated `id` (UUID). */
export interface CreateTransactionRequest {
  id: string;
  amountPaise: number;
  categoryId: Category['id'];
  merchant?: string;
  note?: string;
  spentAt: string;
  needOrWant?: NeedOrWant;
  source: TransactionSource;
}

/** POST /transactions also accepts the originating raw input (chat / voice). */
export interface CreateTransactionFromInputRequest extends CreateTransactionRequest {
  rawInputId?: number;
}

/** PATCH /transactions/{id} — all optional; null/absent = unchanged. */
export interface UpdateTransactionRequest {
  amountPaise?: number;
  categoryId?: Category['id'];
  merchant?: string;
  note?: string;
  spentAt?: string;
  needOrWant?: NeedOrWant;
}

export interface CreateCategoryRequest {
  name: string;
  kind: CategoryKind;
  aliases?: string[];
}

// --- Plans -------------------------------------------------------------------

export type PlanStatus = 'ACTIVE' | 'CLOSED';

export interface PlanLimit {
  categoryId: Category['id'];
  categoryName: string;
  kind: CategoryKind;
  limitPaise: number;
}

export interface Plan {
  id: number;
  /** YYYY-MM-DD */
  periodStart: string;
  /** YYYY-MM-DD (inclusive) */
  periodEnd: string;
  status: PlanStatus;
  incomePaise: number;
  savingsTargetPaise: number;
  limits: PlanLimit[];
  fixedTotalPaise: number;
  variableLimitsPaise: number;
  discretionaryPaise: number;
  unallocatedPaise: number;
  dailyBudgetPaise: number;
}

export interface PlanSummary {
  id: number;
  periodStart: string;
  periodEnd: string;
  status: PlanStatus;
}

export interface PutPlanRequest {
  incomePaise: number;
  savingsTargetPaise: number;
  limits: { categoryId: Category['id']; limitPaise: number }[];
}

// --- Goals -------------------------------------------------------------------

export interface Goal {
  id: number;
  name: string;
  targetPaise: number;
  savedPaise: number;
  monthlyContributionPaise: number;
  /** YYYY-MM-DD */
  targetDate: string | null;
  progressPct: number;
  onTrackDate: string | null;
}

export interface GoalInput {
  name: string;
  targetPaise: number;
  savedPaise?: number;
  monthlyContributionPaise: number;
  targetDate?: string | null;
}

/** PATCH /goals/{id} — partial; `clearTargetDate: true` removes the target date. */
export interface PatchGoal extends Partial<GoalInput> {
  clearTargetDate?: boolean;
}

// --- Dashboard ---------------------------------------------------------------

export type CategoryStatus = 'OK' | 'WARN' | 'OVER';

export interface DashboardCategory {
  categoryId: Category['id'];
  name: string;
  kind: CategoryKind;
  limitPaise: number;
  spentPaise: number;
  leftPaise: number;
  pct: number;
  status: CategoryStatus;
}

export interface BurnPoint {
  date: string;
  cumulativePaise: number;
}

export interface Dashboard {
  plan: { id: number; periodStart: string; periodEnd: string; daysTotal: number; daysElapsed: number; daysLeft: number };
  today: { date: string; budgetPaise: number; spentPaise: number; leftPaise: number };
  month: {
    discretionaryPaise: number;
    spentPaise: number;
    leftPaise: number;
    projectedPaise: number;
    savingsAtRiskPaise: number;
  };
  categories: DashboardCategory[];
  burn: BurnPoint[];
  pausesSavedPaise: number;
  skippedCount: number;
  primaryGoal: Goal | null;
}

// --- Spend checks ------------------------------------------------------------

export type VerdictLevel = 'GREEN' | 'AMBER' | 'RED';
export type CheckDecision = 'PROCEEDED' | 'SKIPPED' | 'DEFERRED';

export interface Verdict {
  checkId: number;
  level: VerdictLevel;
  amountPaise: number;
  categoryId: Category['id'];
  categoryName: string;
  categoryLimitPaise: number;
  categorySpentPaise: number;
  categoryLeftPaise: number;
  overflowPaise: number;
  savingsHitPaise: number;
  goalName: string | null;
  goalDelayDays: number | null;
  todayBudgetPaise: number;
  todaySpentPaise: number;
  safePerDayBeforePaise: number;
  safePerDayAfterPaise: number;
  reasons: string[];
  message: string;
}

export interface DecisionRequest {
  decision: CheckDecision;
  transactionId?: string;
}

export interface DecisionResponse {
  id: number;
  decision: CheckDecision;
}

// --- Text / voice input ------------------------------------------------------

export type InputIntent = 'LOG_EXPENSE' | 'PRE_CHECK' | 'QUERY' | 'UNDO' | 'OTHER';

export interface Draft {
  amountPaise: number;
  categoryId: Category['id'];
  categoryName: string;
  merchant: string | null;
  note: string | null;
  /** ISO-8601 instant. */
  spentAt: string;
  needOrWant: NeedOrWant | null;
  after: { categorySpentPaise: number; categoryLimitPaise: number };
}

export interface UndoCandidate {
  transactionId: string;
  amountPaise: number;
  categoryName: string;
  merchant: string | null;
  /** ISO-8601 instant. */
  spentAt: string;
}

export interface InputResult {
  rawInputId: number;
  intent: InputIntent;
  transcript: string | null;
  draft: Draft | null;
  verdict: Verdict | null;
  answer: { text: string } | null;
  /** UNDO: proposed only — the app confirms via DELETE /transactions/{id}. */
  undoCandidate: UndoCandidate | null;
  /**
   * @deprecated null on current servers; use `undoCandidate`. An older server deleted the spend
   * itself and returned its id here — the app treats that as already removed.
   */
  undoneTransactionId: string | null;
  followUp: { question: string; missing: string[] } | null;
}

// --- Reports -----------------------------------------------------------------

export interface MonthlyReport {
  plan: PlanSummary;
  totals: {
    incomePaise: number;
    fixedPaise: number;
    discretionaryPaise: number;
    spentPaise: number;
    savedPaise: number;
    savingsTargetPaise: number;
    underPlanPaise: number;
  };
  byCategory: { categoryId: Category['id']; name: string; kind: CategoryKind; limitPaise: number; spentPaise: number }[];
  needsPaise: number;
  wantsPaise: number;
  untaggedPaise: number;
  topWants: { label: string; count: number; totalPaise: number }[];
  pausesSavedPaise: number;
  skippedCount: number;
  /** Mon..Sun, paise. */
  wantsByWeekday: number[];
  trend: { planId: number; periodStart: string; spentPaise: number; discretionaryPaise: number }[];
}

// --- Daily summary -----------------------------------------------------------

export interface DailySummary {
  date: string;
  spentPaise: number;
  count: number;
  budgetPaise: number;
  overPaise: number;
  month: { spentPaise: number; discretionaryPaise: number; projectedPaise: number; savingsAtRiskPaise: number };
  pausesSavedPaise: number;
  text: string;
}
