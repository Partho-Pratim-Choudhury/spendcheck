import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { FlatList, KeyboardAvoidingView, StyleSheet, View } from 'react-native';

import { BotBubble, CompactLine, DayDivider, ErrorBubble, UserBubble } from '@/components/chat/Bubbles';
import { ChatHeader } from '@/components/chat/ChatHeader';
import { Composer } from '@/components/chat/Composer';
import { DraftCard } from '@/components/chat/DraftCard';
import { type ChatMessage, type InputSource, nextId, type RetryPayload } from '@/components/chat/model';
import { VerdictCard } from '@/components/chat/VerdictCard';
import { ApiError, isNetworkError } from '@/lib/api';
import { invalidateSpendData, sendTextInput, sendVoiceInput, useTodayTransactions } from '@/lib/queries';
import type { InputResult, Transaction } from '@/lib/types';
import { colors, spacing } from '@/theme';

const INTRO =
  'Tell me what you spent — “450 swiggy” — or ask before you buy: “can I buy shoes for 3000?”. Hold the mic to speak.';

function inputErrorText(e: unknown): string {
  if (isNetworkError(e)) return e.title === 'Request timed out' ? 'That took too long. Check your connection and retry.' : 'Can’t reach the server right now.';
  if (e instanceof ApiError) {
    if (e.status === 503) return 'The assistant isn’t configured on the server yet.';
    if (e.status === 404) return 'Server is out of date — restart the API.';
    if (e.status === 502) return 'I couldn’t understand that just now (the AI service failed).';
    if (e.status === 400) return e.detail || 'That input wasn’t accepted.';
    return e.detail || 'Something went wrong.';
  }
  return 'Something went wrong.';
}

