"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";

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
      router.replace("/unauthorized");
      return;
    }

    if (isForbidden) {
      router.replace("/forbidden");
    }
  }, [loading, user, router, isForbidden]);

  return { user, loading, authorized: isAuthorized };
}
