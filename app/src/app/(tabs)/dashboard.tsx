import { useQuery } from '@tanstack/react-query';
import { ActivityIndicator, FlatList, RefreshControl, StyleSheet, Text, View } from 'react-native';

import { PrimaryButton } from '@/components/PrimaryButton';
import { ApiError, apiFetch } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import type { Category, CategoryKind } from '@/lib/types';
import { colors, radii, spacing, typography } from '@/theme';

const kindChip: Record<CategoryKind, { text: string; bg: string }> = {
  FIXED: { text: colors.primary, bg: '#E8EEFB' },
  NEED: colors.verdict.green,
  WANT: colors.verdict.amber,
};

export default function DashboardScreen() {
  const { user } = useAuth();
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => apiFetch<Category[]>('/categories'),
  });

  const active = categories.data ?? [];

  return (
    <FlatList
      style={styles.list}
      contentContainerStyle={styles.content}
      data={active}
      keyExtractor={(c) => String(c.id)}
      refreshControl={
        <RefreshControl
          refreshing={categories.isRefetching}
          onRefresh={() => void categories.refetch()}
          colors={[colors.primary]}
        />
      }
      ListHeaderComponent={
        <View style={styles.header}>
          <Text style={typography.title}>Hi{user?.name ? `, ${user.name}` : ''}</Text>
          <Text style={styles.muted}>Your categories</Text>
        </View>
      }
      ListEmptyComponent={
        categories.isPending ? (
          <ActivityIndicator style={styles.state} color={colors.primary} />
        ) : categories.isError ? (
          <View style={[styles.state, styles.errorBox]}>
            <Text style={styles.errorText}>
              {categories.error instanceof ApiError ? categories.error.detail : 'Could not load categories.'}
            </Text>
            <PrimaryButton label="Retry" onPress={() => void categories.refetch()} />
          </View>
        ) : (
          <Text style={[styles.state, styles.muted]}>No categories yet.</Text>
        )
      }
      ItemSeparatorComponent={() => <View style={styles.sep} />}
      renderItem={({ item }) => (
        <View style={styles.row}>
          <Text style={styles.name}>{item.name}</Text>
          <View style={[styles.chip, { backgroundColor: kindChip[item.kind].bg }]}>
            <Text style={[styles.chipText, { color: kindChip[item.kind].text }]}>{item.kind}</Text>
          </View>
        </View>
      )}
    />
  );
}

const styles = StyleSheet.create({
  list: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, paddingBottom: spacing.xl },
  header: { gap: spacing.xs, marginBottom: spacing.lg },
  muted: { fontSize: 16, color: colors.muted },
  state: { marginTop: spacing.xl },
  errorBox: {
    gap: spacing.md,
    padding: spacing.lg,
    borderRadius: radii.md,
    backgroundColor: colors.verdict.red.bg,
  },
  errorText: { color: colors.verdict.red.text, fontSize: 14 },
  sep: { height: spacing.sm },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.surface,
    borderRadius: radii.sm,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  name: { fontSize: 16, color: colors.ink, flexShrink: 1 },
  chip: { borderRadius: 999, paddingHorizontal: spacing.sm, paddingVertical: spacing.xs },
  chipText: { fontSize: 12, fontWeight: '600' },
});
