import { dayMonth, ordinal } from './dates';
import type { RolloverPreview } from './types';

/** Longer than any calendar month, so the period must have been stretched to reach the next salary day. */
const USUAL_MAX_DAYS = 35;

const CLOSES = 'This closes the current period and starts a new one today, copying your income, savings and limits.';

/**
 * Body of the "Salary came today" confirm dialog. Without a preview (it failed to load) only the
 * generic explanation is shown.
 */
export function rolloverConfirmText(preview: RolloverPreview | null, salaryDay: number | null | undefined): string {
  if (!preview) return CLOSES;
  // A plan that started today or yesterday is kept and only extended, so nothing is closed.
  const lines = [
    preview.extendsCurrent
      ? `Your current period will run until ${dayMonth(preview.periodEnd)} (${preview.daysTotal} days).`
      : `New month: ${dayMonth(preview.periodStart)} – ${dayMonth(preview.periodEnd)} (${preview.daysTotal} days). ${CLOSES}`,
  ];
  if (preview.daysTotal > USUAL_MAX_DAYS) {
    const day = salaryDay ? ` (${ordinal(salaryDay)})` : '';
    lines.push(`That’s longer than a usual month because today is far from your salary day${day}.`);
  }
  return lines.join('\n\n');
}
