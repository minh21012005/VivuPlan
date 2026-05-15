"use client";

import { useEffect, useMemo, useState } from "react";
import { destinationApi, type DestinationResponse } from "@/lib/api";
import { normalizeVietnameseSearch } from "@/lib/travel-data";

export function useDestinations(options: { featured?: boolean } = {}) {
  const [destinations, setDestinations] = useState<DestinationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    const request = options.featured ? destinationApi.featured() : destinationApi.list();
    request
      .then((data) => {
        if (!cancelled) setDestinations(data);
      })
      .catch(() => {
        if (!cancelled) setError("Không thể tải danh sách điểm đến");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [options.featured]);

  const destinationNames = useMemo(() => destinations.map((item) => item.name), [destinations]);
  const byName = useMemo(() => {
    const map = new Map<string, DestinationResponse>();
    destinations.forEach((item) => map.set(normalizeVietnameseSearch(item.name), item));
    return map;
  }, [destinations]);

  return { destinations, destinationNames, byName, loading, error };
}