export default function ChatScreen() {
  const qc = useQueryClient();
  const today = useTodayTransactions();
  const [seed, setSeed] = useState<Transaction[] | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [busy, setBusy] = useState(false);
  // One input at a time: the ref closes the gap before `busy` re-renders (double taps).
  const inFlight = useRef(false);
  const seeded = useRef(false);

  // Snapshot today's confirmed spends once per session; later confirms show as their own cards.
  useEffect(() => {
    if (!seeded.current && today.data) {
      seeded.current = true;
      setSeed([...today.data].sort((a, b) => Date.parse(a.spentAt) - Date.parse(b.spentAt)));
    }
  }, [today.data]);

  const add = useCallback((...ms: ChatMessage[]) => setMessages((prev) => [...prev, ...ms]), []);
  const patch = useCallback(
    (id: string, fn: (m: ChatMessage) => ChatMessage) => setMessages((prev) => prev.map((m) => (m.id === id ? fn(m) : m))),
    [],
  );

  const handleResult = useCallback(
    (r: InputResult, source: InputSource) => {
      const out: ChatMessage[] = [];
      if (r.followUp) out.push({ id: nextId(), kind: 'bot', text: r.followUp.question });
      if (r.verdict) {
        out.push({ id: nextId(), kind: 'verdict', rawInputId: r.rawInputId, source, verdict: r.verdict, draft: r.draft, outcome: null });
      } else if (r.draft) {
        out.push({ id: nextId(), kind: 'draft', rawInputId: r.rawInputId, source, draft: r.draft, confirmed: null });
      }
      if (r.answer) out.push({ id: nextId(), kind: 'bot', text: r.answer.text });
      if (out.length === 0) out.push({ id: nextId(), kind: 'bot', text: 'Sorry, I didn’t get that. Try “450 swiggy”.' });

      const undone = r.undoneTransactionId;
      if (undone) {
        void invalidateSpendData(qc);
        setSeed((s) => s?.map((t) => (t.id === undone ? { ...t, status: 'CANCELLED' } : t)) ?? s);
        setMessages((prev) =>
          prev.map((m) => {
            if (m.kind === 'draft' && m.confirmed?.id === undone) return { ...m, undone: true };
            if (m.kind === 'verdict' && m.outcome?.decision === 'PROCEEDED' && m.outcome.tx.id === undone) return { ...m, undone: true };
            if (m.kind === 'line' && m.tx.id === undone) return { ...m, undone: true };
            return m;
          }),
        );
      }
      add(...out);
    },
    [add, qc],
  );

  const run = useCallback(
    async (payload: RetryPayload) => {
      inFlight.current = true;
      setBusy(true);
      try {
        if (payload.type === 'text') {
          handleResult(await sendTextInput(payload.text), 'TEXT');
        } else {
          const r = await sendVoiceInput(payload.uri);
          patch(payload.bubbleId, (m) => (m.kind === 'user' ? { ...m, text: r.transcript ?? '', pending: false } : m));
          handleResult(r, 'VOICE');
        }
      } catch (e) {
        if (payload.type === 'voice') patch(payload.bubbleId, (m) => (m.kind === 'user' ? { ...m, pending: false } : m));
        add({ id: nextId(), kind: 'error', text: inputErrorText(e), retry: payload });
      } finally {
        inFlight.current = false;
        setBusy(false);
      }
    },
    [add, handleResult, patch],
  );

  const sendText = (text: string) => {
    if (inFlight.current) return;
    add({ id: nextId(), kind: 'user', text, voice: false });
    void run({ type: 'text', text });
  };

  const sendVoice = (uri: string) => {
    if (inFlight.current) return;
    const bubbleId = nextId();
    add({ id: bubbleId, kind: 'user', text: '', voice: true, pending: true });
    void run({ type: 'voice', uri, bubbleId });
  };

  const retry = (m: Extract<ChatMessage, { kind: 'error' }>) => {
    if (inFlight.current) return;
    setMessages((prev) => prev.filter((x) => x.id !== m.id));
    if (m.retry.type === 'voice') {
      const bubbleId = m.retry.bubbleId;
      patch(bubbleId, (x) => (x.kind === 'user' ? { ...x, pending: true } : x));
    }
    void run(m.retry);
  };

  const items = useMemo<ChatMessage[]>(() => {
    const head: ChatMessage[] = [{ id: 'today', kind: 'divider', label: 'TODAY' }];
    for (const t of seed ?? []) head.push({ id: `seed-${t.id}`, kind: 'line', tx: t, undone: t.status === 'CANCELLED' });
    if (messages.length === 0) head.push({ id: 'intro', kind: 'bot', text: INTRO });
    // Inverted list: newest first.
    return [...head, ...messages].reverse();
  }, [seed, messages]);

  const render = ({ item: m }: { item: ChatMessage }) => {
    switch (m.kind) {
      case 'divider':
        return <DayDivider label={m.label} />;
      case 'line':
        return (
          <CompactLine
            amountPaise={m.tx.amountPaise}
            categoryName={m.tx.categoryName}
            merchant={m.tx.merchant}
            needOrWant={m.tx.needOrWant}
            undone={m.undone}
          />
        );
      case 'user':
        return <UserBubble text={m.text} voice={m.voice} pending={m.pending} />;
      case 'bot':
        return <BotBubble text={m.text} />;
      case 'error':
        return <ErrorBubble text={m.text} onRetry={() => retry(m)} disabled={busy} />;
      case 'draft':
        return (
          <DraftCard
            draft={m.draft}
            rawInputId={m.rawInputId}
            source={m.source}
            confirmed={m.confirmed}
            undone={m.undone}
            onConfirmed={(tx) => patch(m.id, (x) => (x.kind === 'draft' ? { ...x, confirmed: tx } : x))}
          />
        );
      case 'verdict':
        return (
          <VerdictCard
            verdict={m.verdict}
            draft={m.draft}
            rawInputId={m.rawInputId}
            source={m.source}
            outcome={m.outcome}
            undone={m.undone}
            onOutcome={(o) => patch(m.id, (x) => (x.kind === 'verdict' ? { ...x, outcome: o } : x))}
          />
        );
    }
  };

  return (
    <View style={styles.screen}>
      <ChatHeader />
      <KeyboardAvoidingView behavior="padding" style={styles.flex}>
        <FlatList
          data={items}
          inverted
          keyExtractor={(m) => m.id}
          renderItem={render}
          contentContainerStyle={styles.list}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          ListHeaderComponent={busy ? <BotBubble text="…" muted /> : null}
        />
        <Composer onSendText={sendText} onSendVoice={sendVoice} disabled={busy} />
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  flex: { flex: 1 },
  list: { paddingVertical: spacing.md },
});
