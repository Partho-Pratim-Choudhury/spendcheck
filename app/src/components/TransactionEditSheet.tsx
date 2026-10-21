import { Feather } from '@expo/vector-icons';
import { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';

import { addDays, friendlyDate, instantToYmd, moveInstantToDay, toYmd } from '@/lib/dates';
import { paiseToInput, parseRupees } from '@/lib/money';
import { useCategories, useDeleteTransaction, useUpdateTransaction } from '@/lib/queries';
import type { NeedOrWant, Transaction, UpdateTransactionRequest } from '@/lib/types';
import { ApiError } from '@/lib/api';
import { colors, radii, spacing } from '@/theme';

import { CategoryPicker } from './CategoryPicker';
import { PrimaryButton } from './PrimaryButton';
import { SegmentedToggle } from './SegmentedToggle';
import { Sheet } from './Sheet';
import { errorMessage } from './States';
import { TextField } from './TextField';

const NW_OPTIONS: { value: NeedOrWant; label: string }[] = [
  { value: 'NEED', label: 'Need' },
  { value: 'WANT', label: 'Want' },
];

export function TransactionEditSheet({ tx, onClose }: { tx: Transaction | null; onClose: () => void }) {
  return (
    <Sheet visible={!!tx} title="Edit spend" onClose={onClose}>
      {tx ? <EditForm key={tx.id} tx={tx} onDone={onClose} /> : null}
    </Sheet>
  );
}

function EditForm({ tx, onDone }: { tx: Transaction; onDone: () => void }) {
  const categories = useCategories();
  const update = useUpdateTransaction();
  const del = useDeleteTransaction();

  const [amount, setAmount] = useState(paiseToInput(tx.amountPaise));
  const [categoryId, setCategoryId] = useState(tx.categoryId);
  const [merchant, setMerchant] = useState(tx.merchant ?? '');
  const [note, setNote] = useState(tx.note ?? '');
  const [nw, setNw] = useState<NeedOrWant | null>(tx.needOrWant);
  const [day, setDay] = useState(instantToYmd(tx.spentAt));
  const [formError, setFormError] = useState<string | null>(null);

  const fieldErrors = update.error instanceof ApiError ? update.error.fieldErrors : {};
  const amountPaise = parseRupees(amount);
  const today = toYmd(new Date());
  // Match the chat draft picker: variable spends move between NEED/WANT categories only;
  // a spend already in a FIXED category (rent, EMI…) stays among FIXED ones.
  const all = categories.data ?? [];
  const txFixed = all.find((c) => String(c.id) === String(tx.categoryId))?.kind === 'FIXED';
  const pickable = all.filter((c) => (c.kind === 'FIXED') === txFixed);

  const save = () => {
    setFormError(null);
    if (!amountPaise || amountPaise <= 0) {
      setFormError('Enter an amount greater than ₹0.');
      return;
    }
    const body: UpdateTransactionRequest = {};
    if (amountPaise !== tx.amountPaise) body.amountPaise = amountPaise;
    if (String(categoryId) !== String(tx.categoryId)) body.categoryId = categoryId;
    if (merchant.trim() !== (tx.merchant ?? '')) body.merchant = merchant.trim();
    if (note.trim() !== (tx.note ?? '')) body.note = note.trim();
    if (nw && nw !== tx.needOrWant) body.needOrWant = nw;
    if (day !== instantToYmd(tx.spentAt)) body.spentAt = moveInstantToDay(tx.spentAt, day);
    if (Object.keys(body).length === 0) {
      onDone();
      return;
    }
    update.mutate({ id: tx.id, body }, { onSuccess: onDone });
  };

  const confirmDelete = () => {
    Alert.alert('Delete this spend?', 'It will be removed from your plan and reports.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: () => del.mutate(tx.id, { onSuccess: onDone, onError: (e) => setFormError(errorMessage(e)) }),
      },
    ]);
  };

  return (
    <View style={styles.form}>
      <TextField
        label="Amount"
        prefix="₹"
        value={amount}
        onChangeText={setAmount}
        keyboardType="decimal-pad"
        error={fieldErrors.amountPaise}
      />
      <CategoryPicker categories={pickable} value={categoryId} onChange={setCategoryId} />
      {fieldErrors.categoryId ? <Text style={styles.error}>{fieldErrors.categoryId}</Text> : null}
      <TextField label="Merchant" value={merchant} onChangeText={setMerchant} placeholder="e.g. Swiggy" error={fieldErrors.merchant} />
      <TextField label="Note" value={note} onChangeText={setNote} placeholder="Optional" error={fieldErrors.note} />
      <View style={styles.group}>
        <Text style={styles.label}>Need or want</Text>
        <SegmentedToggle options={NW_OPTIONS} value={nw} onChange={setNw} label="Need or want" />
      </View>
      <View style={styles.group}>
        <Text style={styles.label}>Date</Text>
        <View style={styles.dateRow}>
          <Pressable
            onPress={() => setDay(addDays(day, -1))}
            accessibilityRole="button"
            accessibilityLabel="Previous day"
            style={styles.dateBtn}>
            <Feather name="chevron-left" size={22} color={colors.ink} />
          </Pressable>
          <Text style={styles.dateText}>{day === today ? `Today · ${friendlyDate(day)}` : friendlyDate(day)}</Text>
          <Pressable
            onPress={() => setDay(addDays(day, 1))}
            disabled={day >= today}
            accessibilityRole="button"
            accessibilityLabel="Next day"
            accessibilityState={{ disabled: day >= today }}
            style={[styles.dateBtn, day >= today && styles.dim]}>
            <Feather name="chevron-right" size={22} color={colors.ink} />
          </Pressable>
        </View>
        {fieldErrors.spentAt ? <Text style={styles.error}>{fieldErrors.spentAt}</Text> : null}
      </View>
      {formError || (update.error && Object.keys(fieldErrors).length === 0) ? (
        <Text style={styles.error}>{formError ?? errorMessage(update.error)}</Text>
      ) : null}
      <PrimaryButton label="Save" onPress={save} loading={update.isPending} />
      <PrimaryButton label="Delete spend" variant="outline" onPress={confirmDelete} loading={del.isPending} />
    </View>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacing.md },
  group: { gap: spacing.xs },
  label: { fontSize: 13, fontWeight: '500', color: colors.muted },
  error: { fontSize: 13, color: colors.verdict.red.text },
  dateRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    backgroundColor: colors.surface,
  },
  dateBtn: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  dim: { opacity: 0.3 },
  dateText: { fontSize: 16, color: colors.ink },
});
