// ─── Open-Meteo WMO Weather Interpretation Codes ─────────────────────────────

export interface DailyWeather {
  date: string;             // "YYYY-MM-DD"
  code: number;             // WMO weathercode
  maxTemp: number;
  minTemp: number;
  precipitationMm: number;
  precipitationProbability: number; // 0-100
  windspeedKmh: number;
  outdoorRiskLevel?: number; // 0 = low/good, 1 = rain flex, 2 = severe
  timeWindows?: WeatherWindow[];
}

export interface WeatherWindow {
  label: string;
  startHour: number;
  endHour: number;
  code: number;
  precipitationMm: number;
  precipitationProbability: number;
  windspeedKmh: number;
  outdoorRiskLevel?: number;
}

export interface WeatherCondition {
  label: string;
  severity: "clear" | "mild" | "moderate" | "severe";
  isRainy: boolean;
  isWindy: boolean;
  isFoggy: boolean;
  iconKey: "sun" | "cloudy" | "fog" | "rain" | "snow" | "storm" | "unknown";
}

export function interpretWeatherCode(code: number): WeatherCondition {
  if (code === 0) return { label: "Trời nắng", severity: "clear", isRainy: false, isWindy: false, isFoggy: false, iconKey: "sun" };
  if (code <= 3) return { label: "Có mây", severity: "mild", isRainy: false, isWindy: false, isFoggy: false, iconKey: "cloudy" };
  if (code <= 49) return { label: "Sương mù", severity: "mild", isRainy: false, isWindy: false, isFoggy: true, iconKey: "fog" };
  if (code <= 57) return { label: "Mưa phùn", severity: "mild", isRainy: true, isWindy: false, isFoggy: false, iconKey: "rain" };
  if (code <= 60) return { label: "Mưa nhỏ", severity: "moderate", isRainy: true, isWindy: false, isFoggy: false, iconKey: "rain" };
  if (code <= 64) return { label: "Mưa vừa", severity: "moderate", isRainy: true, isWindy: false, isFoggy: false, iconKey: "rain" };
  if (code <= 65) return { label: "Mưa to", severity: "severe", isRainy: true, isWindy: false, isFoggy: false, iconKey: "rain" };
  if (code <= 77) return { label: "Có tuyết", severity: "severe", isRainy: false, isWindy: false, isFoggy: false, iconKey: "snow" };
  if (code <= 82) return { label: code === 82 ? "Mưa rào lớn" : "Mưa rào", severity: code === 82 ? "severe" : "moderate", isRainy: true, isWindy: false, isFoggy: false, iconKey: "rain" };
  if (code <= 86) return { label: "Mưa tuyết", severity: "severe", isRainy: true, isWindy: false, isFoggy: false, iconKey: "snow" };
  if (code <= 99) return { label: "Giông bão", severity: "severe", isRainy: true, isWindy: true, isFoggy: false, iconKey: "storm" };
  return { label: "N/A", severity: "mild", isRainy: false, isWindy: false, isFoggy: false, iconKey: "unknown" };
}

export function getOutdoorRiskLevel(weather: DailyWeather): 0 | 1 | 2 {
  if (typeof weather.outdoorRiskLevel === "number") {
    return weather.outdoorRiskLevel >= 2 ? 2 : weather.outdoorRiskLevel >= 1 ? 1 : 0;
  }

  const { code, precipitationMm, precipitationProbability, windspeedKmh } = weather;
  if (code >= 95 && code <= 99) return 2;
  if (code === 65 || code === 67 || code === 82 || code === 86) return 2;
  if (precipitationMm >= 25) return 2;
  if (windspeedKmh >= 50 && precipitationProbability >= 70) return 2;
  if (precipitationProbability >= 95 && precipitationMm >= 15) return 2;
  if ((code >= 51 && code <= 64) || (code >= 80 && code <= 81)) return 1;
  if (precipitationMm >= 1) return 1;
  if (precipitationProbability >= 60) return 1;
  return 0;
}

