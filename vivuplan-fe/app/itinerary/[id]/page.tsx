/* eslint-disable react-hooks/set-state-in-effect */
"use client";


import React, { type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";
import Navbar from "@/components/layout/Navbar";
import { PurchaseModal } from "@/components/billing/PurchaseModal";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ItineraryLoadingState } from "@/components/travel/ItineraryLoadingState";
import { ApiError, tripApi, type ActivityMutationRequest, type ActivityResponse, type RegenerateDayPreviewResponse, type RegenerateDayRequest, type TripResponse } from "@/lib/api";
import { useBilling } from "@/hooks/useBilling";
import { copyTextToClipboard, getTripShareUrl } from "@/lib/share";
import { findDestinationByName, getDestinationImage } from "@/lib/travel-data";
import { useDestinations } from "@/lib/use-destinations";
import { useWeather } from "@/lib/use-weather";
import { useGeocode } from "@/lib/use-geocode";
import { WeatherIcon } from "@/components/travel/WeatherIcon";
import { DayRouteMap } from "@/components/travel/DayRouteMap";
import {
  getRescheduleSuggestions,
  getActivityWeatherWarning,
  summarizeItineraryDayWeather,
} from "@/lib/weather-utils";

import {
  AlertCircle,
  AlertTriangle,
  Calendar,
  Camera,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  ChevronDown,
  ChevronUp,
  Coffee,
  BedDouble,
  Edit3,
  ExternalLink,
  Lightbulb,
  ListChecks,
  MapPin,
  Navigation,
  Plus,
  RefreshCw,
  Printer,
  Save,
  Share2,
  Sparkles,
  Star,
  Thermometer,
  Trash2,
  Users,
  Utensils,
  Wallet,
  Wind,
  X,
  CloudRain,
  CloudFog,
} from "lucide-react";

// ─── Lightweight toast system ────────────────────────────────────────────────
type ToastItem = { id: number; message: string; type: "error" | "success" | "info" };
const REGENERATE_INSTRUCTION_MAX_LENGTH = 500;

function useToast() {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const counterRef = useRef(0);
  const show = (message: string, type: ToastItem["type"] = "info", durationMs = 5000) => {
    const id = ++counterRef.current;
    setToasts((prev) => [...prev, { id, message, type }]);
    window.setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), durationMs);
  };
  const dismiss = (id: number) => setToasts((prev) => prev.filter((t) => t.id !== id));
  return { toasts, show, dismiss };
}

