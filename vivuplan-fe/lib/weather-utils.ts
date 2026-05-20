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
  temperatureC?: number;
  precipitationMm: number;
  precipitationProbability: number;
  windspeedKmh: number;
  outdoorRiskLevel?: number;
}

export interface CurrentHourlyWeather {
  code: number;
  temperatureC: number;
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
  if (code >= 96 && code <= 99) return 2;
  if (code === 95 && (
    precipitationProbability >= 60 ||
    precipitationMm >= 3 ||
    windspeedKmh >= 40 ||
    (precipitationProbability >= 50 && precipitationMm >= 1)
  )) return 2;
  if (code === 65 || code === 67 || code === 82 || code === 86) return 2;
  if (precipitationMm >= 25) return 2;
  if (windspeedKmh >= 50 && precipitationProbability >= 70) return 2;
  if (precipitationProbability >= 95 && precipitationMm >= 15) return 2;
  if (code === 95) return 1;
  if ((code >= 51 && code <= 64) || (code >= 80 && code <= 81)) return 1;
  if (precipitationMm >= 1) return 1;
  if (precipitationProbability >= 60) return 1;
  return 0;
}

export function getWindowOutdoorRiskLevel(window: WeatherWindow): 0 | 1 | 2 {
  if (typeof window.outdoorRiskLevel === "number") {
    return window.outdoorRiskLevel >= 2 ? 2 : window.outdoorRiskLevel >= 1 ? 1 : 0;
  }

  const { code, precipitationMm, precipitationProbability, windspeedKmh } = window;
  if (code >= 96 && code <= 99) return 2;
  if (code === 95 && (
    precipitationProbability >= 60 ||
    precipitationMm >= 3 ||
    windspeedKmh >= 40 ||
    (precipitationProbability >= 50 && precipitationMm >= 1)
  )) return 2;
  if (code === 65 || code === 67 || code === 82 || code === 86) return 2;
  if (precipitationMm >= 25) return 2;
  if (windspeedKmh >= 50 && precipitationProbability >= 70) return 2;
  if (precipitationProbability >= 95 && precipitationMm >= 15) return 2;
  if (code === 95) return 1;
  if ((code >= 51 && code <= 64) || (code >= 80 && code <= 81)) return 1;
  if (precipitationMm >= 1) return 1;
  if (precipitationProbability >= 60) return 1;
  return 0;
}

export function getCurrentHourlyOutdoorRiskLevel(weather: CurrentHourlyWeather): 0 | 1 | 2 {
  if (typeof weather.outdoorRiskLevel === "number") {
    return weather.outdoorRiskLevel >= 2 ? 2 : weather.outdoorRiskLevel >= 1 ? 1 : 0;
  }

  const { code, precipitationMm, precipitationProbability, windspeedKmh } = weather;
  if (code >= 96 && code <= 99) return 2;
  if (code === 95 && (
    precipitationProbability >= 60 ||
    precipitationMm >= 3 ||
    windspeedKmh >= 40 ||
    (precipitationProbability >= 50 && precipitationMm >= 1)
  )) return 2;
  if (code === 65 || code === 67 || code === 82 || code === 86) return 2;
  if (precipitationMm >= 25) return 2;
  if (windspeedKmh >= 50 && precipitationProbability >= 70) return 2;
  if (precipitationProbability >= 95 && precipitationMm >= 15) return 2;
  if (code === 95) return 1;
  if ((code >= 51 && code <= 64) || (code >= 80 && code <= 81)) return 1;
  if (precipitationMm >= 1) return 1;
  if (precipitationProbability >= 60) return 1;
  return 0;
}

function softenLowRiskThunderstorm(
  base: WeatherCondition,
  risk: 0 | 1 | 2,
  precipitationProbability: number,
  precipitationMm: number,
): WeatherCondition {
  if (base.iconKey !== "storm" || risk === 2) return base;

  const looksRainy = precipitationProbability >= 40 || precipitationMm >= 1;
  return {
    ...base,
    label: looksRainy ? "Có thể có mưa giông" : "Có mây, khả năng giông thấp",
    severity: looksRainy ? "moderate" : "mild",
    isRainy: looksRainy,
    isWindy: false,
    iconKey: looksRainy ? "rain" : "cloudy",
  };
}

