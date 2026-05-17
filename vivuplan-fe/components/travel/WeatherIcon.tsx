import React from "react";
import {
  Sun,
  CloudSun,
  CloudRain,
  CloudLightning,
  CloudFog,
  Snowflake,
  HelpCircle,
} from "lucide-react";

export type WeatherIconKey =
  | "sun"
  | "cloudy"
  | "fog"
  | "rain"
  | "snow"
  | "storm"
  | "unknown";

interface Props {
  iconKey: WeatherIconKey;
  size?: number;
  className?: string;
  style?: React.CSSProperties;
}

export function WeatherIcon({ iconKey, size = 16, className, style }: Props) {
  let IconComponent = HelpCircle;
  let color = "#64748b"; // Slate 500 fallback

  switch (iconKey) {
    case "sun":
      IconComponent = Sun;
      color = "#f59e0b"; // Amber 500
      break;
    case "cloudy":
      IconComponent = CloudSun;
      color = "#64748b"; // Slate 500
      break;
    case "fog":
      IconComponent = CloudFog;
      color = "#94a3b8"; // Slate 400
      break;
    case "rain":
      IconComponent = CloudRain;
      color = "#0284c7"; // Sky 600
      break;
    case "snow":
      IconComponent = Snowflake;
      color = "#38bdf8"; // Sky 300
      break;
    case "storm":
      IconComponent = CloudLightning;
      color = "#7c3aed"; // Violet 600
      break;
  }

  return (
    <IconComponent
      size={size}
      className={className}
      style={{ color, flexShrink: 0, ...style }}
    />
  );
}
