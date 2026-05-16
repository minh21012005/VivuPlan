// ─── Open-Meteo WMO Weather Interpretation Codes ─────────────────────────────

export interface DailyWeather {
  date: string;             // "YYYY-MM-DD"
  code: number;             // WMO weathercode
  maxTemp: number;
  minTemp: number;
  precipitationMm: number;
  precipitationProbability: number; // 0-100
  windspeedKmh: number;
}

export interface WeatherCondition {
  label: string;
  emoji: string;
  severity: "clear" | "mild" | "moderate" | "severe";
  isRainy: boolean;
  isWindy: boolean;
  isFoggy: boolean;
}

export function interpretWeatherCode(code: number): WeatherCondition {
  if (code === 0) return { label: "Trời nắng", emoji: "☀️", severity: "clear", isRainy: false, isWindy: false, isFoggy: false };
  if (code <= 3) return { label: "Có mây", emoji: "⛅", severity: "mild", isRainy: false, isWindy: false, isFoggy: false };
  if (code <= 49) return { label: "Sương mù", emoji: "🌫️", severity: "mild", isRainy: false, isWindy: false, isFoggy: true };
  if (code <= 57) return { label: "Mưa phùn", emoji: "🌦️", severity: "mild", isRainy: true, isWindy: false, isFoggy: false };
  if (code <= 65) return { label: code >= 63 ? "Mưa to" : "Mưa nhỏ", emoji: code >= 63 ? "🌧️" : "🌦️", severity: code >= 63 ? "severe" : "moderate", isRainy: true, isWindy: false, isFoggy: false };
  if (code <= 77) return { label: "Có tuyết", emoji: "❄️", severity: "severe", isRainy: false, isWindy: false, isFoggy: false };
  if (code <= 82) return { label: code === 82 ? "Mưa rào lớn" : "Mưa rào", emoji: code === 82 ? "⛈️" : "🌧️", severity: code === 82 ? "severe" : "moderate", isRainy: true, isWindy: false, isFoggy: false };
  if (code <= 86) return { label: "Mưa tuyết", emoji: "🌨️", severity: "severe", isRainy: true, isWindy: false, isFoggy: false };
  if (code <= 99) return { label: "Giông bão", emoji: "⛈️", severity: "severe", isRainy: true, isWindy: true, isFoggy: false };
  return { label: "N/A", emoji: "", severity: "mild", isRainy: false, isWindy: false, isFoggy: false };
}

// ─── Activity outdoor risk assessment ────────────────────────────────────────

const OUTDOOR_RISKY_KEYWORDS = [
  "thuyền", "kayak", "vịnh", "biển", "bơi", "lặn", "leo núi", "trekking",
  "cáp treo", "đèo", "thác", "ngoài trời", "công viên", "bãi biển", "hồ", "cao nguyên", "thung lũng", "di tích",
  "boat", "cruise", "snorkeling", "diving", "hiking", "waterfall", "beach", "plateau", "valley",
];

export function isOutdoorRiskyActivity(activityName: string, activityLocation?: string): boolean {
  // Replace punctuation with spaces, then pad with spaces to allow whole-word matching.
  // This prevents false positives e.g. "Hồng Mai" incorrectly matching keyword "hồ".
  const text = ` ${activityName} ${activityLocation ?? ""} `
    .toLowerCase()
    .replace(/[.,!?;:()\-/]/g, " ");

  return OUTDOOR_RISKY_KEYWORDS.some((kw) =>
    text.includes(` ${kw.toLowerCase()} `)
  );
}

export interface ActivityWeatherWarning {
  icon: "wind" | "rain" | "fog";
  message: string;
}

export function getActivityWeatherWarning(
  activityName: string,
  weather: DailyWeather,
  activityLocation?: string,
  activityType?: string,
): ActivityWeatherWarning | null {
  // Indoor activities and transport are not strictly 'outdoor activities' in the sense of being canceled by rain
  if (activityType === "FOOD" || activityType === "CAFE" || activityType === "ACCOMMODATION" || activityType === "TRANSPORT") {
    return null;
  }

  const condition = interpretWeatherCode(weather.code);
  const isRisky = isOutdoorRiskyActivity(activityName, activityLocation);
  if (!isRisky) return null;

  const actText = `"${activityName}"`;

  if (weather.windspeedKmh > 50 && (activityName.toLowerCase().includes("vịnh") || activityName.toLowerCase().includes("biển") || activityName.toLowerCase().includes("thuyền"))) {
    return { icon: "wind", message: `Gió mạnh (${weather.windspeedKmh.toFixed(0)} km/h) – Hoạt động ${actText} có thể bị hoãn. Kiểm tra lại với đơn vị vận hành.` };
  }
  if (condition.severity === "severe" && condition.isRainy) {
    return { icon: "rain", message: `Dự báo mưa lớn – Hoạt động ngoài trời ${actText} có thể bị ảnh hưởng, hãy chuẩn bị phương án thay thế.` };
  }
  if (condition.isFoggy && (activityName.toLowerCase().includes("cáp treo") || activityName.toLowerCase().includes("leo"))) {
    return { icon: "fog", message: `Có sương mù – Hoạt động ${actText} có thể bị hạn chế tầm nhìn, hãy kiểm tra trước khi khởi hành.` };
  }
  if (condition.severity === "moderate" && condition.isRainy && weather.precipitationProbability >= 40) {
    return { icon: "rain", message: `Xác suất mưa ${weather.precipitationProbability}% – Nên mang áo mưa khi tham gia ${actText}.` };
  }
  return null;
}

