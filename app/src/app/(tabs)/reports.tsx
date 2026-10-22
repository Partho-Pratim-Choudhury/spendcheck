import { Feather } from '@expo/vector-icons';
import { useState } from 'react';
import { RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Card, CardTitle } from '@/components/Card';
import { Donut, donutA11y } from '@/components/charts/Donut';
import { TrendBars } from '@/components/charts/TrendBars';
import { IconButton } from '@/components/IconButton';
import { MoneyText } from '@/components/MoneyText';
import { EmptyState, ErrorState, LoadingState } from '@/components/States';
import { monthName, periodLabel } from '@/lib/dates';
import { onTrackToSavePaise, peakWantsWeekday, splitPct } from '@/lib/insights';
import { formatINR, type Rounding } from '@/lib/money';
import { useDashboard, usePlans, useReport } from '@/lib/queries';
import type { Dashboard, MonthlyReport, PlanSummary } from '@/lib/types';
import { categoryColor, colors, radii, spacing, typography } from '@/theme';

export default function ReportsScreen() {
  const plans = usePlans();
  // null = latest (API default) until the user picks a month.
  const [planId, setPlanId] = useState<number | null>(null);
  const list = plans.data ?? [];
  const effectiveId = planId ?? list[0]?.id ?? null;
  // Wait for the plan list so the report is fetched once, under the id it will actually show.
  const report = useReport(effectiveId, plans.isSuccess && effectiveId !== null);
  const idx = list.findIndex((p) => p.id === effectiveId);
  // Only the current plan's "Saved" needs the dashboard's projection (usually already cached by Home).
  const dashboard = useDashboard();

  const refresh = () => {
    void plans.refetch();
    if (effectiveId !== null) void report.refetch();
  };

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={report.isRefetching} onRefresh={refresh} colors={[colors.primary]} />}>
      {plans.isPending ? (
        <LoadingState />
      ) : plans.isError ? (
        <ErrorState error={plans.error} onRetry={refresh} />
      ) : list.length === 0 ? (
        <EmptyState icon="bar-chart-2" title="No reports yet" body="Reports appear once you have a monthly plan." />
      ) : (
        <>
          <MonthPicker
            plan={list[idx] ?? report.data?.plan ?? null}
            // newest first: "previous" = older = idx + 1
            onPrev={idx >= 0 && idx < list.length - 1 ? () => setPlanId(list[idx + 1].id) : undefined}
            onNext={idx > 0 ? () => setPlanId(list[idx - 1].id) : undefined}
          />
          {report.isPending ? (
            <LoadingState />
          ) : report.isError ? (
            <ErrorState error={report.error} onRetry={() => void report.refetch()} />
          ) : !report.data ? (
            <EmptyState icon="bar-chart-2" title="No report for this month" />
          ) : (
            <ReportBody r={report.data} dashboard={dashboard.data ?? null} />
          )}
        </>
      )}
    </ScrollView>
  );
}

function MonthPicker({ plan, onPrev, onNext }: { plan: PlanSummary | null; onPrev?: () => void; onNext?: () => void }) {
  return (
    <View style={styles.picker}>
      <IconButton name="chevron-left" label="Previous month" onPress={() => onPrev?.()} disabled={!onPrev} />
      <View style={styles.pickerMid}>
        <Text style={styles.pickerTitle} accessibilityRole="header">
          {plan ? `${monthName(plan.periodStart)} report` : 'Report'}
        </Text>
        {plan ? (
          <Text style={styles.caption}>
            {periodLabel(plan.periodStart, plan.periodEnd)} · {plan.status === 'ACTIVE' ? 'current' : 'closed'}
          </Text>
        ) : null}
      </View>
      <IconButton name="chevron-right" label="Next month" onPress={() => onNext?.()} disabled={!onNext} />
    </View>
  );
}

