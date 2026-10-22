import type { KeptRecording } from '@/lib/queries';
import type { Category, Draft, NeedOrWant, Transaction, UndoCandidate, Verdict } from '@/lib/types';

export type InputSource = 'TEXT' | 'VOICE';

/** What the user just sent. */
export type InputPayload = { type: 'text'; text: string } | { type: 'voice'; uri: string; bubbleId: string };

/**
 * What an error bubble can replay. A voice retry needs a KeptRecording — only sendVoiceInput's
 * retryable failure hands one out — and is dropped (see dropVoiceRetries) when that file is deleted.
 */
export type RetryPayload = { type: 'text'; text: string } | { type: 'voice'; recording: KeptRecording; bubbleId: string };

export type UndoState = 'pending' | 'removed' | 'kept';

export type VerdictAction = 'skip' | 'buy' | 'defer';

/** What the user may have changed on a draft card before confirming it. */
export interface DraftEdits {
  amountPaise: number;
  categoryId: Category['id'];
  merchant?: string;
  needOrWant?: NeedOrWant;
}

export type VerdictOutcome =
  | { decision: 'SKIPPED' }
  /** remindOn: YYYY-MM-DD fixed when the user deferred, so the card doesn't drift on later renders. */
  | { decision: 'DEFERRED'; reminded: boolean; remindOn: string }
  | { decision: 'PROCEEDED'; tx: Transaction };

export type ChatMessage =
  | { id: string; kind: 'divider'; label: string }
  | { id: string; kind: 'line'; tx: Transaction; undone?: boolean }
  | { id: string; kind: 'user'; text: string; voice: boolean; pending?: boolean }
  | { id: string; kind: 'bot'; text: string }
  /** retry = null when the input can't be replayed (final error, or the recording was deleted). */
  | { id: string; kind: 'error'; text: string; retry: RetryPayload | null }
  /*
   * Request state (removing / confirming / busy) and `error` live here, not in the card: the chat list
   * virtualizes, so a card can unmount mid-request and must come back showing the same thing.
   */
  | { id: string; kind: 'undo'; text: string; candidate: UndoCandidate; state: UndoState; removing?: boolean; error?: string | null }
  | {
      id: string;
      kind: 'draft';
      rawInputId: number;
      source: InputSource;
      draft: Draft;
      /** Client id for the spend, fixed per card so a retried confirm is idempotent server-side. */
      txId: string;
      confirmed: Transaction | null;
      confirming?: boolean;
      error?: string | null;
      undone?: boolean;
    }
  | {
      id: string;
      kind: 'verdict';
      rawInputId: number;
      source: InputSource;
      verdict: Verdict;
      draft: Draft | null;
      txId: string;
      outcome: VerdictOutcome | null;
      busy?: VerdictAction | null;
      error?: string | null;
      undone?: boolean;
    };

let seq = 0;
export const nextId = () => `m${Date.now().toString(36)}${(seq++).toString(36)}`;

/** Strike spend `txId` through wherever it shows, and settle every pending undo card for it as removed. */
export function markTxRemoved(messages: ChatMessage[], txId: string): ChatMessage[] {
  return messages.map((m) => {
    if (m.kind === 'draft' && m.confirmed?.id === txId) return { ...m, undone: true };
    if (m.kind === 'verdict' && m.outcome?.decision === 'PROCEEDED' && m.outcome.tx.id === txId) return { ...m, undone: true };
    if (m.kind === 'line' && m.tx.id === txId) return { ...m, undone: true };
    if (m.kind === 'undo' && m.state === 'pending' && m.candidate.transactionId === txId) {
      return { ...m, state: 'removed', removing: false, error: null };
    }
    return m;
  });
}

export const hasPendingUndo = (messages: readonly ChatMessage[], txId: string) =>
  messages.some((m) => m.kind === 'undo' && m.state === 'pending' && m.candidate.transactionId === txId);

/** Voice recordings were deleted: their error bubbles can no longer offer Retry. */
export function dropVoiceRetries(messages: ChatMessage[]): ChatMessage[] {
  return messages.map((m) => (m.kind === 'error' && m.retry?.type === 'voice' ? { ...m, retry: null } : m));
}

/** A card with a request in flight (its buttons are locked). */
export function isCardBusy(m: ChatMessage): boolean {
  if (m.kind === 'undo') return !!m.removing;
  if (m.kind === 'draft') return !!m.confirming;
  if (m.kind === 'verdict') return !!m.busy;
  return false;
}

/** The user acted on a still-open card: lock it and clear the last error. Settled cards are left alone. */
export function beginCardAction(m: ChatMessage, action: VerdictAction = 'buy'): ChatMessage {
  if (isCardBusy(m)) return m;
  if (m.kind === 'undo' && m.state === 'pending') return { ...m, removing: true, error: null };
  if (m.kind === 'draft' && !m.confirmed) return { ...m, confirming: true, error: null };
  if (m.kind === 'verdict' && !m.outcome) return { ...m, busy: action, error: null };
  return m;
}

/** The request failed: unlock the card and show `error` inline. */
export function failCardAction(m: ChatMessage, error: string): ChatMessage {
  if (m.kind === 'undo' && m.state === 'pending') return { ...m, removing: false, error };
  if (m.kind === 'draft' && !m.confirmed) return { ...m, confirming: false, error };
  if (m.kind === 'verdict' && !m.outcome) return { ...m, busy: null, error };
  return m;
}

export function confirmDraft(m: ChatMessage, tx: Transaction): ChatMessage {
  return m.kind === 'draft' ? { ...m, confirmed: tx, confirming: false, error: null } : m;
}

export function settleVerdict(m: ChatMessage, outcome: VerdictOutcome): ChatMessage {
  return m.kind === 'verdict' ? { ...m, outcome, busy: null, error: null } : m;
}

export function keepUndo(m: ChatMessage): ChatMessage {
  return m.kind === 'undo' && m.state === 'pending' && !m.removing ? { ...m, state: 'kept', error: null } : m;
}
