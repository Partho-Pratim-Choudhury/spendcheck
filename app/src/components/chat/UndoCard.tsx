import { StyleSheet, Text, View } from 'react-native';

import { PrimaryButton } from '@/components/PrimaryButton';
import { errorMessage } from '@/components/States';
import { ApiError } from '@/lib/api';
import { formatINR } from '@/lib/money';
import { useDeleteTransaction } from '@/lib/queries';
import type { UndoCandidate } from '@/lib/types';
import { colors, spacing } from '@/theme';

import { BotBubble } from './Bubbles';
import type { UndoState } from './model';

interface Props {
  text: string;
  candidate: UndoCandidate;
  state: UndoState;
  onRemoved: () => void;
  onKept: () => void;
}

/** "₹450 · Food · Swiggy" */
function describe(c: UndoCandidate) {
  return [formatINR(c.amountPaise), c.categoryName, c.merchant?.trim() || null].filter(Boolean).join(' · ');
}

/** UNDO is confirm-first: the server only proposes a spend; Remove deletes it. */
export function UndoCard({ text, candidate, state, onRemoved, onKept }: Props) {
  const del = useDeleteTransaction();

  if (state === 'removed') return <BotBubble text={`Removed ${describe(candidate)}`} />;
  if (state === 'kept') return <BotBubble text="Kept it." />;

  const remove = () =>
    del.mutate(candidate.transactionId, {
      onSuccess: onRemoved,
      // Already gone (e.g. deleted from the Spends tab) — same end state.
      onError: (e) => {
        if (e instanceof ApiError && e.status === 404) onRemoved();
      },
    });

  const failed = del.error && !(del.error instanceof ApiError && del.error.status === 404);

  return (
    <View style={styles.row}>
      <View style={styles.bot}>
        <Text style={styles.text}>{text}</Text>
        {failed ? <Text style={styles.error}>{errorMessage(del.error)}</Text> : null}
        <View style={styles.buttons}>
          <PrimaryButton
            label="Remove"
            variant="outline"
            onPress={remove}
            loading={del.isPending}
            style={styles.flex}
            accessibilityLabel={`Remove ${describe(candidate)}`}
          />
          <PrimaryButton label="Keep" variant="secondary" onPress={onKept} disabled={del.isPending} style={styles.flex} />
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