// ─── Packing suggestions ──────────────────────────────────────────────────────

export interface PackingSuggestion {
  icon: "jacket" | "scarf" | "sun-glasses" | "umbrella-heavy" | "umbrella" | "fog" | "wind" | "check";
  text: string;
}

export function getPackingSuggestions(forecast: DailyWeather[]): PackingSuggestion[] {
  const suggestions: PackingSuggestion[] = [];

  const maxTemp = Math.max(...forecast.map((d) => d.maxTemp));
  const minTemp = Math.min(...forecast.map((d) => d.minTemp));
  const hasRain = forecast.some((d) => d.precipitationProbability >= 40 || d.precipitationMm > 3);
  const hasHeavyRain = forecast.some((d) => d.precipitationMm > 20 || interpretWeatherCode(d.code).severity === "severe");
  const hasFog = forecast.some((d) => d.code >= 45 && d.code <= 48);
  const hasStrongWind = forecast.some((d) => d.windspeedKmh > 35);

  if (minTemp < 15) {
    suggestions.push({ icon: "jacket", text: `Thời tiết lạnh (xuống ${minTemp.toFixed(0)}°C) – Mang áo khoác dày, áo len và quần dài.` });
  } else if (minTemp < 22) {
    suggestions.push({ icon: "scarf", text: `Trời mát (${minTemp.toFixed(0)}–${maxTemp.toFixed(0)}°C) – Nên mang theo áo khoác mỏng hoặc áo gió.` });
  }

  if (maxTemp > 33) {
    suggestions.push({ icon: "sun-glasses", text: `Nắng nóng (đến ${maxTemp.toFixed(0)}°C) – Chuẩn bị kem chống nắng SPF50+, kính mắt và mũ rộng vành.` });
  }

  if (hasHeavyRain) {
    suggestions.push({ icon: "umbrella-heavy", text: "Có thể có mưa lớn – Mang theo ô loại lớn hoặc áo mưa che toàn thân." });
  } else if (hasRain) {
    suggestions.push({ icon: "umbrella", text: "Dự báo có mưa – Đừng quên mang ô dù và túi chống nước cho đồ điện tử." });
  }

  if (hasFog) {
    suggestions.push({ icon: "fog", text: "Sẽ có sương mù – Mang giày chống trơn nếu có hoạt động leo núi hoặc đi đường đèo." });
  }

  if (hasStrongWind) {
    suggestions.push({ icon: "wind", text: "Gió mạnh được dự báo – Tránh mặc quần áo rộng quá, buộc chắc mũ nón khi ra ngoài." });
  }

  if (suggestions.length === 0) {
    suggestions.push({ icon: "check", text: `Thời tiết thuận lợi (${minTemp.toFixed(0)}–${maxTemp.toFixed(0)}°C) – Chỉ cần trang phục nhẹ, thoải mái là đủ!` });
  }

  return suggestions;
}

// ─── Smart re-scheduling suggestions ─────────────────────────────────────────

export interface RescheduleSuggestion {
  fromDay: number;
  toDay: number;
  activityName: string;
  reason: string;
}

export function getRescheduleSuggestions(
  schedule: Array<{ day: number; activities: Array<{ name: string; type: string; location?: string }> }>,
  forecast: DailyWeather[],
  startDate?: string,
): RescheduleSuggestion[] {
  const suggestions: RescheduleSuggestion[] = [];
  if (!startDate || forecast.length === 0) return suggestions;

  const start = new Date(`${startDate}T00:00:00`);

  schedule.forEach((day) => {
    const dayIndex = day.day - 1;
    const forecastForDay = forecast[dayIndex];
    if (!forecastForDay) return;

    const condition = interpretWeatherCode(forecastForDay.code);
    if (condition.severity !== "severe" || !condition.isRainy) return;

    // Find risky outdoor activities on this bad-weather day
    day.activities.forEach((act) => {
      if (!isOutdoorRiskyActivity(act.name, act.location)) return;

      // Find a better day in the schedule
      const betterDayEntry = schedule.find((other) => {
        if (other.day === day.day) return false;
        const otherForecast = forecast[other.day - 1];
        if (!otherForecast) return false;
        const otherCondition = interpretWeatherCode(otherForecast.code);
        return otherCondition.severity === "clear" || otherCondition.severity === "mild";
      });

      if (betterDayEntry) {
        const betterDayForecast = forecast[betterDayEntry.day - 1];
        const betterCondition = interpretWeatherCode(betterDayForecast.code);
        suggestions.push({
          fromDay: day.day,
          toDay: betterDayEntry.day,
          activityName: act.name,
          reason: `Ngày ${day.day} dự báo ${condition.label} (${forecastForDay.precipitationProbability}% mưa), trong khi Ngày ${betterDayEntry.day} ${betterCondition.emoji} ${betterCondition.label} phù hợp hơn.`,
        });
      }
    });
  });

  return suggestions;
}

// ─── Current weather for Explore ─────────────────────────────────────────────

export function getWeatherStatusBadge(weather: DailyWeather): { text: string; emoji: string; color: string } {
  const condition = interpretWeatherCode(weather.code);
  const temp = Math.round((weather.maxTemp + weather.minTemp) / 2);

  let color = "#0d9488"; // teal default
  if (condition.severity === "severe") color = "#dc2626";
  else if (condition.severity === "moderate") color = "#d97706";
  else if (condition.severity === "clear") color = "#16a34a";

  return {
    text: `${temp}°C · ${condition.label}`,
    emoji: condition.emoji,
    color,
  };
}
