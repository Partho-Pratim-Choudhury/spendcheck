import { ActivityIndicator, Pressable, StyleSheet, Text } from 'react-native';

import { colors, radii, spacing } from '@/theme';

interface Props {
  label: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  variant?: 'primary' | 'outline';
}

export function PrimaryButton({ label, onPress, loading = false, disabled = false, variant = 'primary' }: Props) {
  const inactive = disabled || loading;
  const outline = variant === 'outline';
  return (
    <Pressable
      onPress={onPress}
      disabled={inactive}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: inactive, busy: loading }}
      style={({ pressed }) => [
        styles.base,
        outline ? styles.outline : { backgroundColor: pressed ? colors.primaryPressed : colors.primary },
        outline && pressed && styles.outlinePressed,
        inactive && styles.inactive,
      ]}>
      {loading ? (
        <ActivityIndicator color={outline ? colors.primary : colors.onPrimary} />
      ) : (
        <Text style={[styles.label, outline && styles.outlineLabel]}>{label}</Text>
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
  inactive: { opacity: 0.6 },
});
