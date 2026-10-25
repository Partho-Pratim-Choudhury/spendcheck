import * as SecureStore from 'expo-secure-store';
import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { apiFetch, DEFAULT_API_URL, getApiUrl, isNetworkError, setApiUrl, setAuthToken, setUnauthorizedHandler } from './api';
import { normaliseServerUrl, SERVER_URL_KEY } from './serverUrl';
import type { LoginResponse, RegisterRequest, User } from './types';

// The free backend host (Render) sleeps when idle and can take ~a minute to wake; give the
// auth calls a long timeout so the first attempt after inactivity still succeeds.
const WAKE_TIMEOUT_MS = 60_000;

// Startup re-validation runs in the background behind an already-restored session, so it must never
// block the UI waiting for a sleeping server. Keep it short — an unreachable server just leaves the
// optimistic session in place (the data screens have their own cold-start retry). login/register
// still use WAKE_TIMEOUT_MS because an explicit tap may legitimately wait through a cold start.
const REFRESH_TIMEOUT_MS = 12_000;

const TOKEN_KEY = 'spendcheck.token';
const EXPIRES_KEY = 'spendcheck.expiresAt';
const USER_KEY = 'spendcheck.user';

export type AuthStatus = 'loading' | 'signedOut' | 'signedIn';

interface AuthContextValue {
  status: AuthStatus;
  user: User | null;
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  /** Invite-gated self sign-up; signs in on success exactly like login. */
  register: (input: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  /** Replace the cached user after a profile change (e.g. salary day). */
  updateUser: (user: User) => void;
  /** Backend origin in use (stored override, else the build-time default); null if none. */
  serverUrl: string | null;
  /** Use and persist an already-normalised server URL. */
  saveServerUrl: (url: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

async function persist(token: string, expiresAt: string, user: User) {
  await SecureStore.setItemAsync(TOKEN_KEY, token);
  await SecureStore.setItemAsync(EXPIRES_KEY, expiresAt);
  await SecureStore.setItemAsync(USER_KEY, JSON.stringify(user));
}

async function clearPersisted() {
  await SecureStore.deleteItemAsync(TOKEN_KEY);
  await SecureStore.deleteItemAsync(EXPIRES_KEY);
  await SecureStore.deleteItemAsync(USER_KEY);
}

/** Parse a cached user blob; null on a missing/corrupt value so startup falls back to awaiting /me. */
function readCachedUser(raw: string | null): User | null {
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (parsed && typeof parsed === 'object' && 'id' in parsed && 'email' in parsed) {
      return parsed as User;
    }
  } catch {
    // Corrupt JSON: treat as no cache.
  }
  return null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [serverUrl, setServerUrlState] = useState<string | null>(getApiUrl);

  const signOutLocal = useCallback(async () => {
    setAuthToken(null);
    setToken(null);
    setUser(null);
    setStatus('signedOut');
    try {
      await clearPersisted();
    } catch {
      // Secure store failures shouldn't block signing out.
    }
  }, []);

  // Any 401 from an authenticated call -> drop the session; the root guard routes to login.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      void signOutLocal();
    });
    return () => setUnauthorizedHandler(null);
  }, [signOutLocal]);

  // Restore session on start.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      // Server URL first, so /me goes to the user's chosen server. A bad/missing value keeps the default.
      try {
        const stored = normaliseServerUrl(await SecureStore.getItemAsync(SERVER_URL_KEY));
        const url = stored.ok ? stored.url : DEFAULT_API_URL;
        setApiUrl(url);
        if (!cancelled) setServerUrlState(url);
      } catch {
        // Secure store unavailable: stay on the build-time default.
      }
      try {
        const [storedToken, expiresAt, cachedUserRaw] = await Promise.all([
          SecureStore.getItemAsync(TOKEN_KEY),
          SecureStore.getItemAsync(EXPIRES_KEY),
          SecureStore.getItemAsync(USER_KEY),
        ]);
        // `!expiresAt` in the guard narrows expiresAt to string below, so persist() can reuse it.
        if (!storedToken || !expiresAt) {
          await signOutLocal();
          return;
        }
        const expiry = Date.parse(expiresAt);
        if (Number.isNaN(expiry) || expiry <= Date.now()) {
          await signOutLocal();
          return;
        }

        const cachedUser = readCachedUser(cachedUserRaw);
        if (cachedUser) {
          // Optimistic startup: the token is unexpired and we have the user — show the app at once
          // and never await /me, so a sleeping server can't stall the splash.
          setAuthToken(storedToken);
          setToken(storedToken);
          setUser(cachedUser);
          setStatus('signedIn');
          // Re-validate in the background with a short timeout.
          void (async () => {
            try {
              const me = await apiFetch<User>('/me', { timeoutMs: REFRESH_TIMEOUT_MS });
              if (cancelled) return;
              setUser(me);
              await persist(storedToken, expiresAt, me);
            } catch {
              // A 401 is handled by the unauthorized handler (signs out); on any network/timeout error
              // we keep the optimistic session so a mere cold start never logs the user out.
            }
          })();
          return;
        }

        // Older install with no cached user: fall back to the original blocking validation.
        setAuthToken(storedToken);
        const me = await apiFetch<User>('/me', { timeoutMs: WAKE_TIMEOUT_MS });
        if (cancelled) return;
        setToken(storedToken);
        setUser(me);
        await persist(storedToken, expiresAt, me); // cache the user so the next launch is optimistic
        setStatus('signedIn');
      } catch (e) {
        if (cancelled) return;
        if (isNetworkError(e)) {
          // Server unreachable: can't validate. Show login (in memory only) but keep the stored
          // token so the next launch can retry instead of forcing a re-login.
          setAuthToken(null);
          setStatus('signedOut');
          return;
        }
        // 401 is already handled by the unauthorized handler; anything else also drops the session.
        await signOutLocal();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [signOutLocal]);

  // Single post-auth path: login and register both land here once the server hands back a session.
  const applyAuth = useCallback(async (res: LoginResponse) => {
    await persist(res.token, res.expiresAt, res.user);
    setAuthToken(res.token);
    setToken(res.token);
    setUser(res.user);
    setStatus('signedIn');
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await apiFetch<LoginResponse>('/auth/login', {
        method: 'POST',
        auth: false,
        body: { email: email.trim(), password },
        timeoutMs: WAKE_TIMEOUT_MS, // the free host can take ~a minute to wake from idle
      });
      await applyAuth(res);
    },
    [applyAuth],
  );

  const register = useCallback(
    async (input: RegisterRequest) => {
      const res = await apiFetch<LoginResponse>('/auth/register', {
        method: 'POST',
        auth: false,
        timeoutMs: WAKE_TIMEOUT_MS,
        body: {
          email: input.email.trim(),
          password: input.password,
          name: input.name.trim(),
          salaryDay: input.salaryDay,
          inviteCode: input.inviteCode.trim(),
        },
      });
      await applyAuth(res);
    },
    [applyAuth],
  );

  const saveServerUrl = useCallback(async (url: string) => {
    setApiUrl(url);
    setServerUrlState(url);
    try {
      await SecureStore.setItemAsync(SERVER_URL_KEY, url);
    } catch {
      // Still used for this session; just won't survive a restart.
    }
  }, []);

  const logout = useCallback(() => signOutLocal(), [signOutLocal]);

  const updateUser = useCallback((u: User) => setUser(u), []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      token,
      login,
      register,
      logout,
      updateUser,
      serverUrl,
      saveServerUrl,
    }),
    [status, user, token, login, register, logout, updateUser, serverUrl, saveServerUrl],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
