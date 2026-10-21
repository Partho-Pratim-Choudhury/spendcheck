import type { ProblemDetail } from './types';

/** Raw value from .env (inlined at build time — must be accessed as a literal). */
const RAW_API_URL = process.env.EXPO_PUBLIC_API_URL;

/** Backend origin, e.g. http://192.168.1.10:8077 — null when not configured. */
export const API_URL: string | null = RAW_API_URL?.trim() ? RAW_API_URL.trim().replace(/\/+$/, '') : null;
export const API_BASE: string | null = API_URL ? `${API_URL}/api/v1` : null;

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

/** status 0 = never reached the server (config / network / timeout). */
export const isNetworkError = (e: unknown): e is ApiError => e instanceof ApiError && e.status === 0;

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

  if (!API_BASE) {
    throw new ApiError({
      status: 0,
      title: 'API URL not configured',
      detail: 'EXPO_PUBLIC_API_URL is not set. Copy .env.example to .env, set your laptop IP, and restart Expo.',
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
    res = await fetch(buildUrl(API_BASE, path, query), {
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
    throw new ApiError({
      status: 0,
      title: timedOut ? 'Request timed out' : 'Network error',
      detail: timedOut
        ? `${API_URL} did not respond within ${Math.round(timeoutMs / 1000)}s.`
        : `Can't reach server at ${API_URL}. Check the backend is running and your phone is on the same network.`,
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
