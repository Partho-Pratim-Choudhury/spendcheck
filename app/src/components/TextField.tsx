import { StyleSheet, Text, TextInput, type TextInputProps, View } from 'react-native';

import { colors, radii, spacing } from '@/theme';

interface Props extends TextInputProps {
  label: string;
  error?: string;
  prefix?: string;
}

export function TextField({ label, error, prefix, style, ...rest }: Props) {
  return (
    <View style={styles.wrap}>
      <Text style={styles.label}>{label}</Text>
      <View style={[styles.box, !!error && styles.boxError]}>
        {prefix ? <Text style={styles.prefix}>{prefix}</Text> : null}
        <TextInput
          accessibilityLabel={label}
          placeholderTextColor={colors.muted}
          style={[styles.input, style]}
          {...rest}
        />
      </View>
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.xs },
  label: { fontSize: 13, fontWeight: '500', color: colors.muted },
  box: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 48,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    backgroundColor: colors.surface,
    paddingHorizontal: spacing.md,
  },
  boxError: { borderColor: colors.verdict.red.text },
  prefix: { fontSize: 16, color: colors.muted, marginRight: 4 },
  input: { flex: 1, fontSize: 16, color: colors.ink, paddingVertical: spacing.sm, fontVariant: ['tabular-nums'] },
  error: { fontSize: 13, color: colors.verdict.red.text },
});
