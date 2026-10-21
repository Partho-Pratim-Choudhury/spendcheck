import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { colors, spacing, typography } from '@/theme';

export function Placeholder({ title, note, children }: { title: string; note: string; children?: ReactNode }) {
  return (
    <View style={styles.container}>
      <Text style={typography.title} accessibilityRole="header">
        {title}
      </Text>
      <Text style={styles.note}>{note}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg, padding: spacing.xl, gap: spacing.sm },
  note: { fontSize: 16, color: colors.muted },
});
