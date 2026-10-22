import { Feather } from '@expo/vector-icons';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import { ApiError } from '@/lib/api';
import { colors, radii, spacing } from '@/theme';

import { decorative } from './a11y';
import { Card } from './Card';
import { PrimaryButton } from './PrimaryButton';

type FeatherName = keyof typeof Feather.glyphMap;

export function LoadingState({ label = 'Loading' }: { label?: string }) {
  return (
    <View style={styles.center} accessibilityLabel={label}>
      <ActivityIndicator size="large" color={colors.primary} />
    </View>
  );
}

export function errorMessage(e: unknown, fallback = 'Something went wrong.'): string {
  if (e instanceof ApiError) return e.detail || e.title;
  return fallback;
}

export function ErrorState({ error, onRetry, title = 'Couldn’t load this' }: { error: unknown; onRetry?: () => void; title?: string }) {
  return (
    <View style={styles.error}>
      <View style={styles.row}>
        <Feather {...decorative} name="alert-circle" size={18} color={colors.verdict.red.text} />
        <Text style={styles.errorTitle}>{title}</Text>
      </View>
      <Text style={styles.errorText}>{errorMessage(error)}</Text>
      {onRetry ? <PrimaryButton label="Retry" onPress={onRetry} /> : null}
    </View>
  );
}

interface EmptyProps {
  icon?: FeatherName;
  title: string;
  body?: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function EmptyState({ icon = 'inbox', title, body, actionLabel, onAction }: EmptyProps) {
  return (
    <Card style={styles.empty}>
      <View style={styles.emptyIcon}>
        <Feather {...decorative} name={icon} size={22} color={colors.primary} />
      </View>
      <Text style={styles.emptyTitle}>{title}</Text>
      {body ? <Text style={styles.emptyBody}>{body}</Text> : null}
      {actionLabel && onAction ? <PrimaryButton label={actionLabel} onPress={onAction} /> : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl, minHeight: 200 },
  error: { gap: spacing.md, padding: spacing.lg, borderRadius: radii.md, backgroundColor: colors.verdict.red.bg },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  errorTitle: { color: colors.verdict.red.text, fontSize: 16, fontWeight: '600' },
  errorText: { color: colors.verdict.red.text, fontSize: 14 },
  empty: { alignItems: 'stretch' },
  emptyIcon: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.primarySoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyTitle: { fontSize: 18, fontWeight: '600', color: colors.ink },
  emptyBody: { fontSize: 14, color: colors.muted, lineHeight: 20 },
});
