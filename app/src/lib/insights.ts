import { WEEKDAY_NAMES_MON_FIRST } from './dates';

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
