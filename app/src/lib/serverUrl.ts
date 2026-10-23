/** SecureStore key for the user-chosen backend origin. */
export const SERVER_URL_KEY = 'spendcheck.serverUrl';

export const SERVER_URL_PLACEHOLDER = 'http://192.168.x.x:8077';

/** Origin only: scheme + hostname / IPv4 + optional port, no path, query, fragment or userinfo. */
const SERVER_URL_PATTERN = /^https?:\/\/[A-Za-z0-9.-]+(?::(\d{1,5}))?$/;

export type ServerUrlResult = { ok: true; url: string } | { ok: false; error: string };

/** Trim, strip trailing slashes, default to http://, then validate as a bare origin. */
export function normaliseServerUrl(input: string | null | undefined): ServerUrlResult {
  let url = (input ?? '').trim();
  if (!url.replace(/\/+$/, '')) return { ok: false, error: 'Enter the server address, e.g. http://192.168.1.10:8077' };
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(url)) url = `http://${url}`;
  // Strip after adding the scheme so a bare "http://" doesn't collapse into a host named "http:".
  url = url.replace(/\/+$/, '');
  // Scheme is case-insensitive; keep the rest as typed.
  url = url.replace(/^[a-z][a-z0-9+.-]*:/i, (s) => s.toLowerCase());
  const m = SERVER_URL_PATTERN.exec(url);
  const port = m?.[1] === undefined ? null : Number(m[1]);
  if (!m || (port !== null && (port < 1 || port > 65535))) {
    return { ok: false, error: 'Use http(s)://host[:port] with no path, e.g. http://192.168.1.10:8077' };
  }
  return { ok: true, url };
}
