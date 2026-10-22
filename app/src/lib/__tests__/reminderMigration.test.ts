import { describe, expect, it } from '@jest/globals';

import { legacyDateReminders } from '../reminderMigration';

const NOW = 1_000_000;
const LEGACY = ['reminders', 'reminders-v2'];

describe('legacyDateReminders', () => {
  it('picks future DATE triggers on legacy channels (Android `value` or input `date`)', () => {
    const out = legacyDateReminders(
      [
        { identifier: 'a', trigger: { type: 'date', channelId: 'reminders-v2', value: NOW + 5 } },
        { identifier: 'b', trigger: { type: 'date', channelId: 'reminders', date: new Date(NOW + 9) } },
      ],
      LEGACY,
      NOW,
    );
    expect(out).toEqual([
      { identifier: 'a', date: new Date(NOW + 5) },
      { identifier: 'b', date: new Date(NOW + 9) },
    ]);
  });

  it('skips current-channel, non-date, past and unrecoverable triggers', () => {
    const out = legacyDateReminders(
      [
        { identifier: 'cur', trigger: { type: 'date', channelId: 'reminders-v3', value: NOW + 5 } },
        { identifier: 'daily', trigger: { type: 'daily', channelId: 'reminders-v2', hour: 21, minute: 30 } },
        { identifier: 'past', trigger: { type: 'date', channelId: 'reminders-v2', value: NOW - 1 } },
        { identifier: 'nan', trigger: { type: 'date', channelId: 'reminders-v2' } },
        { identifier: 'nochan', trigger: { type: 'date', value: NOW + 5 } },
        { identifier: 'null', trigger: null },
      ],
      LEGACY,
      NOW,
    );
    expect(out).toEqual([]);
  });
});
