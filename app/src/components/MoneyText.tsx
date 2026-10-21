import { type StyleProp, Text, type TextStyle } from 'react-native';

import { formatINR } from '@/lib/money';
import { colors, typography } from '@/theme';

interface Props {
  paise: number;
  style?: StyleProp<TextStyle>;
  prefix?: string;
  suffix?: string;
}

/** Rupee amount with tabular digits. */
export function MoneyText({ paise, style, prefix = '', suffix = '' }: Props) {
  return (
    <Text style={[{ color: colors.ink }, typography.amount, style]}>
      {prefix}
      {formatINR(paise)}
      {suffix}
    </Text>
  );
}
