import { describe, expect, it } from '@jest/globals';

import { formatINR, paiseToInput, parseRupees } from '../money';

describe('money', () => {
  it('formats with Indian grouping', () => {
    expect(formatINR(12000000)).toBe('₹1,20,000');
    expect(formatINR(19050)).toBe('₹190.50');
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