function interpretWeatherWindow(window: WeatherWindow): WeatherCondition {
  const rawBase = interpretWeatherCode(window.code);
  const risk = getWindowOutdoorRiskLevel(window);
  const base = softenLowRiskThunderstorm(rawBase, risk, window.precipitationProbability, window.precipitationMm);

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

export function interpretWeather(weather: DailyWeather): WeatherCondition {
  const rawBase = interpretWeatherCode(weather.code);
  const risk = getOutdoorRiskLevel(weather);
  const base = softenLowRiskThunderstorm(rawBase, risk, weather.precipitationProbability, weather.precipitationMm);

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

export function interpretCurrentDisplayWeather(weather: CurrentHourlyWeather): WeatherCondition {
  const { code, precipitationMm, precipitationProbability, windspeedKmh } = weather;
  const hasRain = precipitationMm >= 0.1;
  const rainCode = (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
  const credibleRainForecast = rainCode && precipitationProbability >= 50;
  const heavyRain = precipitationMm >= 3 || ((code === 65 || code === 67 || code === 82) && precipitationProbability >= 50);
  const strongWind = windspeedKmh >= 40;

  if (code >= 45 && code <= 48) {
    return {
      label: "Sương mù",
      severity: "mild",
      isRainy: false,
      isWindy: strongWind,
      isFoggy: true,
      iconKey: "fog",
    };
  }

  if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) {
    return {
      label: "Có tuyết",
      severity: "severe",
      isRainy: false,
      isWindy: strongWind,
      isFoggy: false,
      iconKey: "snow",
    };
  }

  if (code >= 95 && code <= 99) {
    if (code >= 96 || hasRain || strongWind || precipitationProbability >= 70) {
      return {
        label: "Giông bão",
        severity: code >= 96 || precipitationMm >= 3 || strongWind || precipitationProbability >= 80 ? "severe" : "moderate",
        isRainy: true,
        isWindy: strongWind,
        isFoggy: false,
        iconKey: "storm",
      };
    }

    return {
      label: "Có mây",
      severity: "mild",
      isRainy: false,
      isWindy: false,
      isFoggy: false,
      iconKey: "cloudy",
    };
  }

  if (hasRain || credibleRainForecast) {
    return {
      label: heavyRain ? "Mưa lớn" : "Có thể có mưa",
      severity: heavyRain ? "severe" : "moderate",
      isRainy: true,
      isWindy: strongWind,
      isFoggy: false,
      iconKey: "rain",
    };
  }

  if (code === 0) {
    return {
      label: "Trời nắng",
      severity: "clear",
      isRainy: false,
      isWindy: strongWind,
      isFoggy: false,
      iconKey: "sun",
    };
  }

  if (code >= 1 && code <= 3) {
    return {
      label: "Có mây",
      severity: "mild",
      isRainy: false,
      isWindy: strongWind,
      isFoggy: false,
      iconKey: "cloudy",
    };
  }

  return {
    label: "Có mây",
    severity: "mild",
    isRainy: false,
    isWindy: strongWind,
    isFoggy: false,
    iconKey: "cloudy",
  };
}

export function interpretCurrentHourlyWeather(weather: CurrentHourlyWeather): WeatherCondition {
  return interpretCurrentDisplayWeather(weather);
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
  activityTime?: string,
): ActivityWeatherWarning | null {
  // Indoor activities and transport are not strictly 'outdoor activities' in the sense of being canceled by rain
  if (activityType === "FOOD" || activityType === "CAFE" || activityType === "ACCOMMODATION" || activityType === "TRANSPORT") {
    return null;
  }

  const isRisky = isOutdoorRiskyActivity(activityName, activityLocation);
  if (!isRisky) return null;

  const activityWindow = findWeatherWindowForActivity(weather, activityTime);
  const condition = activityWindow ? interpretWeatherWindow(activityWindow) : interpretWeather(weather);
  const outdoorRisk = activityWindow ? getWindowOutdoorRiskLevel(activityWindow) : getOutdoorRiskLevel(weather);
  const rainChance = activityWindow?.precipitationProbability ?? weather.precipitationProbability;
  const rainMm = activityWindow?.precipitationMm ?? weather.precipitationMm;
  const windKmh = activityWindow?.windspeedKmh ?? weather.windspeedKmh;
  const actText = `"${activityName}"`;

  if (activityWindow && outdoorRisk < 2 && rainChance < 60 && rainMm < 1 && windKmh <= 50) {
    return null;
  }

  if (windKmh > 50 && (activityName.toLowerCase().includes("vịnh") || activityName.toLowerCase().includes("biển") || activityName.toLowerCase().includes("thuyền"))) {
    return { icon: "wind", message: `Gió mạnh (${windKmh.toFixed(0)} km/h) – Hoạt động ${actText} có thể bị hoãn. Kiểm tra lại với đơn vị vận hành.` };
  }
  if (outdoorRisk === 2 && condition.isRainy && (rainChance >= 50 || rainMm >= 2)) {
    return { icon: "rain", message: `Dự báo mưa lớn hoặc thời tiết xấu – Hoạt động ngoài trời ${actText} có thể bị ảnh hưởng, hãy kiểm tra điều kiện thực tế trước khi đi.` };
  }
  if (condition.isFoggy && (activityName.toLowerCase().includes("cáp treo") || activityName.toLowerCase().includes("leo"))) {
    return { icon: "fog", message: `Có sương mù – Hoạt động ${actText} có thể bị hạn chế tầm nhìn, hãy kiểm tra trước khi khởi hành.` };
  }
  if (outdoorRisk === 1 && condition.isRainy && (rainChance >= 60 || rainMm >= 1)) {
    return { icon: "rain", message: `Có thể có mưa trong khung giờ này (${rainChance}%) – ${actText} vẫn có thể phù hợp, nên mang áo mưa và linh hoạt khung giờ.` };
  }
  return null;
}

// ─── Hourly window helpers ────────────────────────────────────────────────────

export function findWeatherWindowForActivity(weather: DailyWeather, activityTime?: string): WeatherWindow | null {
  if (!activityTime || !weather.timeWindows?.length) return null;
  const match = activityTime.match(/^([01]\d|2[0-3]):[0-5]\d/);
  if (!match) return null;
  const hour = Number(match[1]);
  return weather.timeWindows.find((window) => hour >= window.startHour && hour <= window.endHour) ?? null;
}

// ─── Smart re-scheduling suggestions ─────────────────────────────────────────

export interface RescheduleSuggestion {
  fromDay: number;
  toDay: number;
  activityName: string;
  reason: string;
}

export interface ItineraryDayWeatherSummary {
  condition: WeatherCondition;
  title: string;
  rainChance: number;
  windKmh: number;
  source: "day-windows" | "daily";
}

export interface CurrentWeatherWindowSummary {
  condition: WeatherCondition;
  title: string;
  temp: number;
  rainChance: number;
  windKmh: number;
  source: "current-window" | "nearest-window" | "daily";
}

function activityWeatherContext(weather: DailyWeather, activityTime?: string) {
  const window = findWeatherWindowForActivity(weather, activityTime);
  if (window) {
    const condition = interpretWeatherWindow(window);
    return {
      condition,
      risk: getWindowOutdoorRiskLevel(window),
      rainChance: window.precipitationProbability,
      rainMm: window.precipitationMm,
      windKmh: window.windspeedKmh,
      window,
    };
  }

  return {
    condition: interpretWeather(weather),
    risk: getOutdoorRiskLevel(weather),
    rainChance: weather.precipitationProbability,
    rainMm: weather.precipitationMm,
    windKmh: weather.windspeedKmh,
    window: null,
  };
}

function isMeaningfullyBadWeather(context: ReturnType<typeof activityWeatherContext>) {
  if (context.windKmh > 50) return true;
  return context.risk === 2 && context.condition.isRainy && (context.rainChance >= 50 || context.rainMm >= 2);
}

function isGoodActivityWindow(weather: DailyWeather, activityTime?: string) {
  const context = activityWeatherContext(weather, activityTime);
  return context.risk === 0 || (context.risk === 1 && context.rainChance < 40 && context.rainMm < 1 && context.windKmh <= 35);
}

function interpretAverageDayWeather(
  risk: 0 | 1 | 2,
  rainChance: number,
  rainMm: number,
  windKmh: number,
): WeatherCondition {
  if (risk === 2) {
    const stormLike = rainChance >= 60 || rainMm >= 3 || windKmh >= 40;
    return {
      label: stormLike ? "Giông bão" : "Mưa lớn",
      severity: "severe",
      isRainy: true,
      isWindy: windKmh >= 40,
      isFoggy: false,
      iconKey: stormLike ? "storm" : "rain",
    };
  }

  if (rainChance >= 60 || rainMm >= 1) {
    return {
      label: "Có thể có mưa",
      severity: "moderate",
      isRainy: true,
      isWindy: windKmh >= 40,
      isFoggy: false,
      iconKey: "rain",
    };
  }

  if (risk === 1 || rainChance >= 30) {
    return {
      label: "Có mây",
      severity: "mild",
      isRainy: false,
      isWindy: windKmh >= 40,
      isFoggy: false,
      iconKey: "cloudy",
    };
  }

  return {
    label: "Thời tiết thuận lợi",
    severity: "clear",
    isRainy: false,
    isWindy: windKmh >= 40,
    isFoggy: false,
    iconKey: "sun",
  };
}

export function summarizeItineraryDayWeather(
  weather: DailyWeather,
  _activities: Array<{ time?: string }> = [],
): ItineraryDayWeatherSummary {
  const windows = weather.timeWindows ?? [];

  if (windows.length > 0) {
    const avgRisk = windows.reduce((sum, window) => sum + getWindowOutdoorRiskLevel(window), 0) / windows.length;
    const avgRainChance = Math.round(windows.reduce((sum, window) => sum + window.precipitationProbability, 0) / windows.length);
    const avgRainMm = windows.reduce((sum, window) => sum + window.precipitationMm, 0) / windows.length;
    const avgWindKmh = windows.reduce((sum, window) => sum + window.windspeedKmh, 0) / windows.length;
    const roundedRisk: 0 | 1 | 2 = avgRisk >= 1.5 ? 2 : avgRisk >= 0.5 ? 1 : 0;
    const condition = interpretAverageDayWeather(roundedRisk, avgRainChance, avgRainMm, avgWindKmh);
    const title = `${condition.label} · mưa trung bình ${avgRainChance}% · gió ${avgWindKmh.toFixed(0)} km/h`;
    return {
      condition,
      title,
      rainChance: avgRainChance,
      windKmh: avgWindKmh,
      source: "day-windows",
    };
  }

  const condition = interpretWeather(weather);
  return {
    condition,
    title: `${condition.label} · mưa ${weather.precipitationProbability}% · gió ${weather.windspeedKmh.toFixed(0)} km/h`,
    rainChance: weather.precipitationProbability,
    windKmh: weather.windspeedKmh,
    source: "daily",
  };
}

function getVietnamHour(now = new Date()): number {
  const hour = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Ho_Chi_Minh",
    hour: "2-digit",
    hour12: false,
  }).format(now);
  const parsed = Number(hour);
  return Number.isFinite(parsed) ? parsed : now.getHours();
}

