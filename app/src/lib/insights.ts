import { WEEKDAY_NAMES_MON_FIRST } from './dates';
import { formatINR } from './money';
import type { Category, Dashboard, MonthlyReport, Transaction } from './types';

export interface WeekdayInsight {
  /** 0 = Monday. */
  index: number;
  weekday: string;
  paise: number;
  /** Share of all wants, 0–100 (rounded). */
  sharePct: number;
}

/** The weekday most wants land on, or null when there were no wants. Ties → earlier weekday. */
export function peakWantsWeekday(wantsByWeekday: number[]): WeekdayInsight | null {
  const values = wantsByWeekday.slice(0, 7);
  const total = values.reduce((s, v) => s + Math.max(0, v), 0);
  if (total <= 0) return null;
  let index = 0;
  values.forEach((v, i) => {
    if (v > values[index]) index = i;
  });
  return {
    index,
    weekday: WEEKDAY_NAMES_MON_FIRST[index],
    paise: values[index],
    sharePct: Math.round((values[index] * 100) / total),
  };
}

/** Percent split of two non-negative parts; returns integers summing to 100 (or 0/0 when empty). */
export function splitPct(a: number, b: number): [number, number] {
  const total = a + b;
  if (total <= 0) return [0, 0];
  const pa = Math.round((a * 100) / total);
  return [pa, 100 - pa];
}

/** Progress bar tone for a spent/limit pair: ≥100% red, ≥75% amber, else normal. */
export function usageTone(spent: number, limit: number): 'ok' | 'warn' | 'over' {
  const pct = limit > 0 ? (spent * 100) / limit : spent > 0 ? 100 : 0;
  if (pct >= 100) return 'over';
  if (pct >= 75) return 'warn';
  return 'ok';
}

/**
 * Split a spend list into variable (NEED/WANT categories) and FIXED totals by category kind, so the
 * headline matches Home's "spent". Spends whose category isn't in `categories` count as variable.
 */
export function splitFixedSpends(
  txs: Pick<Transaction, 'amountPaise' | 'categoryId'>[],
  categories: Pick<Category, 'id' | 'kind'>[],
): { variablePaise: number; fixedPaise: number } {
  const fixedIds = new Set(categories.filter((c) => c.kind === 'FIXED').map((c) => String(c.id)));
  let variablePaise = 0;
  let fixedPaise = 0;
  for (const t of txs) {
    if (fixedIds.has(String(t.categoryId))) fixedPaise += t.amountPaise;
    else variablePaise += t.amountPaise;
  }
  return { variablePaise, fixedPaise };
}

/**
 * What an ACTIVE plan is on track to save: the savings target plus whatever of the discretionary
 * budget the current pace leaves unspent (negative when projected overspend eats into it), floored at 0.
 */
export function onTrackToSavePaise(savingsTargetPaise: number, discretionaryPaise: number, projectedPaise: number): number {
  return Math.max(0, savingsTargetPaise + discretionaryPaise - projectedPaise);
}

export interface SavingsTile {
  label: string;
  /** null = not known yet (render a placeholder). */
  paise: number | null;
  sub: string;
  tone?: 'good' | 'warn';
}

/**
 * The report's savings tile. A closed plan shows what was saved. Mid-period, totals.savedPaise counts
 * everything not yet spent as saved, so an ACTIVE plan shows the dashboard's projection instead: a
 * placeholder while the dashboard loads, and plain "unspent so far" if it can't be had.
 */
export function savingsTile(r: MonthlyReport, dashboard: Dashboard | null | undefined, dashboardLoading: boolean): SavingsTile {
  const t = r.totals;
  if (r.plan.status !== 'ACTIVE') {
    return {
      label: 'Saved',
      paise: t.savedPaise,
      sub: `target ${formatINR(t.savingsTargetPaise)}`,
      tone: t.savedPaise >= t.savingsTargetPaise ? 'good' : 'warn',
    };
  }
  if (dashboard && dashboard.plan.id === r.plan.id) {
    const m = dashboard.month;
    const paise = onTrackToSavePaise(t.savingsTargetPaise, m.discretionaryPaise, m.projectedPaise);
    return { label: 'On track to save', paise, sub: 'projected', tone: paise >= t.savingsTargetPaise ? 'good' : 'warn' };
  }
  if (dashboardLoading) return { label: 'On track to save', paise: null, sub: 'projected' };
  return { label: 'Unspent so far', paise: Math.max(0, t.discretionaryPaise - t.spentPaise), sub: 'of the spending budget' };
}
