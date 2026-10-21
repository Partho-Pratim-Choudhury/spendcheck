import { useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';

import { ApiError } from '@/lib/api';
import { paiseToInput, parseRupees } from '@/lib/money';
import { useDeleteGoal, useSaveGoal } from '@/lib/queries';
import type { Goal, GoalInput } from '@/lib/types';
import { colors, spacing } from '@/theme';

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
  const [target, setTarget] = useState(goal ? paiseToInput(goal.targetPaise) : '');
  const [saved, setSaved] = useState(goal ? paiseToInput(goal.savedPaise, true) : '');
  const [monthly, setMonthly] = useState(goal ? paiseToInput(goal.monthlyContributionPaise, true) : '');
  const [date, setDate] = useState(goal?.targetDate ?? '');
  const [local, setLocal] = useState<Record<string, string>>({});

  const server = save.error instanceof ApiError ? save.error.fieldErrors : {};
  const err = (k: string) => local[k] ?? server[k];

  const submit = () => {
    const errs: Record<string, string> = {};
    const targetPaise = parseRupees(target);
    const savedPaise = saved.trim() ? parseRupees(saved) : 0;
    const monthlyPaise = monthly.trim() ? parseRupees(monthly) : 0;
    if (!name.trim()) errs.name = 'Give the goal a name.';
    if (!targetPaise || targetPaise <= 0) errs.targetPaise = 'Enter a target amount.';
    if (savedPaise === null) errs.savedPaise = 'Enter a valid amount.';
    if (monthlyPaise === null) errs.monthlyContributionPaise = 'Enter a valid amount.';
    if (date.trim() && !DATE_RE.test(date.trim())) errs.targetDate = 'Use YYYY-MM-DD.';
    setLocal(errs);
    if (Object.keys(errs).length > 0) return;
    const body: GoalInput = {
      name: name.trim(),
      targetPaise: targetPaise!,
      savedPaise: savedPaise!,
      monthlyContributionPaise: monthlyPaise!,
      targetDate: date.trim() || null,
    };
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
      <TextField label="Target" prefix="₹" value={target} onChangeText={setTarget} keyboardType="decimal-pad" error={err('targetPaise')} />
      <TextField label="Saved so far" prefix="₹" value={saved} onChangeText={setSaved} keyboardType="decimal-pad" placeholder="0" error={err('savedPaise')} />
      <TextField
        label="Monthly contribution"
        prefix="₹"
        value={monthly}
        onChangeText={setMonthly}
        keyboardType="decimal-pad"
        placeholder="0"
        error={err('monthlyContributionPaise')}
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
