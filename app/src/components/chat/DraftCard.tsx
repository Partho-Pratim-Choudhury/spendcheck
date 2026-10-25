import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { AmountInput } from '@/components/AmountInput';
import { CategoryPicker } from '@/components/CategoryPicker';
import { PrimaryButton } from '@/components/PrimaryButton';
import { ProgressBar, usageColor } from '@/components/ProgressBar';
import { SegmentedToggle } from '@/components/SegmentedToggle';
import { TextField } from '@/components/TextField';
import { relativeDay, toYmd } from '@/lib/dates';
import { formatINR } from '@/lib/money';
import { useCategories } from '@/lib/queries';
import type { Category, Draft, NeedOrWant, Transaction } from '@/lib/types';
import { categoryColor, colors, radii, spacing, typography } from '@/theme';

import { CompactLine } from './Bubbles';
import type { DraftEdits } from './model';

const NW: { value: NeedOrWant; label: string }[] = [
  { value: 'NEED', label: 'Need' },
  { value: 'WANT', label: 'Want' },
];

interface Props {
  draft: Draft;
  confirmed: Transaction | null;
  confirming?: boolean;
  error?: string | null;
  undone?: boolean;
  /** The chat screen owns the POST so its outcome lands even if this card scrolls away. */
  onConfirm: (edits: DraftEdits) => void;
}

export function DraftCard({ draft, confirmed, confirming, error, undone, onConfirm }: Props) {
  if (confirmed) {
    return (
      <CompactLine
        amountPaise={confirmed.amountPaise}
        categoryName={confirmed.categoryName}
        merchant={confirmed.merchant}
        needOrWant={confirmed.needOrWant}
        undone={undone}
      />
    );
  }
  return <PendingDraft draft={draft} confirming={confirming} error={error} onConfirm={onConfirm} />;
}

function PendingDraft({ draft, confirming, error, onConfirm }: Omit<Props, 'confirmed' | 'undone'>) {
  const categories = useCategories();
  const [nw, setNw] = useState<NeedOrWant | null>(draft.needOrWant);
  const [editing, setEditing] = useState(false);
  const [amountPaise, setAmountPaise] = useState<number | null>(draft.amountPaise);
  const [categoryId, setCategoryId] = useState<Category['id']>(draft.categoryId);
  const [merchant, setMerchant] = useState(draft.merchant ?? '');
  const [localError, setLocalError] = useState<string | null>(null);

  // Null (empty / invalid) falls back to 0 for the display + preview, matching the old behaviour.
  const amountForPreview = amountPaise ?? 0;
  const sameCategory = String(categoryId) === String(draft.categoryId);
  const categoryName =
    (categories.data ?? []).find((c) => String(c.id) === String(categoryId))?.name ?? draft.categoryName;
  // Display-only: shift the server's "after" by the edited amount; unknown for another category.
  const after = sameCategory
    ? { spent: draft.after.categorySpentPaise - draft.amountPaise + amountForPreview, limit: draft.after.categoryLimitPaise }
    : null;

  const confirm = () => {
    if (amountPaise == null || amountPaise <= 0) {
      setLocalError('Enter an amount greater than ₹0.');
      return;
    }
    setLocalError(null);
    onConfirm({ amountPaise, categoryId, merchant: merchant.trim() || undefined, needOrWant: nw ?? undefined });
  };

  const today = toYmd(new Date());
  const chipColor = categoryColor(categoryName);

  return (
    <View style={styles.wrap}>
      <View style={styles.card}>
        <Text style={styles.kicker}>LOG THIS SPEND?</Text>
        {editing ? (
          <View style={styles.edit}>
            <AmountInput
              label="Amount"
              prefix="₹"
              valuePaise={amountPaise}
              onChangeValuePaise={(p) => {
                setAmountPaise(p);
                setLocalError(null);
              }}
            />
            <CategoryPicker
              categories={(categories.data ?? []).filter((c) => c.kind !== 'FIXED' || String(c.id) === String(categoryId))}
              value={categoryId}
              onChange={setCategoryId}
            />
            <TextField label="Merchant" value={merchant} onChangeText={setMerchant} placeholder="Optional" />
          </View>
        ) : (
          <>
            <Text style={[styles.amount, typography.amount]}>{formatINR(amountForPreview, 'exact')}</Text>
            <View style={styles.metaRow}>
              <View style={[styles.catChip, { backgroundColor: `${chipColor}1F` }]}>
                <Text style={[styles.catChipText, { color: chipColor }]}>{categoryName}</Text>
              </View>
              <Text style={styles.meta} numberOfLines={1}>
                {[merchant.trim() || null, relativeDay(draft.spentAt, today)].filter(Boolean).join(' · ')}
              </Text>
            </View>
          </>
        )}
        {after ? (
          <View style={styles.afterBox}>
            <Text style={styles.afterText}>
              {categoryName} after this{' '}
              <Text style={[styles.afterNum, typography.amount]}>
                {formatINR(after.spent)} / {formatINR(after.limit)}
              </Text>
            </Text>
            <ProgressBar
              value={after.limit > 0 ? after.spent / after.limit : 1}
              color={usageColor(after.spent, after.limit)}
              height={6}
              label={`${categoryName} after this spend: ${formatINR(after.spent)} of ${formatINR(after.limit)}`}
            />
          </View>
        ) : null}
        <SegmentedToggle options={NW} value={nw} onChange={setNw} label="Need or want" />
        {localError || error ? <Text style={styles.error}>{localError ?? error}</Text> : null}
        <View style={styles.buttons}>
          <PrimaryButton label="Confirm" onPress={confirm} loading={confirming} style={styles.flex} />
          <PrimaryButton
            label={editing ? 'Done' : 'Edit'}
            variant="secondary"
            onPress={() => setEditing((e) => !e)}
            disabled={confirming}
            style={styles.editBtn}
          />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { paddingHorizontal: spacing.lg, marginVertical: 4 },
  card: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.lg,
    padding: spacing.lg,
    gap: spacing.md,
  },
  kicker: { fontSize: 12, fontWeight: '700', color: colors.muted, letterSpacing: 0.8 },
  amount: { fontSize: 34, fontWeight: '700', color: colors.ink, marginTop: -4 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginTop: -4 },
  catChip: { borderRadius: 999, paddingHorizontal: 10, paddingVertical: 3 },
  catChipText: { fontSize: 13, fontWeight: '600' },
  meta: { fontSize: 14, color: colors.muted, flexShrink: 1 },
  edit: { gap: spacing.md },
  afterBox: { gap: 6 },
  afterText: { fontSize: 13, color: colors.muted },
  afterNum: { color: colors.ink, fontWeight: '600' },
  error: { fontSize: 13, color: colors.verdict.red.text },
  buttons: { flexDirection: 'row', gap: spacing.sm },
  flex: { flex: 1 },
  editBtn: { minWidth: 96 },
});
