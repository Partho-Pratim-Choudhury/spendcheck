import { describe, expect, it } from '@jest/globals';

import type { KeptRecording } from '@/lib/queries';
import type { Transaction, UndoCandidate } from '@/lib/types';

import { type ChatMessage, dropVoiceRetries, hasPendingUndo, markTxRemoved } from '../model';

const cand = (transactionId: string): UndoCandidate => ({
  transactionId,
  amountPaise: 45000,
  categoryName: 'Food',
  merchant: null,
  spentAt: '2026-10-22T10:00:00Z',
});
const undo = (id: string, txId: string, state: 'pending' | 'removed' | 'kept' = 'pending'): ChatMessage => ({
  id,
  kind: 'undo',
  text: 'Remove?',
  candidate: cand(txId),
  state,
});
const tx = (id: string) => ({ id }) as Transaction;

describe('markTxRemoved', () => {
  it('settles every pending undo card for the spend and strikes its lines', () => {
    const out = markTxRemoved(
      [undo('u1', 'x'), undo('u2', 'x'), undo('u3', 'x', 'kept'), undo('u4', 'y'), { id: 'l', kind: 'line', tx: tx('x') }],
      'x',
    );
    expect(out.map((m) => (m.kind === 'undo' ? m.state : m.kind === 'line' ? m.undone : null))).toEqual([
      'removed',
      'removed',
      'kept',
      'pending',
      true,
    ]);
  });
});

describe('hasPendingUndo', () => {
  it('only counts pending cards for that spend', () => {
    expect(hasPendingUndo([undo('u1', 'x')], 'x')).toBe(true);
    expect(hasPendingUndo([undo('u1', 'x', 'removed')], 'x')).toBe(false);
    expect(hasPendingUndo([undo('u1', 'y')], 'x')).toBe(false);
  });
});

describe('dropVoiceRetries', () => {
  it('removes voice retries and keeps text ones', () => {
    const out = dropVoiceRetries([
      { id: 'v', kind: 'error', text: 'e', retry: { type: 'voice', recording: 'file:///a.m4a' as KeptRecording, bubbleId: 'b' } },
      { id: 't', kind: 'error', text: 'e', retry: { type: 'text', text: 'hi' } },
    ]);
    expect(out.map((m) => (m.kind === 'error' ? m.retry?.type ?? null : null))).toEqual([null, 'text']);
  });
});
