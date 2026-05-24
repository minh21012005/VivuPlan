"use client";

import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from "react";
import { authApi, type AuthResponse, type RegisterOtpResponse, type User } from "@/lib/api";

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
  error: string | null;
}

interface AuthContextType extends AuthState {
  isLoggedIn: boolean;
  login: (email: string, password: string) => Promise<AuthResponse>;
  register: (name: string, email: string, password: string) => Promise<RegisterOtpResponse>;
  requestRegisterOtp: (name: string, email: string, password: string) => Promise<RegisterOtpResponse>;
  verifyRegisterOtp: (email: string, otp: string) => Promise<AuthResponse>;
  setSession: (auth: AuthResponse) => void;
  logout: () => void;
  updateUser: (user: User) => void;
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
    setState({ user, token, loading: false, error: null });
  }, []);

  const updateUser = useCallback((user: User) => {
    setState((s) => ({ ...s, user }));
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem("vp_token");
    // Clean up legacy cached user data from older builds.
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
      const res = await authApi.requestRegisterOtp({ name, email, password });
      setState((s) => ({ ...s, loading: false, error: null }));
      return res;
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Đăng ký thất bại";
      setState((s) => ({ ...s, loading: false, error: msg }));
      throw e;
    }
  }, []);

  const requestRegisterOtp = register;

  const verifyRegisterOtp = useCallback(async (email: string, otp: string) => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const res = await authApi.verifyRegisterOtp({ email, otp });
      setToken(res.token, res.user);
      return res;
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Đăng ký thất bại";
      setState((s) => ({ ...s, loading: false, error: msg }));
      throw e;
    }
  }, [setToken]);

  const setSession = useCallback((auth: AuthResponse) => {
    setToken(auth.token, auth.user);
  }, [setToken]);

  useEffect(() => {
    const token = localStorage.getItem("vp_token");
    if (!token) {
      localStorage.removeItem("vp_user");
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
      requestRegisterOtp,
      verifyRegisterOtp,
      setSession,
      logout,
      updateUser,
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
