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
