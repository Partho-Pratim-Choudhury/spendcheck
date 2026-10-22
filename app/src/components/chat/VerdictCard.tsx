import { Feather } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';

import { PrimaryButton } from '@/components/PrimaryButton';
import { ProgressBar } from '@/components/ProgressBar';
import { verdictMeta } from '@/components/VerdictBadge';
import { friendlyDate } from '@/lib/dates';
import { formatINR } from '@/lib/money';
import type { Verdict } from '@/lib/types';
import { colors, radii, spacing, typography } from '@/theme';

import { CompactLine } from './Bubbles';
import type { VerdictAction, VerdictOutcome } from './model';

interface Props {
  verdict: Verdict;
  outcome: VerdictOutcome | null;
  busy?: VerdictAction | null;
  error?: string | null;
  undone?: boolean;
  /** The chat screen owns the requests so their outcome lands even if this card scrolls away. */
  onDecide: (action: VerdictAction) => void;
}

export function VerdictCard(props: Props) {
  const { verdict: v, outcome, undone } = props;
  if (outcome?.decision === 'PROCEEDED') {
    const t = outcome.tx;
    return (
      <CompactLine amountPaise={t.amountPaise} categoryName={t.categoryName} merchant={t.merchant} needOrWant={t.needOrWant} undone={undone} />
    );
  }
  if (outcome?.decision === 'SKIPPED') {
    return <Outcome icon="award" text={`Skipped · ${formatINR(v.amountPaise)} stays in your plan. Nice pause.`} />;
  }
  if (outcome?.decision === 'DEFERRED') {
    const when = friendlyDate(outcome.remindOn);
    const what = `${v.categoryName} · ${formatINR(v.amountPaise)}`;
    return (
      <Outcome
        icon="clock"
        text={
          outcome.reminded
            ? `Okay — I’ll ask again about ${what} on ${when}.`
            : `${what} parked until ${when}. Turn on notifications to get a reminder.`
        }
      />
    );
  }
  return <Pending {...props} />;
}

function Outcome({ icon, text }: { icon: keyof typeof Feather.glyphMap; text: string }) {
  return (
    <View style={styles.wrap}>
      <View style={styles.outcome}>
        <Feather name={icon} size={14} color={colors.verdict.green.text} />
        <Text style={styles.outcomeText}>{text}</Text>
      </View>
    </View>
  );
}

