import { describe, expect, it } from '@jest/globals';

import { peakWantsWeekday, splitPct, usageTone } from '../insights';

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
});
