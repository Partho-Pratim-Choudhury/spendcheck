import { describe, expect, it } from '@jest/globals';

import { evalAmount } from '../calc';

/** Helper: assert ok and return the value, failing loudly otherwise. */
function value(expr: string): number {
  const r = evalAmount(expr);
  if (!r.ok) throw new Error(`expected "${expr}" to evaluate, got ${r.reason}`);
  return r.value;
}

describe('evalAmount', () => {
  it('evaluates a plain number', () => {
    expect(value('5000')).toBe(5000);
    expect(value('5000.50')).toBe(5000.5);
    expect(value('0')).toBe(0);
  });

  it('adds, subtracts, multiplies and divides', () => {
    expect(value('5000+2000')).toBe(7000);
    expect(value('5000-500')).toBe(4500);
    expect(value('1200*3')).toBe(3600);
    expect(value('1000/4')).toBe(250);
  });

  it('respects operator precedence', () => {
    expect(value('2+3*4')).toBe(14);
    expect(value('2*3+4')).toBe(10);
    expect(value('100-10/2')).toBe(95);
  });

  it('is left-associative for same-precedence operators', () => {
    expect(value('100-50-20')).toBe(30);
    expect(value('100/5/2')).toBe(10);
  });

  it('handles chained expressions', () => {
    expect(value('5000+2000-500')).toBe(6500);
    expect(value('1000+2000+3000-500')).toBe(5500);
  });

  it('tolerates spaces anywhere', () => {
    expect(value('5000 + 2000')).toBe(7000);
    expect(value('  1200 * 3  ')).toBe(3600);
    expect(value('5000')).toBe(5000);
  });

  it('supports decimals and rounds the result to two places (paise)', () => {
    expect(value('5000.25+0.25')).toBe(5000.5);
    expect(value('10/3')).toBe(3.33);
    expect(value('100/8')).toBe(12.5);
    expect(value('0.1+0.2')).toBe(0.3); // float noise cleaned up
  });

  it('supports parentheses', () => {
    expect(value('(2+3)*4')).toBe(20);
    expect(value('100-(20+5)')).toBe(75);
    expect(value('(1000+500)/3')).toBe(500);
  });

  it('can produce a negative via subtraction (caller decides if allowed)', () => {
    expect(value('5-10')).toBe(-5);
  });

  it('rejects empty / whitespace', () => {
    expect(evalAmount('').ok).toBe(false);
    expect(evalAmount('   ').ok).toBe(false);
  });

  it('rejects a trailing operator', () => {
    expect(evalAmount('5000+').ok).toBe(false);
    expect(evalAmount('5000-').ok).toBe(false);
    expect(evalAmount('12*').ok).toBe(false);
  });

  it('rejects a leading operator (no unary)', () => {
    expect(evalAmount('+5000').ok).toBe(false);
    expect(evalAmount('-5000').ok).toBe(false);
    expect(evalAmount('*5').ok).toBe(false);
  });

  it('rejects consecutive operators', () => {
    expect(evalAmount('5+*2').ok).toBe(false);
    expect(evalAmount('5++2').ok).toBe(false);
    expect(evalAmount('5 * / 2').ok).toBe(false);
  });

  it('rejects non-numeric characters', () => {
    expect(evalAmount('abc').ok).toBe(false);
    expect(evalAmount('5000a').ok).toBe(false);
    expect(evalAmount('1,000').ok).toBe(false);
    expect(evalAmount('₹500').ok).toBe(false);
  });

  it('rejects malformed numbers', () => {
    expect(evalAmount('5.0.0').ok).toBe(false);
    expect(evalAmount('1 2').ok).toBe(false);
  });

  it('rejects division by zero', () => {
    expect(evalAmount('5/0').ok).toBe(false);
    expect(evalAmount('100/(5-5)').ok).toBe(false);
  });

  it('rejects unbalanced parentheses', () => {
    expect(evalAmount('(5+2').ok).toBe(false);
    expect(evalAmount('5+2)').ok).toBe(false);
  });
});
