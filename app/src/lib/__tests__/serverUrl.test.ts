import { describe, expect, it } from '@jest/globals';

import { normaliseServerUrl } from '../serverUrl';

const ok = (input: string) => {
  const r = normaliseServerUrl(input);
  if (!r.ok) throw new Error(`expected ok for "${input}": ${r.error}`);
  return r.url;
};

describe('normaliseServerUrl', () => {
  it('trims whitespace and trailing slashes', () => {
    expect(ok('  http://192.168.1.10:8077///  ')).toBe('http://192.168.1.10:8077');
  });

  it('adds http:// when no scheme is given', () => {
    expect(ok('192.168.1.10:8077')).toBe('http://192.168.1.10:8077');
    expect(ok('my-laptop.tailnet.ts.net')).toBe('http://my-laptop.tailnet.ts.net');
  });

  it('keeps https and lower-cases the scheme', () => {
    expect(ok('https://api.example.com')).toBe('https://api.example.com');
    expect(ok('HTTP://10.0.0.2:8077')).toBe('http://10.0.0.2:8077');
  });

  it('rejects empty input', () => {
    expect(normaliseServerUrl('').ok).toBe(false);
    expect(normaliseServerUrl('   ').ok).toBe(false);
    expect(normaliseServerUrl(null).ok).toBe(false);
    expect(normaliseServerUrl(undefined).ok).toBe(false);
  });

  it('rejects paths, spaces and other schemes', () => {
    for (const bad of [
      'http://10.0.0.2:8077/api/v1',
      'http://10.0.0.2 :8077',
      'ftp://10.0.0.2',
      'http://',
      'https:///',
      'http://10.0.0.2:80a',
      'http://10.0.0.2:8077:1',
    ]) {
      expect(normaliseServerUrl(bad).ok).toBe(false);
    }
  });
});