function ToastContainer({ toasts, onDismiss }: { toasts: ToastItem[]; onDismiss: (id: number) => void }) {
  if (toasts.length === 0) return null;
  return (
    <div style={{
      position: "fixed", top: 86, right: 24, zIndex: 9999,
      display: "flex", flexDirection: "column", gap: 10, alignItems: "flex-end",
      pointerEvents: "none",
    }}>
      {toasts.map((t) => (
        <div
          key={t.id}
          role="alert"
          style={{
            pointerEvents: "auto",
            display: "flex", alignItems: "flex-start", gap: 10,
            minWidth: 280, maxWidth: 380,
            padding: "12px 14px",
            borderRadius: "var(--r-md, 10px)",
            boxShadow: "0 4px 20px rgba(0,0,0,0.18)",
            background: t.type === "error" ? "#FEF2F2" : t.type === "success" ? "#F0FDF4" : "#EFF6FF",
            color: t.type === "error" ? "#B91C1C" : t.type === "success" ? "#15803D" : "#1D4ED8",
            fontSize: 13, lineHeight: 1.5, fontWeight: 500,
            animation: "slideInRight 0.22s ease",
          }}
        >
          {t.type === "error" && <AlertCircle size={16} style={{ flexShrink: 0, marginTop: 1 }} />}
          {t.type === "success" && <CheckCircle2 size={16} style={{ flexShrink: 0, marginTop: 1 }} />}
          <span style={{ flex: 1 }}>{t.message}</span>
          <button
            type="button"
            onClick={() => onDismiss(t.id)}
            style={{ background: "transparent", border: "none", cursor: "pointer", padding: 0, color: "inherit", opacity: 0.6, flexShrink: 0 }}
            aria-label="Đóng thông báo"
          >
            <X size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}

const typeConfig: Record<string, { icon: typeof Coffee; color: string; bg: string; label: string }> = {
  FOOD: { icon: Utensils, color: "#0F9F9C", bg: "#E6FFFB", label: "Ăn uống" },
  CAFE: { icon: Coffee, color: "#0284C7", bg: "#E0F2FE", label: "Cà phê" },
  ATTRACTION: { icon: Camera, color: "#22C55E", bg: "#F0FDF4", label: "Địa điểm" },
  ACTIVITY: { icon: Camera, color: "#22C55E", bg: "#F0FDF4", label: "Hoạt động" },
  NIGHTLIFE: { icon: Star, color: "#DB2777", bg: "#FCE7F3", label: "Buổi tối" },
  TRANSPORT: { icon: Navigation, color: "#6366F1", bg: "#EEF2FF", label: "Di chuyển" },
  ACCOMMODATION: { icon: MapPin, color: "#A855F7", bg: "#FAF5FF", label: "Lưu trú" },
};

const fmtCost = (value: number) => {
  if (!value) return "Miễn phí";
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}tr ₫`;
  return `${Math.round(value / 1000)}k ₫`;
};

const needsCostReview = (activity: ActivityResponse) => activity.costEstimateStatus === "NEEDS_REVIEW";

const fmtActivityCost = (activity: ActivityResponse) => {
  if (needsCostReview(activity)) return "Cần kiểm tra";
  return fmtCost(activity.estimatedCost);
};

const styleLabel: Record<string, string> = {
  ADVENTURE: "Phiêu lưu",
  RELAXING: "Nghỉ dưỡng",
  CULTURAL: "Văn hóa",
  FOODIE: "Ẩm thực",
};

const groupLabel: Record<string, string> = {
  SOLO: "Một mình",
  COUPLE: "Cặp đôi",
  FRIENDS: "Nhóm bạn",
  FAMILY: "Gia đình",
};

function fmtDate(value?: string) {
  if (!value) return null;
  return new Date(`${value}T00:00:00`).toLocaleDateString("vi-VN");
}

function getActivityPlace(activity: ActivityResponse, destination?: string) {
  return [activity.name, activity.location, destination].filter(Boolean).join(" ");
}

function buildMapsSearchUrl(query: string) {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`;
}

function buildDayDirectionsUrl(activities: ActivityResponse[], destination?: string) {
  const places = activities
    .map((activity) => getActivityPlace(activity, destination))
    .filter(Boolean);

  if (places.length <= 1) {
    return buildMapsSearchUrl(places[0] || destination || "");
  }

  const query = new URLSearchParams({
    api: "1",
    origin: places[0],
    destination: places[places.length - 1],
    travelmode: "driving",
  });
  const waypoints = places.slice(1, -1).slice(0, 8);
  if (waypoints.length > 0) query.set("waypoints", waypoints.join("|"));
  return `https://www.google.com/maps/dir/?${query.toString()}`;
}

function normalizeMapPlaceText(value?: string | null) {
  return String(value ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

const MAP_ADMINISTRATIVE_PREFIXES = [
  "thanh pho",
  "tp",
  "tinh",
  "huyen",
  "quan",
  "thi xa",
  "thi tran",
  "xa",
  "phuong",
];

function stripMapAdministrativePrefix(value?: string | null) {
  let normalized = normalizeMapPlaceText(value);
  let changed = true;
  while (changed) {
    changed = false;
    for (const prefix of MAP_ADMINISTRATIVE_PREFIXES) {
      if (normalized === prefix) return "";
      if (normalized.startsWith(`${prefix} `)) {
        normalized = normalized.slice(prefix.length).trim();
        changed = true;
        break;
      }
    }
  }
  return normalized;
}

function isSameMapTravelPlace(a?: string | null, b?: string | null) {
  const normalizedA = normalizeMapPlaceText(a);
  const normalizedB = normalizeMapPlaceText(b);
  if (!normalizedA || !normalizedB) return false;
  if (normalizedA === normalizedB) return true;

  const strippedA = stripMapAdministrativePrefix(a);
  const strippedB = stripMapAdministrativePrefix(b);
  return Boolean(strippedA && strippedB && strippedA === strippedB);
}

function hasValidMapCoordinate(activity: ActivityResponse, departure?: string | null, destination?: string | null) {
  if (activity.type === "TRANSPORT") return false;

  const normalizedDeparture = normalizeMapPlaceText(departure);
  const normalizedActivityPlace = normalizeMapPlaceText([activity.name, activity.location].filter(Boolean).join(" "));
  if (
    normalizedDeparture
    && !isSameMapTravelPlace(departure, destination)
    && normalizedActivityPlace.includes(normalizedDeparture)
  ) return false;

  const lat = Number(activity.latitude);
  const lon = Number(activity.longitude);
  return Number.isFinite(lat) && Number.isFinite(lon) && lat >= 7 && lat <= 24.8 && lon >= 102 && lon <= 110.8;
}

function buildHotelAffiliateUrl(
  activity: ActivityResponse,
  context: {
    destination?: string | null;
    startDate?: string | null;
    endDate?: string | null;
    travelerCount?: number | null;
  },
) {
  const template = process.env.NEXT_PUBLIC_HOTEL_AFFILIATE_URL_TEMPLATE?.trim();
  if (!template || activity.type !== "ACCOMMODATION") return null;

  const tokens: Record<string, string> = {
    hotel: activity.name ?? "",
    location: activity.location ?? "",
    destination: context.destination ?? "",
    checkin: context.startDate ?? "",
    checkout: context.endDate ?? "",
    guests: String(Math.max(1, context.travelerCount ?? 1)),
  };

  return template.replace(/\{(hotel|location|destination|checkin|checkout|guests)\}/g, (_, key: string) =>
    encodeURIComponent(tokens[key] ?? ""),
  );
}

function getDayTimeRange(activities: ActivityResponse[]) {
  const times = activities.map((activity) => activity.time).filter(Boolean).sort();
  if (times.length === 0) return "Chưa có giờ";
  return times.length === 1 ? times[0] : `${times[0]} - ${times[times.length - 1]}`;
}

function buildDayCopyText(trip: TripResponse, day: NonNullable<TripResponse["schedule"]>[number]) {
  const rows = day.activities.map((activity) => {
    const location = activity.location ? ` tại ${activity.location}` : "";
    const cost = activity.estimatedCost || needsCostReview(activity) ? ` - ${fmtActivityCost(activity)}` : "";
    const note = activity.note ? `\n  Ghi chú: ${activity.note}` : "";
    return `${activity.time} - ${activity.name}${location} (${activity.duration})${cost}${note}`;
  });

  return [
    `Lịch trình ${trip.destination} - Ngày ${day.day}`,
    day.title,
    day.summary,
    "",
    ...rows,
  ].filter(Boolean).join("\n");
}

type AiMessageKind = "positive" | "caution";
type AiMessage = {
  raw: string;
  kind: AiMessageKind;
  message: string;
  displayText: string;
};

function normalizeAiMessage(raw: string): AiMessage {
  const trimmed = raw.trim();
  const normalized = trimmed
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase();
  const kind: AiMessageKind = normalized.startsWith("toi uu") ? "positive" : "caution";
  const message = cleanAiMessage(
    trimmed.replace(/^(Tối ưu|Toi uu|Yêu cầu|Yeu cau)\s*:\s*/i, "").trim(),
  );
  return {
    raw,
    kind,
    message,
    displayText: getAiMessageDisplayText(message),
  };
}

function cleanAiMessage(message: string) {
  return message
    .replace(/^Lịch trình được thiết kế phù hợp cho\s+\d+\s+người,\s*đặc biệt là\s+/i, "Lịch trình phù hợp với ")
    .replace(/^Lịch trình được thiết kế phù hợp cho\s+\d+\s+người\s*,?\s*/i, "Lịch trình ")
    .replace(/\s+/g, " ")
    .trim();
}

function getAiMessageDisplayText(message: string) {
  const clean = message.replace(/\s+/g, " ").trim();
  if (clean.length <= 420) return clean;
  return `${clean.slice(0, 417).trim()}...`;
}

export default function ItineraryPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const routeLooksLikeTripId = /^\d+$/.test(params.id);
  const formatDayTitle = (dayNum: number, titleStr: string) => {
    const clean = titleStr.trim();
    if (clean.toLowerCase().startsWith(`ngày ${dayNum}`)) {
      return clean;
    }
    return `Ngày ${dayNum}: ${clean}`;
  };
  const { user: authUser, loading: authLoading } = useAuth();
  const { destinations, loading: destinationsLoading } = useDestinations();
  const [trip, setTrip] = useState<TripResponse | null>(null);
  const [activeDay, setActiveDay] = useState(0);
  const dayTabsRef = useRef<HTMLDivElement | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [redirectingForbidden, setRedirectingForbidden] = useState(false);
  const [readOnlyShareView, setReadOnlyShareView] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [shareError, setShareError] = useState("");
  const [exportingPdf, setExportingPdf] = useState(false);
  const logoUrl = typeof window !== "undefined" ? window.location.origin + "/logo.png" : "/logo.png";
  const [editor, setEditor] = useState<{ mode: "add" | "edit"; dayNumber: number; activity?: ActivityResponse } | null>(null);
  const [savingActivity, setSavingActivity] = useState(false);
  const [activityError, setActivityError] = useState("");
  const [regenerateOpen, setRegenerateOpen] = useState(false);
  const [regeneratePreview, setRegeneratePreview] = useState<RegenerateDayPreviewResponse | null>(null);
  const [regeneratePreviewError, setRegeneratePreviewError] = useState("");
  const [selectedRegenerateIndexes, setSelectedRegenerateIndexes] = useState<number[]>([]);
  const [regeneratingDay, setRegeneratingDay] = useState(false);
  const [applyingRegeneration, setApplyingRegeneration] = useState(false);
  const [regenerateCloseConfirmOpen, setRegenerateCloseConfirmOpen] = useState(false);
  const regenerateRequestId = useRef(0);
  const [purchaseOpen, setPurchaseOpen] = useState(false);
  const { wallet, refreshWallet } = useBilling();
  const [clientWarnings, setClientWarnings] = useState<string[]>([]);
  const [aiWarningsCollapsed, setAiWarningsCollapsed] = useState(false);
  const [weatherAdvisoryDismissed, setWeatherAdvisoryDismissed] = useState(false);
  const { toasts, show: showToast, dismiss: dismissToast } = useToast();

  // ─── Weather ──────────────────────────────────────────────────────────────
  // Step 1: try to get coordinates from the known destinations list (DB-backed)
  const dbCoords = useMemo(() => {
    if (!trip) return null;
    const match = findDestinationByName(trip.destination, destinations);
    return match?.latitude != null && match?.longitude != null
      ? { lat: match.latitude, lon: match.longitude }
      : null;
  }, [trip, destinations]);

  // Step 2: after DB destinations finish loading, fall back to backend geocoding only for unknown places
  const shouldGeocodeDestination = Boolean(trip?.destination && !destinationsLoading && !dbCoords);
  const geocodedCoords = useGeocode(shouldGeocodeDestination ? trip?.destination : null);

  // Use DB coordinates first, then geocoded, then null (no weather)
  const destCoords = dbCoords ?? geocodedCoords;

  const { forecast, getByDayIndex } = useWeather(destCoords?.lat, destCoords?.lon);

  useEffect(() => {
    if (authLoading) return;
    let cancelled = false;

    async function loadTrip() {
      const id = params.id;
      setLoading(true);
      setError("");
      try {
        let data: TripResponse;
        let sharedView = true;

        if (routeLooksLikeTripId && authUser) {
          try {
            data = await tripApi.getTrip(id);
            sharedView = false;
          } catch (ownerRouteError) {
            if (!(ownerRouteError instanceof ApiError) || ![403, 404].includes(ownerRouteError.status)) {
              throw ownerRouteError;
            }
            data = await tripApi.getByShareCode(id);
          }
        } else {
          data = await tripApi.getByShareCode(id);
        }

        if (cancelled) return;
        setTrip(data);
        setReadOnlyShareView(sharedView);
        setActiveDay(0);
        setCopied(false);
        setShareError("");
        setClientWarnings([]);
        setAiWarningsCollapsed(false);
        setRegenerateOpen(false);
        setRegeneratePreview(null);
        setRegenerateCloseConfirmOpen(false);
        setSelectedRegenerateIndexes([]);
      } catch (e) {
        if (cancelled) return;
        const isForbidden =
          routeLooksLikeTripId
          && Boolean(authUser)
          && (e instanceof ApiError
            ? e.status === 403
            : e instanceof Error && (e.message.includes("không có quyền") || e.message.includes("quyền")));

        if (isForbidden) {
          setRedirectingForbidden(true);
          router.replace("/forbidden");
          return;
        }

        setError(e instanceof Error ? e.message : "Không thể tải lịch trình");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void loadTrip();
    return () => {
      cancelled = true;
    };
  }, [params.id, authLoading, authUser, routeLooksLikeTripId, router]);

  useEffect(() => {
    if (!trip?.id || typeof window === "undefined") return;
    const storageKey = `vivuplan:trip:${trip.id}:warnings`;
    const rawWarnings = window.sessionStorage.getItem(storageKey);
    if (!rawWarnings) return;

    window.sessionStorage.removeItem(storageKey);
    try {
      const warnings = JSON.parse(rawWarnings);
      if (Array.isArray(warnings)) {
        setClientWarnings(warnings.filter((warning): warning is string => typeof warning === "string" && warning.trim().length > 0));
        return;
      }
    } catch {
      // Fall through and store the raw value below.
    }
    setClientWarnings([rawWarnings]);
  }, [trip?.id]);

  const image = useMemo(() => getDestinationImage(trip?.destination, destinations), [destinations, trip?.destination]);
  const visibleWarnings = useMemo(() => {
    const seen = new Set<string>();
    return [...(trip?.warnings ?? []), ...clientWarnings]
      .map((warning) => warning.trim())
      .filter((warning) => {
        if (warning.length === 0 || seen.has(warning)) return false;
        seen.add(warning);
        return true;
      });
  }, [clientWarnings, trip?.warnings]);
  const aiMessages = useMemo(() => visibleWarnings.map(normalizeAiMessage), [visibleWarnings]);
  const positiveMessages = useMemo(() => aiMessages.filter((message) => message.kind === "positive"), [aiMessages]);
  const cautionMessages = useMemo(() => aiMessages.filter((message) => message.kind === "caution"), [aiMessages]);
  const shownAiMessages = useMemo(() => [...positiveMessages, ...cautionMessages], [cautionMessages, positiveMessages]);

  const weatherSignature = useMemo(() => {
    if (!trip?.id || !trip.startDate || forecast.length === 0) return "";
    const suggestions = getRescheduleSuggestions(
      (trip.schedule ?? []).map((d) => ({
        day: d.day,
        activities: d.activities.map((a) => ({ name: a.name, type: a.type, location: a.location, time: a.time })),
      })),
      forecast,
      trip.startDate,
    );
    if (suggestions.length === 0) return "";
    return suggestions.map((s) => `${s.activityName}-${s.fromDay}-${s.toDay}`).join("|");
  }, [trip, forecast]);

  useEffect(() => {
    if (!trip?.id || !weatherSignature || typeof window === "undefined") {
      setWeatherAdvisoryDismissed(false);
      return;
    }
    const dismissedSignature = window.localStorage.getItem(`vivuplan:trip:${trip.id}:weather-dismissed`);
    setWeatherAdvisoryDismissed(dismissedSignature === weatherSignature);
  }, [trip?.id, weatherSignature]);

  const dismissWeatherAdvisory = () => {
    if (!trip?.id || !weatherSignature || typeof window === "undefined") return;
    window.localStorage.setItem(`vivuplan:trip:${trip.id}:weather-dismissed`, weatherSignature);
    setWeatherAdvisoryDismissed(true);
  };

  const restoreWeatherAdvisory = () => {
    if (trip?.id && typeof window !== "undefined") {
      window.localStorage.removeItem(`vivuplan:trip:${trip.id}:weather-dismissed`);
    }
    setWeatherAdvisoryDismissed(false);
  };

  const day = trip?.schedule?.[activeDay];
  const canEdit = Boolean(authUser) && !readOnlyShareView;
  const dayActivities = day?.activities ?? [];
  const dayTotal = day?.activities?.reduce((sum, activity) => sum + activity.estimatedCost, 0) ?? 0;
  const dayTransportCount = dayActivities.filter((activity) => activity.type === "TRANSPORT").length;
  const dayPlaceCount = dayActivities.filter((activity) => activity.type !== "TRANSPORT").length;
  const hasDayMapCoordinates = dayActivities.some((activity) =>
    hasValidMapCoordinate(activity, trip?.departure, trip?.destination));
  const dayTimeRange = getDayTimeRange(dayActivities);
  const dayDirectionsUrl = buildDayDirectionsUrl(dayActivities, trip?.destination);
  const showDayScrollControls = (trip?.schedule?.length ?? 0) > 5;
  const scrollDayTabs = (direction: "left" | "right") => {
    const container = dayTabsRef.current;
    if (!container) return;
    container.scrollBy({
      left: direction === "left" ? -220 : 220,
      behavior: "smooth",
    });
  };
  const budget = trip?.budget;

  const targetBudget =
    trip
      ? trip.budgetMode === "TOTAL" && trip.budgetTotal
        ? trip.budgetTotal
        : trip.budgetPerPerson * Math.max(1, trip.travelerCount ?? 1)
      : 0;
  const budgetDiff = budget && targetBudget ? budget.total - targetBudget : 0;
  const budgetOverPercent = targetBudget > 0 ? Math.round((budgetDiff / targetBudget) * 100) : 0;
  const isBudgetWarning = budgetDiff > targetBudget * 0.1;
  const budgetRows = budget
    ? [
      ["Di chuyển", budget.transport],
      ["Lưu trú", budget.accommodation],
      ["Ăn uống", budget.food],
      ["Tham quan", budget.activities],
    ]
    : [];

  const exportPDF = async () => {
    if (!trip) return;
    setExportingPdf(true);
    const element = document.getElementById("pdf-booklet");
    try {
      const html2pdf = (await import("html2pdf.js")).default;
      if (!element) {
        showToast("Không tìm thấy dữ liệu lịch trình để xuất PDF", "error");
        return;
      }

      const opt = {
        margin: 0,
        filename: `VivuPlan_${trip.destination.replace(/\s+/g, "_")}.pdf`,
        image: { type: "jpeg" as const, quality: 0.98 },
        html2canvas: {
          scale: 2,
          useCORS: true,
          logging: false,
          letterRendering: true,
          windowWidth: 1024,
          width: 794,
        },
        jsPDF: { unit: "mm" as const, format: "a4" as const, orientation: "portrait" as const },
        pagebreak: { mode: "css" as const },
      };

      await html2pdf().set(opt).from(element).save();
    } catch (e) {
      console.error(e);
      showToast("Gặp lỗi khi xuất file PDF. Vui lòng thử lại.", "error");
    } finally {
      setExportingPdf(false);
    }
  };

  const shareTrip = async () => {
    if (!trip?.shareCode) {
      setShareError("Lịch trình chưa có link chia sẻ.");
      return;
    }

    setSharing(true);
    setShareError("");
    const shareUrl = getTripShareUrl(trip.shareCode);
    const copyAttempt = copyTextToClipboard(shareUrl);
    try {
      let shareableTrip = trip;
      if (!trip.isPublic) {
        const updated = await tripApi.ensurePublicShare(trip.id);
        shareableTrip = { ...trip, ...updated, isPublic: true };
        setTrip(shareableTrip);
      }

      const copied = await copyAttempt;
      if (!copied) {
        setShareError(
          trip.isPublic
            ? "Không thể sao chép link. Hãy thử lại hoặc sao chép từ thanh địa chỉ."
            : "Đã bật chia sẻ nhưng không thể sao chép link. Hãy thử lại.",
        );
        return;
      }

      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch (e) {
      setShareError(e instanceof Error ? e.message : "Không thể tạo link chia sẻ. Vui lòng thử lại.");
    } finally {
      setSharing(false);
    }
  };

  const previewRegenerateDay = async (request: RegenerateDayRequest) => {
    if (!trip || !day) return;
    const requestId = regenerateRequestId.current + 1;
    regenerateRequestId.current = requestId;
    setRegeneratingDay(true);
    setRegenerateCloseConfirmOpen(false);
    setRegeneratePreview(null);
    setRegeneratePreviewError("");
    setSelectedRegenerateIndexes([]);
    try {
      const preview = await tripApi.previewRegenerateDay(trip.id, day.day, request);
      if (regenerateRequestId.current !== requestId) return;
      setRegenerateCloseConfirmOpen(false);
      refreshWallet();
      setRegeneratePreview(preview);
      setSelectedRegenerateIndexes(preview.day.activities.map((_, index) => index));
      const requestWarning = preview.warnings.find((warning) => warning.includes("Yêu cầu"));
      if (requestWarning) {
        showToast(requestWarning, "info", 9000);
      }
    } catch (e) {
      if (regenerateRequestId.current !== requestId) return;
      setRegenerateCloseConfirmOpen(false);
      if (e instanceof ApiError && e.status === 402) {
        setPurchaseOpen(true);
        setRegeneratePreviewError("Bạn đã hết lượt chỉnh ngày bằng AI. Mua thêm lượt để tiếp tục nhé.");
      } else {
        setRegeneratePreviewError(e instanceof Error ? e.message : "Không thể tạo phương án mới cho ngày này");
      }
    } finally {
      if (regenerateRequestId.current === requestId) {
        setRegeneratingDay(false);
      }
    }
  };

  const closeRegenerateModal = () => {
    setRegenerateOpen(false);
    setRegeneratePreview(null);
    setRegeneratePreviewError("");
    setSelectedRegenerateIndexes([]);
    setRegenerateCloseConfirmOpen(false);
  };

  const requestCloseRegenerateModal = () => {
    if (regeneratingDay) {
      setRegenerateCloseConfirmOpen(true);
      return;
    }
    if (applyingRegeneration) return;
    closeRegenerateModal();
  };

  const forceCloseRegenerateModal = () => {
    regenerateRequestId.current += 1;
    setRegeneratingDay(false);
    closeRegenerateModal();
  };

  const applyRegeneratedDay = async () => {
    if (!trip || !regeneratePreview) return;
    setApplyingRegeneration(true);
    setRegeneratePreviewError("");
    try {
      const updated = await tripApi.applyRegenerateDay(
        trip.id,
        regeneratePreview.dayNumber,
        regeneratePreview.proposalId,
        selectedRegenerateIndexes,
      );
      setTrip(updated);
      const nextIndex = updated.schedule?.findIndex((item) => item.day === regeneratePreview.dayNumber) ?? activeDay;
      if (nextIndex >= 0) setActiveDay(nextIndex);
      setExpanded(null);
      closeRegenerateModal();
      showToast("Đã áp dụng thay đổi cho ngày thành công!", "success", 3000);
    } catch (e) {
      const message = e instanceof Error ? e.message : "Không thể áp dụng phương án mới";
      setRegeneratePreviewError(message);
    } finally {
      setApplyingRegeneration(false);
    }
  };

  const saveActivity = async (payload: ActivityMutationRequest) => {
    if (!trip || !editor) return;
    setSavingActivity(true);
    setActivityError("");
    try {
      const updated =
        editor.mode === "add"
          ? await tripApi.addActivity(trip.id, editor.dayNumber, payload)
          : await tripApi.updateActivity(trip.id, editor.activity!.id, payload);
      setTrip(updated);
      setEditor(null);
      setExpanded(null);
    } catch (e) {
      setActivityError(e instanceof Error ? e.message : "Không thể lưu hoạt động");
    } finally {
      setSavingActivity(false);
    }
  };

  const deleteActivity = async (activity: ActivityResponse) => {
    if (!trip) return;
    setActivityError("");
    try {
      const updated = await tripApi.deleteActivity(trip.id, activity.id);
      setTrip(updated);
      setExpanded(null);
    } catch (e) {
      setActivityError(e instanceof Error ? e.message : "Không thể xóa hoạt động");
    }
  };

  // Wait for auth resolution so numeric owner routes can be distinguished from public share links.
  if (authLoading || redirectingForbidden) return null;

  if (loading) {
    return <ItineraryLoadingState message="Đang tải lịch trình..." />;
  }

  if (error || !trip || !day) {
    return (
      <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
        <Navbar />
        <div className="container" style={{ paddingTop: 120 }}>
          <Card style={{ padding: 32, textAlign: "center" }}>
            <AlertCircle size={34} style={{ color: "#DC2626", marginBottom: 12 }} />
            <h1 style={{ fontSize: 22, marginBottom: 8 }}>Không thể mở lịch trình</h1>
            <p style={{ color: "var(--text-3)", marginBottom: 18 }}>{error || "Lịch trình không có dữ liệu hoạt động."}</p>
            <Button onClick={() => router.push("/itinerary")}>Quay về chuyến đi của tôi</Button>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="itinerary-detail-page" style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
      <Navbar />

      <section
        className="itinerary-detail-hero"
        style={{
          paddingTop: 64,
          backgroundImage: `linear-gradient(90deg, rgba(4,47,46,0.56), rgba(4,47,46,0.12)), url(${image})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          color: "white",
        }}
      >
        <div className="container itinerary-detail-hero-inner">
          <div className="itinerary-detail-hero-layout">
            <div className="itinerary-detail-hero-content">
              <Badge tone="glass" style={{ marginBottom: 14 }}>
                <MapPin size={13} /> {trip.destination}
              </Badge>
              <h1 className="itinerary-detail-title">
                Lịch trình {trip.destination} {trip.days} ngày
              </h1>
              <div className="itinerary-header-meta">
                <div className="itinerary-meta-item">
                  <Navigation size={15} />
                  <span>{trip.departure || "Điểm xuất phát"} → {trip.destination}</span>
                </div>
                <div className="itinerary-meta-divider" />
                {trip.startDate && trip.endDate && (
                  <>
                    <div className="itinerary-meta-item">
                      <Calendar size={15} />
                      <span>{fmtDate(trip.startDate)} - {fmtDate(trip.endDate)}</span>
                    </div>
                    <div className="itinerary-meta-divider" />
                  </>
                )}
                <div className="itinerary-meta-item">
                  <Clock size={15} />
                  <span>{trip.days}N{trip.days - 1}Đ</span>
                </div>
                <div className="itinerary-meta-divider" />
                <div className="itinerary-meta-item">
                  <Wallet size={15} />
                  <span>{fmtCost(trip.budget?.total || trip.budgetTotal || trip.budgetPerPerson * (trip.travelerCount || 1))}</span>
                </div>
                <div className="itinerary-meta-divider" />
                <div className="itinerary-meta-item">
                  <Sparkles size={15} />
                  <span>{styleLabel[trip.style] ?? trip.style}</span>
                </div>
                <div className="itinerary-meta-divider" />
                <div className="itinerary-meta-item">
                  <Users size={15} />
                  <span>{groupLabel[trip.groupType] ?? trip.groupType} ({trip.travelerCount || 1} người)</span>
                </div>
              </div>
            </div>
            <div className="itinerary-share-actions">
              <Button
                variant="secondary"
                size="sm"
                className="itinerary-print-button no-print"
                onClick={exportPDF}
                disabled={exportingPdf}
                aria-busy={exportingPdf}
              >
                {exportingPdf ? <span className="spinner spinner-inline" /> : <Printer size={14} />}
                {exportingPdf ? "Đang tải" : "Tải PDF"}
              </Button>
              {!readOnlyShareView && (
                <>
                  <Button
                    variant="secondary"
                    size="sm"
                    className="trip-share-button itinerary-share-button"
                    onClick={shareTrip}
                    disabled={sharing}
                    aria-busy={sharing}
                  >
                    {sharing ? <span className="spinner spinner-inline" /> : copied ? <CheckCircle2 size={14} /> : <Share2 size={14} />}
                    {sharing ? "Đang chia sẻ..." : copied ? "Đã copy link" : "Chia sẻ"}
                  </Button>
                  {shareError && <span className="itinerary-share-error">{shareError}</span>}
                </>
              )}
            </div>
          </div>
        </div>
      </section>

      <main className="container itinerary-detail-main">
        {aiMessages.length > 0 && (
          <section className="itinerary-ai-messages" aria-label="Thông điệp từ AI">
            <div
              className="itinerary-ai-messages-head"
              style={{ cursor: "pointer", userSelect: "none" }}
              onClick={() => setAiWarningsCollapsed(!aiWarningsCollapsed)}
            >
              <Sparkles size={16} />
              <div style={{ flex: 1 }}>
                <strong>AI đã tối ưu lịch trình</strong>
              </div>
              <div style={{ marginLeft: "auto", display: "flex", alignItems: "center" }}>
                {aiWarningsCollapsed ? <ChevronDown size={18} style={{ color: "#92400E" }} /> : <ChevronUp size={18} style={{ color: "#92400E" }} />}
              </div>
            </div>
            {!aiWarningsCollapsed && (
              <>
                <div className="itinerary-ai-summary-list">
                  {shownAiMessages.map((message) => (
                    <div key={message.raw} className={`itinerary-ai-summary-item ${message.kind}`}>
                      {message.kind === "positive" ? <CheckCircle2 size={14} /> : <AlertCircle size={14} />}
                      <span>{message.displayText}</span>
                    </div>
                  ))}
                </div>
              </>
            )}
          </section>
        )}

        {/* Feature 1 – Smart Reschedule Suggestions */}
        {trip.startDate && forecast.length > 0 && (() => {
          const suggestions = getRescheduleSuggestions(
            (trip.schedule ?? []).map((d) => ({
              day: d.day,
              activities: d.activities.map((a) => ({ name: a.name, type: a.type, location: a.location, time: a.time })),
            })),
            forecast,
            trip.startDate,
          );
          if (suggestions.length === 0) return null;

          return weatherAdvisoryDismissed ? (
            <div className="itinerary-weather-messages-collapsed">
              <span><Sparkles size={13} style={{ color: "#2563eb" }} /> Trợ lý AI gợi ý tối ưu lịch trình theo thời tiết</span>
              <button type="button" onClick={restoreWeatherAdvisory}>Hiện lại</button>
            </div>
          ) : (
            <div style={{
              marginBottom: 24,
              padding: "16px 20px",
              borderRadius: "var(--r-lg)",
              background: "linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)",
              border: "1px solid #93c5fd",
              boxShadow: "0 4px 18px -4px rgba(59, 130, 246, 0.12)",
              display: "flex",
              flexDirection: "column",
              gap: 12
            }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <p style={{ margin: 0, fontWeight: 700, fontSize: 14, color: "#1e40af", display: "flex", alignItems: "center", gap: 8 }}>
                  <Sparkles size={16} className="animate-pulse" style={{ color: "#2563eb" }} />
                  Trợ lý AI gợi ý tối ưu lịch trình theo thời tiết
                </p>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <span style={{ fontSize: 11, background: "#dbeafe", color: "#1e40af", padding: "2px 8px", borderRadius: 99, fontWeight: 600 }}>Tối ưu</span>
                  <button
                    type="button"
                    style={{
                      border: 0,
                      background: "transparent",
                      color: "#1e40af",
                      cursor: "pointer",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      padding: 2,
                      opacity: 0.7,
                      transition: "opacity 0.15s ease"
                    }}
                    onClick={dismissWeatherAdvisory}
                    onMouseEnter={(e) => e.currentTarget.style.opacity = "1"}
                    onMouseLeave={(e) => e.currentTarget.style.opacity = "0.7"}
                    aria-label="Ẩn gợi ý thời tiết"
                  >
                    <X size={14} />
                  </button>
                </div>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {suggestions.map((s, i) => (
                  <div key={i} style={{ display: "flex", gap: 10, alignItems: "flex-start", padding: "10px 12px", background: "rgba(255, 255, 255, 0.6)", borderRadius: "var(--r-md)", border: "1px solid rgba(147, 197, 253, 0.3)" }}>
                    <span style={{
                      display: "flex", alignItems: "center", justifyContent: "center",
                      flexShrink: 0, width: 22, height: 22, borderRadius: "50%",
                      background: "rgba(245, 158, 11, 0.12)", color: "#d97706",
                      marginTop: 1
                    }}>
                      <Lightbulb size={12} />
                    </span>
                    <p style={{ margin: 0, fontSize: 13, color: "#1e40af", lineHeight: 1.5 }}>
                      Cân nhắc chuyển <strong>&quot;{s.activityName}&quot;</strong> từ Ngày {s.fromDay} sang Ngày {s.toDay}: {s.reason}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          );
        })()}

        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 320px", gap: 24 }} className="itinerary-grid itinerary-detail-grid">
          <section className="itinerary-detail-primary">

            <div className="itinerary-day-toolbar">
              <div className="itinerary-day-tabs-wrap">
                {showDayScrollControls && (
                  <button
                    type="button"
                    className="itinerary-day-scroll-button"
                    onClick={() => scrollDayTabs("left")}
                    aria-label="Xem các ngày trước"
                  >
                    <ChevronLeft size={16} />
                  </button>
                )}
                <div ref={dayTabsRef} className="itinerary-day-tabs no-scrollbar">
                {trip.schedule?.map((item, index) => {
                  const dw = getByDayIndex(item.day - 1, trip.startDate);
                  const weatherSummary = dw ? summarizeItineraryDayWeather(dw) : null;
                  const dc = weatherSummary?.condition ?? null;
                  return (
                    <button
                      key={item.day}
                      onClick={() => {
                        setActiveDay(index);
                        setExpanded(null);
                        setRegenerateOpen(false);
                        setRegeneratePreview(null);
                      }}
                      className={activeDay === index ? "btn btn-primary btn-sm" : "btn btn-secondary btn-sm"}
                      title={dc ? `${dc.label} · ${dw?.minTemp.toFixed(0)}–${dw?.maxTemp.toFixed(0)}°C` : undefined}
                      style={{ display: "flex", alignItems: "center", gap: 6, whiteSpace: "nowrap" }}
                    >
                      {dc && (
                        <WeatherIcon iconKey={dc.iconKey} size={14} />
                      )}
                      Ngày {item.day}
                    </button>
                  );
                })}
                </div>
                {showDayScrollControls && (
                  <button
                    type="button"
                    className="itinerary-day-scroll-button"
                    onClick={() => scrollDayTabs("right")}
                    aria-label="Xem các ngày sau"
                  >
                    <ChevronRight size={16} />
                  </button>
                )}
              </div>
              {canEdit && (
                <Button variant="primary" size="sm" onClick={() => setEditor({ mode: "add", dayNumber: day.day })}>
                  <Plus size={13} /> Thêm hoạt động
                </Button>
              )}
            </div>

            <Card className="itinerary-day-overview">
              <div className="itinerary-day-overview-head">
                <div className="itinerary-day-title-row">
                  <h2>{day.title}</h2>
                  {canEdit && (
                    <div className="itinerary-day-actions">
                      <Button type="button" variant="secondary" size="sm" onClick={() => {
                        setRegeneratePreviewError("");
                        setRegenerateCloseConfirmOpen(false);
                        setRegenerateOpen(true);
                      }}>
                        <Sparkles size={13} /> Tạo lại ngày
                      </Button>
                    </div>
                  )}
                </div>
                <p style={{ color: "var(--text-3)", fontSize: 14, lineHeight: 1.65, margin: 0 }}>{day.summary}</p>
              </div>

              <div className="itinerary-day-insights">
                <div>
                  <Clock size={15} />
                  <span>Khung giờ</span>
                  <strong>{dayTimeRange}</strong>
                </div>
                <div>
                  <Wallet size={15} />
                  <span>Chi phí ngày</span>
                  <strong>{fmtCost(dayTotal)}</strong>
                </div>
                {/* Feature 3 – Weather insight chip (always rendered for layout consistency) */}
                {(() => {
                  const dw = getByDayIndex(activeDay, trip.startDate);
                  const weatherSummary = dw ? summarizeItineraryDayWeather(dw) : null;
                  const dc = weatherSummary?.condition ?? null;
                  return (
                    <div title={weatherSummary?.title ?? "Chưa có dữ liệu thời tiết"}>
                      {dc ? <WeatherIcon iconKey={dc.iconKey} size={16} /> : <Thermometer size={16} />}
                      <span>Thời tiết</span>
                      <strong>{dw ? `${dw.minTemp.toFixed(0)}–${dw.maxTemp.toFixed(0)}°C` : "--°C"}</strong>
                    </div>
                  );
                })()}
              </div>
            </Card>

            <div className="itinerary-activity-timeline" style={{ position: "relative" }}>
              <div style={{ position: "absolute", left: 22, top: 18, bottom: 18, width: 2, background: "linear-gradient(to bottom, var(--primary), var(--border))", borderRadius: 99 }} />

              <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                {day.activities.map((activity, index) => {
                  const dw = getByDayIndex(activeDay, trip.startDate);
                  const warning = dw ? getActivityWeatherWarning(activity.name, dw, activity.location, activity.type, activity.time) : null;
                  return (
                    <ActivityItem
                      key={activity.id ?? `${activity.time}-${activity.name}`}
                      activity={activity}
                      tripContext={{
                        destination: trip.destination,
                        startDate: trip.startDate,
                        endDate: trip.endDate,
                        travelerCount: trip.travelerCount,
                      }}
                      warning={warning}
                      expanded={expanded === `${activeDay}-${index}`}
                      onToggle={() => setExpanded(expanded === `${activeDay}-${index}` ? null : `${activeDay}-${index}`)}
                      canEdit={canEdit}
                      onEdit={() => {
                        setActivityError("");
                        setEditor({ mode: "edit", dayNumber: day.day, activity });
                      }}
                      onDelete={() => void deleteActivity(activity)}
                    />
                  );
                })}
              </div>
            </div>
          </section>

          <aside className="itinerary-detail-aside" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <Card className="map-preview-card" style={{ overflow: "hidden" }}>
              {hasDayMapCoordinates ? (
                <DayRouteMap activities={dayActivities} destination={trip?.destination} departure={trip?.departure} directionsUrl={dayDirectionsUrl} />
              ) : (
                <div style={{ height: 150, backgroundImage: `linear-gradient(180deg, rgba(15,159,156,0.1), rgba(15,159,156,0.32)), url(${image})`, backgroundSize: "cover", backgroundPosition: "center" }} />
              )}
              <div style={{ padding: 18 }}>
                <h3 style={{ fontSize: 16, marginBottom: 8, display: "flex", alignItems: "center", gap: 8 }}>
                  <Navigation size={16} style={{ color: "var(--primary)" }} /> Các điểm trong ngày
                </h3>
                <p className="itinerary-route-summary">
                  {dayPlaceCount} điểm dừng, {dayTransportCount} chặng di chuyển. Hiển thị theo thứ tự lịch trình, mở Google Maps để xem đường đi thực tế.
                </p>
                <Button
                  variant="secondary"
                  size="sm"
                  href={dayDirectionsUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  Mở Google Maps <ExternalLink size={12} />
                </Button>
              </div>
            </Card>

            <Card style={{ padding: 22 }}>
              <h3 style={{ fontSize: 16, marginBottom: 14, display: "flex", alignItems: "center", gap: 8 }}>
                <Wallet size={17} style={{ color: "var(--primary)" }} /> Ngân sách
              </h3>
              <div style={{ fontSize: 30, fontFamily: "var(--font-heading)", fontWeight: 900, color: "var(--primary)", marginBottom: 4 }}>
                {fmtCost(budget?.total ?? trip.budgetPerPerson)}
              </div>
              <p style={{ color: "var(--text-4)", fontSize: 12, marginBottom: 18 }}>Tổng ước tính toàn chuyến</p>
              {targetBudget > 0 && budget && (
                <div
                  style={{
                    marginBottom: 16,
                    padding: "10px 12px",
                    borderRadius: "var(--r-md)",
                    background: isBudgetWarning ? "#FEF2F2" : "var(--primary-light)",
                    color: isBudgetWarning ? "#B91C1C" : "var(--primary-hover)",
                    fontSize: 12,
                    lineHeight: 1.55,
                  }}
                >
                  {isBudgetWarning
                    ? `Cảnh báo: ước tính vượt ngân sách ${budgetOverPercent}%. Bạn có thể giảm hoạt động hoặc chấp nhận vượt ngân sách.`
                    : `Ước tính hiện tại ${fmtCost(budget.total)} trên ngân sách ${fmtCost(targetBudget)}.`}
                </div>
              )}
              <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                {budgetRows.map(([label, value]) => (
                  <div key={label}>
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, marginBottom: 5 }}>
                      <span style={{ color: "var(--text-3)" }}>{label}</span>
                      <strong>{fmtCost(Number(value))}</strong>
                    </div>
                    <div style={{ height: 7, background: "var(--surface-2)", borderRadius: 99 }}>
                      <div
                        style={{
                          height: "100%",
                          width: `${Math.min(100, Math.round((Number(value) / Math.max(1, budget?.total ?? trip.budgetPerPerson)) * 100))}%`,
                          background: "linear-gradient(90deg, var(--primary), var(--secondary))",
                          borderRadius: 99,
                        }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </Card>

          </aside>
        </div>
      </main>

      {canEdit && editor && (
        <ActivityEditorModal
          key={`${editor.mode}-${editor.activity?.id ?? editor.dayNumber}`}
          activity={editor.activity}
          saving={savingActivity}
          error={activityError}
          onSave={saveActivity}
          onCancel={() => {
            setEditor(null);
            setActivityError("");
          }}
        />
      )}

      {canEdit && regenerateOpen && day && (
        <RegenerateDayModal
          day={day}
          preview={regeneratePreview}
          error={regeneratePreviewError}
          loading={regeneratingDay}
          applying={applyingRegeneration}
          closeConfirmOpen={regenerateCloseConfirmOpen}
          selectedIndexes={selectedRegenerateIndexes}
          onSelectedIndexesChange={setSelectedRegenerateIndexes}
          editCredits={wallet?.editCredits}
          onClearError={() => setRegeneratePreviewError("")}
          onPreview={previewRegenerateDay}
          onApply={applyRegeneratedDay}
          onCancel={requestCloseRegenerateModal}
          onContinueWaiting={() => setRegenerateCloseConfirmOpen(false)}
          onForceClose={forceCloseRegenerateModal}
        />
      )}
      <PurchaseModal
        open={canEdit && purchaseOpen}
        reason="EDIT"
        onClose={() => setPurchaseOpen(false)}
      />

      {/* ── PDF Booklet (hidden, rendered by html2pdf) ── */}
      <div style={{ position: "absolute", left: -9999, top: -9999, width: 794, height: "auto", overflow: "hidden" }}>
        <div id="pdf-booklet" className="pdf">
          {/* Cover / Header */}
          <div className="pdf-header">
            <div className="pdf-header-top">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={logoUrl} alt="" width={48} height={72} className="pdf-logo" />
              <span className="pdf-brand">
                <span className="pdf-brand-v">Vivu</span><span className="pdf-brand-p">Plan</span>
              </span>
            </div>
            <h1 className="pdf-destination">{trip.destination}</h1>
            <p className="pdf-subtitle">Lịch trình {trip.days} ngày {trip.days - 1} đêm</p>
          </div>

          {/* Trip meta */}
          <div className="pdf-meta">
            <div className="pdf-meta-item">
              <span className="pdf-meta-label">Xuất phát</span>
              <span className="pdf-meta-value">{trip.departure || "—"}</span>
            </div>
            {trip.startDate && trip.endDate && (
              <div className="pdf-meta-item">
                <span className="pdf-meta-label">Ngày đi</span>
                <span className="pdf-meta-value">{fmtDate(trip.startDate)} – {fmtDate(trip.endDate)}</span>
              </div>
            )}
            <div className="pdf-meta-item">
              <span className="pdf-meta-label">Thành viên</span>
              <span className="pdf-meta-value">{groupLabel[trip.groupType] ?? trip.groupType} · {trip.travelerCount || 1} người</span>
            </div>
            <div className="pdf-meta-item">
              <span className="pdf-meta-label">Ngân sách</span>
              <span className="pdf-meta-value">{fmtCost(trip.budget?.total || trip.budgetTotal || trip.budgetPerPerson * (trip.travelerCount || 1))}</span>
            </div>
          </div>

          {/* Day sections */}
          {trip.schedule?.map((item) => (
            <div key={item.day} className="pdf-day">
              <div className="pdf-day-head">
                <h2 className="pdf-day-title">{formatDayTitle(item.day, item.title)}</h2>
                {item.summary && <p className="pdf-day-summary">{item.summary}</p>}
              </div>

              <table className="pdf-table">
                <thead>
                  <tr>
                    <th className="pdf-th-time">Giờ</th>
                    <th className="pdf-th-detail">Hoạt động</th>
                    <th className="pdf-th-dur">Thời lượng</th>
                    <th className="pdf-th-cost">Chi phí</th>
                  </tr>
                </thead>
                <tbody>
                  {item.activities?.map((a) => (
                    <tr key={a.id} className="pdf-row">
                      <td className="pdf-td-time">{a.time}</td>
                      <td className="pdf-td-detail">
                        <strong className="pdf-act-name">{a.name}</strong>
                        <span className="pdf-act-loc">{a.location}</span>
                        {a.note && <span className="pdf-act-note">{a.note}</span>}
                      </td>
                      <td className="pdf-td-dur">{a.duration}</td>
                      <td className="pdf-td-cost">{fmtActivityCost(a)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

const activityTypeOptions = [
  { value: "FOOD", label: "Ăn uống" },
  { value: "CAFE", label: "Cà phê" },
  { value: "ATTRACTION", label: "Địa điểm" },
  { value: "ACTIVITY", label: "Hoạt động" },
  { value: "NIGHTLIFE", label: "Buổi tối" },
  { value: "TRANSPORT", label: "Di chuyển" },
  { value: "ACCOMMODATION", label: "Lưu trú" },
];

function parseActivityTimeMinutes(time?: string) {
  const match = time?.match(/^([01]\d|2[0-3]):([0-5]\d)$/);
  if (!match) return null;
  return Number(match[1]) * 60 + Number(match[2]);
}

function parseActivityDurationMinutes(duration?: string) {
  if (!duration?.trim()) return 60;
  const normalized = duration
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
  let minutes = 0;
  const hourMatch = normalized.match(/(\d+(?:[\.,]\d+)?)\s*(gio|h)/);
  if (hourMatch) minutes += Math.round(Number(hourMatch[1].replace(",", ".")) * 60);
  const minuteMatch = normalized.match(/(\d+)\s*(phut|p|min)/);
  if (minuteMatch) minutes += Number(minuteMatch[1]);
  return minutes > 0 ? minutes : 60;
}

function findActivityTimeConflicts(activities: ActivityResponse[]) {
  const ranges = activities
    .map((activity) => {
      const start = parseActivityTimeMinutes(activity.time);
      if (start == null) return null;
      return {
        name: activity.name,
        start,
        end: start + Math.max(15, parseActivityDurationMinutes(activity.duration)),
      };
    })
    .filter((item): item is { name: string; start: number; end: number } => Boolean(item))
    .sort((a, b) => a.start - b.start);

  const conflicts: string[] = [];
  for (let index = 1; index < ranges.length; index++) {
    const previous = ranges[index - 1];
    const current = ranges[index];
    if (current.start < previous.end) {
      conflicts.push(`${previous.name} bị trùng giờ với ${current.name}`);
    }
  }
  return conflicts;
}

function RegenerateDayModal({
  day,
  preview,
  error,
  loading,
  applying,
  closeConfirmOpen,
  selectedIndexes,
  editCredits,
  onClearError,
  onSelectedIndexesChange,
  onPreview,
  onApply,
  onCancel,
  onContinueWaiting,
  onForceClose,
}: {
  day: NonNullable<TripResponse["schedule"]>[number];
  preview: RegenerateDayPreviewResponse | null;
  error: string;
  loading: boolean;
  applying: boolean;
  closeConfirmOpen: boolean;
  selectedIndexes: number[];
  editCredits?: number;
  onClearError: () => void;
  onSelectedIndexesChange: (indexes: number[]) => void;
  onPreview: (request: RegenerateDayRequest) => Promise<void>;
  onApply: () => Promise<void>;
  onCancel: () => void;
  onContinueWaiting: () => void;
  onForceClose: () => void;
}) {
  const [instruction, setInstruction] = useState("");
  const [localError, setLocalError] = useState("");
  const costDiff = preview ? preview.newBudget - preview.oldBudget : 0;
  const selectedCount = preview ? selectedIndexes.length : 0;
  const allPreviewIndexes = preview?.day.activities.map((_, index) => index) ?? [];
  const allSelected = preview ? selectedIndexes.length === preview.day.activities.length : false;
  const selectionTimeConflicts = useMemo(() => {
    if (!preview) return [];
    const selected = new Set(selectedIndexes);
    const previewLength = preview.day.activities.length;
    const oldLength = day.activities.length;
    const allNewSelected = selectedIndexes.length === previewLength;
    const mergedActivities: ActivityResponse[] = [];

    for (let index = 0; index < Math.max(oldLength, previewLength); index++) {
      const hasNew = index < previewLength && preview.day.activities[index] != null;
      const hasOld = index < oldLength && day.activities[index] != null;

      if (hasNew && selected.has(index)) {
        mergedActivities.push(preview.day.activities[index]);
      } else if (hasNew && !selected.has(index) && hasOld) {
        mergedActivities.push(day.activities[index]);
      } else if (!hasNew && hasOld && !allNewSelected) {
        // Only keep old "tail" activities (no new counterpart) when the user is
        // doing a PARTIAL apply. If all new activities are selected, the AI
        // intentionally merged/replaced these activities → exclude them.
        mergedActivities.push(day.activities[index]);
      }
    }

    return findActivityTimeConflicts(mergedActivities);
  }, [day.activities, preview, selectedIndexes]);
  const previewPairs = preview
    ? Array.from({ length: Math.max(day.activities.length, preview.day.activities.length) }, (_, index) => ({
      oldActivity: day.activities[index],
      newActivity: preview.day.activities[index],
      index,
    }))
    : [];

  const renderPreviewActivity = (activity: NonNullable<TripResponse["schedule"]>[number]["activities"][number]) => (
    <>
      <span>{activity.time}</span>
      <div>
        <strong>{activity.name}</strong>
        <small>{activity.location || typeConfig[activity.type]?.label || activity.type} · {activity.duration} · {fmtActivityCost(activity)}</small>
      </div>
    </>
  );

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedInstruction = instruction.trim();
    if (!trimmedInstruction) {
      setLocalError("Bạn hãy nhập điều muốn thay đổi để VivuPlan tạo phương án phù hợp hơn.");
      return;
    }
    if (trimmedInstruction.length > REGENERATE_INSTRUCTION_MAX_LENGTH) {
      setLocalError(`Yêu cầu chỉnh ngày tối đa ${REGENERATE_INSTRUCTION_MAX_LENGTH} ký tự.`);
      return;
    }
    setLocalError("");
    onClearError();
    void onPreview({ intent: "REGENERATE", instruction: trimmedInstruction });
  };

  const toggleSelectedIndex = (index: number) => {
    onSelectedIndexesChange(
      selectedIndexes.includes(index)
        ? selectedIndexes.filter((item) => item !== index)
        : [...selectedIndexes, index].sort((a, b) => a - b),
    );
  };

  return (
    <div
      className="activity-editor-modal-backdrop regenerate-day-modal-backdrop"
      role="dialog"
      aria-modal="true"
    >
      <div className="activity-editor-modal-panel regenerate-day-modal-panel">
        <Card className="activity-editor-card regenerate-day-card">
          <div className="regenerate-day-header">
            <div>
              <Badge tone="teal">Ngày {day.day}</Badge>
              <h3>Bạn muốn chỉnh ngày này như thế nào?</h3>
              <p>VivuPlan sẽ tạo một phương án mới cho riêng ngày này và chỉ thay đổi lịch trình khi bạn áp dụng.</p>
            </div>
            <div className="modal-close-anchor">
              <button type="button" className="btn btn-ghost btn-icon" onClick={onCancel} disabled={applying} aria-label="Đóng tạo lại ngày">
                <X size={16} />
              </button>
            </div>
          </div>

          <form onSubmit={submit} className="regenerate-day-form">
            <label className="regenerate-instruction-field regenerate-chat-field">
              Yêu cầu của bạn
              <textarea
                className="input textarea-compact"
                value={instruction}
                maxLength={REGENERATE_INSTRUCTION_MAX_LENGTH}
                onChange={(event) => {
                  setInstruction(event.target.value);
                  if (localError) setLocalError("");
                  if (error) onClearError();
                }}
                placeholder="VD: Tôi muốn ngày này tiết kiệm hơn, thêm hải sản, bớt đi bộ hoặc đổi quán ăn tối."
                disabled={loading || applying}
                required
              />
            </label>
            <p className="regenerate-chat-hint">
              Bạn có thể nói rất cụ thể: muốn ăn gì, tránh gì, giảm chi phí, đổi địa điểm, bớt di chuyển, thêm trải nghiệm địa phương hoặc giữ lại một điểm đang thích.
            </p>

            {editCredits !== undefined && (
              <div className="credit-inline-note">
                <Sparkles size={14} />
                <span>Còn <strong>{editCredits}</strong> lần chỉnh ngày bằng AI</span>
              </div>
            )}

            {localError && <div className="form-error">{localError}</div>}
            {error && !localError && <div className="form-error">{error}</div>}

            {loading && (
              <div className="regenerate-generation-status" role="status" aria-live="polite">
                <div className="spinner" />
                <div>
                  <strong>AI đang tạo phương án mới cho ngày {day.day}...</strong>
                  <p>VivuPlan đang xem lại ngày hiện tại và tạo một phương án mới để bạn so sánh trước khi áp dụng.</p>
                  <span>Quá trình này có thể mất khoảng 30 giây đến 1 phút, bạn vui lòng chờ một chút nhé.</span>
                </div>
              </div>
            )}

            {!preview && (
              <div className="regenerate-actions">
                <Button type="button" variant="secondary" onClick={onCancel} disabled={applying}>
                  Hủy
                </Button>
                <Button type="submit" disabled={loading || applying}>
                  {loading ? <span className="spinner spinner-inline spinner-on-primary" /> : <RefreshCw size={14} />}
                  {loading ? "Đang tạo phương án..." : "Gửi yêu cầu"}
                </Button>
              </div>
            )}
          </form>

          {preview && (
            <section className="regenerate-preview">
              <div className="regenerate-preview-head">
                <div className="regenerate-preview-title-row">
                  <h4>{preview.day.title}</h4>
                  <div className={costDiff > 0 ? "regenerate-cost-diff is-up" : "regenerate-cost-diff"}>
                    <span>{fmtCost(preview.oldBudget)} → {fmtCost(preview.newBudget)}</span>
                    <strong>{costDiff === 0 ? "Không đổi" : `${costDiff > 0 ? "+" : ""}${fmtCost(costDiff)}`}</strong>
                  </div>
                </div>
                <p>{preview.day.summary}</p>
              </div>

              {preview.warnings.length > 0 && (
                <div className="regenerate-warnings">
                  {preview.warnings.map((warning) => (
                    <p key={warning}><AlertCircle size={13} /> {warning}</p>
                  ))}
                </div>
              )}

              <div className="regenerate-selection-toolbar">
                <p>{selectedCount}/{preview.day.activities.length} mục mới sẽ được áp dụng</p>
                <div>
                  <button type="button" onClick={() => onSelectedIndexesChange(allPreviewIndexes)} disabled={loading || applying || allSelected}>
                    Chọn tất cả
                  </button>
                  <button type="button" onClick={() => onSelectedIndexesChange([])} disabled={loading || applying || selectedIndexes.length === 0}>
                    Bỏ chọn
                  </button>
                </div>
              </div>

              {selectionTimeConflicts.length > 0 && (
                <div className="regenerate-merge-conflicts" role="alert">
                  <AlertCircle size={14} />
                  <div>
                    <strong>Lựa chọn hiện tại đang làm trùng thời gian</strong>
                    {selectionTimeConflicts.slice(0, 3).map((conflict) => (
                      <p key={conflict}>{conflict}</p>
                    ))}
                    <span>Hãy chọn thêm các mục mới liên quan, áp dụng toàn bộ ngày mới hoặc tạo lại preview với yêu cầu rõ hơn.</span>
                  </div>
                </div>
              )}

              <div className="regenerate-pair-list">
                {previewPairs.map(({ oldActivity, newActivity, index }) => {
                  const selected = Boolean(newActivity && selectedIndexes.includes(index));
                  return (
                    <div
                      key={`pair-${index}-${oldActivity?.time ?? "new"}-${newActivity?.time ?? "old"}`}
                      className={`regenerate-pair-row${selected ? " selected" : ""}${!newActivity ? " no-new" : ""}`}
                    >
                      <div className="regenerate-pair-side">
                        <div className="regenerate-pair-title">
                          <span>{oldActivity ? `Mục cũ ${index + 1}` : "Không có mục cũ"}</span>
                          {!selected && oldActivity && <strong>Giữ nguyên</strong>}
                        </div>
                        {oldActivity ? (
                          <div className="regenerate-preview-item">
                            {renderPreviewActivity(oldActivity)}
                          </div>
                        ) : (
                          <div className="regenerate-empty-item">Nếu chọn mục mới này, nó sẽ được thêm vào ngày hiện tại.</div>
                        )}
                      </div>

                      <div className={`regenerate-pair-status${selected ? " selected" : ""}`}>
                        {selected ? (oldActivity ? "Thay bằng" : "Thêm mới") : "Không áp dụng"}
                      </div>

                      <div className="regenerate-pair-side is-new">
                        <div className="regenerate-pair-title">
                          <span>{newActivity ? `Mục mới ${index + 1}` : "Không có mục mới"}</span>
                          {selected && <strong>Sẽ áp dụng</strong>}
                        </div>
                        {newActivity ? (
                          <label className={`regenerate-selectable-item${selected ? " selected" : ""}`}>
                            <input
                              type="checkbox"
                              checked={selected}
                              onChange={() => toggleSelectedIndex(index)}
                              disabled={loading || applying}
                              aria-label={`Áp dụng ${newActivity.name}`}
                            />
                            {renderPreviewActivity(newActivity)}
                          </label>
                        ) : (
                          <div className="regenerate-empty-item">Không có đề xuất mới cho vị trí này, mục cũ sẽ được giữ lại.</div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>

              <div className="regenerate-actions">
                <Button type="button" variant="secondary" onClick={onCancel} disabled={applying}>
                  Hủy
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    const trimmedInstruction = instruction.trim();
                    if (!trimmedInstruction) {
                      setLocalError("Bạn hãy nhập điều muốn thay đổi để VivuPlan tạo phương án phù hợp hơn.");
                      return;
                    }
                    if (trimmedInstruction.length > REGENERATE_INSTRUCTION_MAX_LENGTH) {
                      setLocalError(`Yêu cầu chỉnh ngày tối đa ${REGENERATE_INSTRUCTION_MAX_LENGTH} ký tự.`);
                      return;
                    }
                    setLocalError("");
                    void onPreview({ intent: "REGENERATE", instruction: trimmedInstruction });
                  }}
                  disabled={loading || applying}
                >
                  <RefreshCw size={14} /> Tạo lại preview
                </Button>
                <Button type="button" onClick={onApply} disabled={loading || applying || selectedIndexes.length === 0 || selectionTimeConflicts.length > 0}>
                  {applying ? <span className="spinner spinner-inline spinner-on-primary" /> : <Save size={14} />}
                  {applying ? "Đang áp dụng..." : allSelected ? "Áp dụng ngày mới" : "Áp dụng mục đã chọn"}
                </Button>
              </div>
            </section>
          )}
        </Card>
      </div>
      {closeConfirmOpen && (
        <div className="modal-confirm-backdrop">
          <div className="modal-confirm-dialog" role="alertdialog" aria-modal="true" aria-label="Xác nhận đóng tạo lại ngày">
            <strong>AI vẫn đang tạo phương án mới</strong>
            <p>Nếu đóng bây giờ, kết quả tạo lại ngày sẽ không được hiển thị.</p>
            <div>
              <button type="button" onClick={onContinueWaiting}>
                Tiếp tục chờ
              </button>
              <button type="button" className="danger" onClick={onForceClose}>
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ActivityEditorModal({
  activity,
  saving,
  error,
  onSave,
  onCancel,
}: {
  activity?: ActivityResponse;
  saving: boolean;
  error?: string;
  onSave: (payload: ActivityMutationRequest) => Promise<void>;
  onCancel: () => void;
}) {
  return (
    <div
      className="activity-editor-modal-backdrop"
      role="dialog"
      aria-modal="true"
    >
      <div className="activity-editor-modal-panel">
        <ActivityEditor activity={activity} saving={saving} error={error} onSave={onSave} onCancel={onCancel} />
      </div>
    </div>
  );
}

function ActivityEditor({
  activity,
  saving,
  error,
  onSave,
  onCancel,
}: {
  activity?: ActivityResponse;
  saving: boolean;
  error?: string;
  onSave: (payload: ActivityMutationRequest) => Promise<void>;
  onCancel: () => void;
}) {
  const [form, setForm] = useState<ActivityMutationRequest>({
    time: activity?.time ?? "09:00",
    name: activity?.name ?? "",
    type: activity?.type ?? "ATTRACTION",
    location: activity?.location ?? "",
    duration: activity?.duration ?? "1 giờ",
    estimatedCost: activity?.estimatedCost ?? 0,
    note: activity?.note ?? "",
    latitude: activity?.latitude,
    longitude: activity?.longitude,
    googlePlaceId: activity?.googlePlaceId,
    sortOrder: activity?.sortOrder ?? 0,
  });
  const [localError, setLocalError] = useState("");

  const setField = (field: keyof ActivityMutationRequest, value: string | number | undefined) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const normalizeTimeInput = (value: string) => {
    const cleaned = value.replace(/[^\d:]/g, "");
    if (cleaned.includes(":")) return cleaned.slice(0, 5);
    const digits = cleaned.slice(0, 4);
    return digits.length > 2 ? `${digits.slice(0, 2)}:${digits.slice(2)}` : digits;
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!form.name?.trim()) {
      setLocalError("Tên hoạt động không được để trống.");
      return;
    }
    if (!form.time?.trim()) {
      setLocalError("Thời gian không được để trống.");
      return;
    }
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(form.time.trim())) {
      setLocalError("Giờ bắt đầu phải theo định dạng 24h HH:mm, ví dụ 08:30 hoặc 19:45.");
      return;
    }
    setLocalError("");
    void onSave({
      ...form,
      time: form.time.trim(),
      name: form.name.trim(),
      type: form.type || "ATTRACTION",
      location: form.location?.trim(),
      duration: form.duration?.trim() || "1 giờ",
      estimatedCost: Math.max(0, Number(form.estimatedCost) || 0),
      note: form.note?.trim(),
      sortOrder: activity?.sortOrder ?? form.sortOrder ?? 0,
    });
  };

  return (
    <Card className="activity-editor-card" style={{ padding: 20, borderColor: "var(--primary-muted)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", marginBottom: 16 }}>
        <h3 style={{ fontSize: 17 }}>{activity ? "Sửa hoạt động" : "Thêm hoạt động"}</h3>
        <button type="button" className="btn btn-ghost btn-icon" onClick={onCancel} aria-label="Đóng form">
          <X size={16} />
        </button>
      </div>

      {(localError || error) && (
        <div style={{ marginBottom: 12, padding: "9px 11px", borderRadius: "var(--r-md)", background: "#FEF2F2", color: "#B91C1C", fontSize: 13 }}>
          {localError || error}
        </div>
      )}

      <form onSubmit={submit} style={{ display: "grid", gap: 14 }}>
        <div className="activity-editor-meta-grid">
          <label style={{ display: "grid", gap: 6, fontSize: 13, fontWeight: 700, color: "var(--text-2)" }}>
            Giờ bắt đầu
            <input
              className="input"
              type="text"
              inputMode="numeric"
              pattern="([01]\d|2[0-3]):[0-5]\d"
              maxLength={5}
              placeholder="08:30"
              value={form.time}
              onChange={(event) => setField("time", normalizeTimeInput(event.target.value))}
              required
            />
          </label>
          <label style={{ display: "grid", gap: 6, fontSize: 13, fontWeight: 700, color: "var(--text-2)" }}>
            Loại
            <select className="input" value={form.type} onChange={(event) => setField("type", event.target.value)}>
              {activityTypeOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <label style={{ display: "grid", gap: 6, fontSize: 13, fontWeight: 700, color: "var(--text-2)" }}>
            Chi phí
            <input
              className="input"
              type="number"
              min={0}
              step={10000}
              value={form.estimatedCost ?? 0}
              onChange={(event) => setField("estimatedCost", event.target.value === "" ? 0 : Number(event.target.value))}
            />
          </label>
          <label style={{ display: "grid", gap: 6, fontSize: 13, fontWeight: 700, color: "var(--text-2)" }}>
            Thời lượng
            <input className="input" value={form.duration ?? ""} onChange={(event) => setField("duration", event.target.value)} placeholder="1 giờ 30 phút" />
          </label>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: 12 }}>
          <label style={{ display: "grid", gap: 6, fontSize: 13, fontWeight: 700, color: "var(--text-2)" }}>
            Tên hoạt động
            <input className="input" value={form.name} onChange={(event) => setField("name", event.target.value)} placeholder="VD: Ăn trưa tại..." required />
          </label>
          <label style={{ display: "grid", gap: 6, fontSize: 13, fontWeight: 700, color: "var(--text-2)" }}>
            Vị trí
            <input className="input" value={form.location ?? ""} onChange={(event) => setField("location", event.target.value)} placeholder="Tên quán / địa chỉ" />
          </label>
        </div>

        <label style={{ display: "grid", gap: 6, fontSize: 13, fontWeight: 700, color: "var(--text-2)" }}>
          Mô tả / ghi chú
          <textarea className="input textarea-compact" value={form.note ?? ""} onChange={(event) => setField("note", event.target.value)} />
        </label>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, flexWrap: "wrap" }}>
          <Button type="button" variant="secondary" size="sm" onClick={onCancel} disabled={saving}>
            Hủy
          </Button>
          <Button type="submit" size="sm" disabled={saving}>
            <Save size={13} /> {saving ? "Đang lưu..." : "Lưu hoạt động"}
          </Button>
        </div>
      </form>
    </Card>
  );
}

function ActivityItem({
  activity,
  tripContext,
  warning,
  expanded,
  onToggle,
  canEdit,
  onEdit,
  onDelete,
}: {
  activity: ActivityResponse;
  tripContext: {
    destination?: string | null;
    startDate?: string | null;
    endDate?: string | null;
    travelerCount?: number | null;
  };
  warning?: { icon: "wind" | "rain" | "fog"; message: string } | null;
  expanded: boolean;
  onToggle: () => void;
  canEdit: boolean;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const cfg = typeConfig[activity.type] ?? typeConfig.ATTRACTION;
  const Icon = cfg.icon;
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const mapUrl =
    activity.latitude && activity.longitude
      ? `https://www.google.com/maps/search/?api=1&query=${activity.latitude},${activity.longitude}`
      : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${activity.name} ${activity.location}`)}`;
  const hotelAffiliateUrl = buildHotelAffiliateUrl(activity, tripContext);

  return (
    <div className="itinerary-activity-row">
      <div
        style={{
          width: 30,
          height: 30,
          borderRadius: "50%",
          flexShrink: 0,
          marginTop: 12,
          zIndex: 1,
          background: cfg.bg,
          border: `2px solid ${cfg.color}`,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <Icon size={13} style={{ color: cfg.color }} />
      </div>
      <article className="card itinerary-activity-card">
        <button
          className="itinerary-activity-toggle"
          onClick={onToggle}
        >
          <span style={{ fontSize: 12, fontWeight: 800, color: cfg.color, background: cfg.bg, padding: "4px 9px", borderRadius: 999, whiteSpace: "nowrap" }}>
            {activity.time}
          </span>
          <div style={{ minWidth: 0, flex: 1 }}>
            <h3 className="itinerary-activity-title">
              <span>{activity.name}</span>
              {warning && (
                <span
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 4,
                    fontSize: 10,
                    fontWeight: 600,
                    color: "#d97706",
                    background: "rgba(245, 158, 11, 0.1)",
                    padding: "2px 8px",
                    borderRadius: 99,
                    border: "1px solid rgba(245, 158, 11, 0.2)",
                    flexShrink: 0
                  }}
                >
                  <AlertTriangle size={10} style={{ color: "#d97706" }} />
                  Lưu ý thời tiết
                </span>
              )}
            </h3>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 12, color: "var(--text-4)", fontSize: 12 }}>
              <span>{activity.duration}</span>
              <span style={{ fontWeight: 700, color: needsCostReview(activity) ? "#d97706" : activity.estimatedCost ? "var(--text-2)" : "var(--accent)" }}>{fmtActivityCost(activity)}</span>
              {activity.rating > 0 && (
                <span style={{ display: "flex", alignItems: "center", gap: 3 }}>
                  <Star size={11} fill="#FBBF24" color="#FBBF24" /> {activity.rating.toFixed(1)}
                </span>
              )}
            </div>
          </div>
          {expanded ? <ChevronUp size={16} style={{ color: "var(--text-4)" }} /> : <ChevronDown size={16} style={{ color: "var(--text-4)" }} />}
        </button>
        {expanded && (
          <div style={{ padding: "0 16px 16px", borderTop: "1px solid var(--divider)" }}>
            {warning && (
              <div style={{
                display: "flex",
                gap: 8,
                alignItems: "flex-start",
                padding: "10px 12px",
                background: "#fffbeb",
                border: "1px solid #fde68a",
                borderRadius: "var(--r-md)",
                fontSize: 13,
                color: "#92400e",
                lineHeight: 1.5,
                marginTop: 12,
                marginBottom: 8
              }}>
                <span style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  flexShrink: 0,
                  width: 22,
                  height: 22,
                  borderRadius: "50%",
                  background: "rgba(245, 158, 11, 0.15)",
                  color: "#d97706",
                  marginTop: 1
                }}>
                  {warning.icon === "wind" ? <Wind size={11} /> : warning.icon === "rain" ? <CloudRain size={11} /> : <CloudFog size={11} />}
                </span>
                <span style={{ flex: 1 }}>{warning.message}</span>
              </div>
            )}
            {needsCostReview(activity) && (
              <div style={{
                display: "flex",
                gap: 8,
                alignItems: "flex-start",
                padding: "10px 12px",
                background: "#fffbeb",
                border: "1px solid #fde68a",
                borderRadius: "var(--r-md)",
                fontSize: 13,
                color: "#92400e",
                lineHeight: 1.5,
                marginTop: 12,
                marginBottom: 8
              }}>
                <AlertTriangle size={14} style={{ color: "#d97706", flexShrink: 0, marginTop: 2 }} />
                <span>{activity.costEstimateMessage || "Chi phí này cần được kiểm tra lại trước khi sử dụng."}</span>
              </div>
            )}
            <div style={{ display: "flex", gap: 7, marginTop: 12, marginBottom: 10, color: "var(--text-3)", fontSize: 13 }}>
              <MapPin size={14} style={{ color: "var(--primary)", flexShrink: 0, marginTop: 2 }} />
              <span>{activity.location}</span>
            </div>
            {activity.note && (
              <p style={{ background: "var(--surface-2)", padding: 12, borderRadius: "var(--r-md)", color: "var(--text-3)", fontSize: 13, lineHeight: 1.65, marginBottom: 12 }}>
                {activity.note}
              </p>
            )}
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              {hotelAffiliateUrl && (
                <a href={hotelAffiliateUrl} target="_blank" rel="noreferrer" className="btn btn-secondary btn-sm">
                  <BedDouble size={12} /> Kiểm tra phòng
                </a>
              )}
              <a href={mapUrl} target="_blank" rel="noreferrer" className="btn btn-secondary btn-sm">
                <ExternalLink size={12} /> Mở bản đồ
              </a>
              {canEdit && (
                <>
                  <button type="button" className="btn btn-secondary btn-sm" onClick={onEdit}>
                    <Edit3 size={12} /> Sửa
                  </button>
                  <div className="modal-close-anchor">
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      onClick={() => setDeleteConfirmOpen(true)}
                      style={{ color: "#B91C1C" }}
                    >
                      <Trash2 size={12} /> Xóa
                    </button>
                    {deleteConfirmOpen && (
                      <div className="modal-close-popconfirm itinerary-activity-delete-popconfirm" role="alertdialog" aria-label="Xác nhận xóa hoạt động">
                        <div>
                          <strong>Xóa hoạt động này?</strong>
                          <p>Hoạt động “{activity.name}” sẽ được gỡ khỏi ngày hiện tại.</p>
                        </div>
                        <div>
                          <button type="button" onClick={() => setDeleteConfirmOpen(false)}>
                            Giữ lại
                          </button>
                          <button
                            type="button"
                            className="danger"
                            onClick={() => {
                              setDeleteConfirmOpen(false);
                              onDelete();
                            }}
                          >
                            Xóa hoạt động
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                </>
              )}
              <span className="badge badge-teal">{cfg.label}</span>
            </div>
          </div>
        )}
      </article>
    </div>
  );
}
