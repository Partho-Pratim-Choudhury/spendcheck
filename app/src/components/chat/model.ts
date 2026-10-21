import type { Draft, Transaction, Verdict } from '@/lib/types';

export type InputSource = 'TEXT' | 'VOICE';

export type RetryPayload = { type: 'text'; text: string } | { type: 'voice'; uri: string; bubbleId: string };

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
  | { id: string; kind: 'error'; text: string; retry: RetryPayload }
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
