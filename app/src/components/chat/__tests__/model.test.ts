import { describe, expect, it } from '@jest/globals';

import type { KeptRecording } from '@/lib/queries';
import type { Transaction, UndoCandidate } from '@/lib/types';

import {
  beginCardAction,
  type ChatMessage,
  confirmDraft,
  dropVoiceRetries,
  failCardAction,
  hasPendingUndo,
  isCardBusy,
  keepUndo,
  markTxRemoved,
  settleVerdict,
} from '../model';

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

const draft = (id: string): ChatMessage =>
  ({ id, kind: 'draft', rawInputId: 1, source: 'TEXT', draft: {}, txId: 't1', confirmed: null }) as unknown as ChatMessage;
const verdict = (id: string): ChatMessage =>
  ({ id, kind: 'verdict', rawInputId: 1, source: 'TEXT', verdict: {}, draft: null, txId: 't1', outcome: null }) as unknown as ChatMessage;

describe('card request state', () => {
  it('undo: removing -> failed keeps the card pending with the error; removed clears it', () => {
    const busy = beginCardAction(undo('u', 'x'));
    expect(busy).toMatchObject({ state: 'pending', removing: true, error: null });
    expect(isCardBusy(busy)).toBe(true);
    // A card that is mid-request is still the open card for that spend.
    expect(hasPendingUndo([busy], 'x')).toBe(true);
    expect(keepUndo(busy)).toBe(busy);

    const failed = failCardAction(busy, 'Network down');
    expect(failed).toMatchObject({ state: 'pending', removing: false, error: 'Network down' });
    expect(isCardBusy(failed)).toBe(false);

    const [removed] = markTxRemoved([beginCardAction(failed)], 'x');
    expect(removed).toMatchObject({ state: 'removed', removing: false, error: null });
  });

  it('undo: settled cards ignore begin/fail; keep settles a pending card', () => {
    const kept = undo('u', 'x', 'kept');
    expect(beginCardAction(kept)).toBe(kept);
    expect(failCardAction(kept, 'e')).toBe(kept);
    expect(keepUndo(undo('u', 'x'))).toMatchObject({ state: 'kept' });
  });

  it('draft: confirming -> error -> confirmed', () => {
    const busy = beginCardAction(draft('d'));
    expect(busy).toMatchObject({ confirming: true, error: null, txId: 't1' });
    expect(beginCardAction(busy)).toBe(busy);
    const failed = failCardAction(busy, 'Bad amount');
    expect(failed).toMatchObject({ confirming: false, error: 'Bad amount', txId: 't1' });
    expect(confirmDraft(beginCardAction(failed), tx('t1'))).toMatchObject({ confirmed: { id: 't1' }, confirming: false, error: null });
  });

  it('verdict: records which action is busy, then the outcome', () => {
    const busy = beginCardAction(verdict('v'), 'defer');
    expect(busy).toMatchObject({ busy: 'defer', error: null });
    expect(failCardAction(busy, 'oops')).toMatchObject({ busy: null, error: 'oops' });
    const done = settleVerdict(busy, { decision: 'SKIPPED' });
    expect(done).toMatchObject({ outcome: { decision: 'SKIPPED' }, busy: null, error: null });
    expect(beginCardAction(done, 'buy')).toBe(done);
  });
});
