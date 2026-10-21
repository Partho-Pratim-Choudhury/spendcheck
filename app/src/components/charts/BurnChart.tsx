import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, Line, Path } from 'react-native-svg';

import { buildBurnChart } from '@/lib/chart';
import { formatINR } from '@/lib/money';
import { colors, spacing } from '@/theme';

interface Props {
  cumulative: number[];
  daysTotal: number;
  discretionaryPaise: number;
  projectedPaise: number;
  height?: number;
}

/** Cumulative spend vs straight plan line, with a dotted projection to period end. */
export function BurnChart({ cumulative, daysTotal, discretionaryPaise, projectedPaise, height = 140 }: Props) {
  const [width, setWidth] = useState(0);
  const onLayout = (e: LayoutChangeEvent) => setWidth(Math.round(e.nativeEvent.layout.width));
  const over = projectedPaise > discretionaryPaise;
  const model = width > 0
    ? buildBurnChart({
        cumulative,
        daysTotal,
        discretionary: discretionaryPaise,
        projected: projectedPaise,
        width,
        height,
        pad: { top: 8, right: 6, bottom: 4, left: 2 },
      })
    : null;

  const a11y = `Spent ${formatINR(cumulative[cumulative.length - 1] ?? 0)} so far against a plan of ${formatINR(
    discretionaryPaise,
  )}; projected ${formatINR(projectedPaise)} by period end.`;

  return (
    <View style={styles.wrap}>
      <View onLayout={onLayout} style={{ height }} accessible accessibilityLabel={a11y}>
        {model ? (
          <Svg width={width} height={height}>
            <Line x1={0} x2={width} y1={height - 4} y2={height - 4} stroke={colors.border} strokeWidth={1} />
            <Path d={model.plan} stroke={colors.muted} strokeWidth={1.5} strokeDasharray="6 5" fill="none" />
            {model.projection ? (
              <Path
                d={model.projection}
                stroke={over ? colors.amberBar : colors.primary}
                strokeWidth={2}
                strokeDasharray="2 4"
                strokeLinecap="round"
                fill="none"
              />
            ) : null}
            <Path d={model.actual} stroke={colors.primary} strokeWidth={2.5} fill="none" strokeLinejoin="round" />
            {model.last ? <Circle cx={model.last.x} cy={model.last.y} r={4} fill={colors.primary} /> : null}
          </Svg>
        ) : null}
      </View>
      <View style={styles.legend}>
        <LegendItem color={colors.primary} label="Spent" />
        <LegendItem color={colors.muted} label="Plan" dashed />
        <LegendItem color={over ? colors.amberBar : colors.primary} label="Projected" dotted />
      </View>
    </View>
  );
}

function LegendItem({ color, label, dashed, dotted }: { color: string; label: string; dashed?: boolean; dotted?: boolean }) {
  return (
    <View style={styles.legendItem}>
      <Svg width={18} height={6}>
        <Line
          x1={1}
          x2={17}
          y1={3}
          y2={3}
          stroke={color}
          strokeWidth={dotted ? 2 : 2.5}
          strokeDasharray={dashed ? '4 3' : dotted ? '1 3' : undefined}
          strokeLinecap="round"
        />
      </Svg>
      <Text style={styles.legendText}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.sm },
  legend: { flexDirection: 'row', gap: spacing.lg, flexWrap: 'wrap' },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  legendText: { fontSize: 12, color: colors.muted },
});
