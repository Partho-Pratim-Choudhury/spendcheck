import { describe, expect, it } from '@jest/globals';

import { buildBurnChart, donutSegments, layoutBars, linePath, linearScale, niceCeil } from '../chart';

describe('chart helpers', () => {
  it('scales linearly', () => {
    const s = linearScale([0, 10], [100, 0]);
    expect(s(0)).toBe(100);
    expect(s(5)).toBe(50);
    expect(linearScale([3, 3], [7, 9])(3)).toBe(7);
  });

  it('builds paths', () => {
    expect(linePath([{ x: 0, y: 1 }, { x: 2.345, y: 3 }])).toBe('M0 1 L2.3 3');
  });

  it('rounds up nicely', () => {
    expect(niceCeil(0)).toBe(1);
    expect(niceCeil(7)).toBe(10);
    expect(niceCeil(1800)).toBe(2000);
    expect(niceCeil(2100)).toBe(2500);
  });

  it('builds a burn chart with projection', () => {
    const m = buildBurnChart({
      cumulative: [100, 200],
      daysTotal: 4,
      discretionary: 400,
      projected: 400,
      width: 100,
      height: 50,
      pad: { top: 0, right: 0, bottom: 0, left: 0 },
    });
    expect(m.plan.startsWith('M0 50')).toBe(true);
    expect(m.last?.x).toBe(50);
    expect(m.projection).not.toBeNull();
    expect(m.planEnd.x).toBe(100);
  });

  it('omits projection when the period is complete', () => {
    const m = buildBurnChart({ cumulative: [1, 2], daysTotal: 2, discretionary: 2, projected: 2, width: 10, height: 10 });
    expect(m.projection).toBeNull();
  });

  it('builds donut segments', () => {
    const r = 10;
    const segs = donutSegments([1, 0, 3], r);
    const c = 2 * Math.PI * r;
    expect(segs).toHaveLength(2);
    expect(segs[0].index).toBe(0);
    expect(segs[1].index).toBe(2);
    expect(segs[1].offset).toBeCloseTo(c / 4);
    expect(segs[0].length + segs[1].length).toBeCloseTo(c);
    expect(donutSegments([0, 0], r)).toEqual([]);
  });

  it('lays out bars', () => {
    const bars = layoutBars([50, 100], 100, 200, 100, 0.5);
    expect(bars[0]).toEqual({ x: 25, y: 50, width: 50, height: 50 });
    expect(bars[1].height).toBe(100);
  });
});
