import { describe, expect, it } from '@jest/globals';

import { onTrackToSavePaise, peakWantsWeekday, savingsTile, splitFixedSpends, splitPct, usageTone } from '../insights';
import type { Dashboard, MonthlyReport } from '../types';

describe('insights', () => {
  it('finds the peak wants weekday', () => {
    expect(peakWantsWeekday([0, 0, 0, 0, 0, 0, 0])).toBeNull();
    const r = peakWantsWeekday([100, 0, 0, 0, 300, 100, 0]);
    expect(r).toEqual({ index: 4, weekday: 'Friday', paise: 300, sharePct: 60 });
  });

  it('breaks ties toward the earlier weekday', () => {
    expect(peakWantsWeekday([0, 50, 50, 0, 0, 0, 0])?.weekday).toBe('Tuesday');
  });

  it('splits percentages to 100', () => {
    expect(splitPct(1, 2)).toEqual([33, 67]);
    expect(splitPct(0, 0)).toEqual([0, 0]);
  });

  it('classifies usage', () => {
    expect(usageTone(50, 100)).toBe('ok');
    expect(usageTone(75, 100)).toBe('warn');
    expect(usageTone(100, 100)).toBe('over');
    expect(usageTone(1, 0)).toBe('over');
    expect(usageTone(0, 0)).toBe('ok');
  });

  it('splits spends into variable and FIXED by category kind', () => {
    const cats = [
      { id: 1, kind: 'FIXED' as const },
      { id: 2, kind: 'WANT' as const },
      { id: '3', kind: 'NEED' as const },
    ];
    const txs = [
      { amountPaise: 2500000, categoryId: 1 },
      { amountPaise: 45000, categoryId: 2 },
      { amountPaise: 10000, categoryId: 3 }, // id type differs from the category's — still matched
      { amountPaise: 500, categoryId: 99 }, // unknown category counts as variable
      { amountPaise: 100, categoryId: '1' },
    ];
    expect(splitFixedSpends(txs, cats)).toEqual({ variablePaise: 55500, fixedPaise: 2500100 });
    expect(splitFixedSpends([], cats)).toEqual({ variablePaise: 0, fixedPaise: 0 });
  });

  it('projects savings for an active plan, floored at 0', () => {
    // target 10k, discretionary 50k, projected 47,295 → 12,705 on track
    expect(onTrackToSavePaise(1000000, 5000000, 4729505)).toBe(1270495);
    // overspend eats into the target
    expect(onTrackToSavePaise(1000000, 5000000, 5400000)).toBe(600000);
    expect(onTrackToSavePaise(1000000, 5000000, 7000000)).toBe(0);
  });
});

describe('savingsTile', () => {
  const report = (status: 'ACTIVE' | 'CLOSED'): MonthlyReport =>
    ({
      plan: { id: 7, periodStart: '2026-10-07', periodEnd: '2026-11-06', status },
      // mid-period savedPaise counts the unspent budget as saved: 10k target + 30k unspent
      totals: { discretionaryPaise: 5000000, spentPaise: 2000000, savedPaise: 4000000, savingsTargetPaise: 1000000 },
    }) as MonthlyReport;
  const dashboard = (planId: number): Dashboard =>
    ({ plan: { id: planId }, month: { discretionaryPaise: 5000000, projectedPaise: 4729505 } }) as Dashboard;

  it('shows what was saved for a closed plan', () => {
    expect(savingsTile(report('CLOSED'), undefined, false)).toEqual({
      label: 'Saved',
      paise: 4000000,
      sub: 'target ₹10,000',
      tone: 'good',
    });
  });

  it('shows the projection for an active plan once the dashboard is in', () => {
    expect(savingsTile(report('ACTIVE'), dashboard(7), false)).toEqual({
      label: 'On track to save',
      paise: 1270495,
      sub: 'projected',
      tone: 'good',
    });
  });

  it('shows a placeholder, never "Saved", while the dashboard loads', () => {
    expect(savingsTile(report('ACTIVE'), undefined, true)).toEqual({ label: 'On track to save', paise: null, sub: 'projected' });
    expect(savingsTile(report('ACTIVE'), dashboard(6), true).paise).toBeNull();
  });

  it('falls back to unspent so far when the dashboard is unavailable', () => {
    for (const d of [undefined, dashboard(6)]) {
      const tile = savingsTile(report('ACTIVE'), d, false);
      expect(tile.label).toBe('Unspent so far');
      expect(tile.paise).toBe(3000000);
    }
  });
});
