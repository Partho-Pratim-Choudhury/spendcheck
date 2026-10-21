import { StyleSheet, View } from 'react-native';

import { usageTone } from '@/lib/insights';
import { colors } from '@/theme';

interface Props {
  /** 0..1+ (clamped for drawing). */
  value: number;
  color?: string;
  trackColor?: string;
  height?: number;
  label?: string;
}

export function ProgressBar({ value, color = colors.primary, trackColor = colors.track, height = 8, label }: Props) {
  const pct = Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0));
  return (
    <View
      style={[styles.track, { backgroundColor: trackColor, height, borderRadius: height / 2 }]}
      accessible={!!label}
      accessibilityRole="progressbar"
      accessibilityLabel={label}
      accessibilityValue={{ min: 0, max: 100, now: Math.round(pct * 100) }}>
      <View style={{ width: `${pct * 100}%`, backgroundColor: color, height, borderRadius: height / 2 }} />
    </View>
  );
}

/** Bar colour for spent vs limit: ≥100% red, ≥75% amber, else primary. */
export function usageColor(spent: number, limit: number): string {
  const tone = usageTone(spent, limit);
  return tone === 'over' ? colors.redBar : tone === 'warn' ? colors.amberBar : colors.primary;
}

/** Text colour for the same tones (meets contrast on white). */
export function usageTextColor(spent: number, limit: number): string {
  const tone = usageTone(spent, limit);
  return tone === 'over' ? colors.verdict.red.text : tone === 'warn' ? colors.verdict.amber.text : colors.ink;
}

const styles = StyleSheet.create({
  track: { width: '100%', overflow: 'hidden' },
});
