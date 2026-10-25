import { useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';

import { ApiError } from '@/lib/api';
import { useDeleteGoal, useSaveGoal } from '@/lib/queries';
import type { Goal, GoalInput, PatchGoal } from '@/lib/types';
import { colors, spacing } from '@/theme';

import { AmountInput } from './AmountInput';
import { PrimaryButton } from './PrimaryButton';
import { Sheet } from './Sheet';
import { errorMessage } from './States';
import { TextField } from './TextField';

/** `goal` = null → closed; 'new' → create. */
export function GoalSheet({ goal, onClose }: { goal: Goal | 'new' | null; onClose: () => void }) {
  return (
    <Sheet visible={goal !== null} title={goal === 'new' ? 'New goal' : 'Edit goal'} onClose={onClose}>
      {goal !== null ? <GoalForm key={goal === 'new' ? 'new' : goal.id} goal={goal === 'new' ? null : goal} onDone={onClose} /> : null}
    </Sheet>
  );
}

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function GoalForm({ goal, onDone }: { goal: Goal | null; onDone: () => void }) {
  const save = useSaveGoal();
  const del = useDeleteGoal();
  const [name, setName] = useState(goal?.name ?? '');
  const [targetPaise, setTargetPaise] = useState<number | null>(goal ? goal.targetPaise : null);
  // Optional amounts: seed a stored 0 as empty so the field shows its "0" placeholder, as before.
  const [savedPaise, setSavedPaise] = useState<number | null>(goal && goal.savedPaise ? goal.savedPaise : null);
  const [monthlyPaise, setMonthlyPaise] = useState<number | null>(
    goal && goal.monthlyContributionPaise ? goal.monthlyContributionPaise : null,
  );
  const [savedInvalid, setSavedInvalid] = useState(false);
  const [monthlyInvalid, setMonthlyInvalid] = useState(false);
  const [date, setDate] = useState(goal?.targetDate ?? '');
  const [local, setLocal] = useState<Record<string, string>>({});

  const server = save.error instanceof ApiError ? save.error.fieldErrors : {};
  const err = (k: string) => local[k] ?? server[k];

  const submit = () => {
    const errs: Record<string, string> = {};
    if (!name.trim()) errs.name = 'Give the goal a name.';
    if (targetPaise == null || targetPaise <= 0) errs.targetPaise = 'Enter a target amount.';
    if (date.trim() && !DATE_RE.test(date.trim())) errs.targetDate = 'Use YYYY-MM-DD.';
    setLocal(errs);
    // Invalid optional amounts already show the field's inline hint; just block the save.
    if (Object.keys(errs).length > 0 || savedInvalid || monthlyInvalid) return;
    const targetDate = date.trim() || null;
    const base: GoalInput = {
      name: name.trim(),
      targetPaise: targetPaise!,
      savedPaise: savedPaise ?? 0,
      monthlyContributionPaise: monthlyPaise ?? 0,
      targetDate,
    };
    // PATCH treats null as "unchanged", so emptying the date on edit needs an explicit clear.
    const body: GoalInput | PatchGoal = goal?.targetDate && !targetDate ? { ...base, clearTargetDate: true } : base;
    save.mutate({ id: goal?.id ?? null, body }, { onSuccess: onDone });
  };

  const remove = () => {
    if (!goal) return;
    Alert.alert(`Delete “${goal.name}”?`, 'This can’t be undone.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => del.mutate(goal.id, { onSuccess: onDone }) },
    ]);
  };

  const generic = (save.error && Object.keys(server).length === 0) || del.error ? errorMessage(save.error ?? del.error) : null;

  return (
    <View style={styles.form}>
      <TextField label="Name" value={name} onChangeText={setName} placeholder="e.g. Goa trip" error={err('name')} />
      <AmountInput label="Target" prefix="₹" valuePaise={targetPaise} onChangeValuePaise={setTargetPaise} error={err('targetPaise')} />
      <AmountInput
        label="Saved so far"
        prefix="₹"
        valuePaise={savedPaise}
        onChangeValuePaise={setSavedPaise}
        onInvalidChange={setSavedInvalid}
        allowZero
        placeholder="0"
        error={server.savedPaise}
      />
      <AmountInput
        label="Monthly contribution"
        prefix="₹"
        valuePaise={monthlyPaise}
        onChangeValuePaise={setMonthlyPaise}
        onInvalidChange={setMonthlyInvalid}
        allowZero
        placeholder="0"
        error={server.monthlyContributionPaise}
      />
      <TextField
        label="Target date (optional)"
        value={date}
        onChangeText={setDate}
        placeholder="YYYY-MM-DD"
        keyboardType="numbers-and-punctuation"
        autoCapitalize="none"
        error={err('targetDate')}
      />
      {generic ? <Text style={styles.error}>{generic}</Text> : null}
      <PrimaryButton label={goal ? 'Save goal' : 'Add goal'} onPress={submit} loading={save.isPending} />
      {goal ? <PrimaryButton label="Delete goal" variant="outline" onPress={remove} loading={del.isPending} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacing.md },
  error: { fontSize: 13, color: colors.verdict.red.text },
});
