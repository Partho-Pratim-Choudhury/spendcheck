import { Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, radii } from '@/theme';

interface Props<T extends string> {
  options: { value: T; label: string }[];
  value: T | null;
  onChange: (v: T) => void;
  label?: string;
}

export function SegmentedToggle<T extends string>({ options, value, onChange, label }: Props<T>) {
  return (
    <View style={styles.wrap} accessibilityRole="radiogroup" accessibilityLabel={label}>
      {options.map((o) => {
        const selected = o.value === value;
        return (
          <Pressable
            key={o.value}
            onPress={() => onChange(o.value)}
            accessibilityRole="radio"
            accessibilityState={{ checked: selected }}
            accessibilityLabel={o.label}
            style={[styles.seg, selected && styles.selected]}>
            <Text style={[styles.text, selected && styles.selectedText]}>{o.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    flexDirection: 'row',
    backgroundColor: colors.track,
    borderRadius: radii.sm,
    padding: 3,
  },
  seg: { flex: 1, minHeight: 40, alignItems: 'center', justifyContent: 'center', borderRadius: 10 },
  selected: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border },
  text: { fontSize: 14, fontWeight: '500', color: colors.muted },
  selectedText: { color: colors.ink, fontWeight: '600' },
});
