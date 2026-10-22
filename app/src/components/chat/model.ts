import type { KeptRecording } from '@/lib/queries';
import type { Draft, Transaction, UndoCandidate, Verdict } from '@/lib/types';

export type InputSource = 'TEXT' | 'VOICE';

/** What the user just sent. */
export type InputPayload = { type: 'text'; text: string } | { type: 'voice'; uri: string; bubbleId: string };

/**
 * What an error bubble can replay. A voice retry needs a KeptRecording — only sendVoiceInput's
 * retryable failure hands one out — and is dropped (see dropVoiceRetries) when that file is deleted.
 */
export type RetryPayload = { type: 'text'; text: string } | { type: 'voice'; recording: KeptRecording; bubbleId: string };

export type UndoState = 'pending' | 'removed' | 'kept';

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
  | { id: string; kind: 'undo'; text: string; candidate: UndoCandidate; state: UndoState }
  | {
      id: string;
      kind: 'draft';
      rawInputId: number;
      source: InputSource;
      draft: Draft;
      confirmed: Transaction | null;
      undone?: boolean;
    }
  | {
      id: string;
      kind: 'verdict';
      rawInputId: number;
      source: InputSource;
      verdict: Verdict;
      draft: Draft | null;
      outcome: VerdictOutcome | null;
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
    if (m.kind === 'undo' && m.state === 'pending' && m.candidate.transactionId === txId) return { ...m, state: 'removed' };
    return m;
  });
}

export const hasPendingUndo = (messages: readonly ChatMessage[], txId: string) =>
  messages.some((m) => m.kind === 'undo' && m.state === 'pending' && m.candidate.transactionId === txId);

/** Voice recordings were deleted: their error bubbles can no longer offer Retry. */
export function dropVoiceRetries(messages: ChatMessage[]): ChatMessage[] {
  return messages.map((m) => (m.kind === 'error' && m.retry?.type === 'voice' ? { ...m, retry: null } : m));
}
