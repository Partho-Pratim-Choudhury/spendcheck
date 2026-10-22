import { StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { periodLabel } from '@/lib/dates';
import { formatINR } from '@/lib/money';
import { useDashboard } from '@/lib/queries';
import { colors, spacing } from '@/theme';

export function ChatHeader() {
  const insets = useSafeAreaInsets();
  const d = useDashboard().data;
  const left = d?.today.leftPaise ?? 0;
  const over = left < 0;
  return (
    <View style={[styles.header, { paddingTop: insets.top + spacing.sm }]}>
      <View style={styles.titles}>
        <Text style={styles.title} accessibilityRole="header">
          SpendCheck
        </Text>
        {d ? (
          <Text style={styles.sub} numberOfLines={1}>
            {periodLabel(d.plan.periodStart, d.plan.periodEnd)} · Day {d.plan.daysElapsed} of {d.plan.daysTotal}
          </Text>
        ) : null}
      </View>
      {d ? (
        <View
          style={[styles.pill, over && styles.pillOver]}
          accessible
          accessibilityLabel={over ? `Over today's safe spend by ${formatINR(-left, 'up')}` : `Safe to spend today: ${formatINR(left, 'down')} left`}>
          <Text style={[styles.pillLabel, over && styles.pillTextOver]}>{over ? 'OVER TODAY' : 'SAFE TODAY'}</Text>
          <Text style={[styles.pillValue, over && styles.pillTextOver]}>
            {formatINR(Math.abs(left), over ? 'up' : 'down')}
            {over ? '' : ' left'}
          </Text>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  titles: { flex: 1, gap: 2 },
  title: { fontSize: 20, fontWeight: '700', color: colors.ink },
  sub: { fontSize: 13, color: colors.muted },
  pill: {
    backgroundColor: colors.verdict.green.bg,
    borderRadius: 14,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    alignItems: 'flex-end',
  },
  pillOver: { backgroundColor: colors.verdict.red.bg },
  pillLabel: { fontSize: 10, fontWeight: '700', letterSpacing: 0.6, color: colors.verdict.green.text },
  pillValue: { fontSize: 15, fontWeight: '700', color: colors.verdict.green.text, fontVariant: ['tabular-nums'] },
  pillTextOver: { color: colors.verdict.red.text },
});
