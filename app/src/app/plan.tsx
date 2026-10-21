import { StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Placeholder } from '@/components/Placeholder';
import { PrimaryButton } from '@/components/PrimaryButton';
import { useAuth } from '@/lib/auth';
import { colors, spacing } from '@/theme';

export default function PlanScreen() {
  const { user, logout } = useAuth();
  const insets = useSafeAreaInsets();
  return (
    <Placeholder title="Plan" note="Coming in week 2: plan form">
      <View style={[styles.account, { paddingBottom: insets.bottom }]}>
        {user ? <Text style={styles.signedInAs}>Signed in as {user.email}</Text> : null}
        <PrimaryButton label="Log out" variant="outline" onPress={() => void logout()} />
      </View>
    </Placeholder>
  );
}

const styles = StyleSheet.create({
  account: { marginTop: 'auto', gap: spacing.sm },
  signedInAs: { fontSize: 14, color: colors.muted, textAlign: 'center' },
});
