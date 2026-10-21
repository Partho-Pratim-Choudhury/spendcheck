import { ActivityIndicator, Pressable, type StyleProp, StyleSheet, Text, type ViewStyle } from 'react-native';

import { colors, radii, spacing } from '@/theme';

interface Props {
  label: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  /** outline = red destructive text (logout); secondary = outline with primary-blue text. */
  variant?: 'primary' | 'outline' | 'secondary';
  style?: StyleProp<ViewStyle>;
  accessibilityLabel?: string;
}

export function PrimaryButton({ label, onPress, loading = false, disabled = false, variant = 'primary', style, accessibilityLabel }: Props) {
  const inactive = disabled || loading;
  const outline = variant !== 'primary';
  const secondary = variant === 'secondary';
  return (
    <Pressable
      onPress={onPress}
      disabled={inactive}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ disabled: inactive, busy: loading }}
      style={({ pressed }) => [
        styles.base,
        outline ? styles.outline : { backgroundColor: pressed ? colors.primaryPressed : colors.primary },
        outline && pressed && styles.outlinePressed,
        inactive && styles.inactive,
        style,
      ]}>
      {loading ? (
        <ActivityIndicator color={outline ? colors.primary : colors.onPrimary} />
      ) : (
        <Text style={[styles.label, outline && styles.outlineLabel, secondary && styles.secondaryLabel]} numberOfLines={1}>
          {label}
        </Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    height: 48,
    borderRadius: radii.sm,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing.lg,
  },
  label: { color: colors.onPrimary, fontSize: 16, fontWeight: '600' },
  outline: { borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface },
  outlinePressed: { backgroundColor: colors.bg },
  outlineLabel: { color: colors.verdict.red.text },
  secondaryLabel: { color: colors.primary },
  inactive: { opacity: 0.6 },
});
