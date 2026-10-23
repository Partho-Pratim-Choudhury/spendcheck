import { describe, expect, it } from '@jest/globals';

import { rolloverConfirmText } from '../rollover';

const CLOSES = 'This closes the current period and starts a new one today, copying your income, savings and limits.';

describe('rolloverConfirmText', () => {
  it('falls back to the generic text without a preview', () => {
    expect(rolloverConfirmText(null, 7)).toBe(CLOSES);
  });

  it('shows the new period', () => {
    expect(rolloverConfirmText({ periodStart: '2026-11-05', periodEnd: '2026-12-06', daysTotal: 32, extendsCurrent: false }, 7)).toBe(
      `New month: 5 Nov – 6 Dec (32 days). ${CLOSES}`,
    );
  });

  it('says the current period is extended, not closed, when the backend keeps it', () => {
    const text = rolloverConfirmText(
      { periodStart: '2026-11-07', periodEnd: '2026-12-06', daysTotal: 30, extendsCurrent: true },
      7,
    );
    expect(text).toBe('Your current period will run until 6 Dec (30 days).');
    expect(text).not.toMatch(/closes|from today|new one/);
  });

  it('still warns about a long extended period', () => {
    expect(
      rolloverConfirmText({ periodStart: '2026-10-23', periodEnd: '2026-12-06', daysTotal: 45, extendsCurrent: true }, 7),
    ).toBe(
      'Your current period will run until 6 Dec (45 days).\n\n' +
        'That’s longer than a usual month because today is far from your salary day (7th).',
    );
  });

  it('warns when the period is longer than a usual month', () => {
    expect(rolloverConfirmText({ periodStart: '2026-10-23', periodEnd: '2026-12-06', daysTotal: 45, extendsCurrent: false }, 7)).toBe(
      `New month: 23 Oct – 6 Dec (45 days). ${CLOSES}\n\n` +
        'That’s longer than a usual month because today is far from your salary day (7th).',
    );
  });

  it('does not warn at 35 days and copes with an unknown salary day', () => {
    expect(rolloverConfirmText({ periodStart: '2026-10-01', periodEnd: '2026-11-04', daysTotal: 35, extendsCurrent: false }, 1)).not.toContain(
      'longer',
    );
    expect(rolloverConfirmText({ periodStart: '2026-10-23', periodEnd: '2026-12-06', daysTotal: 45, extendsCurrent: false }, null)).toMatch(
      /from your salary day\.$/,
    );
  });
});
