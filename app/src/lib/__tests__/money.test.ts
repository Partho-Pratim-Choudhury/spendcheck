import { describe, expect, it } from '@jest/globals';

import { formatINR, paiseToInput, parseRupees, roundToRupees } from '../money';

describe('money', () => {
  it('formats whole rupees with Indian grouping', () => {
    expect(formatINR(12000000)).toBe('₹1,20,000');
    expect(formatINR(1234567890)).toBe('₹1,23,45,679');
    expect(formatINR(0)).toBe('₹0');
  });

  it('rounds half-up by default (spent, projected, totals, limits)', () => {
    expect(formatINR(19050)).toBe('₹191');
    expect(formatINR(19049)).toBe('₹190');
    expect(formatINR(4729505)).toBe('₹47,295');
    expect(formatINR(165686)).toBe('₹1,657');
    expect(formatINR(-19050)).toBe('-₹191');
  });

  it("rounds down for amounts left so they never flatter", () => {
    expect(formatINR(90986, 'down')).toBe('₹909');
    expect(formatINR(161290, 'down')).toBe('₹1,612');
    expect(formatINR(90900, 'down')).toBe('₹909');
    // a negative "left" rounds further negative, never toward zero
    expect(formatINR(-1010, 'down')).toBe('-₹11');
    expect(formatINR(-0, 'down')).toBe('₹0');
  });

  it('rounds up for over amounts', () => {
    expect(formatINR(1210, 'up')).toBe('₹13');
    expect(formatINR(1200, 'up')).toBe('₹12');
    expect(formatINR(1, 'up')).toBe('₹1');
    expect(formatINR(-50, 'up')).toBe('₹0');
  });

  it('keeps paise in exact mode only when non-zero', () => {
    expect(formatINR(19050, 'exact')).toBe('₹190.50');
    expect(formatINR(19000, 'exact')).toBe('₹190');
    expect(formatINR(-5, 'exact')).toBe('-₹0.05');
    expect(formatINR(12345678, 'exact')).toBe('₹1,23,456.78');
  });

  it('rounds paise to rupees per mode, tolerating float paise', () => {
    expect(roundToRupees(15050)).toBe(151);
    expect(roundToRupees(15049.6)).toBe(151);
    expect(roundToRupees(15099, 'down')).toBe(150);
    expect(roundToRupees(15001, 'up')).toBe(151);
    expect(roundToRupees(-15050)).toBe(-151);
  });

  it('parses rupee input', () => {
    expect(parseRupees('1,250')).toBe(125000);
    expect(parseRupees('₹450.5')).toBe(45050);
    expect(parseRupees('')).toBeNull();
    expect(parseRupees('abc')).toBeNull();
    expect(parseRupees('1.234')).toBeNull();
  });

  it('round-trips to input strings', () => {
    expect(paiseToInput(125000)).toBe('1250');
    expect(paiseToInput(45050)).toBe('450.50');
    expect(paiseToInput(0, true)).toBe('');
  });
});
