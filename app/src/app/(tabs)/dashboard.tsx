import { Feather } from '@expo/vector-icons';
import { router } from 'expo-router';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Card, CardTitle } from '@/components/Card';
import { BurnChart } from '@/components/charts/BurnChart';
import { MoneyText } from '@/components/MoneyText';
import { ProgressBar, usageColor } from '@/components/ProgressBar';
import { EmptyState, ErrorState, LoadingState } from '@/components/States';
import { periodLabel, shortDate } from '@/lib/dates';
import { formatINR } from '@/lib/money';
import { useDashboard } from '@/lib/queries';
import type { CategoryStatus, Dashboard, DashboardCategory, Goal } from '@/lib/types';
import { colors, radii, spacing, typography } from '@/theme';

const statusText: Record<CategoryStatus, string> = {
  OK: colors.verdict.green.text,
  WARN: colors.verdict.amber.text,
  OVER: colors.verdict.red.text,
};

export default function DashboardScreen() {
  const q = useDashboard();
  const refreshing = q.isRefetching && !q.isPending;

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void q.refetch()} colors={[colors.primary]} />}>
      {q.isPending ? (
        <LoadingState />
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : q.data === null ? (
        <EmptyState
          icon="sliders"
          title="Set up your monthly plan"
          body="Tell SpendCheck your income, fixed costs and savings target. It works out what’s safe to spend each day."
          actionLabel="Set up plan"
          onAction={() => router.push('/plan')}
        />
      ) : (
        <DashboardBody d={q.data} />
      )}
    </ScrollView>
  );
}

function DashboardBody({ d }: { d: Dashboard }) {
  return (
    <>
      <Hero d={d} />
      <MonthCard d={d} />
      <CategoriesCard categories={d.categories} />
      {d.primaryGoal ? <GoalCard goal={d.primaryGoal} /> : null}
    </>
  );
}

function Hero({ d }: { d: Dashboard }) {
  const { today, plan } = d;
  const over = today.leftPaise < 0;
  return (
    <View style={styles.hero}>
      <View style={styles.heroTop}>
        <Text style={styles.heroCaption}>
          {periodLabel(plan.periodStart, plan.periodEnd)} · Day {plan.daysElapsed} of {plan.daysTotal}
        </Text>
        <Pressable
          onPress={() => router.push('/summary')}
          accessibilityRole="link"
          accessibilityLabel="Open today's summary"
          hitSlop={10}>
          <Text style={styles.heroLink}>Today’s summary ›</Text>
        </Pressable>
      </View>
      <Text style={styles.heroLabel}>{over ? 'Over today’s safe spend' : 'Safe to spend today'}</Text>
      <MoneyText paise={Math.abs(today.leftPaise)} round={over ? 'up' : 'down'} style={[styles.heroAmount, over && { color: '#FFB4B8' }]} />
      <Text style={styles.heroSub}>
        of {formatINR(today.budgetPaise)} · spent {formatINR(today.spentPaise)} today
      </Text>
      <ProgressBar
        value={today.budgetPaise > 0 ? today.spentPaise / today.budgetPaise : today.spentPaise > 0 ? 1 : 0}
        color={over ? colors.redBar : colors.heroBar}
        trackColor={colors.heroTrack}
        label="Spent today against today's budget"
      />
      <View style={styles.heroTile}>
        <Text style={styles.heroTileLabel}>PAUSES SAVED</Text>
        <MoneyText paise={d.pausesSavedPaise} round="down" style={styles.heroTileValue} />
        {d.skippedCount > 0 ? (
          <Text style={styles.heroTileNote}>
            {d.skippedCount} {d.skippedCount === 1 ? 'spend' : 'spends'} skipped
          </Text>
        ) : null}
      </View>
    </View>
  );
}

function MonthCard({ d }: { d: Dashboard }) {
  const m = d.month;
  return (
    <Card>
      <CardTitle>This month</CardTitle>
      <Text style={styles.monthLine}>
        <Text style={styles.monthBig}>{formatINR(m.spentPaise)}</Text>
        <Text style={styles.muted}> / {formatINR(m.discretionaryPaise)}</Text>
      </Text>
      <BurnChart
        cumulative={d.burn.map((b) => b.cumulativePaise)}
        daysTotal={d.plan.daysTotal}
        discretionaryPaise={m.discretionaryPaise}
        projectedPaise={m.projectedPaise}
      />
      {m.savingsAtRiskPaise > 0 ? (
        <View style={styles.warnRow} accessibilityRole="alert">
          <Feather name="alert-triangle" size={16} color={colors.verdict.amber.text} />
          <Text style={styles.warnText}>
            At this pace: {formatINR(m.projectedPaise)} · {formatINR(m.savingsAtRiskPaise, 'up')} from savings
          </Text>
        </View>
      ) : null}
    </Card>
  );
}