function findCurrentOrNearestWeatherWindow(weather: DailyWeather, now = new Date()) {
  const windows = weather.timeWindows ?? [];
  if (windows.length === 0) return null;

  const hour = getVietnamHour(now);
  const current = windows.find((window) => hour >= window.startHour && hour <= window.endHour);
  if (current) return { window: current, source: "current-window" as const };

  const nearest = windows
    .slice()
    .sort((a, b) => {
      const aDistance = Math.min(Math.abs(hour - a.startHour), Math.abs(hour - a.endHour));
      const bDistance = Math.min(Math.abs(hour - b.startHour), Math.abs(hour - b.endHour));
      return aDistance - bDistance;
    })[0];

  return nearest ? { window: nearest, source: "nearest-window" as const } : null;
}

export function summarizeCurrentWeatherWindow(weather: DailyWeather, now = new Date()): CurrentWeatherWindowSummary {
  const match = findCurrentOrNearestWeatherWindow(weather, now);
  if (match) {
    const condition = interpretWeatherWindow(match.window);
    const temp = Math.round(match.window.temperatureC ?? (weather.maxTemp + weather.minTemp) / 2);
    return {
      condition,
      title: `${condition.label} · ${match.window.label} ${match.window.startHour}:00-${match.window.endHour}:00 · mưa ${match.window.precipitationProbability}% · gió ${match.window.windspeedKmh.toFixed(0)} km/h`,
      temp,
      rainChance: match.window.precipitationProbability,
      windKmh: match.window.windspeedKmh,
      source: match.source,
    };
  }

  const condition = interpretWeather(weather);
  return {
    condition,
    title: `${condition.label} · hôm nay · mưa ${weather.precipitationProbability}% · gió ${weather.windspeedKmh.toFixed(0)} km/h`,
    temp: Math.round((weather.maxTemp + weather.minTemp) / 2),
    rainChance: weather.precipitationProbability,
    windKmh: weather.windspeedKmh,
    source: "daily",
  };
}