export function interpretWeather(weather: DailyWeather): WeatherCondition {
  const base = interpretWeatherCode(weather.code);
  const risk = getOutdoorRiskLevel(weather);

  if (risk === 2) {
    return {
      ...base,
      severity: "severe",
      label: base.isRainy ? base.label : "Thời tiết khắc nghiệt",
    };
  }

  if (risk === 1 && base.isRainy) {
    return {
      ...base,
      severity: "moderate",
    };
  }

  if (base.isRainy) {
    return {
      ...base,
      severity: "mild",
    };
  }

  return base;
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

  const condition = interpretWeather(weather);
  const outdoorRisk = getOutdoorRiskLevel(weather);
  const isRisky = isOutdoorRiskyActivity(activityName, activityLocation);
  if (!isRisky) return null;

  const actText = `"${activityName}"`;

  if (weather.windspeedKmh > 50 && (activityName.toLowerCase().includes("vịnh") || activityName.toLowerCase().includes("biển") || activityName.toLowerCase().includes("thuyền"))) {
    return { icon: "wind", message: `Gió mạnh (${weather.windspeedKmh.toFixed(0)} km/h) – Hoạt động ${actText} có thể bị hoãn. Kiểm tra lại với đơn vị vận hành.` };
  }
  if (outdoorRisk === 2 && condition.isRainy) {
    return { icon: "rain", message: `Dự báo mưa lớn hoặc thời tiết xấu – Hoạt động ngoài trời ${actText} có thể bị ảnh hưởng, hãy kiểm tra điều kiện thực tế trước khi đi.` };
  }
  if (condition.isFoggy && (activityName.toLowerCase().includes("cáp treo") || activityName.toLowerCase().includes("leo"))) {
    return { icon: "fog", message: `Có sương mù – Hoạt động ${actText} có thể bị hạn chế tầm nhìn, hãy kiểm tra trước khi khởi hành.` };
  }
  if (outdoorRisk === 1 && condition.isRainy && (weather.precipitationProbability >= 60 || weather.precipitationMm >= 1)) {
    return { icon: "rain", message: `Có thể có mưa trong ngày (${weather.precipitationProbability}%) – ${actText} vẫn có thể phù hợp, nên mang áo mưa và linh hoạt khung giờ.` };
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
  const hasRain = forecast.some((d) => d.precipitationProbability >= 40 || d.precipitationMm > 1);
  const hasHeavyRain = forecast.some((d) => getOutdoorRiskLevel(d) === 2);
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

  const getForecastForDay = (dayNum: number): DailyWeather | undefined => {
    const date = new Date(`${startDate}T00:00:00`);
    date.setDate(date.getDate() + dayNum - 1);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const dayStr = String(date.getDate()).padStart(2, "0");
    const dateStr = `${year}-${month}-${dayStr}`;
    return forecast.find((d) => d.date === dateStr);
  };

  schedule.forEach((day) => {
    const forecastForDay = getForecastForDay(day.day);
    if (!forecastForDay) return;

    const condition = interpretWeather(forecastForDay);
    if (getOutdoorRiskLevel(forecastForDay) !== 2 || !condition.isRainy) return;

    // Find risky outdoor activities on this bad-weather day
    day.activities.forEach((act) => {
      // Không bao giờ gợi ý dời lịch đối với các hoạt động di chuyển, nhận phòng hay ăn uống
      if (["TRANSPORT", "ACCOMMODATION", "FOOD", "CAFE"].includes(act.type)) return;

      if (!isOutdoorRiskyActivity(act.name, act.location)) return;

      // Find a better day in the schedule
      const betterDayEntry = schedule.find((other) => {
        if (other.day === day.day) return false;
        const otherForecast = getForecastForDay(other.day);
        if (!otherForecast) return false;
        return getOutdoorRiskLevel(otherForecast) === 0;
      });

      if (betterDayEntry) {
        const betterDayForecast = getForecastForDay(betterDayEntry.day)!;
        const betterCondition = interpretWeather(betterDayForecast);
        suggestions.push({
          fromDay: day.day,
          toDay: betterDayEntry.day,
          activityName: act.name,
          reason: `Ngày ${day.day} dự báo ${condition.label} (${forecastForDay.precipitationProbability}% mưa), trong khi Ngày ${betterDayEntry.day} thời tiết ${betterCondition.label} phù hợp hơn.`,
        });
      }
    });
  });

  return suggestions;
}
