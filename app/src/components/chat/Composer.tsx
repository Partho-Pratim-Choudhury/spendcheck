import { Feather } from '@expo/vector-icons';
import {
  getRecordingPermissionsAsync,
  RecordingPresets,
  requestRecordingPermissionsAsync,
  setAudioModeAsync,
  useAudioRecorder,
} from 'expo-audio';
import { useEffect, useRef, useState } from 'react';
import { Alert, type GestureResponderEvent, Linking, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { colors, spacing } from '@/theme';

const MIN_MS = 700;
const MAX_MS = 60_000;
const CANCEL_DX = -80;

interface Props {
  onSendText: (text: string) => void;
  onSendVoice: (uri: string) => void;
  disabled?: boolean;
}

/** Explain → ask. Returns true only when already granted (the user presses again after granting). */
async function ensureMicPermission(): Promise<boolean> {
  const current = await getRecordingPermissionsAsync();
  if (current.granted) return true;
  if (!current.canAskAgain) {
    Alert.alert('Microphone is off', 'Allow microphone access for SpendCheck in Settings to log spends by voice.', [
      { text: 'Not now', style: 'cancel' },
      { text: 'Open settings', onPress: () => void Linking.openSettings() },
    ]);
    return false;
  }
  await new Promise<void>((resolve) =>
    Alert.alert(
      'Log spends by voice',
      'Hold the mic and say something like “two hundred on chai at Chai Point”. SpendCheck needs the microphone only while you hold the button.',
      [{ text: 'Continue', onPress: () => resolve() }],
      { cancelable: true, onDismiss: () => resolve() },
    ),
  );
  const res = await requestRecordingPermissionsAsync();
  if (res.granted) Alert.alert('All set', 'Now press and hold the mic to record.');
  return false;
}

function fmt(ms: number) {
  const s = Math.floor(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

export function Composer({ onSendText, onSendVoice, disabled }: Props) {
  const [text, setText] = useState('');
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const [recording, setRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [cancelArmed, setCancelArmed] = useState(false);
  const [hint, setHint] = useState<string | null>(null);

  const holding = useRef(false);
  const started = useRef<Promise<boolean> | null>(null);
  const startedAt = useRef(0);
  const startX = useRef(0);
  const armed = useRef(false);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);
  const finishing = useRef(false);

  const clearTimer = () => {
    if (timer.current) clearInterval(timer.current);
    timer.current = null;
  };
  useEffect(() => clearTimer, []);

  const finish = async (mode: 'send' | 'cancel') => {
    holding.current = false;
    if (finishing.current) return;
    const p = started.current;
    if (!p) return;
    finishing.current = true;
    try {
      const ok = await p;
      started.current = null;
      clearTimer();
      setRecording(false);
      setCancelArmed(false);
      if (!ok) return;
      const duration = Date.now() - startedAt.current;
      await recorder.stop();
      const uri = recorder.uri;
      if (mode === 'cancel' || armed.current) return;
      if (duration < MIN_MS) {
        setHint('Hold the mic to record');
        setTimeout(() => setHint(null), 2000);
        return;
      }
      if (uri) onSendVoice(uri);
    } catch {
      setHint('Couldn’t record — try again');
      setTimeout(() => setHint(null), 2500);
    } finally {
      armed.current = false;
      finishing.current = false;
    }
  };

  const begin = async (): Promise<boolean> => {
    if (!(await ensureMicPermission())) return false;
    await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
    await recorder.prepareToRecordAsync();
    recorder.record();
    startedAt.current = Date.now();
    return true;
  };

  const onGrant = (e: GestureResponderEvent) => {
    if (disabled || started.current) return;
    holding.current = true;
    armed.current = false;
    startX.current = e.nativeEvent.pageX;
    setHint(null);
    started.current = begin()
      .then((ok) => {
        if (ok) {
          setElapsed(0);
          setRecording(true);
          timer.current = setInterval(() => {
            const ms = Date.now() - startedAt.current;
            setElapsed(ms);
            if (ms >= MAX_MS) void finish('send');
          }, 200);
          // Released while we were still starting.
          if (!holding.current) void finish('send');
        }
        return ok;
      })
      .catch(() => false);
  };

  const onMove = (e: GestureResponderEvent) => {
    const nowArmed = e.nativeEvent.pageX - startX.current < CANCEL_DX;
    if (nowArmed !== armed.current) {
      armed.current = nowArmed;
      setCancelArmed(nowArmed);
    }
  };

  const trimmed = text.trim();

  const send = () => {
    if (!trimmed || disabled) return;
    onSendText(trimmed.slice(0, 500));
    setText('');
  };

  return (
    <View style={styles.bar}>
      {recording ? (
        <View style={styles.recording} accessibilityLiveRegion="polite">
          <View style={[styles.dot, cancelArmed && styles.dotCancel]} />
          <Text style={styles.timer}>{fmt(elapsed)}</Text>
          <Text style={[styles.recHint, cancelArmed && styles.recHintCancel]} numberOfLines={1}>
            {cancelArmed ? 'Release to cancel' : '‹ Slide to cancel · release to send'}
          </Text>
        </View>
      ) : (
        <View style={styles.inputWrap}>
          <TextInput
            value={text}
            onChangeText={setText}
            placeholder={hint ?? '“450 swiggy” or “can I buy…”'}
            placeholderTextColor={hint ? colors.verdict.amber.text : colors.muted}
            style={styles.input}
            multiline
            maxLength={500}
            accessibilityLabel="Message"
            accessibilityHint="Type a spend like 450 swiggy, or ask can I buy"
            onSubmitEditing={send}
            submitBehavior="submit"
            returnKeyType="send"
          />
        </View>
      )}
      {trimmed && !recording ? (
        <Pressable
          onPress={send}
          disabled={disabled}
          accessibilityRole="button"
          accessibilityLabel="Send"
          style={({ pressed }) => [styles.round, { backgroundColor: pressed ? colors.primaryPressed : colors.primary }, disabled && styles.dim]}>
          <Feather name="send" size={20} color={colors.onPrimary} />
        </Pressable>
      ) : (
        <View
          onStartShouldSetResponder={() => !disabled}
          onResponderGrant={onGrant}
          onResponderMove={onMove}
          onResponderRelease={() => void finish('send')}
          onResponderTerminate={() => void finish('cancel')}
          onResponderTerminationRequest={() => false}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Hold to record a voice message"
          accessibilityHint="Press and hold, speak, then release to send"
          style={[styles.round, styles.mic, recording && styles.micActive, cancelArmed && styles.micCancel, disabled && styles.dim]}>
          <Feather name={cancelArmed ? 'x' : 'mic'} size={24} color={colors.onPrimary} />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: spacing.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  inputWrap: {
    flex: 1,
    minHeight: 52,
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 26,
    backgroundColor: colors.bg,
    paddingHorizontal: spacing.lg,
  },
  input: { fontSize: 16, color: colors.ink, maxHeight: 120, paddingVertical: 12 },
  recording: {
    flex: 1,
    minHeight: 52,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    paddingHorizontal: spacing.lg,
    borderRadius: 26,
    backgroundColor: colors.verdict.red.bg,
  },
  dot: { width: 10, height: 10, borderRadius: 5, backgroundColor: colors.redBar },
  dotCancel: { backgroundColor: colors.muted },
  timer: { fontSize: 16, fontWeight: '600', color: colors.ink, fontVariant: ['tabular-nums'] },
  recHint: { flex: 1, fontSize: 13, color: colors.muted },
  recHintCancel: { color: colors.verdict.red.text, fontWeight: '600' },
  round: { width: 52, height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center' },
  mic: { backgroundColor: colors.primary },
  micActive: { backgroundColor: colors.redBar, transform: [{ scale: 1.12 }] },
  micCancel: { backgroundColor: colors.muted },
  dim: { opacity: 0.5 },
});
