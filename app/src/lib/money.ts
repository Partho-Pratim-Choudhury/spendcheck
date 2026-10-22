/**
 * How a paise amount becomes whole rupees. Pick the direction that never flatters the user:
 * - `down`    — amounts left / safe to spend / budget remaining (₹909.86 left → ₹909)
 * - `nearest` — spent, projected, totals, limits; half-up (₹47,295.50 → ₹47,296)
 * - `up`      — overflow / over-by amounts (₹12.10 over → ₹13)
 * - `exact`   — no rounding; paise shown when non-zero (₹190.50)
 * `down`/`up` are floor/ceil of the signed value, so a negative "left" rounds further negative.
 */
export type Rounding = 'nearest' | 'down' | 'up' | 'exact';

/** Paise → whole rupees per `mode` ('exact' is not a rupee rounding; callers handle it). */
export function roundToRupees(paise: number, mode: Exclude<Rounding, 'exact'> = 'nearest'): number {
  const p = Math.round(paise); // guard against float paise from arithmetic
  if (mode === 'down') return Math.floor(p / 100);
  if (mode === 'up') return Math.ceil(p / 100);
  // half-up, symmetric: -₹0.50 → -₹1, like ₹0.50 → ₹1
  return Math.sign(p) * Math.floor((Math.abs(p) + 50) / 100);
}

function groupIndian(rupees: number): string {
  const digits = String(rupees);
  if (digits.length <= 3) return digits;
  const last3 = digits.slice(-3);
  const rest = digits.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ',');
  return `${rest},${last3}`;
}

/**
 * Format paise as whole Indian rupees with lakh/crore grouping, e.g. 12000000 -> "₹1,20,000".
 * `mode` picks the rounding (see {@link Rounding}); 'exact' keeps paise when non-zero.
 * Render the result with `typography.amount` (tabular-nums) so columns of amounts line up.
 */
export function formatINR(paise: number, mode: Rounding = 'nearest'): string {
  if (mode === 'exact') {
    const abs = Math.abs(Math.round(paise));
    const fraction = abs % 100;
    const tail = fraction !== 0 ? `.${String(fraction).padStart(2, '0')}` : '';
    return `${paise < 0 && abs !== 0 ? '-' : ''}₹${groupIndian(Math.floor(abs / 100))}${tail}`;
  }
  const rupees = roundToRupees(paise, mode);
  return `${rupees < 0 ? '-' : ''}₹${groupIndian(Math.abs(rupees))}`;
}

/**
 * Parse user-typed rupees ("1,250", "₹450.5", "450") into paise. Returns null when empty or invalid.
 */
export function parseRupees(input: string): number | null {
  const cleaned = input.replace(/[₹,\s]/g, '');
  if (cleaned === '') return null;
  if (!/^\d+(\.\d{0,2})?$/.test(cleaned)) return null;
  const [whole, frac = ''] = cleaned.split('.');
  return Number(whole) * 100 + Number(frac.padEnd(2, '0'));
}

/** Paise → editable rupee string ("1250", "450.50"); '' for 0 when `blankZero`. */
export function paiseToInput(paise: number, blankZero = false): string {
  if (blankZero && paise === 0) return '';
  const rupees = Math.floor(Math.abs(paise) / 100);
  const frac = Math.abs(paise) % 100;
  return `${paise < 0 ? '-' : ''}${rupees}${frac ? `.${String(frac).padStart(2, '0')}` : ''}`;
}
