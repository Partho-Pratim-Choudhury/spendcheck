import { useQueryClient } from '@tanstack/react-query';
import * as Crypto from 'expo-crypto';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { FlatList, KeyboardAvoidingView, StyleSheet, View } from 'react-native';

import { BotBubble, CompactLine, DayDivider, ErrorBubble, UserBubble } from '@/components/chat/Bubbles';
import { ChatHeader } from '@/components/chat/ChatHeader';
import { Composer } from '@/components/chat/Composer';
import { DraftCard } from '@/components/chat/DraftCard';
import {
  beginCardAction,
  type ChatMessage,
  confirmDraft,
  type DraftEdits,
  dropVoiceRetries,
  failCardAction,
  hasPendingUndo,
  type InputPayload,
  type InputSource,
  keepUndo,
  markTxRemoved,
  nextId,
  settleVerdict,
  type VerdictAction,
} from '@/components/chat/model';
import { UndoCard } from '@/components/chat/UndoCard';
import { VerdictCard } from '@/components/chat/VerdictCard';
import { errorMessage } from '@/components/States';
import { ApiError, isNetworkError, serverDetail } from '@/lib/api';
import { addDays, toYmd } from '@/lib/dates';
import { deleteLocalFile } from '@/lib/files';
import { DEFER_REMINDER, scheduleDeferReminder } from '@/lib/notifications';
import {
  invalidateSpendData,
  sendTextInput,
  sendVoiceInput,
  useCreateTransaction,
  useDecision,
  useDeleteTransaction,
  useTodayTransactions,
  VoiceUploadError,
} from '@/lib/queries';
import type { InputResult, Transaction } from '@/lib/types';
import { colors, spacing } from '@/theme';

const DEFER_DAYS = 3;

type Msg<K extends ChatMessage['kind']> = Extract<ChatMessage, { kind: K }>;

const INTRO =
  'Tell me what you spent — “450 swiggy” — or ask before you buy: “can I buy shoes for 3000?”. Hold the mic to speak.';

