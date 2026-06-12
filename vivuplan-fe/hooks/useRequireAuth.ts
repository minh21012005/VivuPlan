"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";

const LOGOUT_REDIRECT_FLAG = "vp_logout_redirect";
const LOGOUT_REDIRECT_WINDOW_MS = 5000;

/**
 * Redirects to /unauthorized if the user is not logged in.
 * Optionally redirects to /forbidden if a custom condition is met.
 * Waits for AuthContext to verify any stored token before redirecting.
 */
export function useRequireAuth(forbiddenCondition?: (user: NonNullable<ReturnType<typeof useAuth>["user"]>) => boolean) {
  const router = useRouter();
  const { user, loading } = useAuth();

  const isForbidden = user && forbiddenCondition ? forbiddenCondition(user) : false;
  const isAuthorized = !!user && !isForbidden;

  useEffect(() => {
    if (loading) return;

    if (!user) {
      const logoutAt = Number(window.sessionStorage.getItem(LOGOUT_REDIRECT_FLAG) ?? 0);
      if (logoutAt > 0 && Date.now() - logoutAt < LOGOUT_REDIRECT_WINDOW_MS) {
        window.sessionStorage.removeItem(LOGOUT_REDIRECT_FLAG);
        router.replace("/");
        return;
      }
      window.sessionStorage.removeItem(LOGOUT_REDIRECT_FLAG);
      router.replace("/unauthorized");
      return;
    }

    if (isForbidden) {
      router.replace("/forbidden");
    }
  }, [loading, user, router, isForbidden]);

  return { user, loading, authorized: isAuthorized };
}
