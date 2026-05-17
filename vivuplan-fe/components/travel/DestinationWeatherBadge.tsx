"use client";

import { useWeather } from "@/lib/use-weather";
import { interpretWeatherCode } from "@/lib/weather-utils";
import { WeatherIcon } from "@/components/travel/WeatherIcon";

interface Props {
  lat: number;
  lon: number;
}

/**
 * Lazily fetches today's weather for a destination and renders a small inline badge.
 * Renders nothing while loading or on error.
 */
export function DestinationWeatherBadge({ lat, lon }: Props) {
  const { forecast } = useWeather(lat, lon);

  if (forecast.length === 0) return null;

  const today = forecast[0];
  const condition = interpretWeatherCode(today.code);
  const avgTemp = Math.round((today.maxTemp + today.minTemp) / 2);

  return (
    <span
      title={`${condition.label} · ${today.minTemp.toFixed(0)}–${today.maxTemp.toFixed(0)}°C hôm nay`}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 4,
        fontSize: 12,
        fontWeight: 600,
        padding: "3px 8px",
        borderRadius: 100,
        background: "rgba(255,255,255,0.88)",
        color: "#0f172a",
        backdropFilter: "blur(4px)",
        boxShadow: "0 1px 4px rgba(0,0,0,0.15)",
      }}
    >
      <WeatherIcon iconKey={condition.iconKey} size={14} style={{ marginRight: 2 }} /> {avgTemp}°C
    </span>
  );
}
