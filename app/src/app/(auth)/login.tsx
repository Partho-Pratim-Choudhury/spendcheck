import { Feather } from '@expo/vector-icons';
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

import { PrimaryButton } from '@/components/PrimaryButton';
import { ApiError, checkServerHealth } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { normaliseServerUrl, SERVER_URL_PLACEHOLDER } from '@/lib/serverUrl';
import { colors, radii, spacing, typography } from '@/theme';

export default function LoginScreen() {
  const { login, serverUrl, saveServerUrl, serverSettingsRequested } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const passwordRef = useRef<ComponentRef<typeof TextInput>>(null);
  const scrollRef = useRef<ComponentRef<typeof ScrollView>>(null);
  // The ScrollView brings a focused input into view, but not the Test button below the Server
  // field — so while that field is focused, scroll the (last) server section fully into view.
  const serverFocused = useRef(false);
  useEffect(() => {
    const sub = Keyboard.addListener('keyboardDidShow', () => {
      if (serverFocused.current) scrollRef.current?.scrollToEnd({ animated: true });
    });
    return () => sub.remove();
  }, []);

  // Server settings: collapsed when a URL is already known, unless the user asked to change it.
  const [serverOpen, setServerOpen] = useState(serverSettingsRequested || !serverUrl);
  const [serverInput, setServerInput] = useState(serverUrl ?? '');
  const [serverError, setServerError] = useState<string | null>(null);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; message: string } | null>(null);

  const canSubmit = email.trim().length > 0 && password.length > 0 && !submitting;

  function onServerChange(text: string) {
    setServerInput(text);
    setServerError(null);
    setTestResult(null);
  }

  /** Normalise the field; on failure show the inline error and expand the section. */
  function resolveServerUrl(): string | null {
    const r = normaliseServerUrl(serverInput);
    if (!r.ok) {
      setServerError(r.error);
      setServerOpen(true);
      return null;
    }
    setServerInput(r.url);
    setServerError(null);
    return r.url;
  }

  async function onTest() {
    const url = resolveServerUrl();
    if (!url) return;
    setTesting(true);
    setTestResult(null);
    const reachable = await checkServerHealth(url);
    if (reachable) await saveServerUrl(url);
    setTestResult(reachable ? { ok: true, message: 'Server reachable' } : { ok: false, message: `Can't reach ${url}` });
    setTesting(false);
  }

  async function onSubmit() {
    if (!canSubmit) return;
    const url = resolveServerUrl();
    if (!url) return;
    setSubmitting(true);
    setError(null);
    try {
      if (url !== serverUrl) await saveServerUrl(url);
      await login(email, password);
      // On success the root guard swaps to the tabs automatically.
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.status === 401 ? (e.detail || 'Incorrect email or password.') : e.detail);
      } else {
        setError('Something went wrong. Please try again.');
      }
      setSubmitting(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      {/* 'padding' on both platforms, as the chat screen and sheets already use: under Android
          edge-to-edge the window isn't resized for the keyboard, so this view must make room. */}
      <KeyboardAvoidingView style={styles.flex} behavior="padding">
        <ScrollView ref={scrollRef} contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
          <View style={styles.header}>
            <Text style={typography.title} accessibilityRole="header">
              SpendCheck
            </Text>
            <Text style={styles.subtitle}>Sign in to track this month&apos;s spending.</Text>
          </View>

          <View style={styles.card}>
            <View style={styles.field}>
              <Text style={typography.label} nativeID="emailLabel">
                Email
              </Text>
              <TextInput
                style={styles.input}
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
            </View>

            <View style={styles.field}>
              <Text style={typography.label} nativeID="passwordLabel">
                Password
              </Text>
              <TextInput
                ref={passwordRef}
                style={styles.input}
                value={password}
                onChangeText={setPassword}
                placeholder="Password"
                placeholderTextColor={colors.muted}
                secureTextEntry
                autoCapitalize="none"
                autoCorrect={false}
                autoComplete="current-password"
                textContentType="password"
                returnKeyType="go"
                onSubmitEditing={onSubmit}
                accessibilityLabel="Password"
                accessibilityLabelledBy="passwordLabel"
                editable={!submitting}
              />
            </View>

            {error ? (
              <Text style={styles.error} accessibilityRole="alert" accessibilityLiveRegion="polite">
                {error}
              </Text>
            ) : null}

            <PrimaryButton label="Sign in" onPress={onSubmit} loading={submitting} disabled={!canSubmit} />
          </View>

          <View style={styles.serverSection}>
            <Pressable
              onPress={() => setServerOpen((o) => !o)}
              style={styles.serverToggle}
              hitSlop={8}
              accessibilityRole="button"
              accessibilityLabel="Server settings"
              accessibilityState={{ expanded: serverOpen }}>
              <Feather name={serverOpen ? 'chevron-down' : 'chevron-right'} size={16} color={colors.muted} />
              <Text style={styles.serverToggleLabel}>Server settings</Text>
              {!serverOpen ? (
                <Text style={styles.serverSummary} numberOfLines={1}>
                  {serverUrl ?? 'not set'}
                </Text>
              ) : null}
            </Pressable>

            {serverOpen ? (
              <View style={styles.card}>
                <View style={styles.field}>
                  <Text style={typography.label} nativeID="serverLabel">
                    Server
                  </Text>
                  <TextInput
                    style={[styles.input, serverError ? styles.inputError : null]}
                    value={serverInput}
                    onChangeText={onServerChange}
                    placeholder={SERVER_URL_PLACEHOLDER}
                    placeholderTextColor={colors.muted}
                    autoCapitalize="none"
                    autoCorrect={false}
                    keyboardType="url"
                    inputMode="url"
                    returnKeyType="done"
                    onFocus={() => {
                      serverFocused.current = true;
                      // Keyboard already up (moved from another field): no keyboardDidShow will fire.
                      if (Keyboard.isVisible()) scrollRef.current?.scrollToEnd({ animated: true });
                    }}
                    onBlur={() => {
                      serverFocused.current = false;
                    }}
                    accessibilityLabel="Server"
                    accessibilityLabelledBy="serverLabel"
                    editable={!submitting && !testing}
                  />
                  {serverError ? (
                    <Text style={styles.fieldError} accessibilityRole="alert">
                      {serverError}
                    </Text>
                  ) : testResult ? (
                    <Text
                      style={testResult.ok ? styles.testOk : styles.fieldError}
                      accessibilityLiveRegion="polite">
                      {testResult.message}
                    </Text>
                  ) : null}
                </View>
                <PrimaryButton
                  label="Test"
                  variant="secondary"
                  onPress={onTest}
                  loading={testing}
                  disabled={submitting || testing}
                  accessibilityLabel="Test server connection"
                />
              </View>
            ) : null}
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
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
  error: {
    color: colors.verdict.red.text,
    backgroundColor: colors.verdict.red.bg,
    borderRadius: radii.sm,
    padding: spacing.md,
    fontSize: 14,
  },
  inputError: { borderColor: colors.verdict.red.text },
  fieldError: { fontSize: 13, color: colors.verdict.red.text },
  testOk: { fontSize: 13, color: colors.verdict.green.text },
  serverSection: { gap: spacing.md },
  serverToggle: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, alignSelf: 'flex-start', maxWidth: '100%' },
  serverToggleLabel: { fontSize: 14, color: colors.muted, fontWeight: '500' },
  serverSummary: { ...typography.caption, flexShrink: 1 },
});
