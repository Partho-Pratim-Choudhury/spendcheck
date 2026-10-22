/** Pure helpers for moving scheduled reminders off a deleted Android channel. */

export interface ScheduledLike {
  identifier: string;
  trigger: unknown;
}

/**
 * One-off (DATE) reminders scheduled on one of `legacyChannelIds`, with their fire time.
 * Android serializes a DATE trigger as `{ type: 'date', value: <ms>, channelId }`; the input shape
 * (`date`) is accepted too. Anything whose time can't be recovered, or that is already due, is skipped.
 */
export function legacyDateReminders(
  scheduled: readonly ScheduledLike[],
  legacyChannelIds: readonly string[],
  now = Date.now(),
): { identifier: string; date: Date }[] {
  const out: { identifier: string; date: Date }[] = [];
  for (const req of scheduled) {
    const t = req.trigger;
    if (!t || typeof t !== 'object') continue;
    const { type, channelId, value, date } = t as Record<string, unknown>;
    if (type !== 'date' || typeof channelId !== 'string' || !legacyChannelIds.includes(channelId)) continue;
    const raw = value ?? date;
    const ms = typeof raw === 'number' ? raw : raw instanceof Date ? raw.getTime() : typeof raw === 'string' ? Date.parse(raw) : NaN;
    if (!Number.isFinite(ms) || ms <= now) continue;
    out.push({ identifier: req.identifier, date: new Date(ms) });
  }
  return out;
}
