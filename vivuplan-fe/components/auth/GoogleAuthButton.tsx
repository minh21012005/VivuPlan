"use client";

import Script from "next/script";
import { useCallback, useEffect, useRef, useState } from "react";
import { Globe } from "lucide-react";
import { authApi, type AuthResponse } from "@/lib/api";

interface GoogleCredentialResponse {
  credential?: string;
}

interface GoogleButtonOptions {
  theme: "outline";
  size: "large";
  type: "standard";
  shape: "rectangular";
  text: "signin_with";
  width: number;
  locale: string;
}

interface GoogleAccountsId {
  initialize: (options: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
    auto_select?: boolean;
    cancel_on_tap_outside?: boolean;
  }) => void;
  renderButton: (parent: HTMLElement, options: GoogleButtonOptions) => void;
  cancel: () => void;
}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: GoogleAccountsId;
      };
    };
  }
}

interface GoogleAuthButtonProps {
  onSuccess: (response: AuthResponse) => void;
  onError: (message: string) => void;
}

const googleClientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

export function GoogleAuthButton({ onSuccess, onError }: GoogleAuthButtonProps) {
  const buttonRef = useRef<HTMLDivElement>(null);
  const [scriptReady, setScriptReady] = useState(false);
  const [loading, setLoading] = useState(false);
  const [buttonWidth, setButtonWidth] = useState(400);

  const handleCredential = useCallback(
    async (response: GoogleCredentialResponse) => {
      if (!response.credential) {
        onError("Khong nhan duoc thong tin dang nhap tu Google");
        return;
      }

      setLoading(true);
      try {
        const authResponse = await authApi.google({ idToken: response.credential });
        onSuccess(authResponse);
      } catch (err: unknown) {
        onError(err instanceof Error ? err.message : "Dang nhap Google that bai");
      } finally {
        setLoading(false);
      }
    },
    [onError, onSuccess]
  );

  useEffect(() => {
    const element = buttonRef.current;
    if (!element) return;

    const updateWidth = () => {
      setButtonWidth(Math.min(400, Math.max(200, Math.floor(element.clientWidth || 400))));
    };

    updateWidth();
    const observer = new ResizeObserver(updateWidth);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!scriptReady || !googleClientId || !buttonRef.current || !window.google?.accounts?.id) return;

    buttonRef.current.innerHTML = "";
    window.google.accounts.id.initialize({
      client_id: googleClientId,
      callback: handleCredential,
      auto_select: false,
      cancel_on_tap_outside: true,
    });
    window.google.accounts.id.renderButton(buttonRef.current, {
      theme: "outline",
      size: "large",
      type: "standard",
      shape: "rectangular",
      text: "signin_with",
      width: buttonWidth,
      locale: "vi",
    });

    return () => {
      window.google?.accounts?.id.cancel();
    };
  }, [buttonWidth, handleCredential, scriptReady]);

  if (!googleClientId) {
    return (
      <button
        type="button"
        disabled
        className="btn btn-secondary"
        style={{ width: "100%", marginBottom: "20px", justifyContent: "center", padding: "12px" }}
      >
        <Globe size={17} /> Google chua duoc cau hinh
      </button>
    );
  }

  return (
    <div style={{ width: "100%", marginBottom: "20px" }}>
      <Script
        src="https://accounts.google.com/gsi/client"
        async
        defer
        onLoad={() => setScriptReady(true)}
        onReady={() => setScriptReady(true)}
      />
      {loading && (
        <button
          type="button"
          disabled
          className="btn btn-secondary"
          style={{ width: "100%", justifyContent: "center", padding: "12px" }}
        >
          <div className="spinner" /> Dang nhap voi Google...
        </button>
      )}
      <div
        ref={buttonRef}
        style={{
          display: loading ? "none" : "flex",
          minHeight: "44px",
          width: "100%",
          justifyContent: "center",
        }}
      />
    </div>
  );
}
