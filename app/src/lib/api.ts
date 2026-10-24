import { normaliseServerUrl } from './serverUrl';
import type { ProblemDetail } from './types';

/** Build-time default from .env (inlined at build time — must be accessed as a literal). */
const RAW_API_URL = process.env.EXPO_PUBLIC_API_URL;
const envUrl = normaliseServerUrl(RAW_API_URL);

/** Backend origin baked into this build, e.g. http://192.168.1.10:8077 — null when not configured. */
export const DEFAULT_API_URL: string | null = envUrl.ok ? envUrl.url : null;

// --- runtime server URL (set by AuthProvider from SecureStore, or by the login screen) ---------

let apiUrl: string | null = DEFAULT_API_URL;

/** Current backend origin (no trailing slash, no /api/v1) — null when not configured. */
export function getApiUrl(): string | null {
  return apiUrl;
}

/** Point all subsequent requests at a new origin. Pass an already-normalised URL (see serverUrl.ts). */
export function setApiUrl(url: string | null): void {
  apiUrl = url;
}

const HEALTH_TIMEOUT_MS = 5_000;

/** GET {url}/actuator/health (outside /api/v1, unauthenticated) — true on any 2xx within 5 s. */
export async function checkServerHealth(url: string): Promise<boolean> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HEALTH_TIMEOUT_MS);
  try {
    const res = await fetch(`${url}/actuator/health`, {
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    });
    return res.ok;
  } catch {
    return false;
  } finally {
    clearTimeout(timer);
  }
}

const REQUEST_TIMEOUT_MS = 15_000;

export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly fieldErrors: Record<string, string>;

  constructor(opts: { status: number; title: string; detail: string; fieldErrors?: Record<string, string> }) {
    super(opts.detail || opts.title);
    this.name = 'ApiError';
    this.status = opts.status;
    this.title = opts.title;
    this.detail = opts.detail;
    this.fieldErrors = opts.fieldErrors ?? {};
  }
}

/**
 * The server's own ProblemDetail `detail`, or null when apiFetch had to fall back (no detail,
 * a non-JSON body from a proxy, or a network error).
 */
export function serverDetail(e: ApiError): string | null {
  if (e.status === 0) return null;
  const d = e.detail.trim();
  if (!d || d === e.title || d === `Request failed with status ${e.status}`) return null;
  return d;
}

/** status 0 = never reached the server (config / network / timeout). */
export const isNetworkError = (e: unknown): e is ApiError => e instanceof ApiError && e.status === 0;

/** Worth sending again unchanged: network/timeout, 408, 429 or 5xx. Other 4xx and unknown errors are final. */
export const isRetryableError = (e: unknown): boolean =>
  e instanceof ApiError && (e.status === 0 || e.status === 408 || e.status === 429 || e.status >= 500);

// --- auth wiring (set by AuthProvider) -------------------------------------

let authToken: string | null = null;
let onUnauthorized: (() => void) | null = null;

export function setAuthToken(token: string | null): void {
  authToken = token;
}

/** Called on any 401 except from /auth/login; AuthProvider clears state and the guard routes to login. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

// --- fetch wrapper -----------------------------------------------------------

type Query = Record<string, string | number | boolean | null | undefined>;

export interface ApiFetchOptions extends Omit<RequestInit, 'body'> {
  /** JSON-serialised as the request body; a FormData body is sent as multipart as-is. */
  body?: unknown;
  /** Override the default 15 s timeout (e.g. voice uploads). */
  timeoutMs?: number;
  query?: Query;
  /** Skip the Authorization header (login). */
  auth?: boolean;
}

function buildUrl(base: string, path: string, query?: Query): string {
  const url = `${base}${path.startsWith('/') ? path : `/${path}`}`;
  if (!query) return url;
  const qs = Object.entries(query)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&');
  return qs ? `${url}?${qs}` : url;
}

export async function apiFetch<T>(path: string, opts: ApiFetchOptions = {}): Promise<T> {
  const { body, query, auth = true, headers, timeoutMs = REQUEST_TIMEOUT_MS, ...init } = opts;
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData;

  // Snapshot so error messages name the server this request actually went to.
  const baseUrl = apiUrl;
  if (!baseUrl) {
    throw new ApiError({
      status: 0,
      title: 'Server not configured',
      detail: 'No server address set. Set it with "Change server" on the Plan screen.',
    });
  }

  const finalHeaders: Record<string, string> = {
    Accept: 'application/json',
    ...(headers as Record<string, string> | undefined),
  };
  // FormData: let fetch set multipart/form-data with its boundary.
  if (body !== undefined && !isForm) finalHeaders['Content-Type'] = 'application/json';
  // Remember which token this request carried, so a late 401 can't sign out a newer session.
  const sentToken = auth ? authToken : null;
  if (sentToken) finalHeaders.Authorization = `Bearer ${sentToken}`;

  // Our own controller enforces the timeout; a caller's signal (e.g. React Query cancel) is forwarded into it.
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const external = init.signal;
  const forwardAbort = () => controller.abort();
  if (external?.aborted) controller.abort();
  else external?.addEventListener('abort', forwardAbort);

  let res: Response;
  let text: string;
  try {
    res = await fetch(buildUrl(`${baseUrl}/api/v1`, path, query), {
      ...init,
      headers: finalHeaders,
      body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
      signal: controller.signal,
    });
    // Inside the try so the timeout also covers a stalled body.
    text = await res.text();
  } catch (e) {
    if (external?.aborted) throw e;
    const timedOut = controller.signal.aborted;
    // Only the error message (no URL params, body or token) — visible via `adb logcat -s ReactNativeJS`.
    console.warn(`[api] ${path} failed before a response: ${e instanceof Error ? e.message : String(e)}`);
    throw new ApiError({
      status: 0,
      title: timedOut ? 'Request timed out' : 'Network error',
      detail: timedOut
        ? `${baseUrl} did not respond within ${Math.round(timeoutMs / 1000)}s.`
        : `Can't reach server at ${baseUrl}. Check the backend is running and your phone is on the same network.`,
    });
  } finally {
    clearTimeout(timer);
    external?.removeEventListener('abort', forwardAbort);
  }

  let data: unknown = undefined;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = undefined;
    }
  }

  if (!res.ok) {
    if (res.status === 401 && sentToken !== null && sentToken === authToken) onUnauthorized?.();
    const problem = (data ?? {}) as ProblemDetail;
    throw new ApiError({
      status: res.status,
      title: problem.title ?? `HTTP ${res.status}`,
      detail: problem.detail ?? problem.title ?? `Request failed with status ${res.status}`,
      fieldErrors: problem.errors,
    });
  }

  return data as T;
}
