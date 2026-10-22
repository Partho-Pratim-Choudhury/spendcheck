import { describe, expect, it } from '@jest/globals';

import { ApiError, isRetryableError } from '../api';

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
