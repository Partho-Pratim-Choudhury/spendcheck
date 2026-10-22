import { StyleSheet, Text, View } from 'react-native';

import { PrimaryButton } from '@/components/PrimaryButton';
import { formatINR } from '@/lib/money';
import type { UndoCandidate } from '@/lib/types';
import { colors, spacing } from '@/theme';

import { BotBubble } from './Bubbles';
import type { UndoState } from './model';

interface Props {
  text: string;
  candidate: UndoCandidate;
  state: UndoState;
  removing?: boolean;
  error?: string | null;
  /** The chat screen owns the DELETE so its outcome lands even if this card scrolls away. */
  onRemove: () => void;
  onKept: () => void;
}

/** "₹450 · Food · Swiggy" */
function describe(c: UndoCandidate) {
  return [formatINR(c.amountPaise), c.categoryName, c.merchant?.trim() || null].filter(Boolean).join(' · ');
}

/** UNDO is confirm-first: the server only proposes a spend; Remove deletes it. */
export function UndoCard({ text, candidate, state, removing, error, onRemove, onKept }: Props) {
  if (state === 'removed') return <BotBubble text={`Removed ${describe(candidate)}`} />;
  if (state === 'kept') return <BotBubble text="Kept it." />;

  return (
    <View style={styles.row}>
      <View style={styles.bot}>
        <Text style={styles.text}>{text}</Text>
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <View style={styles.buttons}>
          <PrimaryButton
            label="Remove"
            variant="outline"
            onPress={onRemove}
            loading={removing}
            style={styles.flex}
            accessibilityLabel={`Remove ${describe(candidate)}`}
          />
          <PrimaryButton label="Keep" variant="secondary" onPress={onKept} disabled={removing} style={styles.flex} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', paddingHorizontal: spacing.lg, marginVertical: 4 },
  bot: {
    maxWidth: '86%',
    flexGrow: 1,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 18,
    borderBottomLeftRadius: 6,
    paddingHorizontal: spacing.md,
    paddingVertical: 10,
    gap: spacing.sm,
  },
  text: { color: colors.ink, fontSize: 16, lineHeight: 22 },
  error: { fontSize: 13, color: colors.verdict.red.text },
  buttons: { flexDirection: 'row', gap: spacing.sm },
  flex: { flex: 1 },
});
