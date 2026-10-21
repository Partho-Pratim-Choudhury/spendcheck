import { Feather } from '@expo/vector-icons';
import { router } from 'expo-router';
import { Tabs } from 'expo-router/js-tabs';
import { type ColorValue, Pressable, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors, spacing, tabBar } from '@/theme';

type FeatherName = keyof typeof Feather.glyphMap;

function icon(name: FeatherName) {
  function TabIcon({ color, size }: { color: ColorValue; size: number }) {
    return <Feather name={name} size={size} color={color as string} />;
  }
  return TabIcon;
}

// Created once so each render passes the same component type and icons aren't remounted.
const ChatIcon = icon('message-circle');
const HomeIcon = icon('home');
const SpendsIcon = icon('list');
const ReportsIcon = icon('bar-chart-2');

export default function TabsLayout() {
  const insets = useSafeAreaInsets();
  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.ink,
        headerShadowVisible: false,
        sceneStyle: { backgroundColor: colors.bg },
        tabBarActiveTintColor: tabBar.activeTint,
        tabBarInactiveTintColor: tabBar.inactiveTint,
        tabBarStyle: {
          backgroundColor: tabBar.background,
          borderTopColor: colors.border,
          // 64px of visible bar, plus the system gesture/nav-bar inset below it.
          height: tabBar.height + insets.bottom,
          paddingTop: spacing.xs,
          paddingBottom: spacing.sm + insets.bottom,
        },
        tabBarLabelStyle: { fontSize: 12, fontWeight: '500' },
      }}>
      {/* Order matters: Chat is first, so it is the initial tab. */}
      <Tabs.Screen name="chat" options={{ title: 'Chat', tabBarIcon: ChatIcon }} />
      <Tabs.Screen
        name="dashboard"
        options={{
          title: 'Home',
          tabBarIcon: HomeIcon,
          headerRight: () => (
            <Pressable
              onPress={() => router.push('/plan')}
              accessibilityRole="button"
              accessibilityLabel="Open monthly plan"
              hitSlop={8}
              style={({ pressed }) => [styles.headerBtn, pressed && styles.pressed]}>
              <Feather name="sliders" size={22} color={colors.ink} />
            </Pressable>
          ),
        }}
      />
      <Tabs.Screen name="transactions" options={{ title: 'Spends', tabBarIcon: SpendsIcon }} />
      <Tabs.Screen name="reports" options={{ title: 'Reports', tabBarIcon: ReportsIcon }} />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  headerBtn: { paddingHorizontal: spacing.lg, paddingVertical: spacing.sm },
  pressed: { opacity: 0.5 },
});
