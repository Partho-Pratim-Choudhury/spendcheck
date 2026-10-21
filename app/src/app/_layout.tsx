import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as Notifications from 'expo-notifications';
import { type Href, router, Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { ApiError } from '@/lib/api';
import { AuthProvider, useAuth } from '@/lib/auth';
import { cancelAllReminders, setupDailyReminder } from '@/lib/notifications';
import { colors } from '@/theme';

void SplashScreen.preventAutoHideAsync();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Don't retry client errors (4xx); retry network/5xx twice.
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2,
    },
  },
});

export default function RootLayout() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <StatusBar style="dark" />
        <RootNavigator />
      </AuthProvider>
    </QueryClientProvider>
  );
}

function RootNavigator() {
  const { status } = useAuth();

  useEffect(() => {
    if (status !== 'loading') void SplashScreen.hideAsync();
    // Never leak one session's cached data (or reminders / a pending notification tap) into the next.
    // Covers every sign-out path: explicit logout, 401, expired or missing token.
    if (status === 'signedOut') {
      queryClient.clear();
      void cancelAllReminders();
    }
  }, [status]);

  useNotificationRouting(status === 'signedIn');

  if (status === 'loading') {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  const signedIn = status === 'signedIn';

  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.ink,
        headerShadowVisible: false,
        contentStyle: { backgroundColor: colors.bg },
      }}>
      {/* Auth guard: routes outside the active guard are unreachable and redirect to the first allowed one. */}
      <Stack.Protected guard={signedIn}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="plan" options={{ title: 'Monthly plan' }} />
        <Stack.Screen name="summary" options={{ title: 'Today’s summary' }} />
      </Stack.Protected>
      <Stack.Protected guard={!signedIn}>
        <Stack.Screen name="(auth)/login" options={{ headerShown: false }} />
      </Stack.Protected>
    </Stack>
  );
}

/** Once signed in: schedule the daily reminder, and route notification taps (cold start + running). */
function useNotificationRouting(signedIn: boolean) {
  const handled = useRef<string | null>(null);
  useEffect(() => {
    if (!signedIn) return;
    void setupDailyReminder();

    const open = (response: Notifications.NotificationResponse | null) => {
      if (!response) return;
      const id = response.notification.request.identifier + response.notification.date;
      if (handled.current === id) return;
      handled.current = id;
      // Consume it so a later re-login (or effect re-run) can't replay this tap.
      try {
        Notifications.clearLastNotificationResponse();
      } catch {
        // Not available on every platform.
      }
      const url = response.notification.request.content.data?.url;
      if (typeof url === 'string' && url.startsWith('/')) router.push(url as Href);
    };
    open(Notifications.getLastNotificationResponse());
    const sub = Notifications.addNotificationResponseReceivedListener(open);
    return () => sub.remove();
  }, [signedIn]);
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg },
});