function Pending({ verdict: v, busy, error, onDecide }: Props) {
  const m = verdictMeta[v.level];

  const used = v.categorySpentPaise + v.amountPaise;
  const leftAfter = v.categoryLeftPaise - v.amountPaise;

  if (v.level === 'GREEN') {
    return (
      <View style={styles.wrap}>
        <View style={[styles.card, styles.compact]}>
          <View style={styles.compactTop}>
            <View style={[styles.pill, { backgroundColor: m.bg }]}>
              <Feather name={m.icon} size={14} color={m.text} />
              <Text style={[styles.pillText, { color: m.text }]}>{m.label}</Text>
            </View>
            <Text style={[styles.headCat, typography.amount]} numberOfLines={1}>
              {v.categoryName} · {formatINR(v.amountPaise)}
            </Text>
          </View>
          <Text style={styles.message}>{v.message}</Text>
          {error ? <Text style={styles.error}>{error}</Text> : null}
          <PrimaryButton label="Log it" onPress={() => onDecide('buy')} loading={busy === 'buy'} disabled={!!busy} />
        </View>
      </View>
    );
  }

  return (
    <View style={styles.wrap}>
      <View style={styles.card}>
        <View style={[styles.band, { backgroundColor: m.bg }]} accessibilityRole="header">
          <Feather name={m.icon} size={18} color={m.text} />
          <Text style={[styles.bandLabel, { color: m.text }]}>{m.label}</Text>
          <Text style={[styles.bandCat, { color: m.text }, typography.amount]} numberOfLines={1}>
            {v.categoryName} · {formatINR(v.amountPaise)}
          </Text>
        </View>
        <View style={styles.body}>
          <View style={styles.barBlock}>
            <Text style={styles.barText}>
              {v.categoryName}:{' '}
              <Text style={[styles.barNum, typography.amount]}>
                {leftAfter >= 0 ? `${formatINR(leftAfter, 'down')} left after` : `${formatINR(-leftAfter, 'up')} over after`}
              </Text>{' '}
              · limit {formatINR(v.categoryLimitPaise)}
            </Text>
            <ProgressBar
              value={v.categoryLimitPaise > 0 ? used / v.categoryLimitPaise : 1}
              color={m.bar}
              height={6}
              label={`${v.categoryName} after this: ${formatINR(used)} of ${formatINR(v.categoryLimitPaise)}`}
            />
          </View>
          <View style={styles.tiles}>
            {v.goalDelayDays && v.goalName ? (
              <View style={styles.tile}>
                <Text style={styles.tileLabel}>GOAL DELAY</Text>
                <Text style={[styles.tileValue, { color: m.text }]}>~{v.goalDelayDays} days</Text>
                <Text style={styles.tileSub} numberOfLines={1}>
                  {v.goalName}
                </Text>
              </View>
            ) : null}
            <View style={styles.tile}>
              <Text style={styles.tileLabel}>SAFE / DAY</Text>
              <Text style={[styles.tileValue, typography.amount]}>
                {formatINR(v.safePerDayBeforePaise, 'down')} → <Text style={{ color: m.text }}>{formatINR(v.safePerDayAfterPaise, 'down')}</Text>
              </Text>
              <Text style={styles.tileSub}>before → after</Text>
            </View>
          </View>
          <Text style={styles.message}>{v.message}</Text>
          {error ? <Text style={styles.error}>{error}</Text> : null}
          <PrimaryButton
            label={`Skip it — save ${formatINR(v.amountPaise)}`}
            onPress={() => onDecide('skip')}
            loading={busy === 'skip'}
            disabled={!!busy}
          />
          <View style={styles.row}>
            <PrimaryButton
              label="Buy anyway"
              variant="secondary"
              onPress={() => onDecide('buy')}
              loading={busy === 'buy'}
              disabled={!!busy}
              style={styles.flex}
            />
            <PrimaryButton
              label="Ask in 3 days"
              variant="secondary"
              onPress={() => onDecide('defer')}
              loading={busy === 'defer'}
              disabled={!!busy}
              style={styles.flex}
            />
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { paddingHorizontal: spacing.lg, marginVertical: 4 },
  card: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.lg,
    overflow: 'hidden',
  },
  compact: { padding: spacing.lg, gap: spacing.md },
  compactTop: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  pill: { flexDirection: 'row', alignItems: 'center', gap: 4, borderRadius: 999, paddingHorizontal: spacing.sm, paddingVertical: 3 },
  pillText: { fontSize: 12, fontWeight: '700', letterSpacing: 0.5 },
  headCat: { fontSize: 15, fontWeight: '600', color: colors.ink, flexShrink: 1 },
  band: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingHorizontal: spacing.lg, paddingVertical: spacing.md },
  bandLabel: { fontSize: 14, fontWeight: '800', letterSpacing: 0.6 },
  bandCat: { fontSize: 14, fontWeight: '600', marginLeft: 'auto', flexShrink: 1 },
  body: { padding: spacing.lg, gap: spacing.md },
  barBlock: { gap: 6 },
  barText: { fontSize: 13, color: colors.muted },
  barNum: { color: colors.ink, fontWeight: '600' },
  tiles: { flexDirection: 'row', gap: spacing.sm },
  tile: { flex: 1, backgroundColor: colors.bg, borderRadius: radii.sm, padding: spacing.md, gap: 2 },
  tileLabel: { fontSize: 11, fontWeight: '700', color: colors.muted, letterSpacing: 0.6 },
  tileValue: { fontSize: 16, fontWeight: '700', color: colors.ink },
  tileSub: { fontSize: 12, color: colors.muted },
  message: { fontSize: 15, color: colors.ink, lineHeight: 21 },
  error: { fontSize: 13, color: colors.verdict.red.text },
  row: { flexDirection: 'row', gap: spacing.sm },
  flex: { flex: 1 },
  outcome: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    alignSelf: 'flex-start',
    backgroundColor: colors.verdict.green.bg,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    maxWidth: '90%',
  },
  outcomeText: { fontSize: 14, color: colors.verdict.green.text, flexShrink: 1 },
});
