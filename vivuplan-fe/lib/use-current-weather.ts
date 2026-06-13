"use client";

import { useEffect, useRef, useState } from "react";
import { destinationApi, type CurrentWeatherResponse } from "@/lib/api";

const CACHE_KEY_PREFIX = "current_weather_cache_";
const CACHE_TTL_MS = 10 * 60 * 1000;

function currentHourKey() {
  return Math.floor(Date.now() / (60 * 60 * 1000));
}

function getCachedCurrentWeather(key: string): CurrentWeatherResponse | null {
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
    // Ignore localStorage errors.
  }
  return null;
}

function setCachedCurrentWeather(key: string, data: CurrentWeatherResponse) {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(
      CACHE_KEY_PREFIX + key,
      JSON.stringify({ data, ts: Date.now() })
    );
  } catch {
    // Ignore localStorage errors.
  }
}

export function useCurrentWeather(lat?: number, lon?: number) {
  const [weather, setWeather] = useState<CurrentWeatherResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const requestIdRef = useRef(0);

  useEffect(() => {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;

    if (lat == null || lon == null) {
      queueMicrotask(() => {
        if (requestIdRef.current !== requestId) return;
        setWeather(null);
        setLoading(false);
      });
      return;
    }

    const key = `${lat.toFixed(4)},${lon.toFixed(4)}@${currentHourKey()}`;
    const cached = getCachedCurrentWeather(key);
    if (cached) {
      queueMicrotask(() => {
        if (requestIdRef.current !== requestId) return;
        setWeather(cached);
        setLoading(false);
      });
      return;
    }

    queueMicrotask(() => {
      if (requestIdRef.current === requestId) setLoading(true);
    });

    destinationApi.currentWeather({ lat, lon })
      .then((data) => {
        if (requestIdRef.current !== requestId) return;
        setCachedCurrentWeather(key, data);
        setWeather(data);
      })
      .catch(() => {
        if (requestIdRef.current === requestId) setWeather(null);
      })
      .finally(() => {
        if (requestIdRef.current === requestId) setLoading(false);
      });
  }, [lat, lon]);

  return { weather, loading };
}
