import { type StyleProp, Text, type TextStyle } from 'react-native';

import { formatINR, type Rounding } from '@/lib/money';
import { colors, typography } from '@/theme';

interface Props {
  paise: number;
  style?: StyleProp<TextStyle>;
  prefix?: string;
  suffix?: string;
  /** Whole-rupee rounding; 'down' for amounts left, 'up' for amounts over (see Rounding). */
  round?: Rounding;
}

/** Rupee amount with tabular digits. */
export function MoneyText({ paise, style, prefix = '', suffix = '', round = 'nearest' }: Props) {
  return (
    <Text style={[{ color: colors.ink }, typography.amount, style]}>
      {prefix}
      {formatINR(paise, round)}
      {suffix}
    </Text>
  );
}
