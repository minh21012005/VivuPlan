"use client";

import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from "react";
import { authApi, type User } from "@/lib/api";

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
  error: string | null;
}

interface AuthContextType extends AuthState {
  isLoggedIn: boolean;
  login: (email: string, password: string) => Promise<any>;
  register: (name: string, email: string, password: string) => Promise<any>;
  logout: () => void;
  updateUser: (user: User) => void;
  syncFromStorage: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    token: null,
    loading: true,
    error: null,
  });

  const setToken = useCallback((token: string, user: User) => {
    localStorage.setItem("vp_token", token);
    localStorage.setItem("vp_user", JSON.stringify(user));
    setState({ user, token, loading: false, error: null });
  }, []);

  const updateUser = useCallback((user: User) => {
    localStorage.setItem("vp_user", JSON.stringify(user));
    setState((s) => ({ ...s, user }));
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem("vp_token");
    localStorage.removeItem("vp_user");
    setState({ user: null, token: null, loading: false, error: null });
    window.location.href = "/";
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const res = await authApi.login({ email, password });
      setToken(res.token, res.user);
      return res;
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Đăng nhập thất bại";
      setState((s) => ({ ...s, loading: false, error: msg }));
      throw e;
    }
  }, [setToken]);

  const register = useCallback(async (name: string, email: string, password: string) => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const res = await authApi.register({ name, email, password });
      setToken(res.token, res.user);
      return res;
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Đăng ký thất bại";
      setState((s) => ({ ...s, loading: false, error: msg }));
      throw e;
    }
  }, [setToken]);

  // Called by useRequireAuth when context is out of sync with localStorage
  // (e.g. after login page writes directly to localStorage)
  const syncFromStorage = useCallback(() => {
    const token = localStorage.getItem("vp_token");
    const savedUser = localStorage.getItem("vp_user");
    if (token && savedUser) {
      try {
        const user = JSON.parse(savedUser) as User;
        setState({ user, token, loading: false, error: null });
      } catch {
        /* ignore parse errors */
      }
    }
  }, []);

  useEffect(() => {
    const token = localStorage.getItem("vp_token");
    if (!token) {
      setState((s) => ({ ...s, loading: false }));
      return;
    }
    authApi.me()
      .then((user) => setState({ user, token, loading: false, error: null }))
      .catch(() => {
        localStorage.removeItem("vp_token");
        localStorage.removeItem("vp_user");
        setState({ user: null, token: null, loading: false, error: null });
      });
  }, []);

  return (
    <AuthContext.Provider value={{
      ...state,
      isLoggedIn: !!state.user,
      login,
      register,
      logout,
      updateUser,
      syncFromStorage,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuthContext() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
