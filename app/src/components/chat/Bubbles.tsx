import { Feather } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { formatINR } from '@/lib/money';
import type { NeedOrWant } from '@/lib/types';
import { colors, radii, spacing } from '@/theme';

export function UserBubble({ text, voice, pending }: { text: string; voice: boolean; pending?: boolean }) {
  return (
    <View style={[styles.row, styles.right]}>
      <View style={styles.user} accessibilityLabel={voice ? `You said: ${text || 'voice message'}` : `You: ${text}`}>
        {voice ? (
          <View style={styles.voiceRow}>
            <Feather name="mic" size={14} color={colors.onPrimary} />
            <Text style={[styles.userText, styles.italic]}>{pending ? 'Transcribing…' : text ? `“${text}”` : 'Voice message'}</Text>
          </View>
        ) : (
          <Text style={styles.userText}>{text}</Text>
        )}
      </View>
    </View>
  );
}

export function BotBubble({ text, muted }: { text: string; muted?: boolean }) {
  return (
    <View style={styles.row}>
      <View style={[styles.bot, muted && styles.botMuted]}>
        <Text style={[styles.botText, muted && styles.mutedText]}>{text}</Text>
      </View>
    </View>
  );
}

export function ErrorBubble({ text, onRetry, disabled }: { text: string; onRetry: () => void; disabled?: boolean }) {
  return (
    <View style={styles.row}>
      <View style={[styles.bot, styles.botMuted]}>
        <View style={styles.voiceRow}>
          <Feather name="cloud-off" size={14} color={colors.muted} />
          <Text style={[styles.botText, styles.mutedText, styles.flex]}>{text}</Text>
        </View>
        <Pressable
          onPress={onRetry}
          disabled={disabled}
          accessibilityRole="button"
          accessibilityLabel="Retry"
          accessibilityState={{ disabled }}
          style={[styles.retry, disabled && styles.retryDisabled]}
          hitSlop={8}>
          <Feather name="refresh-cw" size={14} color={colors.primary} />
          <Text style={styles.retryText}>Retry</Text>
        </Pressable>
      </View>
    </View>
  );
}

export function DayDivider({ label }: { label: string }) {
  return (
    <View style={styles.divider} accessibilityRole="header">
      <View style={styles.rule} />
      <Text style={styles.dividerText}>{label}</Text>
      <View style={styles.rule} />
    </View>
  );
}

/** "✓ ₹190 · Food · Chai Point · Want" */
export function CompactLine({
  amountPaise,
  categoryName,
  merchant,
  needOrWant,
  undone,
  prefix,
}: {
  amountPaise: number;
  categoryName: string;
  merchant: string | null;
  needOrWant: NeedOrWant | null;
  undone?: boolean;
  prefix?: string;
}) {
  const parts = [formatINR(amountPaise), categoryName, merchant?.trim() || null, needOrWant === 'NEED' ? 'Need' : needOrWant === 'WANT' ? 'Want' : null]
    .filter(Boolean)
    .join(' · ');
  return (
    <View style={styles.row}>
      <View style={styles.line} accessibilityLabel={`${undone ? 'Undone: ' : 'Logged: '}${parts}`}>
        <Feather name={undone ? 'rotate-ccw' : 'check'} size={14} color={undone ? colors.muted : colors.verdict.green.text} />
        <Text style={[styles.lineText, undone && styles.undone]} numberOfLines={2}>
          {prefix ? `${prefix} ` : ''}
          {parts}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  row: { flexDirection: 'row', paddingHorizontal: spacing.lg, marginVertical: 4 },
  right: { justifyContent: 'flex-end' },
  user: {
    maxWidth: '82%',
    backgroundColor: colors.primary,
    borderRadius: 18,
    borderBottomRightRadius: 6,
    paddingHorizontal: spacing.md,
    paddingVertical: 10,
  },
  userText: { color: colors.onPrimary, fontSize: 16, lineHeight: 22 },
  italic: { fontStyle: 'italic', flexShrink: 1 },
  voiceRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  bot: {
    maxWidth: '86%',
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 18,
    borderBottomLeftRadius: 6,
    paddingHorizontal: spacing.md,
    paddingVertical: 10,
    gap: spacing.sm,
  },
  botMuted: { backgroundColor: colors.track, borderColor: colors.track },
  botText: { color: colors.ink, fontSize: 16, lineHeight: 22 },
  mutedText: { color: colors.muted, fontSize: 14, lineHeight: 20 },
  retry: { flexDirection: 'row', alignItems: 'center', gap: 6, minHeight: 32, alignSelf: 'flex-start' },
  retryText: { color: colors.primary, fontWeight: '600', fontSize: 14 },
  retryDisabled: { opacity: 0.4 },
  divider: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingHorizontal: spacing.lg, marginVertical: spacing.md },
  rule: { flex: 1, height: 1, backgroundColor: colors.border },
  dividerText: { fontSize: 12, fontWeight: '700', color: colors.muted, letterSpacing: 0.6 },
  line: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: colors.surface,
    borderRadius: radii.sm,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    maxWidth: '90%',
  },
  lineText: { fontSize: 14, color: colors.ink, fontVariant: ['tabular-nums'], flexShrink: 1 },
  undone: { textDecorationLine: 'line-through', color: colors.muted },
});
