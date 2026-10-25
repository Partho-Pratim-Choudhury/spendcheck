import { describe, expect, it } from '@jest/globals';

import { evaluate } from '../AmountInput';

/**
 * Covers the component-specific glue around `evalAmount`: paise conversion, the live "= ₹…"
 * preview (only when an operator is present), the inline hint, char filtering and `allowZero`.
 * The arithmetic itself is covered by calc.test.ts.
 */
describe('AmountInput evaluate', () => {
  it('yields paise and a live result for a valid expression', () => {
    const r = evaluate('5000+2000', false);
    expect(r.paise).toBe(700000);
    expect(r.live).toBe('= ₹7,000');
    expect(r.hint).toBeNull();
    expect(r.invalid).toBe(false);
  });

  it('keeps paise precision for decimals', () => {
    expect(evaluate('5000.50', false).paise).toBe(500050);
    expect(evaluate('1200*3', false).paise).toBe(360000);
  });

  it('shows no live line for a plain number (no operator)', () => {
    const r = evaluate('5000', false);
    expect(r.paise).toBe(500000);
    expect(r.live).toBeNull();
  });

  it('rounds the live preview to whole rupees (half-up)', () => {
    // 10/3 = 3.3333 -> 3.33 stored, preview rounds to ₹3
    expect(evaluate('10/3', false).live).toBe('= ₹3');
    // 5000.50 + 0.50 rounds the preview to ₹5,001
    expect(evaluate('5000.50+0.50', false).live).toBe('= ₹5,001');
  });

  it('treats empty / whitespace as null with no error', () => {
    for (const t of ['', '   ']) {
      const r = evaluate(t, false);
      expect(r.paise).toBeNull();
      expect(r.hint).toBeNull();
      expect(r.invalid).toBe(false);
    }
  });

  it('reports invalid (null + hint) for a non-empty bad expression', () => {
    // All made of allowed chars but malformed (trailing/double op, div-by-zero, two numbers).
    for (const t of ['5000+', '5+*2', '5/0', '1 2']) {
      const r = evaluate(t, false);
      expect(r.paise).toBeNull();
      expect(r.invalid).toBe(true);
      expect(r.hint).toBe('Enter a valid amount');
    }
  });

  it('strips characters outside 0-9 + - * / . and spaces', () => {
    // '₹' and ',' are dropped, leaving '5000+2000'
    expect(evaluate('₹5000+2,000', false).paise).toBe(700000);
    // Text that is only disallowed chars filters down to empty — not an error.
    const letters = evaluate('abc', false);
    expect(letters.text).toBe('');
    expect(letters.paise).toBeNull();
    expect(letters.invalid).toBe(false);
  });

  it('honours allowZero', () => {
    // 0 is invalid when zero is not allowed...
    expect(evaluate('0', false).invalid).toBe(true);
    expect(evaluate('0', false).paise).toBeNull();
    // ...and valid when it is.
    expect(evaluate('0', true).paise).toBe(0);
    expect(evaluate('0', true).invalid).toBe(false);
  });

  it('never accepts a negative result, even with allowZero', () => {
    expect(evaluate('5-10', true).invalid).toBe(true);
    expect(evaluate('5-10', true).paise).toBeNull();
    expect(evaluate('5-10', false).invalid).toBe(true);
  });
});