function ReportBody({ r, dashboard }: { r: MonthlyReport; dashboard: Dashboard | null }) {
  const t = r.totals;
  const spentAny = t.spentPaise > 0;
  // Mid-period, totals.savedPaise counts everything not yet spent as saved; show the projection instead.
  const live = r.plan.status === 'ACTIVE' && dashboard?.plan.id === r.plan.id ? dashboard.month : null;
  const onTrack = live ? onTrackToSavePaise(t.savingsTargetPaise, live.discretionaryPaise, live.projectedPaise) : null;
  return (
    <>
      <View style={styles.tiles}>
        <Tile label="Spent" paise={t.spentPaise} sub={`of ${formatINR(t.discretionaryPaise)}`} />
        {onTrack !== null ? (
          <Tile label="On track to save" paise={onTrack} round="down" sub="projected" tone={onTrack >= t.savingsTargetPaise ? 'good' : 'warn'} />
        ) : (
          <Tile label="Saved" paise={t.savedPaise} round="down" sub={`target ${formatINR(t.savingsTargetPaise)}`} tone={t.savedPaise >= t.savingsTargetPaise ? 'good' : 'warn'} />
        )}
        <Tile label="Pauses" paise={r.pausesSavedPaise} round="down" sub={`${r.skippedCount} skipped`} tone="good" />
      </View>
      {!spentAny ? (
        <EmptyState icon="pie-chart" title="No spends this month" body="Charts fill in as you log spends." />
      ) : (
        <>
          <WhereItWent r={r} />
          <NeedsVsWants r={r} />
          <TopWants r={r} />
        </>
      )}
      {r.trend.length > 1 ? (
        <Card>
          <CardTitle>Month over month</CardTitle>
          <TrendBars
            items={r.trend.map((p) => ({
              label: monthName(p.periodStart).slice(0, 3),
              spentPaise: p.spentPaise,
              planPaise: p.discretionaryPaise,
              highlight: p.planId === r.plan.id,
            }))}
          />
          <Text style={styles.caption}>Bars = spent · dashed line = plan</Text>
        </Card>
      ) : null}
    </>
  );
}

function Tile({
  label,
  paise,
  sub,
  tone,
  round,
}: {
  label: string;
  paise: number;
  sub: string;
  tone?: 'good' | 'warn';
  round?: Rounding;
}) {
  const color = tone === 'good' ? colors.verdict.green.text : tone === 'warn' ? colors.verdict.amber.text : colors.ink;
  return (
    <View style={styles.tile}>
      <Text style={styles.tileLabel}>{label.toUpperCase()}</Text>
      <MoneyText paise={paise} round={round} style={[styles.tileValue, { color }]} />
      <Text style={styles.caption} numberOfLines={1}>
        {sub}
      </Text>
    </View>
  );
}

function WhereItWent({ r }: { r: MonthlyReport }) {
  const cats = r.byCategory.filter((c) => c.spentPaise > 0);
  const values = cats.map((c) => c.spentPaise);
  const colorFor = (i: number) => categoryColor(cats[i].name);
  return (
    <Card>
      <CardTitle>Where it went</CardTitle>
      <View style={styles.donutRow}>
        <Donut
          values={values}
          colorsFor={colorFor}
          centerLabel="SPENT"
          centerValue={formatINR(r.totals.spentPaise)}
          accessibilityLabel={`Spending by category: ${donutA11y(cats.map((c) => c.name), values)}`}
        />
      </View>
      {cats.map((c, i) => {
        const over = c.limitPaise > 0 ? c.spentPaise > c.limitPaise : false;
        return (
          <View key={String(c.categoryId)} style={styles.legendRow}>
            <View style={[styles.swatch, { backgroundColor: colorFor(i) }]} />
            <View style={styles.legendMid}>
              <Text style={styles.legendName}>{c.name}</Text>
              {over ? (
                <View style={styles.overRow}>
                  <Feather name="alert-triangle" size={12} color={colors.verdict.red.text} />
                  <Text style={styles.overText}>
                    {formatINR(c.spentPaise - c.limitPaise, 'up')} over {formatINR(c.limitPaise)} limit
                  </Text>
                </View>
              ) : c.limitPaise > 0 ? (
                <Text style={styles.caption}>of {formatINR(c.limitPaise)}</Text>
              ) : null}
            </View>
            <MoneyText paise={c.spentPaise} style={styles.legendAmount} />
          </View>
        );
      })}
    </Card>
  );
}

