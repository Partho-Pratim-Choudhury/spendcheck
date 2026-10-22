import { Feather } from '@expo/vector-icons';
import { useMemo, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, SectionList, StyleSheet, Text, View } from 'react-native';

import { CategoryAvatar } from '@/components/CategoryAvatar';
import { Chip, Tag } from '@/components/Chip';
import { MoneyText } from '@/components/MoneyText';
import { EmptyState, ErrorState, LoadingState } from '@/components/States';
import { TransactionEditSheet } from '@/components/TransactionEditSheet';
import { dayHeaderLabel, formatTime, groupByDay, toYmd } from '@/lib/dates';
import { splitFixedSpends } from '@/lib/insights';
import { formatINR } from '@/lib/money';
import { useCategories, useSpendsRange, useTransactions } from '@/lib/queries';
import type { Category, Transaction, TransactionQuery } from '@/lib/types';
import { colors, spacing, typography } from '@/theme';

type Filter = { key: string; label: string; query: Pick<TransactionQuery, 'categoryId' | 'needOrWant'> };

export default function TransactionsScreen() {
  const range = useSpendsRange();
  const categories = useCategories();
  const [filterKey, setFilterKey] = useState('all');
  const [editing, setEditing] = useState<Transaction | null>(null);

  const filters = useMemo<Filter[]>(() => {
    const cats = (categories.data ?? []).filter((c) => !c.archived && c.kind !== 'FIXED');
    return [
      { key: 'all', label: 'All', query: {} },
      { key: 'WANT', label: 'Wants', query: { needOrWant: 'WANT' } },
      { key: 'NEED', label: 'Needs', query: { needOrWant: 'NEED' } },
      ...cats.map((c: Category) => ({ key: `c${c.id}`, label: c.name, query: { categoryId: c.id } })),
    ];
  }, [categories.data]);
  const filter = filters.find((f) => f.key === filterKey) ?? filters[0];

  const txQuery = { from: range.from, to: range.to, ...filter.query };
  const tx = useTransactions(txQuery, range.ready);
  const today = toYmd(new Date());

  const sections = useMemo(
    () =>
      groupByDay(tx.data ?? [], (t) => t.spentAt, (t) => t.amountPaise).map((g) => ({
        title: dayHeaderLabel(g.day, today),
        total: g.totalPaise,
        data: g.items,
      })),
    [tx.data, today],
  );
  // Headline = NEED+WANT so it matches Home's "spent"; FIXED (rent, EMI) is called out separately.
  const { variablePaise, fixedPaise } = useMemo(
    () => splitFixedSpends(tx.data ?? [], categories.data ?? []),
    [tx.data, categories.data],
  );

  return (
    <View style={styles.screen}>
      <View style={styles.top}>
        <Text style={styles.total}>
          <Text style={styles.totalAmount}>{formatINR(variablePaise)}</Text>
          <Text style={styles.muted}>
            {' '}
            {range.fromPlan ? 'this period' : 'last 30 days'}
            {filter.key !== 'all' ? ` · ${filter.label}` : ''}
          </Text>
        </Text>
        {fixedPaise > 0 ? (
          <Text style={styles.fixedLine}>+ {formatINR(fixedPaise)} fixed costs (rent, EMI…)</Text>
        ) : null}
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
          {filters.map((f) => (
            <Chip key={f.key} label={f.label} selected={f.key === filter.key} onPress={() => setFilterKey(f.key)} />
          ))}
        </ScrollView>
      </View>
      {tx.isPending ? (
        <LoadingState />
      ) : tx.isError ? (
        <View style={styles.pad}>
          <ErrorState error={tx.error} onRetry={() => void tx.refetch()} />
        </View>
      ) : (
        <SectionList
          sections={sections}
          keyExtractor={(t) => t.id}
          contentContainerStyle={styles.list}
          stickySectionHeadersEnabled={false}
          refreshControl={
            <RefreshControl refreshing={tx.isRefetching} onRefresh={() => void tx.refetch()} colors={[colors.primary]} />
          }
          renderSectionHeader={({ section }) => (
            <View style={styles.sectionHeader}>
              <Text style={styles.sectionTitle}>{section.title}</Text>
              <MoneyText paise={section.total} style={styles.sectionTotal} />
            </View>
          )}
          renderItem={({ item }) => <Row t={item} onPress={() => setEditing(item)} />}
          ListEmptyComponent={
            <EmptyState
              icon="list"
              title={filter.key === 'all' ? 'No spends yet' : `No ${filter.label.toLowerCase()} spends`}
              body="Log one from the Chat tab — type “450 swiggy” or hold the mic."
            />
          }
        />
      )}
      <TransactionEditSheet tx={editing} onClose={() => setEditing(null)} />
    </View>
  );
}

function Row({ t, onPress }: { t: Transaction; onPress: () => void }) {
  const title = t.merchant?.trim() || t.categoryName;
  const nwLabel = t.needOrWant === 'NEED' ? 'Need' : t.needOrWant === 'WANT' ? 'Want' : null;
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${title}, ${t.categoryName}, ${formatINR(t.amountPaise)}, ${formatTime(t.spentAt)}${
        nwLabel ? `, ${nwLabel}` : ''
      }${t.source === 'VOICE' ? ', logged by voice' : ''}. Tap to edit.`}
      style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
      <CategoryAvatar name={t.categoryName} />
      <View style={styles.rowMid}>
        <Text style={styles.rowTitle} numberOfLines={1}>
          {title}
        </Text>
        <View style={styles.rowMeta}>
          <Text style={styles.caption} numberOfLines={1}>
            {t.categoryName} · {formatTime(t.spentAt)}
          </Text>
          {nwLabel ? (
            <Tag
              label={nwLabel}
              color={t.needOrWant === 'WANT' ? colors.verdict.amber.text : colors.verdict.green.text}
              bg={t.needOrWant === 'WANT' ? colors.verdict.amber.bg : colors.verdict.green.bg}
            />
          ) : null}
          {t.source === 'VOICE' ? <Feather name="mic" size={12} color={colors.muted} /> : null}
        </View>
      </View>
      <MoneyText paise={t.amountPaise} style={styles.rowAmount} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  top: { backgroundColor: colors.surface, borderBottomWidth: 1, borderBottomColor: colors.border, paddingTop: spacing.sm },
  total: { paddingHorizontal: spacing.lg, fontVariant: ['tabular-nums'] },
  totalAmount: { fontSize: 22, fontWeight: '700', color: colors.ink },
  muted: { fontSize: 14, color: colors.muted },
  fixedLine: { paddingHorizontal: spacing.lg, fontSize: 13, color: colors.muted, fontVariant: ['tabular-nums'] },
  chips: { gap: spacing.sm, paddingHorizontal: spacing.lg, paddingVertical: spacing.md },
  pad: { padding: spacing.lg },
  list: { padding: spacing.lg, paddingTop: spacing.sm, gap: 0, flexGrow: 1 },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: spacing.lg,
    paddingBottom: spacing.sm,
  },
  sectionTitle: { fontSize: 12, fontWeight: '700', color: colors.muted, letterSpacing: 0.6 },
  sectionTotal: { fontSize: 13, fontWeight: '600', color: colors.muted },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 14,
    padding: spacing.md,
    marginBottom: spacing.sm,
    minHeight: 64,
  },
  pressed: { backgroundColor: colors.bg },
  rowMid: { flex: 1, gap: 3 },
  rowTitle: { fontSize: 16, color: colors.ink, fontWeight: '500' },
  rowMeta: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  caption: { ...typography.caption, flexShrink: 1 },
  rowAmount: { fontSize: 16, fontWeight: '600' },
});
