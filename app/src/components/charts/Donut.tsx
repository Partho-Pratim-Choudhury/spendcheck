import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, G } from 'react-native-svg';

import { donutSegments } from '@/lib/chart';
import { formatINR } from '@/lib/money';
import { colors } from '@/theme';

interface Props {
  values: number[];
  colorsFor: (index: number) => string;
  size?: number;
  stroke?: number;
  centerLabel: string;
  centerValue: string;
  accessibilityLabel?: string;
}

export function Donut({ values, colorsFor, size = 148, stroke = 20, centerLabel, centerValue, accessibilityLabel }: Props) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const segs = donutSegments(values, r, 2);
  return (
    <View style={{ width: size, height: size }} accessible accessibilityLabel={accessibilityLabel}>
      <Svg width={size} height={size}>
        <G rotation={-90} origin={`${size / 2}, ${size / 2}`}>
          <Circle cx={size / 2} cy={size / 2} r={r} stroke={colors.track} strokeWidth={stroke} fill="none" />
          {segs.map((s) => (
            <Circle
              key={s.index}
              cx={size / 2}
              cy={size / 2}
              r={r}
              stroke={colorsFor(s.index)}
              strokeWidth={stroke}
              strokeDasharray={`${s.length} ${c - s.length}`}
              strokeDashoffset={-s.offset}
              fill="none"
            />
          ))}
        </G>
      </Svg>
      <View style={styles.center} pointerEvents="none">
        <Text style={styles.centerLabel}>{centerLabel}</Text>
        <Text style={styles.centerValue}>{centerValue}</Text>
      </View>
    </View>
  );
}

export const donutA11y = (names: string[], values: number[]) =>
  names.map((n, i) => `${n} ${formatINR(values[i] ?? 0)}`).join(', ');

const styles = StyleSheet.create({
  center: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  centerLabel: { fontSize: 11, color: colors.muted, fontWeight: '600', letterSpacing: 0.5 },
  centerValue: { fontSize: 17, color: colors.ink, fontWeight: '700', fontVariant: ['tabular-nums'] },
});
