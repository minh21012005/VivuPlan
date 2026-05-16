"use client";

import { useEffect, useState } from "react";

interface NominatimResult {
  lat: string;
  lon: string;
  display_name: string;
}

interface GeoCoords {
  lat: number;
  lon: number;
}

// In-memory cache to avoid re-geocoding on re-renders
const geocodeCache = new Map<string, GeoCoords | null>();

/**
 * Resolves lat/lon for a place name using Nominatim (OpenStreetMap).
 * Returns null while loading or if the place cannot be resolved.
 * Results are cached for the lifetime of the page session.
 */
export function useGeocode(placeName?: string | null): GeoCoords | null {
  const [coords, setCoords] = useState<GeoCoords | null>(null);

  useEffect(() => {
    if (!placeName?.trim()) return;

    const key = placeName.trim().toLowerCase();

    // Return cached result immediately (including null for unresolvable places)
    if (geocodeCache.has(key)) {
      setCoords(geocodeCache.get(key) ?? null);
      return;
    }

    const url =
      `https://nominatim.openstreetmap.org/search` +
      `?q=${encodeURIComponent(placeName.trim())}` +
      `&format=json&limit=1&countrycodes=vn`;

    fetch(url, {
      headers: { "User-Agent": "VivuPlan/1.0 (travel-planning-app)" },
    })
      .then((r) => r.json())
      .then((data: NominatimResult[]) => {
        if (data.length > 0) {
          const resolved: GeoCoords = {
            lat: parseFloat(data[0].lat),
            lon: parseFloat(data[0].lon),
          };
          geocodeCache.set(key, resolved);
          setCoords(resolved);
        } else {
          geocodeCache.set(key, null); // cache miss so we don't retry
        }
      })
      .catch(() => {
        // Silently fail — geocoding is non-critical
      });
  }, [placeName]);

  return coords;
}
