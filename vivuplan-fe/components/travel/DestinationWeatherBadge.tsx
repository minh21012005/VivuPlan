"use client";

import { useCurrentWeather } from "@/lib/use-current-weather";
import { interpretCurrentDisplayWeather } from "@/lib/weather-utils";
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
  const { weather } = useCurrentWeather(lat, lon);

  if (!weather) return null;

  const condition = interpretCurrentDisplayWeather(weather);
  const hour = weather.time?.slice(11, 16);

  return (
    <span
      title={`${condition.label} · ${hour ?? "hiện tại"} · mưa ${weather.precipitationProbability}% · gió ${weather.windspeedKmh.toFixed(0)} km/h`}
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
      <WeatherIcon iconKey={condition.iconKey} size={14} style={{ marginRight: 2 }} /> {weather.temperatureC.toFixed(0)}°C
    </span>
  );
}
