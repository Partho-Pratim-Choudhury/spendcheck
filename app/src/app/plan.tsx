import { Feather } from '@expo/vector-icons';
import { useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { decorative } from '@/components/a11y';
import { AmountInput, useAmountField } from '@/components/AmountInput';
import { Card, CardTitle } from '@/components/Card';
import { GoalSheet } from '@/components/GoalSheet';
import { PrimaryButton } from '@/components/PrimaryButton';
import { ProgressBar } from '@/components/ProgressBar';
import { Sheet } from '@/components/Sheet';
import { ErrorState, LoadingState, errorMessage } from '@/components/States';
import { TextField } from '@/components/TextField';
import { ApiError, checkServerHealth } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { daysBetween, ordinal, periodLabel, shortDate } from '@/lib/dates';
import { formatINR } from '@/lib/money';
import { rolloverConfirmText } from '@/lib/rollover';
import { normaliseServerUrl, SERVER_URL_PLACEHOLDER } from '@/lib/serverUrl';
import {
  fetchRolloverPreview,
  useCategories,
  useCreateCategory,
  useCurrentPlan,
  useGoals,
  useRollover,
  useSavePlan,
  useSetSalaryDay,
} from '@/lib/queries';
import type { Category, Goal, Plan, PutPlanRequest } from '@/lib/types';
import { colors, radii, spacing, typography } from '@/theme';

export default function PlanScreen() {
  const plan = useCurrentPlan();
  const categories = useCategories();
  const { user, logout, serverUrl } = useAuth();
  const [serverSheet, setServerSheet] = useState(false);
  const insets = useSafeAreaInsets();
  // KeyboardAvoidingView measures itself relative to the screen, not the window; offset by the
  // stack header so the overlap with the keyboard isn't under-counted (inputs left hidden).
  const headerHeight = useHeaderHeight();

  const loading = plan.isPending || categories.isPending;
  const error = plan.error ?? categories.error;

  return (
    <KeyboardAvoidingView behavior="padding" keyboardVerticalOffset={headerHeight} style={styles.flex}>
      <ScrollView
        style={styles.screen}
        contentContainerStyle={[styles.content, { paddingBottom: spacing.xl + insets.bottom }]}
        keyboardShouldPersistTaps="handled">
        {loading ? (
          <LoadingState />
        ) : error ? (
          <ErrorState
            error={error}
            onRetry={() => {
              void plan.refetch();
              void categories.refetch();
            }}
          />
        ) : (
          <PlanForm key={plan.data?.id ?? 'new'} plan={plan.data ?? null} categories={categories.data ?? []} />
        )}
        <View style={styles.account}>
          {user ? <Text style={styles.signedInAs}>Signed in as {user.email}</Text> : null}
          <PrimaryButton
            label="Log out"
            variant="outline"
            // Reminders are cancelled by the root layout on every sign-out.
            onPress={() => void logout()}
          />
          <View style={styles.serverRow}>
            <Text style={styles.serverUrl} numberOfLines={1} selectable>
              Server: {serverUrl ?? 'not set'}
            </Text>
            <Pressable onPress={() => setServerSheet(true)} hitSlop={8} accessibilityRole="button" accessibilityLabel="Change server">
              <Text style={styles.changeServer}>Change server</Text>
            </Pressable>
          </View>
        </View>
        <ServerSheet visible={serverSheet} onClose={() => setServerSheet(false)} />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

/** Switch the backend origin. Validates + tests, then saves and signs out so the user re-auths there. */
function ServerSheet({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const { serverUrl, saveServerUrl, logout } = useAuth();
  const [input, setInput] = useState(serverUrl ?? '');
  const [error, setError] = useState<string | null>(null);
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; message: string } | null>(null);

  const close = () => {
    setInput(serverUrl ?? '');
    setError(null);
    setTestResult(null);
    setTesting(false);
    setSaving(false);
    onClose();
  };

  const onChange = (text: string) => {
    setInput(text);
    setError(null);
    setTestResult(null);
  };

  /** Normalise the field; on failure show the inline error. */
  const resolve = (): string | null => {
    const r = normaliseServerUrl(input);
    if (!r.ok) {
      setError(r.error);
      return null;
    }
    setInput(r.url);
    setError(null);
    return r.url;
  };

  const onTest = async () => {
    const url = resolve();
    if (!url) return;
    setTesting(true);
    setTestResult(null);
    const reachable = await checkServerHealth(url);
    setTestResult(reachable ? { ok: true, message: 'Server reachable' } : { ok: false, message: `Can't reach ${url}` });
    setTesting(false);
  };

  const onSave = async () => {
    const url = resolve();
    if (!url) return;
    setSaving(true);
    await saveServerUrl(url); // never throws
    onClose();
    // Sign out so the next sign-in authenticates against the new server; this unmounts the screen.
    await logout();
  };

  return (
    <Sheet visible={visible} title="Change server" onClose={close}>
      <Text style={styles.help}>Switching server signs you out, so you sign in again against it.</Text>
      <TextField
        label="Server URL"
        value={input}
        onChangeText={onChange}
        placeholder={SERVER_URL_PLACEHOLDER}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="url"
        inputMode="url"
        returnKeyType="done"
        editable={!saving}
        error={error ?? undefined}
      />
      {!error && testResult ? (
        <Text style={testResult.ok ? styles.testOk : styles.error} accessibilityLiveRegion="polite">
          {testResult.message}
        </Text>
      ) : null}
      <PrimaryButton
        label="Test"
        variant="secondary"
        onPress={() => void onTest()}
        loading={testing}
        disabled={testing || saving}
        accessibilityLabel="Test server connection"
      />
      <PrimaryButton label="Save" onPress={() => void onSave()} loading={saving} disabled={testing || saving} />
    </Sheet>
  );
}

/** Category id → limit in paise. `null` means the field is empty (or cleared); 0 is a real, kept value. */
type LimitMap = Record<string, number | null>;
/** Category id → whether its amount field currently holds invalid text (blocks save). */
type InvalidMap = Record<string, boolean>;

function initialLimits(plan: Plan | null): LimitMap {
  const m: LimitMap = {};
  // A stored 0 seeds as empty (null), matching the old blank-zero behaviour.
  for (const l of plan?.limits ?? []) m[String(l.categoryId)] = l.limitPaise || null;
  return m;
}

function PlanForm({ plan, categories }: { plan: Plan | null; categories: Category[] }) {
  const save = useSavePlan();
  const rollover = useRollover();
  const { user } = useAuth();
  const [previewing, setPreviewing] = useState(false);
  // Amounts are held in paise; `null` = empty, a number (incl. 0) = a value the user typed.
  const [income, setIncome] = useState<number | null>(plan && plan.incomePaise ? plan.incomePaise : null);
  const [savings, setSavings] = useState<number | null>(plan && plan.savingsTargetPaise ? plan.savingsTargetPaise : null);
  const [limits, setLimits] = useState<LimitMap>(() => initialLimits(plan));
  // Fields holding invalid expressions (e.g. "5000+") — tracked so save can be blocked.
  const [incomeInvalid, setIncomeInvalid] = useState(false);
  const [savingsInvalid, setSavingsInvalid] = useState(false);
  const [limitsInvalid, setLimitsInvalid] = useState<InvalidMap>({});
  const [localErrors, setLocalErrors] = useState<Record<string, string>>({});
  const [addingFixed, setAddingFixed] = useState(false);
  const [saved, setSaved] = useState(false);

  const active = categories.filter((c) => !c.archived);
  const fixed = active.filter((c) => c.kind === 'FIXED');
  const variable = active.filter((c) => c.kind !== 'FIXED');

  const val = (id: Category['id']) => limits[String(id)] ?? 0;
  const incomePaise = income ?? 0;
  const savingsPaise = savings ?? 0;
  const fixedTotal = fixed.reduce((s, c) => s + val(c.id), 0);
  const discretionary = incomePaise - fixedTotal - savingsPaise;
  const days = plan ? daysBetween(plan.periodStart, plan.periodEnd) + 1 : 30;
  const perDay = discretionary > 0 ? Math.floor(discretionary / days) : 0;
  const allocated = variable.reduce((s, c) => s + val(c.id), 0);
  const allocTone =
    allocated === discretionary
      ? colors.verdict.green
      : allocated < discretionary
        ? colors.verdict.amber
        : colors.verdict.red;

  // Map server field errors ("limits[2].limitPaise") back to the category that was sent at that index.
  const [sentIds, setSentIds] = useState<string[]>([]);
  const server = save.error instanceof ApiError ? save.error.fieldErrors : {};
  const limitError = (id: Category['id']) => {
    const i = sentIds.indexOf(String(id));
    if (i < 0) return undefined;
    return server[`limits[${i}].limitPaise`] ?? server[`limits[${i}].categoryId`] ?? server[`limits[${i}]`];
  };
  const fieldErr = (k: string) => localErrors[k] ?? server[k];
  const otherServerErrors = Object.entries(server).filter(
    ([k]) => k !== 'incomePaise' && k !== 'savingsTargetPaise' && !k.startsWith('limits['),
  );

  const setLimit = (id: Category['id'], paise: number | null) => {
    setSaved(false);
    setLimits((m) => ({ ...m, [String(id)]: paise }));
  };
  const setLimitInvalid = (id: Category['id'], invalid: boolean) =>
    setLimitsInvalid((m) => ({ ...m, [String(id)]: invalid }));

  const submit = () => {
    const errs: Record<string, string> = {};
    // Invalid fields show their own inline "Enter a valid amount" hint; an empty income still needs a prompt.
    if (!incomeInvalid && income == null) errs.incomePaise = 'Enter your monthly income.';
    if (!savingsInvalid && discretionary < 0 && incomePaise > 0)
      errs.savingsTargetPaise = 'Fixed costs + savings are more than your income.';
    setLocalErrors(errs);
    const anyLimitInvalid = active.some((c) => limitsInvalid[String(c.id)]);
    if (Object.keys(errs).length > 0 || incomeInvalid || savingsInvalid || anyLimitInvalid) return;

    const sent = active.filter((c) => limits[String(c.id)] != null);
    setSentIds(sent.map((c) => String(c.id)));
    const body: PutPlanRequest = {
      incomePaise,
      savingsTargetPaise: savingsPaise,
      limits: sent.map((c) => ({ categoryId: c.id, limitPaise: val(c.id) })),
    };
    save.mutate(body, { onSuccess: () => setSaved(true) });
  };

  const confirmRollover = async () => {
    if (previewing) return;
    setPreviewing(true);
    const preview = await fetchRolloverPreview().catch(() => null);
    setPreviewing(false);
    const extends_ = preview?.extendsCurrent === true;
    Alert.alert(
      extends_ ? 'Extend this period?' : 'Start a new month today?',
      rolloverConfirmText(preview, user?.salaryDay),
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: extends_ ? 'Extend period' : 'Start new month',
          onPress: () =>
            rollover.mutate(undefined, {
              onError: (e) => Alert.alert('Couldn’t start a new month', errorMessage(e)),
            }),
        },
      ],
    );
  };

  return (
    <>
      <Card>
        <CardTitle>{plan ? `Current period · ${periodLabel(plan.periodStart, plan.periodEnd)}` : 'Set up your plan'}</CardTitle>
        <AmountInput
          label="Monthly income"
          prefix="₹"
          valuePaise={income}
          onChangeValuePaise={(p) => {
            setSaved(false);
            setIncome(p);
          }}
          onInvalidChange={setIncomeInvalid}
          placeholder="e.g. 85000"
          error={fieldErr('incomePaise')}
        />
        <SalaryDayRow />
        {plan ? (
          <PrimaryButton
            label="Salary came today — start new month"
            variant="secondary"
            onPress={() => void confirmRollover()}
            loading={rollover.isPending || previewing}
          />
        ) : null}
      </Card>

      <Card>
        <CardTitle>Fixed costs</CardTitle>
        <Text style={styles.help}>Rent, EMIs, SIPs — paid every month no matter what.</Text>
        {fixed.map((c) => (
          <AmountRow
            key={String(c.id)}
            label={c.name}
            valuePaise={limits[String(c.id)] ?? null}
            onChangeValuePaise={(p) => setLimit(c.id, p)}
            onInvalidChange={(inv) => setLimitInvalid(c.id, inv)}
            error={limitError(c.id)}
          />
        ))}
        <Pressable
          onPress={() => setAddingFixed(true)}
          accessibilityRole="button"
          accessibilityLabel="Add fixed cost"
          style={styles.addRow}>
          <Feather {...decorative} name="plus" size={18} color={colors.primary} />
          <Text style={styles.link}>Add fixed cost</Text>
        </Pressable>
      </Card>

      <Card>
        <AmountInput
          label="Savings target (per month)"
          prefix="₹"
          valuePaise={savings}
          onChangeValuePaise={(p) => {
            setSaved(false);
            setSavings(p);
          }}
          onInvalidChange={setSavingsInvalid}
          allowZero
          placeholder="0"
          error={fieldErr('savingsTargetPaise')}
        />
      </Card>

      <GoalsCard />

      <View style={styles.computed} accessibilityLiveRegion="polite">
        <Text style={styles.computedLabel}>Left to spend this month</Text>
        <Text style={[styles.computedBig, discretionary < 0 && { color: '#FFB4B8' }]}>{formatINR(discretionary, 'down')}</Text>
        <Text style={styles.computedSub}>
          Per day {formatINR(perDay, 'down')} · {days} days
        </Text>
      </View>

      <Card>
        <CardTitle>Category limits</CardTitle>
        <View style={[styles.allocPill, { backgroundColor: allocTone.bg }]}>
          <Feather {...decorative}
            name={allocated === discretionary ? 'check-circle' : allocated < discretionary ? 'alert-triangle' : 'x-octagon'}
            size={14}
            color={allocTone.text}
          />
          <Text style={[styles.allocText, { color: allocTone.text }]}>
            {formatINR(allocated)} of {formatINR(Math.max(0, discretionary), 'down')} allocated
            {allocated < discretionary ? ` · ${formatINR(discretionary - allocated, 'down')} free` : ''}
            {allocated > discretionary ? ` · ${formatINR(allocated - discretionary, 'up')} over` : ''}
          </Text>
        </View>
        <View style={styles.grid}>
          {variable.map((c) => (
            <View key={String(c.id)} style={styles.gridCell}>
              <AmountRow
                label={c.name}
                caption={c.kind === 'NEED' ? 'Need' : 'Want'}
                valuePaise={limits[String(c.id)] ?? null}
                onChangeValuePaise={(p) => setLimit(c.id, p)}
                onInvalidChange={(inv) => setLimitInvalid(c.id, inv)}
                error={limitError(c.id)}
                stacked
              />
            </View>
          ))}
        </View>
      </Card>

      {otherServerErrors.length > 0 || (save.error && Object.keys(server).length === 0) ? (
        <Text style={styles.error}>
          {otherServerErrors.length > 0 ? otherServerErrors.map(([, v]) => v).join('\n') : errorMessage(save.error)}
        </Text>
      ) : null}
      <PrimaryButton label={saved ? 'Saved ✓' : 'Save plan'} onPress={submit} loading={save.isPending} />

      <AddFixedSheet
        visible={addingFixed}
        onClose={() => setAddingFixed(false)}
        onAdded={(cat, amount) => setLimit(cat.id, amount)}
      />
    </>
  );
}

function AmountRow({
  label,
  caption,
  valuePaise,
  onChangeValuePaise,
  onInvalidChange,
  error,
  stacked,
}: {
  label: string;
  caption?: string;
  valuePaise: number | null;
  onChangeValuePaise: (paise: number | null) => void;
  onInvalidChange?: (invalid: boolean) => void;
  error?: string;
  stacked?: boolean;
}) {
  // Fixed costs and category limits may be 0 (allowZero); empty is fine too, a negative never is.
  const { text, onChangeText, live, hint } = useAmountField({
    valuePaise,
    onChangeValuePaise,
    onInvalidChange,
    allowZero: true,
  });
  const shownError = error ?? hint ?? undefined;
  return (
    <View style={stacked ? styles.stacked : styles.amountRow}>
      <View style={styles.amountLabelWrap}>
        <Text style={styles.amountLabel} numberOfLines={1}>
          {label}
        </Text>
        {caption ? <Text style={styles.caption}>{caption}</Text> : null}
      </View>
      <View style={stacked ? styles.fieldColStacked : styles.fieldCol}>
        <View style={[styles.amountBox, !!shownError && styles.amountBoxError]}>
          <Text style={styles.prefix}>₹</Text>
          <TextInput
            value={text}
            onChangeText={onChangeText}
            // Operators need a keyboard that shows them: iOS numbers-and-punctuation, else the default.
            keyboardType={Platform.select({ ios: 'numbers-and-punctuation', android: 'default', default: 'default' })}
            placeholder="0"
            placeholderTextColor={colors.muted}
            accessibilityLabel={`${label} amount`}
            style={styles.amountInput}
          />
        </View>
        {live ? <Text style={styles.liveRight}>{live}</Text> : null}
      </View>
      {shownError ? <Text style={styles.error}>{shownError}</Text> : null}
    </View>
  );
}

function SalaryDayRow() {
  const { user, updateUser } = useAuth();
  const setDay = useSetSalaryDay();
  const [open, setOpen] = useState(false);
  const current = user?.salaryDay ?? null;

  return (
    <>
      <Pressable
        onPress={() => setOpen(true)}
        accessibilityRole="button"
        accessibilityLabel={`Salary day ${current ?? 'not set'}. Change`}
        style={styles.salaryRow}>
        <View>
          <Text style={styles.caption}>Salary day</Text>
          <Text style={styles.salaryValue}>{current ? `${ordinal(current)} of every month` : 'Not set'}</Text>
        </View>
        <Feather {...decorative} name="chevron-right" size={20} color={colors.muted} />
      </Pressable>
      <Sheet visible={open} title="Salary day" onClose={() => setOpen(false)}>
        <Text style={styles.help}>Your month runs from salary day to the day before the next one. Affects future periods.</Text>
        <View style={styles.dayGrid}>
          {Array.from({ length: 31 }, (_, i) => i + 1).map((d) => {
            const selected = d === current;
            return (
              <Pressable
                key={d}
                disabled={setDay.isPending}
                onPress={() =>
                  setDay.mutate(d, {
                    onSuccess: (u) => {
                      updateUser(u);
                      setOpen(false);
                    },
                  })
                }
                accessibilityRole="button"
                accessibilityState={{ selected, disabled: setDay.isPending }}
                accessibilityLabel={`Day ${d}`}
                style={[styles.dayCell, selected && styles.dayCellSelected, setDay.isPending && styles.dim]}>
                <Text style={[styles.dayText, selected && styles.dayTextSelected]}>{d}</Text>
              </Pressable>
            );
          })}
        </View>
        {setDay.error ? <Text style={styles.error}>{errorMessage(setDay.error)}</Text> : null}
      </Sheet>
    </>
  );
}

function GoalsCard() {
  const goals = useGoals();
  const [editing, setEditing] = useState<Goal | 'new' | null>(null);
  return (
    <Card>
      <CardTitle>Goals</CardTitle>
      {goals.isError ? <Text style={styles.error}>{errorMessage(goals.error)}</Text> : null}
      {(goals.data ?? []).map((g) => (
        <Pressable
          key={g.id}
          onPress={() => setEditing(g)}
          accessibilityRole="button"
          accessibilityLabel={`${g.name}, ${formatINR(g.savedPaise, 'down')} of ${formatINR(g.targetPaise)}. Edit`}
          style={styles.goalRow}>
          <View style={styles.goalTop}>
            <Text style={styles.amountLabel}>{g.name}</Text>
            <Text style={[styles.caption, typography.amount]}>
              {formatINR(g.savedPaise, 'down')} / {formatINR(g.targetPaise)}
            </Text>
          </View>
          <ProgressBar value={g.progressPct / 100} color={colors.verdict.green.text} height={6} />
          <Text style={styles.caption}>
            {formatINR(g.monthlyContributionPaise)}/month
            {g.onTrackDate ? ` · on track for ${shortDate(g.onTrackDate)}` : ''}
            {g.targetDate ? ` · target ${shortDate(g.targetDate)}` : ''}
          </Text>
        </Pressable>
      ))}
      <Pressable onPress={() => setEditing('new')} accessibilityRole="button" accessibilityLabel="Add goal" style={styles.addRow}>
        <Feather {...decorative} name="plus" size={18} color={colors.primary} />
        <Text style={styles.link}>Add goal</Text>
      </Pressable>
      <GoalSheet goal={editing} onClose={() => setEditing(null)} />
    </Card>
  );
}

function AddFixedSheet({
  visible,
  onClose,
  onAdded,
}: {
  visible: boolean;
  onClose: () => void;
  onAdded: (c: Category, amountPaise: number) => void;
}) {
  const create = useCreateCategory();
  const [name, setName] = useState('');
  const [amountPaise, setAmountPaise] = useState<number | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const server = create.error instanceof ApiError ? create.error.fieldErrors : {};

  const close = () => {
    setName('');
    setAmountPaise(null);
    setErr(null);
    create.reset();
    onClose();
  };

  const submit = () => {
    if (!name.trim()) return setErr('Name it, e.g. Rent.');
    if (amountPaise == null || amountPaise <= 0) return setErr('Enter the monthly amount.');
    setErr(null);
    create.mutate(
      { name: name.trim(), kind: 'FIXED' },
      {
        onSuccess: (c) => {
          onAdded(c, amountPaise);
          close();
        },
      },
    );
  };

  return (
    <Sheet visible={visible} title="Add fixed cost" onClose={close}>
      <TextField label="Name" value={name} onChangeText={setName} placeholder="e.g. Rent" error={server.name} />
      <AmountInput
        // This sheet stays mounted; remount the field each open so it starts from the reset value.
        key={visible ? 'open' : 'closed'}
        label="Monthly amount"
        prefix="₹"
        valuePaise={amountPaise}
        onChangeValuePaise={(p) => {
          setAmountPaise(p);
          setErr(null);
        }}
      />
      {err || (create.error && !server.name) ? <Text style={styles.error}>{err ?? errorMessage(create.error)}</Text> : null}
      <PrimaryButton label="Add" onPress={submit} loading={create.isPending} />
      <Text style={styles.help}>Remember to tap “Save plan” afterwards.</Text>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.md },
  help: { fontSize: 13, color: colors.muted, lineHeight: 18 },
  caption: typography.caption,
  link: { color: colors.primary, fontSize: 15, fontWeight: '600' },
  error: { fontSize: 13, color: colors.verdict.red.text },
  addRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, minHeight: 44 },
  amountRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, flexWrap: 'wrap' },
  stacked: { gap: spacing.xs },
  amountLabelWrap: { flex: 1, minWidth: 80 },
  amountLabel: { fontSize: 15, color: colors.ink, fontWeight: '500' },
  fieldCol: { width: 140, gap: 2 },
  fieldColStacked: { width: '100%', gap: 2 },
  amountBox: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 44,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    backgroundColor: colors.surface,
  },
  amountBoxError: { borderColor: colors.verdict.red.text },
  liveRight: { fontSize: 13, color: colors.muted, textAlign: 'right', fontVariant: ['tabular-nums'] },
  prefix: { fontSize: 15, color: colors.muted, marginRight: 4 },
  amountInput: { flex: 1, fontSize: 16, color: colors.ink, textAlign: 'right', fontVariant: ['tabular-nums'] },
  salaryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: 48,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  salaryValue: { fontSize: 16, color: colors.ink },
  dayGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  dayCell: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dayCellSelected: { backgroundColor: colors.primary, borderColor: colors.primary },
  dayText: { fontSize: 15, color: colors.ink, fontVariant: ['tabular-nums'] },
  dayTextSelected: { color: colors.onPrimary, fontWeight: '700' },
  dim: { opacity: 0.5 },
  computed: { backgroundColor: colors.heroBg, borderRadius: radii.lg, padding: spacing.lg, gap: 4 },
  computedLabel: { color: colors.heroMuted, fontSize: 14 },
  computedBig: { color: colors.heroAccent, fontSize: 32, fontWeight: '700', fontVariant: ['tabular-nums'] },
  computedSub: { color: colors.heroMuted, fontSize: 14, fontVariant: ['tabular-nums'] },
  allocPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  allocText: { fontSize: 13, fontWeight: '600', flex: 1, fontVariant: ['tabular-nums'] },
  grid: { flexDirection: 'row', flexWrap: 'wrap', marginHorizontal: -spacing.xs },
  gridCell: { width: '50%', paddingHorizontal: spacing.xs, paddingBottom: spacing.md },
  goalRow: { gap: 6, paddingVertical: spacing.xs, minHeight: 44 },
  goalTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', gap: spacing.sm },
  account: { marginTop: spacing.xl, gap: spacing.sm },
  signedInAs: { fontSize: 14, color: colors.muted, textAlign: 'center' },
  serverRow: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: spacing.sm },
  serverUrl: { ...typography.caption, flexShrink: 1 },
  changeServer: { fontSize: 12, color: colors.primary, fontWeight: '600' },
  testOk: { fontSize: 13, color: colors.verdict.green.text },
});