export function getRescheduleSuggestions(
  schedule: Array<{ day: number; activities: Array<{ name: string; type: string; location?: string; time?: string }> }>,
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

    // Find risky outdoor activities on this bad-weather day
    day.activities.forEach((act) => {
      // Không bao giờ gợi ý dời lịch đối với các hoạt động di chuyển, nhận phòng hay ăn uống
      if (["TRANSPORT", "ACCOMMODATION", "FOOD", "CAFE"].includes(act.type)) return;

      if (!isOutdoorRiskyActivity(act.name, act.location)) return;
      const currentContext = activityWeatherContext(forecastForDay, act.time);
      if (!isMeaningfullyBadWeather(currentContext)) return;

      // Find a better day in the schedule
      const betterDayEntry = schedule.find((other) => {
        if (other.day === day.day) return false;
        const otherForecast = getForecastForDay(other.day);
        if (!otherForecast) return false;
        return isGoodActivityWindow(otherForecast, act.time);
      });

      if (betterDayEntry) {
        const betterDayForecast = getForecastForDay(betterDayEntry.day)!;
        const betterContext = activityWeatherContext(betterDayForecast, act.time);
        suggestions.push({
          fromDay: day.day,
          toDay: betterDayEntry.day,
          activityName: act.name,
          reason: `Ngày ${day.day} khung giờ của hoạt động dự báo ${currentContext.condition.label} (${currentContext.rainChance}% mưa), trong khi Ngày ${betterDayEntry.day} cùng khung giờ thời tiết ${betterContext.condition.label} phù hợp hơn.`,
        });
      }
    });
  });

  return suggestions;
}