function NeedsVsWants({ r }: { r: MonthlyReport }) {
  const [needPct, wantPct] = splitPct(r.needsPaise, r.wantsPaise);
  const total = r.needsPaise + r.wantsPaise;
  return (
    <Card>
      <CardTitle>Needs vs wants</CardTitle>
      <View
        style={styles.stack}
        accessible
        accessibilityLabel={`Needs ${formatINR(r.needsPaise)}, ${needPct} percent. Wants ${formatINR(r.wantsPaise)}, ${wantPct} percent.`}>
        {total > 0 ? (
          <>
            <View style={{ flex: Math.max(r.needsPaise, 0), backgroundColor: colors.verdict.green.text }} />
            <View style={{ flex: Math.max(r.wantsPaise, 0), backgroundColor: colors.amberBar }} />
          </>
        ) : null}
      </View>
      <View style={styles.nwRow}>
        <View style={styles.nwItem}>
          <Text style={[styles.nwLabel, { color: colors.verdict.green.text }]}>■ Needs · {needPct}%</Text>
          <MoneyText paise={r.needsPaise} style={styles.nwAmount} />
        </View>
        <View style={[styles.nwItem, styles.right]}>
          <Text style={[styles.nwLabel, { color: colors.verdict.amber.text }]}>■ Wants · {wantPct}%</Text>
          <MoneyText paise={r.wantsPaise} style={styles.nwAmount} />
        </View>
      </View>
      {r.untaggedPaise > 0 ? <Text style={styles.caption}>{formatINR(r.untaggedPaise)} untagged</Text> : null}
    </Card>
  );
}

function TopWants({ r }: { r: MonthlyReport }) {
  const insight = peakWantsWeekday(r.wantsByWeekday);
  if (r.topWants.length === 0 && !insight) return null;
  return (
    <Card>
      <CardTitle>Top wants</CardTitle>
      {r.topWants.map((w, i) => (
        <View key={`${w.label}-${i}`} style={styles.legendRow}>
          <Text style={styles.rank}>{i + 1}</Text>
          <View style={styles.legendMid}>
            <Text style={styles.legendName}>{w.label}</Text>
            <Text style={styles.caption}>
              {w.count} {w.count === 1 ? 'time' : 'times'}
            </Text>
          </View>
          <MoneyText paise={w.totalPaise} style={styles.legendAmount} />
        </View>
      ))}
      {insight ? (
        <View style={styles.insight}>
          <Feather name="calendar" size={16} color={colors.primary} />
          <Text style={styles.insightText}>
            Most wants land on <Text style={styles.bold}>{insight.weekday}</Text> — {insight.sharePct}% (
            {formatINR(insight.paise)}).
          </Text>
        </View>
      ) : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xl },
  caption: typography.caption,
  bold: { fontWeight: '700' },
  picker: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  pickerMid: { alignItems: 'center', flex: 1 },
  pickerTitle: { fontSize: 20, fontWeight: '700', color: colors.ink },
  tiles: { flexDirection: 'row', gap: spacing.sm },
  tile: {
    flex: 1,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    padding: spacing.md,
    gap: 2,
  },
  tileLabel: { fontSize: 11, fontWeight: '700', color: colors.muted, letterSpacing: 0.6 },
  tileValue: { fontSize: 17, fontWeight: '700' },
  donutRow: { alignItems: 'center', paddingVertical: spacing.sm },
  legendRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, minHeight: 36 },
  swatch: { width: 12, height: 12, borderRadius: 3 },
  legendMid: { flex: 1, gap: 1 },
  legendName: { fontSize: 15, color: colors.ink },
  legendAmount: { fontSize: 15, fontWeight: '600' },
  overRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  overText: { fontSize: 12, color: colors.verdict.red.text, fontVariant: ['tabular-nums'] },
  stack: { flexDirection: 'row', height: 16, borderRadius: 8, overflow: 'hidden', backgroundColor: colors.track },
  nwRow: { flexDirection: 'row', justifyContent: 'space-between' },
  nwItem: { gap: 2 },
  right: { alignItems: 'flex-end' },
  nwLabel: { fontSize: 13, fontWeight: '600' },
  nwAmount: { fontSize: 16, fontWeight: '600' },
  rank: { width: 20, fontSize: 14, fontWeight: '700', color: colors.muted, textAlign: 'center' },
  insight: {
    flexDirection: 'row',
    gap: spacing.sm,
    alignItems: 'center',
    backgroundColor: colors.primarySoft,
    borderRadius: radii.sm,
    padding: spacing.md,
  },
  insightText: { flex: 1, fontSize: 14, color: colors.ink },
});
