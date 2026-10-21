import type { ReactNode } from 'react';
import { type StyleProp, StyleSheet, Text, View, type ViewStyle } from 'react-native';

import { colors, radii, spacing } from '@/theme';

export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

export function CardTitle({ children, right }: { children: ReactNode; right?: ReactNode }) {
  return (
    <View style={styles.titleRow}>
      <Text style={styles.title} accessibilityRole="header">
        {children}
      </Text>
      {right}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    gap: spacing.md,
  },
  titleRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.sm },
  title: { fontSize: 16, fontWeight: '600', color: colors.ink, flexShrink: 1 },
});