function CategoriesCard({ categories }: { categories: DashboardCategory[] }) {
  return (
    <Card>
      <CardTitle
        right={
          <Pressable onPress={() => router.push('/transactions')} accessibilityRole="link" hitSlop={12}>
            <Text style={styles.link}>See spends</Text>
          </Pressable>
        }>
        Categories
      </CardTitle>
      {categories.length === 0 ? (
        <Text style={styles.muted}>No category limits yet — add them in your plan.</Text>
      ) : (
        categories.map((c) => (
          <View key={String(c.categoryId)} style={styles.catRow}>
            <View style={styles.catTop}>
              <Text style={styles.catName} numberOfLines={1}>
                {c.name}
              </Text>
              <Text style={[styles.catLeft, typography.amount, { color: statusText[c.status] }]}>
                {c.leftPaise < 0 ? `${formatINR(-c.leftPaise, 'up')} over` : `${formatINR(c.leftPaise, 'down')} left`}
                <Text style={styles.muted}> / {formatINR(c.limitPaise)}</Text>
              </Text>
            </View>
            <ProgressBar
              value={c.limitPaise > 0 ? c.spentPaise / c.limitPaise : c.spentPaise > 0 ? 1 : 0}
              color={usageColor(c.spentPaise, c.limitPaise)}
              height={4}
              label={`${c.name}: spent ${formatINR(c.spentPaise)} of ${formatINR(c.limitPaise)}`}
            />
          </View>
        ))
      )}
    </Card>
  );
}

function GoalCard({ goal }: { goal: Goal }) {
  return (
    <Card>
      <CardTitle>{goal.name}</CardTitle>
      <Text style={styles.muted}>
        <Text style={[styles.goalSaved, typography.amount]}>{formatINR(goal.savedPaise, 'down')}</Text> of{' '}
        {formatINR(goal.targetPaise)} · {Math.round(goal.progressPct)}%
      </Text>
      <ProgressBar value={goal.progressPct / 100} color={colors.verdict.green.text} label={`${goal.name} progress`} />
      {goal.onTrackDate ? (
        <Text style={styles.caption}>
          On track for {shortDate(goal.onTrackDate)}
          {goal.targetDate ? ` · target ${shortDate(goal.targetDate)}` : ''}
        </Text>
      ) : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xl },
  muted: { fontSize: 14, color: colors.muted },
  caption: typography.caption,
  hero: { backgroundColor: colors.heroBg, borderRadius: radii.lg, padding: spacing.lg, gap: spacing.sm },
  heroTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.sm },
  heroCaption: { color: colors.heroMuted, fontSize: 12, flexShrink: 1 },
  heroLink: { color: colors.heroBar, fontSize: 13, fontWeight: '600' },
  heroLabel: { color: colors.heroMuted, fontSize: 14, marginTop: spacing.xs },
  heroAmount: { color: colors.heroAccent, fontSize: 40, fontWeight: '700' },
  heroSub: { color: colors.heroMuted, fontSize: 14, fontVariant: ['tabular-nums'] },
  heroTile: {
    marginTop: spacing.sm,
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderRadius: radii.sm,
    padding: spacing.md,
    gap: 2,
  },
  heroTileLabel: { color: colors.heroMuted, fontSize: 11, fontWeight: '700', letterSpacing: 0.8 },
  heroTileValue: { color: colors.heroAccent, fontSize: 20, fontWeight: '700' },
  heroTileNote: { color: colors.heroMuted, fontSize: 12 },
  monthLine: { fontVariant: ['tabular-nums'] },
  monthBig: { fontSize: 24, fontWeight: '700', color: colors.ink },
  warnRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    backgroundColor: colors.verdict.amber.bg,
    borderRadius: radii.sm,
    padding: spacing.md,
  },
  warnText: { color: colors.verdict.amber.text, fontSize: 14, flex: 1, fontVariant: ['tabular-nums'] },
  link: { color: colors.primary, fontSize: 14, fontWeight: '600' },
  catRow: { gap: 6 },
  catTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', gap: spacing.sm },
  catName: { fontSize: 15, color: colors.ink, flexShrink: 1 },
  catLeft: { fontSize: 14, fontWeight: '600' },
  goalSaved: { color: colors.ink, fontWeight: '600', fontSize: 16 },
});
