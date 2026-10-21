import * as Notifications from 'expo-notifications';
import * as SecureStore from 'expo-secure-store';
import { Alert, Platform } from 'react-native';

/**
 * Local notifications only (no push tokens) — supported in Expo Go SDK 57 on Android.
 */

// Channel importance is fixed once created, so raising it needs a new id ('reminders' was DEFAULT).
export const REMINDER_CHANNEL_ID = 'reminders-v2';
const LEGACY_CHANNEL_IDS = ['reminders'];
const DAILY_ID = 'daily-summary';
const ASKED_KEY = 'spendcheck.notifAsked';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    // On Android, no sound also suppresses the heads-up alert.
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

async function ensureChannel(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(REMINDER_CHANNEL_ID, {
    name: 'Reminders',
    description: 'Daily summary and "ask me later" reminders',
    // HIGH = heads-up banner; DEFAULT only lands silently in the shade.
    importance: Notifications.AndroidImportance.HIGH,
  });
  await Promise.all(LEGACY_CHANNEL_IDS.map((id) => Notifications.deleteNotificationChannelAsync(id).catch(() => undefined)));
}

function explain(): Promise<boolean> {
  return new Promise((resolve) => {
    Alert.alert(
      'Evening check-in?',
      'SpendCheck can remind you at 9:30 pm to see today’s spends against your plan, and nudge you about purchases you asked to think over. Nothing leaves your phone.',
      [
        { text: 'Not now', style: 'cancel', onPress: () => resolve(false) },
        { text: 'Allow', onPress: () => resolve(true) },
      ],
      { cancelable: true, onDismiss: () => resolve(false) },
    );
  });
}

/** Returns whether notifications are allowed. Asks (with an explanation) at most once. */
async function ensurePermission(askIfNeeded: boolean): Promise<boolean> {
  // Android 13+: the OS prompt only shows once a channel exists.
  await ensureChannel();
  const current = await Notifications.getPermissionsAsync();
  if (current.granted) return true;
  if (!askIfNeeded || !current.canAskAgain) return false;
  const asked = await SecureStore.getItemAsync(ASKED_KEY).catch(() => null);
  if (asked) return false;
  await SecureStore.setItemAsync(ASKED_KEY, '1').catch(() => undefined);
  if (!(await explain())) return false;
  const res = await Notifications.requestPermissionsAsync();
  return res.granted;
}

/** On sign-in: ask once, then (re)schedule the repeating 21:30 daily summary reminder. */
export async function setupDailyReminder(): Promise<void> {
  try {
    if (!(await ensurePermission(true))) return;
    await Notifications.cancelScheduledNotificationAsync(DAILY_ID).catch(() => undefined);
    await Notifications.scheduleNotificationAsync({
      identifier: DAILY_ID,
      content: { title: 'How did today go?', body: 'See today’s spends vs plan', data: { url: '/summary' } },
      trigger: {
        type: Notifications.SchedulableTriggerInputTypes.DAILY,
        hour: 21,
        minute: 30,
        channelId: REMINDER_CHANNEL_ID,
      },
    });
  } catch {
    // Notifications are a nice-to-have; never block the app on them.
  }
}

/** "Ask in 3 days": one-off reminder. Returns false when notifications aren't allowed. */
export async function scheduleDeferReminder(title: string, body: string, days = 3): Promise<boolean> {
  try {
    if (!(await ensurePermission(true))) return false;
    await Notifications.scheduleNotificationAsync({
      content: { title, body, data: { url: '/chat' } },
      trigger: {
        type: Notifications.SchedulableTriggerInputTypes.DATE,
        date: new Date(Date.now() + days * 86_400_000),
        channelId: REMINDER_CHANNEL_ID,
      },
    });
    return true;
  } catch {
    return false;
  }
}

/** On any sign-out: drop scheduled reminders, delivered ones, and any pending tap so nothing replays into the next session. */
export async function cancelAllReminders(): Promise<void> {
  try {
    Notifications.clearLastNotificationResponse();
  } catch {
    // Not available on every platform.
  }
  await Promise.all([
    Notifications.cancelAllScheduledNotificationsAsync().catch(() => undefined),
    Notifications.dismissAllNotificationsAsync().catch(() => undefined),
  ]);
}
