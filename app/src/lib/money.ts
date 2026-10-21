/**
 * Format paise as Indian rupees with lakh/crore grouping, e.g. 12000000 -> "₹1,20,000".
 * Paise are shown only when non-zero. Render the result with `typography.amount`
 * (tabular-nums) so columns of amounts line up.
 */
export function formatINR(paise: number, opts: { showPaise?: boolean } = {}): string {
  const negative = paise < 0;
  const abs = Math.abs(Math.round(paise));
  const rupees = Math.floor(abs / 100);
  const fraction = abs % 100;

  const digits = String(rupees);
  let grouped: string;
  if (digits.length <= 3) {
    grouped = digits;
  } else {
    const last3 = digits.slice(-3);
    const rest = digits.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ',');
    grouped = `${rest},${last3}`;
  }

  const showPaise = opts.showPaise ?? fraction !== 0;
  const tail = showPaise ? `.${String(fraction).padStart(2, '0')}` : '';
  return `${negative ? '-' : ''}₹${grouped}${tail}`;
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
