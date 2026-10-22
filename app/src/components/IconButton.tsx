import { Feather } from '@expo/vector-icons';
import { Pressable, StyleSheet } from 'react-native';

import { colors } from '@/theme';

import { decorative } from './a11y';

type FeatherName = keyof typeof Feather.glyphMap;

export function IconButton({
  name,
  label,
  onPress,
  color = colors.ink,
  size = 22,
  disabled,
}: {
  name: FeatherName;
  label: string;
  onPress: () => void;
  color?: string;
  size?: number;
  disabled?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: !!disabled }}
      hitSlop={4}
      style={({ pressed }) => [styles.btn, (pressed || disabled) && styles.dim]}>
      <Feather {...decorative} name={name} size={size} color={color} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  btn: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  dim: { opacity: 0.5 },
});
