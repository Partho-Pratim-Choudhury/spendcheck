import { Feather } from '@expo/vector-icons';
import { router } from 'expo-router';
import { type ComponentRef, useEffect, useRef, useState } from 'react';
import {
  Keyboard,
  KeyboardAvoidingView,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { decorative } from '@/components/a11y';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Sheet } from '@/components/Sheet';
import { ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { ordinal } from '@/lib/dates';
import { colors, radii, spacing, typography } from '@/theme';

const DAYS = Array.from({ length: 31 }, (_, i) => i + 1);

export default function RegisterScreen() {
  const { register, serverUrl } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [salaryDay, setSalaryDay] = useState<number | null>(null);
  const [inviteCode, setInviteCode] = useState('');
  const [dayPickerOpen, setDayPickerOpen] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const emailRef = useRef<ComponentRef<typeof TextInput>>(null);
  const passwordRef = useRef<ComponentRef<typeof TextInput>>(null);
  const inviteRef = useRef<ComponentRef<typeof TextInput>>(null);
  const scrollRef = useRef<ComponentRef<typeof ScrollView>>(null);

  // The ScrollView keeps a focused input visible, but the Create-account button sits below the last
  // field — so while the invite code (the bottom field) is focused, scroll the end fully into view.
  const inviteFocused = useRef(false);
  useEffect(() => {
    const sub = Keyboard.addListener('keyboardDidShow', () => {
      if (inviteFocused.current) scrollRef.current?.scrollToEnd({ animated: true });
    });
    return () => sub.remove();
  }, []);

  const canSubmit =
    name.trim().length > 0 &&
    email.trim().length > 0 &&
    password.length > 0 &&
    salaryDay !== null &&
    inviteCode.trim().length > 0 &&
    !submitting;

  function goToLogin() {
    if (router.canGoBack()) router.back();
    else router.replace('/login');
  }

  async function onSubmit() {
    if (!canSubmit || salaryDay === null) return;
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    try {
      await register({ email, password, name, salaryDay, inviteCode });
    } catch (e) {
      if (e instanceof ApiError) {
        setFieldErrors(e.fieldErrors);
        // 400 populates per-field messages (shown inline); a 403/409 (bad/closed invite, email taken)
        // or a network error has no field map, so surface the server's detail at the top instead.
        if (Object.keys(e.fieldErrors).length === 0) {
          setError(e.detail || 'Could not create your account. Please try again.');
        }
      } else {
        setError('Something went wrong. Please try again.');
      }
      setSubmitting(false);
      return;
    }
    // Signed in — the root guard swaps to the tabs automatically. Keep the button disabled through
    // the transition by leaving `submitting` true.
  }

  return (
    <SafeAreaView style={styles.safe}>
      {/* 'padding' on both platforms (as login/chat/sheets do): under Android edge-to-edge the window
          isn't resized for the keyboard, so this view must make room. */}
      <KeyboardAvoidingView style={styles.flex} behavior="padding">
        <ScrollView ref={scrollRef} contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
          <View style={styles.header}>
            <Text style={typography.title} accessibilityRole="header">
              Create account
            </Text>
            <Text style={styles.subtitle}>Join SpendCheck with your invite code.</Text>
          </View>

          <View style={styles.card}>
            <View style={styles.field}>
              <Text style={typography.label} nativeID="nameLabel">
                Name
              </Text>
              <TextInput
                style={[styles.input, fieldErrors.name ? styles.inputError : null]}
                value={name}
                onChangeText={setName}
                placeholder="Your name"
                placeholderTextColor={colors.muted}
                autoCapitalize="words"
                autoComplete="name"
                textContentType="name"
                returnKeyType="next"
                onSubmitEditing={() => emailRef.current?.focus()}
                submitBehavior="submit"
                accessibilityLabel="Name"
                accessibilityLabelledBy="nameLabel"
                editable={!submitting}
              />
              {fieldErrors.name ? (
                <Text style={styles.fieldError} accessibilityRole="alert">
                  {fieldErrors.name}
                </Text>
              ) : null}
            </View>

            <View style={styles.field}>
              <Text style={typography.label} nativeID="emailLabel">
                Email
              </Text>
              <TextInput
                ref={emailRef}
                style={[styles.input, fieldErrors.email ? styles.inputError : null]}
                value={email}
                onChangeText={setEmail}
                placeholder="you@example.com"
                placeholderTextColor={colors.muted}
                autoCapitalize="none"
                autoCorrect={false}
                autoComplete="email"
                textContentType="emailAddress"
                keyboardType="email-address"
                inputMode="email"
                returnKeyType="next"
                onSubmitEditing={() => passwordRef.current?.focus()}
                submitBehavior="submit"
                accessibilityLabel="Email"
                accessibilityLabelledBy="emailLabel"
                editable={!submitting}
              />
              {fieldErrors.email ? (
                <Text style={styles.fieldError} accessibilityRole="alert">
                  {fieldErrors.email}
                </Text>
              ) : null}
            </View>

            <View style={styles.field}>
              <Text style={typography.label} nativeID="passwordLabel">
                Password
              </Text>
              <TextInput
                ref={passwordRef}
                style={[styles.input, fieldErrors.password ? styles.inputError : null]}
                value={password}
                onChangeText={setPassword}
                placeholder="At least 8 characters"
                placeholderTextColor={colors.muted}
                secureTextEntry
                autoCapitalize="none"
                autoCorrect={false}
                autoComplete="new-password"
                textContentType="newPassword"
                returnKeyType="next"
                onSubmitEditing={() => inviteRef.current?.focus()}
                submitBehavior="submit"
                accessibilityLabel="Password"
                accessibilityLabelledBy="passwordLabel"
                editable={!submitting}
              />
              {fieldErrors.password ? (
                <Text style={styles.fieldError} accessibilityRole="alert">
                  {fieldErrors.password}
                </Text>
              ) : (
                <Text style={styles.hint}>Use at least 8 characters.</Text>
              )}
            </View>

            <View style={styles.field}>
              <Text style={typography.label} nativeID="salaryDayLabel">
                Salary day
              </Text>
              <Pressable
                onPress={() => {
                  Keyboard.dismiss();
                  setDayPickerOpen(true);
                }}
                disabled={submitting}
                accessibilityRole="button"
                accessibilityLabelledBy="salaryDayLabel"
                accessibilityLabel={`Salary day ${salaryDay ?? 'not set'}. Choose`}
                style={[styles.input, styles.selectRow, fieldErrors.salaryDay ? styles.inputError : null]}>
                <Text style={salaryDay === null ? styles.selectPlaceholder : styles.selectValue}>
                  {salaryDay === null ? 'Select day' : `${ordinal(salaryDay)} of every month`}
                </Text>
                <Feather {...decorative} name="chevron-right" size={20} color={colors.muted} />
              </Pressable>
              {fieldErrors.salaryDay ? (
                <Text style={styles.fieldError} accessibilityRole="alert">
                  {fieldErrors.salaryDay}
                </Text>
              ) : (
                <Text style={styles.hint}>The day your salary lands — your budget cycle starts then.</Text>
              )}
            </View>

            <View style={styles.field}>
              <Text style={typography.label} nativeID="inviteLabel">
                Invite code
              </Text>
              <TextInput
                ref={inviteRef}
                style={[styles.input, fieldErrors.inviteCode ? styles.inputError : null]}
                value={inviteCode}
                onChangeText={setInviteCode}
                placeholder="Invite code"
                placeholderTextColor={colors.muted}
                autoCapitalize="none"
                autoCorrect={false}
                returnKeyType="go"
                onSubmitEditing={onSubmit}
                onFocus={() => {
                  inviteFocused.current = true;
                  // Keyboard already up (moved from another field): no keyboardDidShow will fire.
                  if (Keyboard.isVisible()) scrollRef.current?.scrollToEnd({ animated: true });
                }}
                onBlur={() => {
                  inviteFocused.current = false;
                }}
                accessibilityLabel="Invite code"
                accessibilityLabelledBy="inviteLabel"
                editable={!submitting}
              />
              {fieldErrors.inviteCode ? (
                <Text style={styles.fieldError} accessibilityRole="alert">
                  {fieldErrors.inviteCode}
                </Text>
              ) : null}
            </View>

            {error ? (
              <Text style={styles.error} accessibilityRole="alert" accessibilityLiveRegion="polite">
                {error}
              </Text>
            ) : null}

            <PrimaryButton label="Create account" onPress={onSubmit} loading={submitting} disabled={!canSubmit} />
          </View>

          <Pressable
            onPress={goToLogin}
            hitSlop={8}
            disabled={submitting}
            style={styles.switchAuth}
            accessibilityRole="link"
            accessibilityLabel="Have an account? Sign in">
            <Text style={styles.switchAuthText}>
              Have an account? <Text style={styles.switchAuthLink}>Sign in</Text>
            </Text>
          </Pressable>

          <Text style={styles.serverNote} numberOfLines={1}>
            Server: {serverUrl ?? 'not set'}
          </Text>
        </ScrollView>
      </KeyboardAvoidingView>

      <Sheet visible={dayPickerOpen} title="Salary day" onClose={() => setDayPickerOpen(false)}>
        <Text style={styles.sheetHelp}>Your month runs from salary day to the day before the next one.</Text>
        <View style={styles.dayGrid}>
          {DAYS.map((d) => {
            const selected = d === salaryDay;
            return (
              <Pressable
                key={d}
                onPress={() => {
                  setSalaryDay(d);
                  setFieldErrors((prev) => {
                    if (!prev.salaryDay) return prev;
                    const { salaryDay: _removed, ...rest } = prev;
                    return rest;
                  });
                  setDayPickerOpen(false);
                }}
                accessibilityRole="button"
                accessibilityState={{ selected }}
                accessibilityLabel={`Day ${d}`}
                style={[styles.dayCell, selected && styles.dayCellSelected]}>
                <Text style={[styles.dayText, selected && styles.dayTextSelected]}>{d}</Text>
              </Pressable>
            );
          })}
        </View>
      </Sheet>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  flex: { flex: 1 },
  scroll: { flexGrow: 1, justifyContent: 'center', padding: spacing.xl, gap: spacing.xl },
  header: { gap: spacing.xs },
  subtitle: { fontSize: 16, color: colors.muted },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    gap: spacing.lg,
  },
  field: { gap: spacing.xs },
  input: {
    height: 48,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    fontSize: 16,
    color: colors.ink,
    backgroundColor: colors.surface,
  },
  selectRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  selectValue: { fontSize: 16, color: colors.ink },
  selectPlaceholder: { fontSize: 16, color: colors.muted },
  hint: { fontSize: 13, color: colors.muted },
  error: {
    color: colors.verdict.red.text,
    backgroundColor: colors.verdict.red.bg,
    borderRadius: radii.sm,
    padding: spacing.md,
    fontSize: 14,
  },
  inputError: { borderColor: colors.verdict.red.text },
  fieldError: { fontSize: 13, color: colors.verdict.red.text },
  switchAuth: { alignItems: 'center' },
  switchAuthText: { fontSize: 14, color: colors.muted },
  switchAuthLink: { color: colors.primary, fontWeight: '600' },
  serverNote: { ...typography.caption, textAlign: 'center' },
  sheetHelp: { fontSize: 14, color: colors.muted },
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
});
