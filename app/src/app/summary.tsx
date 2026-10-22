import { router } from 'expo-router';
import { RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Card, CardTitle } from '@/components/Card';
import { MoneyText } from '@/components/MoneyText';
import { PrimaryButton } from '@/components/PrimaryButton';
import { ProgressBar, usageColor } from '@/components/ProgressBar';
import { EmptyState, ErrorState, LoadingState } from '@/components/States';
import { friendlyDate } from '@/lib/dates';
import { formatINR } from '@/lib/money';
import { useDailySummary } from '@/lib/queries';
import { colors, spacing, typography } from '@/theme';

export default function SummaryScreen() {
  const q = useDailySummary();
  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={q.isRefetching} onRefresh={() => void q.refetch()} colors={[colors.primary]} />}>
      {q.isPending ? (
        <LoadingState />
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : q.data === null ? (
        <EmptyState
          icon="sliders"
          title="No plan covers today"
          body="Set up your monthly plan to get a daily summary."
          actionLabel="Set up plan"
          onAction={() => router.push('/plan')}
        />
      ) : (
        <>
          <Card>
            <Text style={styles.date}>{friendlyDate(q.data.date)}</Text>
            <Text style={styles.text}>{q.data.text}</Text>
          </Card>
          <Card>
            <CardTitle>Today</CardTitle>
            <Text style={styles.line}>
              <Text style={styles.big}>{formatINR(q.data.spentPaise)}</Text>
              <Text style={styles.muted}> of {formatINR(q.data.budgetPaise)} safe spend</Text>
            </Text>
            <ProgressBar
              value={q.data.budgetPaise > 0 ? q.data.spentPaise / q.data.budgetPaise : q.data.spentPaise > 0 ? 1 : 0}
              color={usageColor(q.data.spentPaise, q.data.budgetPaise)}
              label="Spent today against today's budget"
            />
            <Text style={styles.muted}>
              {q.data.count} {q.data.count === 1 ? 'spend' : 'spends'}
              {q.data.overPaise > 0 ? ` · ${formatINR(q.data.overPaise, 'up')} over` : ''}
            </Text>
          </Card>
          <Card>
            <CardTitle>This month</CardTitle>
            <Text style={styles.line}>
              <Text style={styles.big}>{formatINR(q.data.month.spentPaise)}</Text>
              <Text style={styles.muted}> / {formatINR(q.data.month.discretionaryPaise)}</Text>
            </Text>
            <Text style={styles.muted}>
              At this pace: {formatINR(q.data.month.projectedPaise)}
              {q.data.month.savingsAtRiskPaise > 0 ? ` · ${formatINR(q.data.month.savingsAtRiskPaise, 'up')} from savings` : ''}
            </Text>
          </Card>
          <Card>
            <View style={styles.tileRow}>
              <Text style={styles.tileLabel}>PAUSES SAVED</Text>
              <MoneyText paise={q.data.pausesSavedPaise} round="down" style={styles.tileValue} />
            </View>
          </Card>
          <PrimaryButton label="See today’s spends" variant="secondary" onPress={() => router.push('/transactions')} />
        </>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.md },
  date: { ...typography.caption, fontWeight: '600', letterSpacing: 0.4 },
  text: { fontSize: 17, lineHeight: 24, color: colors.ink },
  line: { fontVariant: ['tabular-nums'] },
  big: { fontSize: 24, fontWeight: '700', color: colors.ink },
  muted: { fontSize: 14, color: colors.muted, fontVariant: ['tabular-nums'] },
  tileRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  tileLabel: { fontSize: 12, fontWeight: '700', color: colors.muted, letterSpacing: 0.8 },
  tileValue: { fontSize: 20, fontWeight: '700', color: colors.verdict.green.text },
});
