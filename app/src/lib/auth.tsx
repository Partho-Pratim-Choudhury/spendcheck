import * as SecureStore from 'expo-secure-store';
import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { apiFetch, isNetworkError, setAuthToken, setUnauthorizedHandler } from './api';
import type { LoginResponse, User } from './types';

const TOKEN_KEY = 'spendcheck.token';
const EXPIRES_KEY = 'spendcheck.expiresAt';

export type AuthStatus = 'loading' | 'signedOut' | 'signedIn';

interface AuthContextValue {
  status: AuthStatus;
  user: User | null;
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
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

  const login = useCallback(async (email: string, password: string) => {
    const res = await apiFetch<LoginResponse>('/auth/login', {
      method: 'POST',
      auth: false,
      body: { email: email.trim(), password },
    });
    await persist(res.token, res.expiresAt);
    setAuthToken(res.token);
    setToken(res.token);
    setUser(res.user);
    setStatus('signedIn');
  }, []);

  const logout = useCallback(() => signOutLocal(), [signOutLocal]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, token, login, logout }),
    [status, user, token, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
