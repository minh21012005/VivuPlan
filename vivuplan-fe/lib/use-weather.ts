"use client";

import { useEffect, useState, useRef } from "react";
import type { DailyWeather } from "@/lib/weather-utils";

interface OpenMeteoResponse {
  daily: {
    time: string[];
    weathercode: number[];
    temperature_2m_max: number[];
    temperature_2m_min: number[];
    precipitation_sum: number[];
    windspeed_10m_max: number[];
    precipitation_probability_max: number[];
  };
}

// Simple in-memory cache to avoid re-fetching on re-renders
const cache = new Map<string, { data: DailyWeather[]; ts: number }>();
const CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes

async function fetchWeather(lat: number, lon: number): Promise<DailyWeather[]> {
  const url =
    `https://api.open-meteo.com/v1/forecast` +
    `?latitude=${lat.toFixed(4)}&longitude=${lon.toFixed(4)}` +
    `&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_sum,windspeed_10m_max,precipitation_probability_max` +
    `&timezone=Asia%2FHo_Chi_Minh` +
    `&forecast_days=16`;

  const res = await fetch(url);
  if (!res.ok) throw new Error("Weather fetch failed");
  const json: OpenMeteoResponse = await res.json();

  return json.daily.time.map((date, i) => ({
    date,
    code: json.daily.weathercode[i] ?? 0,
    maxTemp: json.daily.temperature_2m_max[i] ?? 0,
    minTemp: json.daily.temperature_2m_min[i] ?? 0,
    precipitationMm: json.daily.precipitation_sum[i] ?? 0,
    precipitationProbability: json.daily.precipitation_probability_max[i] ?? 0,
    windspeedKmh: json.daily.windspeed_10m_max[i] ?? 0,
  }));
}

/**
 * Fetch 16-day daily forecast from Open-Meteo for a given lat/lon.
 * Returns an empty array while loading or on error (fails silently — weather is non-critical UI).
 */
export function useWeather(lat?: number, lon?: number) {
  const [forecast, setForecast] = useState<DailyWeather[]>([]);
  const [loading, setLoading] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (lat == null || lon == null) return;

    const key = `${lat.toFixed(4)},${lon.toFixed(4)}`;
    const cached = cache.get(key);
    if (cached && Date.now() - cached.ts < CACHE_TTL_MS) {
      setForecast(cached.data);
      return;
    }

    abortRef.current?.abort();
    abortRef.current = new AbortController();
    setLoading(true);

    fetchWeather(lat, lon)
      .then((data) => {
        cache.set(key, { data, ts: Date.now() });
        setForecast(data);
      })
      .catch(() => {
        // Silently fail — weather is enhancement, not core feature
      })
      .finally(() => setLoading(false));

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
