/** Pure chart geometry helpers (no React). */

export interface Point {
  x: number;
  y: number;
}

export type Scale = (v: number) => number;

export function linearScale([d0, d1]: [number, number], [r0, r1]: [number, number]): Scale {
  const span = d1 - d0;
  return (v) => (span === 0 ? r0 : r0 + ((v - d0) / span) * (r1 - r0));
}

/** "M x y L x y …" with coordinates rounded to 0.1. */
export function linePath(points: Point[]): string {
  return points
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${Math.round(p.x * 10) / 10} ${Math.round(p.y * 10) / 10}`)
    .join(' ');
}

/** Round up to a "nice" axis maximum (1, 2, 2.5, 5 × 10^n). */
export function niceCeil(v: number): number {
  if (v <= 0) return 1;
  const exp = Math.floor(Math.log10(v));
  const base = 10 ** exp;
  for (const m of [1, 2, 2.5, 5, 10]) {
    if (v <= m * base) return m * base;
  }
  return 10 * base;
}

export interface BurnChartInput {
  /** Cumulative variable spend per day from periodStart..today. */
  cumulative: number[];
  daysTotal: number;
  discretionary: number;
  projected: number;
  width: number;
  height: number;
  pad?: { top: number; right: number; bottom: number; left: number };
}

export interface BurnChartModel {
  actual: string;
  plan: string;
  projection: string | null;
  last: Point | null;
  planEnd: Point;
  maxY: number;
}

/**
 * Burn-down chart: actual cumulative spend (day 1..n), straight plan line 0 → discretionary
 * over daysTotal, and a projected segment from today's point to `projected` at period end.
 * Day i (1-based) is plotted at x(i); the plan line starts at x(0) = 0.
 */
export function buildBurnChart(input: BurnChartInput): BurnChartModel {
  const pad = input.pad ?? { top: 8, right: 8, bottom: 8, left: 8 };
  const days = Math.max(1, input.daysTotal);
  const maxY = Math.max(1, input.discretionary, input.projected, ...input.cumulative) * 1.05;
  const x = linearScale([0, days], [pad.left, input.width - pad.right]);
  const y = linearScale([0, maxY], [input.height - pad.bottom, pad.top]);

  const pts: Point[] = [{ x: x(0), y: y(0) }, ...input.cumulative.map((v, i) => ({ x: x(i + 1), y: y(v) }))];
  const last = input.cumulative.length > 0 ? pts[pts.length - 1] : null;
  const planEnd = { x: x(days), y: y(input.discretionary) };
  const projection =
    last && input.cumulative.length < days ? linePath([last, { x: x(days), y: y(input.projected) }]) : null;

  return {
    actual: linePath(pts),
    plan: linePath([{ x: x(0), y: y(0) }, planEnd]),
    projection,
    last,
    planEnd,
    maxY,
  };
}

export interface DonutSegment {
  index: number;
  /** Fraction of total, 0–1. */
  fraction: number;
  /** Visible arc length along the circle. */
  length: number;
  /** Arc length already used by preceding segments. */
  offset: number;
}

/**
 * Segments for a stroke-dasharray donut. Zero/negative values are dropped. `gap` is subtracted
 * from each visible arc (when there is more than one segment) to separate slices.
 */
export function donutSegments(values: number[], radius: number, gap = 0): DonutSegment[] {
  const circumference = 2 * Math.PI * radius;
  const total = values.reduce((s, v) => s + Math.max(0, v), 0);
  if (total <= 0) return [];
  const positive = values.filter((v) => v > 0).length;
  const segs: DonutSegment[] = [];
  let offset = 0;
  values.forEach((v, index) => {
    if (v <= 0) return;
    const fraction = v / total;
    const full = fraction * circumference;
    const length = positive > 1 ? Math.max(0.5, full - gap) : full;
    segs.push({ index, fraction, length, offset });
    offset += full;
  });
  return segs;
}

export interface Bar {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Evenly spaced vertical bars for `values` scaled against `max`, inside width × height. */
export function layoutBars(values: number[], max: number, width: number, height: number, gapRatio = 0.4): Bar[] {
  const n = values.length;
  if (n === 0) return [];
  const slot = width / n;
  const barW = slot * (1 - gapRatio);
  const y = linearScale([0, Math.max(1, max)], [height, 0]);
  return values.map((v, i) => {
    const top = y(Math.max(0, v));
    return { x: i * slot + (slot - barW) / 2, y: top, width: barW, height: height - top };
  });
}
