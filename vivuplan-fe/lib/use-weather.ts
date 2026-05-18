"use client";

import { useEffect, useState, useRef } from "react";
import type { DailyWeather } from "@/lib/weather-utils";
import { destinationApi } from "@/lib/api";

// Persistent cache using localStorage to avoid re-fetching across page reloads
const CACHE_KEY_PREFIX = "weather_cache_";
const CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes

function getCachedWeather(key: string): DailyWeather[] | null {
  if (typeof window === "undefined") return null;
  try {
    const cachedStr = localStorage.getItem(CACHE_KEY_PREFIX + key);
    if (!cachedStr) return null;
    const cached = JSON.parse(cachedStr);
    if (Date.now() - cached.ts < CACHE_TTL_MS) {
      return cached.data;
    }
    localStorage.removeItem(CACHE_KEY_PREFIX + key);
  } catch {
    // Ignore localStorage errors
  }
  return null;
}

function setCachedWeather(key: string, data: DailyWeather[]) {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(
      CACHE_KEY_PREFIX + key,
      JSON.stringify({ data, ts: Date.now() })
    );
  } catch {
    // Ignore localStorage errors
  }
}

/**
 * Fetch 16-day daily forecast through the backend for a given lat/lon.
 * Returns an empty array while loading or on error (fails silently — weather is non-critical UI).
 */
export function useWeather(lat?: number, lon?: number) {
  const [forecast, setForecast] = useState<DailyWeather[]>([]);
  const [loading, setLoading] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (lat == null || lon == null) {
      return;
    }

    const key = `${lat.toFixed(4)},${lon.toFixed(4)}`;
    const cached = getCachedWeather(key);
    if (cached) {
      queueMicrotask(() => setForecast(cached));
      return;
    }

    abortRef.current?.abort();
    abortRef.current = new AbortController();
    queueMicrotask(() => setLoading(true));

    const signal = abortRef.current.signal;
    destinationApi.weather({ lat, lon })
      .then((data) => {
        if (signal.aborted) return;
        setCachedWeather(key, data);
        setForecast(data);
      })
      .catch(() => {
        // Silently fail — weather is enhancement, not core feature
      })
      .finally(() => {
        if (!signal.aborted) setLoading(false);
      });

    return () => {
      abortRef.current?.abort();
    };
  }, [lat, lon]);

  /**
   * Get the forecast for a specific calendar date string ("YYYY-MM-DD")
   */
  function getByDate(dateStr: string): DailyWeather | undefined {
    return forecast.find((d) => d.date === dateStr);
  }

  /**
   * Get forecast for a trip day index (0-based) given the trip start date
   */
  function getByDayIndex(dayIndex: number, startDate?: string): DailyWeather | undefined {
    if (!startDate) return forecast[dayIndex];

    const date = new Date(`${startDate}T00:00:00`);
    date.setDate(date.getDate() + dayIndex);

    // Format as YYYY-MM-DD using local time to avoid UTC shift
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    const dateStr = `${year}-${month}-${day}`;

    return getByDate(dateStr);
  }

  return { forecast, loading, getByDate, getByDayIndex };
}
