import { Feather } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';

import type { VerdictLevel } from '@/lib/types';
import { colors, spacing } from '@/theme';

type FeatherName = keyof typeof Feather.glyphMap;

export const verdictMeta: Record<VerdictLevel, { label: string; icon: FeatherName; text: string; bg: string; bar: string }> = {
  GREEN: { label: 'OK', icon: 'check-circle', text: colors.verdict.green.text, bg: colors.verdict.green.bg, bar: colors.verdict.green.text },
  AMBER: { label: 'CAREFUL', icon: 'alert-triangle', text: colors.verdict.amber.text, bg: colors.verdict.amber.bg, bar: colors.amberBar },
  RED: { label: 'OVER PLAN', icon: 'x-octagon', text: colors.verdict.red.text, bg: colors.verdict.red.bg, bar: colors.redBar },
};

/** Icon + label + colour — never colour alone. */
export function VerdictBadge({ level, size = 'md' }: { level: VerdictLevel; size?: 'sm' | 'md' }) {
  const m = verdictMeta[level];
  const small = size === 'sm';
  return (
    <View style={[styles.badge, { backgroundColor: m.bg }, small && styles.small]}>
      <Feather name={m.icon} size={small ? 12 : 14} color={m.text} />
      <Text style={[styles.label, { color: m.text }, small && styles.smallLabel]}>{m.label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    alignSelf: 'flex-start',
    borderRadius: 999,
    paddingHorizontal: spacing.sm,
    paddingVertical: 4,
  },
  small: { paddingHorizontal: 6, paddingVertical: 2 },
  label: { fontSize: 12, fontWeight: '700', letterSpacing: 0.5 },
  smallLabel: { fontSize: 11 },
});
