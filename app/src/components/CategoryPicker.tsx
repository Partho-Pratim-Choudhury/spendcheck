import { StyleSheet, Text, View } from 'react-native';

import type { Category } from '@/lib/types';
import { categoryColor, colors, spacing } from '@/theme';

import { Chip } from './Chip';

interface Props {
  categories: Category[];
  value: Category['id'] | null;
  onChange: (id: Category['id']) => void;
  label?: string;
}

/** Wrapping chip grid of non-archived categories. */
export function CategoryPicker({ categories, value, onChange, label = 'Category' }: Props) {
  const usable = categories.filter((c) => !c.archived);
  return (
    <View style={styles.wrap}>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.grid} accessibilityRole="radiogroup" accessibilityLabel={label}>
        {usable.map((c) => (
          <Chip
            key={String(c.id)}
            label={c.name}
            selected={String(c.id) === String(value)}
            color={categoryColor(c.name)}
            onPress={() => onChange(c.id)}
          />
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.xs },
  label: { fontSize: 13, fontWeight: '500', color: colors.muted },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
});
