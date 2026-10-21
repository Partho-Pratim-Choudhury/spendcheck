import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import Svg, { G, Line, Rect } from 'react-native-svg';

import { layoutBars, linearScale, niceCeil } from '@/lib/chart';
import { formatINR } from '@/lib/money';
import { colors, spacing } from '@/theme';

interface Item {
  label: string;
  spentPaise: number;
  planPaise: number;
  highlight?: boolean;
}

/** Month-over-month spend bars with each month's plan as a dashed tick. */
export function TrendBars({ items, height = 120 }: { items: Item[]; height?: number }) {
  const [width, setWidth] = useState(0);
  const onLayout = (e: LayoutChangeEvent) => setWidth(Math.round(e.nativeEvent.layout.width));
  const max = niceCeil(Math.max(1, ...items.map((i) => Math.max(i.spentPaise, i.planPaise))));
  const bars = width > 0 ? layoutBars(items.map((i) => i.spentPaise), max, width, height) : [];
  const y = linearScale([0, max], [height, 0]);
  const a11y = items.map((i) => `${i.label}: spent ${formatINR(i.spentPaise)} of ${formatINR(i.planPaise)}`).join('. ');

  return (
    <View style={styles.wrap}>
      <View onLayout={onLayout} style={{ height }} accessible accessibilityLabel={a11y}>
        {width > 0 ? (
          <Svg width={width} height={height}>
            <Line x1={0} x2={width} y1={height - 0.5} y2={height - 0.5} stroke={colors.border} />
            {bars.map((b, i) => {
              const item = items[i];
              const over = item.spentPaise > item.planPaise;
              const fill = over ? colors.redBar : item.highlight ? colors.primary : colors.heroBar;
              const py = y(item.planPaise);
              return (
                <G key={i}>
                  <Rect x={b.x} y={b.y} width={b.width} height={Math.max(0, b.height)} rx={4} fill={fill} />
                  <Line
                    x1={b.x - 4}
                    x2={b.x + b.width + 4}
                    y1={py}
                    y2={py}
                    stroke={colors.ink}
                    strokeWidth={1.5}
                    strokeDasharray="3 3"
                  />
                </G>
              );
            })}
          </Svg>
        ) : null}
      </View>
      <View style={styles.labels}>
        {items.map((i, idx) => (
          <Text key={idx} style={[styles.label, i.highlight && styles.labelStrong]} numberOfLines={1}>
            {i.label}
          </Text>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.xs },
  labels: { flexDirection: 'row' },
  label: { flex: 1, textAlign: 'center', fontSize: 12, color: colors.muted },
  labelStrong: { color: colors.ink, fontWeight: '600' },
});
