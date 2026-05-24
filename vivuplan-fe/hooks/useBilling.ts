"use client";

import { useBillingContext } from "@/context/BillingContext";

export function useBilling() {
  return useBillingContext();
}
