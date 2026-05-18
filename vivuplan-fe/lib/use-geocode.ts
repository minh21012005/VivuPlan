"use client";

import { useEffect, useState } from "react";
import { destinationApi } from "@/lib/api";

interface GeoCoords {
  lat: number;
  lon: number;
}

// In-memory cache to avoid re-geocoding on re-renders
const geocodeCache = new Map<string, GeoCoords | null>();

/**
 * Resolves lat/lon through the backend so DB coordinates, Nominatim fallback,
 * caching, and logging stay consistent with itinerary generation.
 */
export function useGeocode(placeName?: string | null): GeoCoords | null {
  const [coords, setCoords] = useState<GeoCoords | null>(null);

  useEffect(() => {
    if (!placeName?.trim()) {
      return;
    }

    const key = placeName.trim().toLowerCase();

    // Return cached result immediately (including null for unresolvable places)
    if (geocodeCache.has(key)) {
      queueMicrotask(() => setCoords(geocodeCache.get(key) ?? null));
      return;
    }

    destinationApi.geocode(placeName.trim())
      .then((resolved) => {
        geocodeCache.set(key, resolved);
        setCoords(resolved);
      })
      .catch(() => {
        geocodeCache.set(key, null);
        setCoords(null);
      });
  }, [placeName]);

  return coords;
}
