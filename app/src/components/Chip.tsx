import { Pressable, StyleSheet, Text } from 'react-native';

import { colors, spacing } from '@/theme';

interface Props {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  color?: string;
}

/** Selectable filter / picker chip (44px touch target). */
export function Chip({ label, selected = false, onPress, color = colors.primary }: Props) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      accessibilityLabel={label}
      hitSlop={{ top: 4, bottom: 4 }}
      style={({ pressed }) => [
        styles.chip,
        selected && { backgroundColor: color, borderColor: color },
        pressed && styles.pressed,
      ]}>
      <Text style={[styles.label, selected && styles.selectedLabel]} numberOfLines={1}>
        {label}
      </Text>
    </Pressable>
  );
}

/** Small static tag, e.g. "Need" / "Want". */
export function Tag({ label, color = colors.muted, bg = colors.track }: { label: string; color?: string; bg?: string }) {
  return (
    <Text style={[styles.tag, { color, backgroundColor: bg }]} numberOfLines={1}>
      {label}
    </Text>
  );
}

const styles = StyleSheet.create({
  chip: {
    minHeight: 36,
    paddingHorizontal: spacing.md,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: { opacity: 0.7 },
  label: { fontSize: 14, fontWeight: '500', color: colors.ink },
  selectedLabel: { color: colors.onPrimary },
  tag: {
    fontSize: 11,
    fontWeight: '600',
    paddingHorizontal: 6,
    paddingVertical: 1,
    borderRadius: 6,
    overflow: 'hidden',
  },
});
