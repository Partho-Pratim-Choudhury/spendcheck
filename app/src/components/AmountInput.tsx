import { useState } from 'react';
import { Platform, StyleSheet, Text, type TextInputProps, View } from 'react-native';

import { TextField } from '@/components/TextField';
import { evalAmount } from '@/lib/calc';
import { formatINR, paiseToInput } from '@/lib/money';
import { colors, spacing } from '@/theme';

/**
 * Operators the arithmetic keyboard allows alongside digits, the decimal point and spaces.
 * Any other character is stripped on input so the field only ever holds a candidate expression.
 */
const ALLOWED = /[^0-9+\-*/.\s]/g;
const HAS_OP = /[+\-*/]/;

export type AmountState = { text: string; paise: number | null; live: string | null; hint: string | null; invalid: boolean };

/** Evaluate the raw text once, deriving the paise value, the live "= ₹…" preview and the inline hint. Exported for tests. */
export function evaluate(raw: string, allowZero: boolean): AmountState {
  const text = raw.replace(ALLOWED, '');
  const trimmed = text.trim();
  if (trimmed === '') return { text, paise: null, live: null, hint: null, invalid: false };

  const res = evalAmount(text);
  const valid = res.ok && (allowZero ? res.value >= 0 : res.value > 0);
  if (!valid) return { text, paise: null, live: null, hint: 'Enter a valid amount', invalid: true };

  const paise = Math.round(res.value * 100);
  // Live preview only when the user is actually computing (an operator is present).
  const live = HAS_OP.test(trimmed) ? `= ${formatINR(paise, 'nearest')}` : null;
  return { text, paise, live, hint: null, invalid: false };
}

/**
 * Shared calculator-field logic: holds the raw text locally, re-evaluates on every keystroke,
 * and reports the paise value (null when empty or invalid) plus whether it is invalid.
 * `AmountInput` renders it as a `TextField`; `plan.tsx`'s bespoke rows reuse it directly.
 */
export function useAmountField(opts: {
  valuePaise: number | null;
  onChangeValuePaise: (paise: number | null) => void;
  onInvalidChange?: (invalid: boolean) => void;
  allowZero?: boolean;
}) {
  const { valuePaise, onChangeValuePaise, onInvalidChange, allowZero = false } = opts;
  // Seed from the incoming paise once; the raw text is the source of truth while editing.
  const [state, setState] = useState<AmountState>(() =>
    evaluate(valuePaise == null ? '' : paiseToInput(valuePaise), allowZero),
  );

  const onChangeText = (raw: string) => {
    const next = evaluate(raw, allowZero);
    setState(next);
    onChangeValuePaise(next.paise);
    onInvalidChange?.(next.invalid);
  };

  return { text: state.text, onChangeText, live: state.live, hint: state.hint, invalid: state.invalid };
}

export interface AmountInputProps
  extends Omit<TextInputProps, 'value' | 'onChangeText' | 'onChange' | 'keyboardType' | 'inputMode'> {
  label: string;
  valuePaise: number | null;
  onChangeValuePaise: (paise: number | null) => void;
  /** Fired with `true` when the text is non-empty but not a valid amount — lets a parent block save and tell empty from invalid. */
  onInvalidChange?: (invalid: boolean) => void;
  /** When false (default) a result ≤ 0 is invalid; when true, 0 is allowed but a negative never is. */
  allowZero?: boolean;
  /** External error (e.g. a server field error) shown in place of the built-in hint. */
  error?: string;
  prefix?: string;
}

/**
 * Money field with calculator-style entry: type an arithmetic expression (`5000+2000`, `1200*3`)
 * and see the live result; the computed amount is reported in paise via `onChangeValuePaise`.
 * Styled as the app's amount `TextField`.
 */
export function AmountInput({
  label,
  valuePaise,
  onChangeValuePaise,
  onInvalidChange,
  allowZero,
  error,
  prefix,
  ...rest
}: AmountInputProps) {
  const { text, onChangeText, live, hint } = useAmountField({
    valuePaise,
    onChangeValuePaise,
    onInvalidChange,
    allowZero,
  });
  return (
    <View style={styles.wrap}>
      <TextField
        label={label}
        prefix={prefix}
        value={text}
        onChangeText={onChangeText}
        error={error ?? hint ?? undefined}
        // No pure number pad shows + - * /: iOS gets the numbers-and-punctuation pad; Android has no
        // numeric variant with operators, so it falls to the default keyboard (users type * and /).
        keyboardType={Platform.select({ ios: 'numbers-and-punctuation', android: 'default', default: 'default' })}
        {...rest}
      />
      {live ? <Text style={styles.live}>{live}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.xs },
  live: { fontSize: 13, color: colors.muted, fontVariant: ['tabular-nums'], marginTop: -2 },
});
