import { describe, expect, it } from '@jest/globals';

import { ApiError, isRetryableError, serverDetail } from '../api';

const err = (status: number) => new ApiError({ status, title: 't', detail: 'd' });

describe('isRetryableError', () => {
  it('retries network/timeout, 408, 429 and 5xx', () => {
    for (const s of [0, 408, 429, 500, 502, 503]) expect(isRetryableError(err(s))).toBe(true);
  });

  it('treats other 4xx and unknown errors as final', () => {
    for (const s of [400, 401, 404, 413, 422]) expect(isRetryableError(err(s))).toBe(false);
    expect(isRetryableError(new Error('x'))).toBe(false);
  });
});

describe('serverDetail', () => {
  const mk = (status: number, title: string, detail: string) => new ApiError({ status, title, detail });

  it('returns the ProblemDetail detail the server sent', () => {
    const msg = 'The OpenAI account has no credits. Add credits at platform.openai.com/settings/billing, then try again.';
    expect(serverDetail(mk(503, 'Service Unavailable', msg))).toBe(msg);
    expect(serverDetail(mk(502, 'Bad Gateway', '  Speech-to-text failed. '))).toBe('Speech-to-text failed.');
  });

  it("returns null for apiFetch's fallbacks and network errors", () => {
    expect(serverDetail(mk(503, 'Service Unavailable', 'Service Unavailable'))).toBeNull();
    expect(serverDetail(mk(502, 'HTTP 502', 'Request failed with status 502'))).toBeNull();
    expect(serverDetail(mk(503, 'x', ''))).toBeNull();
    expect(serverDetail(mk(0, 'Network error', 'Could not reach the server.'))).toBeNull();
  });
});
