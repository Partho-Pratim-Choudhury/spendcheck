import { type ComponentRef, useRef, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PrimaryButton } from '@/components/PrimaryButton';
import { API_URL, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { colors, radii, spacing, typography } from '@/theme';

export default function LoginScreen() {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const passwordRef = useRef<ComponentRef<typeof TextInput>>(null);

  const configError = API_URL
    ? null
    : 'EXPO_PUBLIC_API_URL is not set. Copy .env.example to .env, set your laptop IP, then restart Expo (npx expo start -c).';

  const canSubmit = !configError && email.trim().length > 0 && password.length > 0 && !submitting;

  async function onSubmit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
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
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
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

            {(configError ?? error) ? (
              <Text style={styles.error} accessibilityRole="alert" accessibilityLiveRegion="polite">
                {configError ?? error}
              </Text>
            ) : null}

            <PrimaryButton label="Sign in" onPress={onSubmit} loading={submitting} disabled={!canSubmit} />
          </View>

          <Text style={styles.apiUrl} selectable>
            API: {API_URL ?? 'not configured'}
          </Text>
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
  apiUrl: { ...typography.caption, textAlign: 'center' },
});
