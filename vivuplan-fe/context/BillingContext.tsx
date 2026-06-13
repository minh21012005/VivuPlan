"use client";

import React, { createContext, ReactNode, useCallback, useContext, useEffect, useRef, useState } from "react";
import { billingApi, type BillingWallet } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";

interface BillingContextType {
  wallet: BillingWallet | null;
  loading: boolean;
  refreshWallet: () => Promise<void>;
  clearWallet: () => void;
}

const BillingContext = createContext<BillingContextType | undefined>(undefined);

export function BillingProvider({ children }: { children: ReactNode }) {
  const { isLoggedIn, loading: authLoading } = useAuth();
  const [wallet, setWallet] = useState<BillingWallet | null>(null);
  const [loading, setLoading] = useState(false);
  const requestSeq = useRef(0);

  const clearWallet = useCallback(() => {
    requestSeq.current += 1;
    setWallet(null);
    setLoading(false);
  }, []);

  const refreshWallet = useCallback(async () => {
    if (!isLoggedIn) {
      clearWallet();
      return;
    }
    const requestId = ++requestSeq.current;
    setLoading(true);
    try {
      const data = await billingApi.me();
      if (requestId === requestSeq.current) {
        setWallet(data.wallet);
      }
    } catch {
      if (requestId === requestSeq.current) {
        setWallet(null);
      }
    } finally {
      if (requestId === requestSeq.current) {
        setLoading(false);
      }
    }
  }, [clearWallet, isLoggedIn]);

  useEffect(() => {
    let cancelled = false;
    if (authLoading) return;
    if (!isLoggedIn) {
      queueMicrotask(() => {
        if (!cancelled) clearWallet();
      });
      return () => {
        cancelled = true;
      };
    }
    queueMicrotask(() => {
      if (cancelled) return;
      void refreshWallet();
    });
    return () => {
      cancelled = true;
    };
  }, [authLoading, clearWallet, isLoggedIn, refreshWallet]);

  return (
    <BillingContext.Provider value={{ wallet, loading, refreshWallet, clearWallet }}>
      {children}
    </BillingContext.Provider>
  );
}

export function useBillingContext() {
  const context = useContext(BillingContext);
  if (context === undefined) {
    throw new Error("useBilling must be used within a BillingProvider");
  }
  return context;
}
