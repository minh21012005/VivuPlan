"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";

/**
 * Redirects to /unauthorized if the user is not logged in.
 * Optionally redirects to /forbidden if a custom condition is met.
 *
 * @param forbiddenCondition - optional extra check; if true → redirect to /forbidden
 * @returns { user, loading } from useAuth
 */
export function useRequireAuth(forbiddenCondition?: (user: NonNullable<ReturnType<typeof useAuth>["user"]>) => boolean) {
  const router = useRouter();
  const { user, loading } = useAuth();

  useEffect(() => {
    if (loading) return;
    if (!user) {
      router.push("/unauthorized");
      return;
    }
    if (forbiddenCondition && forbiddenCondition(user)) {
      router.push("/forbidden");
    }
  }, [loading, user, router, forbiddenCondition]);

  return { user, loading };
}