function inputErrorText(e: unknown): string {
  if (isNetworkError(e)) return e.title === 'Request timed out' ? 'That took too long. Check your connection and retry.' : 'Can’t reach the server right now.';
  if (e instanceof ApiError) {
    // 502/503 carry actionable causes (no API key, no credits, upstream down) — prefer the server's words.
    if (e.status === 503) return serverDetail(e) ?? 'The assistant isn’t configured on the server yet.';
    if (e.status === 404) return 'Server is out of date — restart the API.';
    if (e.status === 502) return serverDetail(e) ?? 'I couldn’t understand that just now (the AI service failed).';
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
  // Recordings kept on disk so their error bubble can Retry; deleted once superseded or on unmount.
  const keptVoice = useRef(new Set<string>());
  const mounted = useRef(true);
  // Card requests run here, not in the cards: FlatList unmounts off-screen cards, and React Query
  // drops a per-call mutate() callback when its component unmounts — the outcome would be lost.
  const { mutateAsync: deleteTx } = useDeleteTransaction();
  const { mutateAsync: createTx } = useCreateTransaction();
  const { mutateAsync: decide } = useDecision();
  // Message ids with a card request in flight (sync guard against double taps / remounted cards).
  const cardBusy = useRef(new Set<string>());

  useEffect(() => {
    mounted.current = true;
    const kept = keptVoice.current;
    return () => {
      mounted.current = false;
      for (const uri of kept) deleteLocalFile(uri);
      kept.clear();
    };
  }, []);

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

  /** After a confirmed Remove: strike the spend through wherever it shows in this chat. */
  const markRemoved = useCallback((txId: string) => {
    setSeed((s) => s?.map((t) => (t.id === txId ? { ...t, status: 'CANCELLED' } : t)) ?? s);
    setMessages((prev) => markTxRemoved(prev, txId));
  }, []);

  /** Run `work` for card `id` once at a time; the card shows busy until it settles, and the error inline if it throws. */
  const cardAction = useCallback(
    async (id: string, work: () => Promise<void>, action?: VerdictAction) => {
      if (cardBusy.current.has(id)) return;
      cardBusy.current.add(id);
      patch(id, (m) => beginCardAction(m, action));
      try {
        await work();
      } catch (e) {
        patch(id, (m) => failCardAction(m, errorMessage(e)));
      } finally {
        cardBusy.current.delete(id);
      }
    },
    [patch],
  );

  const removeSpend = useCallback(
    (m: Msg<'undo'>) =>
      cardAction(m.id, async () => {
        const txId = m.candidate.transactionId;
        try {
          await deleteTx(txId);
        } catch (e) {
          // Already gone (e.g. deleted from the Spends tab) — same end state.
          if (!(e instanceof ApiError && e.status === 404)) throw e;
        }
        // Settles this card and any other pending card for the same spend.
        markRemoved(txId);
      }),
    [cardAction, deleteTx, markRemoved],
  );

  const confirmSpend = useCallback(
    (m: Msg<'draft'>, edits: DraftEdits) =>
      cardAction(m.id, async () => {
        const tx = await createTx({
          id: m.txId,
          ...edits,
          note: m.draft.note ?? undefined,
          spentAt: m.draft.spentAt,
          source: m.source,
          rawInputId: m.rawInputId,
        });
        patch(m.id, (x) => confirmDraft(x, tx));
      }),
    [cardAction, createTx, patch],
  );

  const decideVerdict = useCallback(
    (m: Msg<'verdict'>, action: VerdictAction) =>
      cardAction(
        m.id,
        async () => {
          const v = m.verdict;
          if (action === 'skip') {
            await decide({ checkId: v.checkId, body: { decision: 'SKIPPED' } });
            patch(m.id, (x) => settleVerdict(x, { decision: 'SKIPPED' }));
          } else if (action === 'defer') {
            await decide({ checkId: v.checkId, body: { decision: 'DEFERRED' } });
            const remindOn = addDays(toYmd(new Date()), DEFER_DAYS);
            const reminded = await scheduleDeferReminder(DEFER_REMINDER.title, DEFER_REMINDER.body, DEFER_DAYS);
            patch(m.id, (x) => settleVerdict(x, { decision: 'DEFERRED', reminded, remindOn }));
          } else {
            const d = m.draft;
            const tx = await createTx({
              id: m.txId,
              amountPaise: d?.amountPaise ?? v.amountPaise,
              categoryId: d?.categoryId ?? v.categoryId,
              merchant: d?.merchant ?? undefined,
              note: d?.note ?? undefined,
              spentAt: d?.spentAt ?? new Date().toISOString(),
              needOrWant: d?.needOrWant ?? undefined,
              source: m.source,
              rawInputId: m.rawInputId,
            });
            try {
              await decide({ checkId: v.checkId, body: { decision: 'PROCEEDED', transactionId: tx.id } });
            } catch {
              // The spend is logged; a missing decision link only affects stats.
            }
            patch(m.id, (x) => settleVerdict(x, { decision: 'PROCEEDED', tx }));
          }
        },
        action,
      ),
    [cardAction, createTx, decide, patch],
  );

  /** Delete kept recordings (except `keep`) and drop the Retry their bubbles offered. */
  const discardKeptVoice = useCallback((keep?: string) => {
    const kept = keptVoice.current;
    let dropped = false;
    for (const uri of kept) {
      if (uri === keep) continue;
      deleteLocalFile(uri);
      kept.delete(uri);
      dropped = true;
    }
    if (dropped) setMessages(dropVoiceRetries);
  }, []);

  const handleResult = useCallback(
    (r: InputResult, source: InputSource) => {
      const out: ChatMessage[] = [];
      if (r.followUp) out.push({ id: nextId(), kind: 'bot', text: r.followUp.question });
      if (r.verdict) {
        out.push({
          id: nextId(),
          kind: 'verdict',
          rawInputId: r.rawInputId,
          source,
          verdict: r.verdict,
          draft: r.draft,
          txId: Crypto.randomUUID(),
          outcome: null,
        });
      } else if (r.draft) {
        out.push({
          id: nextId(),
          kind: 'draft',
          rawInputId: r.rawInputId,
          source,
          draft: r.draft,
          txId: Crypto.randomUUID(),
          confirmed: null,
        });
      }
      if (r.undoneTransactionId) {
        // Older server: it already deleted the spend instead of proposing it.
        markRemoved(r.undoneTransactionId);
        void invalidateSpendData(qc);
        out.push({ id: nextId(), kind: 'bot', text: r.answer?.text ?? 'Removed your last spend.' });
      } else if (r.intent === 'UNDO' && r.undoCandidate) {
        out.push({
          id: nextId(),
          kind: 'undo',
          text: r.answer?.text ?? 'Remove your last spend?',
          candidate: r.undoCandidate,
          state: 'pending',
        });
      } else if (r.answer) {
        out.push({ id: nextId(), kind: 'bot', text: r.answer.text });
      }
      if (out.length === 0) out.push({ id: nextId(), kind: 'bot', text: 'Sorry, I didn’t get that. Try “450 swiggy”.' });
      // One pending card per spend: a repeat proposal points back at the open card instead.
      setMessages((prev) => [
        ...prev,
        ...out.map((m): ChatMessage =>
          m.kind === 'undo' && hasPendingUndo(prev, m.candidate.transactionId)
            ? { id: m.id, kind: 'bot', text: 'Already asked about that one above.' }
            : m,
        ),
      ]);
    },
    [markRemoved, qc],
  );

  const run = useCallback(
    async (payload: InputPayload) => {
      inFlight.current = true;
      setBusy(true);
      // Sending anything else supersedes an older failed recording.
      discardKeptVoice(payload.type === 'voice' ? payload.uri : undefined);
      try {
        if (payload.type === 'text') {
          handleResult(await sendTextInput(payload.text), 'TEXT');
        } else {
          const r = await sendVoiceInput(payload.uri);
          keptVoice.current.delete(payload.uri);
          patch(payload.bubbleId, (m) => (m.kind === 'user' ? { ...m, text: r.transcript ?? '', pending: false } : m));
          handleResult(r, 'VOICE');
        }
      } catch (e) {
        if (payload.type === 'voice') {
          // sendVoiceInput keeps the recording only when the upload is worth retrying.
          const kept = e instanceof VoiceUploadError ? e.kept : null;
          const reason = e instanceof VoiceUploadError ? e.reason : e;
          if (kept && !mounted.current) deleteLocalFile(kept);
          else if (kept) keptVoice.current.add(kept);
          else keptVoice.current.delete(payload.uri);
          patch(payload.bubbleId, (m) => (m.kind === 'user' ? { ...m, pending: false } : m));
          add(
            kept
              ? { id: nextId(), kind: 'error', text: inputErrorText(reason), retry: { type: 'voice', recording: kept, bubbleId: payload.bubbleId } }
              : { id: nextId(), kind: 'error', text: `${inputErrorText(reason)} Hold the mic to try again.`, retry: null },
          );
        } else {
          add({ id: nextId(), kind: 'error', text: inputErrorText(e), retry: payload });
        }
      } finally {
        inFlight.current = false;
        setBusy(false);
      }
    },
    [add, discardKeptVoice, handleResult, patch],
  );

  const sendText = (text: string) => {
    if (inFlight.current) return;
    add({ id: nextId(), kind: 'user', text, voice: false });
    void run({ type: 'text', text });
  };

  /** Returns false (and drops the recording) when another input is still in flight. */
  const sendVoice = (uri: string): boolean => {
    if (inFlight.current) {
      deleteLocalFile(uri);
      return false;
    }
    const bubbleId = nextId();
    add({ id: bubbleId, kind: 'user', text: '', voice: true, pending: true });
    void run({ type: 'voice', uri, bubbleId });
    return true;
  };

  const retry = (m: Extract<ChatMessage, { kind: 'error' }>) => {
    const r = m.retry;
    if (inFlight.current || !r) return;
    setMessages((prev) => prev.filter((x) => x.id !== m.id));
    if (r.type === 'text') {
      void run(r);
    } else {
      patch(r.bubbleId, (x) => (x.kind === 'user' ? { ...x, pending: true } : x));
      void run({ type: 'voice', uri: r.recording, bubbleId: r.bubbleId });
    }
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
        return <ErrorBubble text={m.text} onRetry={m.retry ? () => retry(m) : undefined} disabled={busy} />;
      case 'undo':
        return (
          <UndoCard
            text={m.text}
            candidate={m.candidate}
            state={m.state}
            removing={m.removing}
            error={m.error}
            onRemove={() => void removeSpend(m)}
            onKept={() => patch(m.id, keepUndo)}
          />
        );
      case 'draft':
        return (
          <DraftCard
            draft={m.draft}
            confirmed={m.confirmed}
            confirming={m.confirming}
            error={m.error}
            undone={m.undone}
            onConfirm={(edits) => void confirmSpend(m, edits)}
          />
        );
      case 'verdict':
        return (
          <VerdictCard
            verdict={m.verdict}
            outcome={m.outcome}
            busy={m.busy}
            error={m.error}
            undone={m.undone}
            onDecide={(action) => void decideVerdict(m, action)}
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
