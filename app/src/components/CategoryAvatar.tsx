import { StyleSheet, Text, View } from 'react-native';

import { categoryColor } from '@/theme';

/** Two-letter avatar tinted with the category colour. */
export function CategoryAvatar({ name, size = 40 }: { name: string; size?: number }) {
  const c = categoryColor(name);
  const letters = name.replace(/[^A-Za-z0-9 ]/g, '').trim().slice(0, 2) || '?';
  return (
    <View
      style={[styles.avatar, { width: size, height: size, borderRadius: size / 2, backgroundColor: `${c}1F` }]}
      importantForAccessibility="no-hide-descendants">
      <Text style={[styles.text, { color: c }]}>{letters[0].toUpperCase() + (letters[1] ?? '').toLowerCase()}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  avatar: { alignItems: 'center', justifyContent: 'center' },
  text: { fontSize: 14, fontWeight: '700' },
});
