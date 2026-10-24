import * as SecureStore from 'expo-secure-store';
import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { apiFetch, DEFAULT_API_URL, getApiUrl, isNetworkError, setApiUrl, setAuthToken, setUnauthorizedHandler } from './api';
import { normaliseServerUrl, SERVER_URL_KEY } from './serverUrl';
import type { LoginResponse, RegisterRequest, User } from './types';

const TOKEN_KEY = 'spendcheck.token';
const EXPIRES_KEY = 'spendcheck.expiresAt';

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

async function persist(token: string, expiresAt: string) {
  await SecureStore.setItemAsync(TOKEN_KEY, token);
  await SecureStore.setItemAsync(EXPIRES_KEY, expiresAt);
}

async function clearPersisted() {
  await SecureStore.deleteItemAsync(TOKEN_KEY);
  await SecureStore.deleteItemAsync(EXPIRES_KEY);
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
        const [storedToken, expiresAt] = await Promise.all([
          SecureStore.getItemAsync(TOKEN_KEY),
          SecureStore.getItemAsync(EXPIRES_KEY),
        ]);
        const expired = !expiresAt || Number.isNaN(Date.parse(expiresAt)) || Date.parse(expiresAt) <= Date.now();
        if (!storedToken || expired) {
          await signOutLocal();
          return;
        }
        setAuthToken(storedToken);
        const me = await apiFetch<User>('/me');
        if (cancelled) return;
        setToken(storedToken);
        setUser(me);
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
    await persist(res.token, res.expiresAt);
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
