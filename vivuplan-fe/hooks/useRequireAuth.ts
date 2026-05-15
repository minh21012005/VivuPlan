"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";

/**
 * Redirects to /unauthorized if the user is not logged in.
 * Optionally redirects to /forbidden if a custom condition is met.
 *
 * Handles the case where the login page writes directly to localStorage
 * without going through the AuthContext by syncing from storage first.
 */
export function useRequireAuth(forbiddenCondition?: (user: NonNullable<ReturnType<typeof useAuth>["user"]>) => boolean) {
  const router = useRouter();
  const { user, loading, syncFromStorage } = useAuth();

  useEffect(() => {
    if (loading) return;

    if (!user) {
      // The login page might have written to localStorage without updating the AuthContext.
      // Try to sync from storage before deciding to redirect.
      const token = localStorage.getItem("vp_token");
      const savedUser = localStorage.getItem("vp_user");

      if (token && savedUser) {
        // Context is out of sync — sync it and stay on page
        syncFromStorage();
        return;
      }

      // Genuinely not logged in
      router.push("/unauthorized");
      return;
    }

    if (forbiddenCondition && forbiddenCondition(user)) {
      router.push("/forbidden");
    }
  }, [loading, user, router, syncFromStorage, forbiddenCondition]);

  return { user, loading };
}
