"use client";
import { useState, useEffect, useCallback } from "react";
import { authApi, type User } from "@/lib/api";

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
  error: string | null;
}

export function useAuth() {
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

  const logout = useCallback(() => {
    localStorage.removeItem("vp_token");
    localStorage.removeItem("vp_user");
    setState({ user: null, token: null, loading: false, error: null });
    window.location.href = "/";
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
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
    },
    [setToken]
  );

  const register = useCallback(
    async (name: string, email: string, password: string) => {
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
    },
    [setToken]
  );

  // On mount: restore session from localStorage
  useEffect(() => {
    const timer = window.setTimeout(() => {
      const token = localStorage.getItem("vp_token");
      if (!token) {
        setState((s) => ({ ...s, loading: false }));
        return;
      }
      authApi
        .me()
        .then((user) => setState({ user, token, loading: false, error: null }))
        .catch(() => {
          localStorage.removeItem("vp_token");
          localStorage.removeItem("vp_user");
          setState({ user: null, token: null, loading: false, error: null });
        });
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  return {
    ...state,
    isLoggedIn: !!state.user,
    login,
    register,
    logout,
    setToken,
  };
}
