/**
 * Pure date helpers. Calendar days are `YYYY-MM-DD` strings in the device's local time zone
 * (the backend uses the user's tz, Asia/Kolkata — same as the phone in practice).
 */

const WEEKDAYS_SHORT = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONTHS_LONG = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];
/** Mon-first, matching the reports API `wantsByWeekday`. */
export const WEEKDAY_NAMES_MON_FIRST = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

const pad = (n: number) => String(n).padStart(2, '0');

/** Local calendar day of a Date. */
export function toYmd(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Local midnight of a `YYYY-MM-DD` string. */
export function parseYmd(ymd: string): Date {
  const [y, m, d] = ymd.split('-').map(Number);
  return new Date(y, (m ?? 1) - 1, d ?? 1);
}

/** Local calendar day an ISO instant falls on. */
export function instantToYmd(iso: string): string {
  return toYmd(new Date(iso));
}

export function addDays(ymd: string, n: number): string {
  const d = parseYmd(ymd);
  d.setDate(d.getDate() + n);
  return toYmd(d);
}

/** Local hour the "ask in 3 days" reminder fires at. */
export const DEFER_REMINDER_HOUR = 10;

/**
 * When an "ask me later" reminder fires: `days` calendar days after `now`, at 10:00 device time
 * (never at whatever odd hour the user deferred). DST-safe: built from local date parts.
 */
export function deferReminderDate(now: Date, days = 3): Date {
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + days, DEFER_REMINDER_HOUR, 0, 0, 0);
}

/** Whole days from a to b (b − a). */
export function daysBetween(a: string, b: string): number {
  return Math.round((parseYmd(b).getTime() - parseYmd(a).getTime()) / 86_400_000);
}

/** "Oct 7" */
export function shortDate(ymd: string): string {
  const d = parseYmd(ymd);
  return `${MONTHS_SHORT[d.getMonth()]} ${d.getDate()}`;
}

/** "23 Oct" */
export function dayMonth(ymd: string): string {
  const d = parseYmd(ymd);
  return `${d.getDate()} ${MONTHS_SHORT[d.getMonth()]}`;
}

/** 1 → "1st", 12 → "12th", 23 → "23rd". */
export function ordinal(n: number): string {
  const s = n % 100 >= 11 && n % 100 <= 13 ? 'th' : (['th', 'st', 'nd', 'rd'][n % 10] ?? 'th');
  return `${n}${s}`;
}

/** "Oct 7 – Nov 6" */
export function periodLabel(start: string, end: string): string {
  return `${shortDate(start)} – ${shortDate(end)}`;
}

/** "October" — the month a period is named after (the month it starts in). */
export function monthName(ymd: string): string {
  return MONTHS_LONG[parseYmd(ymd).getMonth()];
}

/** "WED 22 OCT" */
export function weekdayDayMonth(ymd: string): string {
  const d = parseYmd(ymd);
  return `${WEEKDAYS_SHORT[d.getDay()]} ${d.getDate()} ${MONTHS_SHORT[d.getMonth()].toUpperCase()}`;
}

/** "TODAY · WED 22 OCT", "YESTERDAY · TUE 21 OCT", else "MON 20 OCT". */
export function dayHeaderLabel(ymd: string, today: string): string {
  const base = weekdayDayMonth(ymd);
  if (ymd === today) return `TODAY · ${base}`;
  if (ymd === addDays(today, -1)) return `YESTERDAY · ${base}`;
  return base;
}

/** "Wed 22 Oct" */
export function friendlyDate(ymd: string): string {
  const d = parseYmd(ymd);
  const wd = WEEKDAYS_SHORT[d.getDay()];
  return `${wd[0]}${wd.slice(1).toLowerCase()} ${d.getDate()} ${MONTHS_SHORT[d.getMonth()]}`;
}

/** "Today", "Yesterday" or "Wed 22 Oct" — for an instant. */
export function relativeDay(iso: string, today: string): string {
  const day = instantToYmd(iso);
  if (day === today) return 'Today';
  if (day === addDays(today, -1)) return 'Yesterday';
  return friendlyDate(day);
}

/** "8:42 pm" (local time of an ISO instant). */
export function formatTime(iso: string): string {
  const d = new Date(iso);
  const h = d.getHours();
  return `${h % 12 === 0 ? 12 : h % 12}:${pad(d.getMinutes())} ${h < 12 ? 'am' : 'pm'}`;
}

export interface DayGroup<T> {
  day: string;
  items: T[];
  totalPaise: number;
}

/** Group items by the local day of their instant, newest day first; items keep newest-first order. */
export function groupByDay<T>(items: T[], instantOf: (t: T) => string, amountOf: (t: T) => number): DayGroup<T>[] {
  const sorted = [...items].sort((a, b) => Date.parse(instantOf(b)) - Date.parse(instantOf(a)));
  const groups: DayGroup<T>[] = [];
  for (const item of sorted) {
    const day = instantToYmd(instantOf(item));
    let g = groups[groups.length - 1];
    if (!g || g.day !== day) {
      g = { day, items: [], totalPaise: 0 };
      groups.push(g);
    }
    g.items.push(item);
    g.totalPaise += amountOf(item);
  }
  return groups;
}

/** Keep the time-of-day of `iso` but move it to calendar day `ymd` (local). */
export function moveInstantToDay(iso: string, ymd: string): string {
  const src = new Date(iso);
  const day = parseYmd(ymd);
  day.setHours(src.getHours(), src.getMinutes(), src.getSeconds(), src.getMilliseconds());
  return day.toISOString();
}
