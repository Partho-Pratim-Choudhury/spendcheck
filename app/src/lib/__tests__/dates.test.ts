import { describe, expect, it } from '@jest/globals';

import {
  addDays,
  dayHeaderLabel,
  daysBetween,
  formatTime,
  groupByDay,
  monthName,
  moveInstantToDay,
  periodLabel,
  toYmd,
} from '../dates';

const local = (y: number, m: number, d: number, h = 12, min = 0) => new Date(y, m - 1, d, h, min).toISOString();

describe('dates', () => {
  it('adds days across month ends', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(daysBetween('2026-10-07', '2026-11-06')).toBe(30);
  });

  it('labels day headers', () => {
    expect(dayHeaderLabel('2026-10-22', '2026-10-22')).toBe('TODAY · THU 22 OCT');
    expect(dayHeaderLabel('2026-10-21', '2026-10-22')).toBe('YESTERDAY · WED 21 OCT');
    expect(dayHeaderLabel('2026-10-19', '2026-10-22')).toBe('MON 19 OCT');
  });

  it('formats periods and months', () => {
    expect(periodLabel('2026-10-07', '2026-11-06')).toBe('Oct 7 – Nov 6');
    expect(monthName('2026-10-07')).toBe('October');
  });

  it('formats 12-hour times', () => {
    expect(formatTime(local(2026, 10, 22, 0, 5))).toBe('12:05 am');
    expect(formatTime(local(2026, 10, 22, 20, 42))).toBe('8:42 pm');
  });

  it('groups by local day, newest first, with totals', () => {
    const items = [
      { at: local(2026, 10, 21, 9), amt: 100 },
      { at: local(2026, 10, 22, 8), amt: 50 },
      { at: local(2026, 10, 22, 19), amt: 25 },
    ];
    const g = groupByDay(items, (i) => i.at, (i) => i.amt);
    expect(g.map((x) => x.day)).toEqual(['2026-10-22', '2026-10-21']);
    expect(g[0].totalPaise).toBe(75);
    expect(g[0].items[0].amt).toBe(25);
  });

  it('moves an instant to another day keeping the time', () => {
    const moved = new Date(moveInstantToDay(local(2026, 10, 22, 14, 30), '2026-10-20'));
    expect(toYmd(moved)).toBe('2026-10-20');
    expect(moved.getHours()).toBe(14);
    expect(moved.getMinutes()).toBe(30);
  });
});
