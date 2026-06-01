"use client";

import { Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Navbar from "@/components/layout/Navbar";
import { PurchaseModal } from "@/components/billing/PurchaseModal";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ApiError, tripApi, type DestinationSuggestion } from "@/lib/api";
import { useBilling } from "@/hooks/useBilling";
import { useAuth } from "@/hooks/useAuth";
import { findDestinationByName, getDestinationImage, heroImages, normalizeVietnameseSearch, vietnamProvinces, type Destination } from "@/lib/travel-data";
import { useDestinations } from "@/lib/use-destinations";
import {
  ArrowRight,
  Bike,
  Bus,
  Car,
  Clock,
  Coffee,
  MapPin,
  Mountain,
  Navigation,
  Plane,
  Route,
  Sparkles,
  Train,
  Users,
  Wallet,
  Waves,
  CheckCircle2,
  X,
  Zap,
} from "lucide-react";

const styleOptions = [
  { id: "relaxing", label: "Nghỉ dưỡng", icon: Waves },
  { id: "adventure", label: "Phiêu lưu", icon: Mountain },
  { id: "cultural", label: "Văn hóa", icon: Coffee },
  { id: "foodie", label: "Ẩm thực", icon: Sparkles },
];

const groupOptions = [
  { id: "couple", label: "Cặp đôi" },
  { id: "friends", label: "Bạn bè" },
  { id: "family", label: "Gia đình" },
  { id: "kids", label: "Có trẻ em" },
  { id: "seniors", label: "Có người lớn tuổi" },
];

const outboundTransportOptions = [
  { id: "plane", label: "Máy bay", icon: Plane },
  { id: "train", label: "Tàu hỏa", icon: Train },
  { id: "bus", label: "Xe khách", icon: Bus },
  { id: "personal-car", label: "Ô tô cá nhân", icon: Car },
  { id: "personal-motorbike", label: "Xe máy cá nhân", icon: Bike },
  { id: "ai", label: "Để AI chọn", icon: Sparkles },
];

const localTransportOptions = [
  { id: "personal-motorbike", label: "Xe máy cá nhân", icon: Bike, requiresOutbound: "personal-motorbike" },
  { id: "personal-car", label: "Ô tô cá nhân", icon: Car, requiresOutbound: "personal-car" },
  { id: "rental-motorbike", label: "Thuê xe máy", icon: Bike },
  { id: "taxi-grab", label: "Taxi/Grab", icon: Car },
  { id: "rental-car", label: "Thuê ô tô", icon: Car },
  { id: "walking", label: "Đi bộ là chính", icon: Navigation },
  { id: "ai", label: "Để AI chọn", icon: Sparkles },
];

const departureSuggestions = vietnamProvinces;
const DEPARTURE_MAX_LENGTH = 100;
const DESTINATION_MAX_LENGTH = 100;
const MUST_VISIT_MAX_LENGTH = 300;
const AVOID_MAX_LENGTH = 300;
const NOTES_MAX_LENGTH = 800;
const MAX_TRIP_DAYS = 7;
const MAX_TRAVELERS = 10;

function fmtBudget(value: number) {
  return value >= 1_000_000 ? `${(value / 1_000_000).toFixed(1)}tr ₫` : `${Math.round(value / 1000)}k ₫`;
}

function formatVndInput(value: number) {
  return value > 0 ? value.toLocaleString("vi-VN") : "";
}

function parseVndInput(value: string) {
  const digits = value.replace(/\D/g, "");
  return digits ? Number(digits) : 0;
}

function optionLabel(options: Array<{ id: string; label: string }>, id: string) {
  return options.find((item) => item.id === id)?.label ?? id;
}

function getGroupOptions(travelers: number) {
  if (travelers <= 1) return [];
  if (travelers === 2) return groupOptions.filter((item) => ["couple", "friends", "family", "kids", "seniors"].includes(item.id));
  return groupOptions.filter((item) => item.id !== "couple");
}

function getTripDays(startDate: string, endDate: string) {
  if (!startDate || !endDate) return 0;
  const start = new Date(`${startDate}T00:00:00`);
  const end = new Date(`${endDate}T00:00:00`);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end < start) return 0;
  return Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1;
}

function getTodayDateInput() {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, "0");
  const day = String(today.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getOneYearLaterDateInput() {
  const date = new Date();
  date.setFullYear(date.getFullYear() + 1);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function isBeforeToday(value: string) {
  if (!value) return false;
  const selected = new Date(`${value}T00:00:00`);
  const today = new Date(`${getTodayDateInput()}T00:00:00`);
  return selected < today;
}

function isAfterOneYear(value: string) {
  if (!value) return false;
  const selected = new Date(`${value}T00:00:00`);
  const oneYearLater = new Date(`${getOneYearLaterDateInput()}T00:00:00`);
  return selected > oneYearLater;
}

function getRecommendedDayCount(value?: string) {
  if (!value) return 3;
  const days = value.match(/\d+/g)?.map(Number).filter((item) => Number.isFinite(item) && item > 0) ?? [];
  if (days.length === 0) return 3;
  return days.reduce((total, item) => total + item, 0) / days.length;
}

function getMinimumDailyBudget(destination?: Destination) {
  const tags = normalizeVietnameseSearch(destination?.tags.join(" ") ?? "");
  const category = destination?.category ?? "";
  let minimum = 450_000;

  if (category === "HERITAGE" || /(heritage|old-town|unesco|pho-co)/.test(tags)) minimum = Math.max(minimum, 550_000);
  if (/(mountain|trekking|cave|national-park|adventure)/.test(tags)) minimum = Math.max(minimum, 550_000);
  if (/(beach|coast)/.test(tags)) minimum = Math.max(minimum, 550_000);
  if (category === "ISLAND" || /island/.test(tags)) minimum = Math.max(minimum, 650_000);
  if (/(resort|cruise|bay)/.test(tags)) minimum = Math.max(minimum, 750_000);
  if (destination?.estimatedBudgetMin) {
    const estimateDaily = Math.round(destination.estimatedBudgetMin / getRecommendedDayCount(destination.recommendedDays));
    const dataBasedDaily = Math.round(estimateDaily * 0.65);
    minimum = Math.max(minimum, Math.min(dataBasedDaily, Math.round(minimum * 1.2)));
  }

  return minimum;
}

function getBudgetHardBlockError({
  budgetPerPerson,
  days,
}: {
  budgetPerPerson: number;
  days: number;
}) {
  const absurdMaximum = Math.max(200_000_000, days * 50_000_000);
  const unrealisticDailyMinimum = days >= 4 ? 350_000 : 300_000;
  const absoluteMinimum = days <= 1 ? 300_000 : 500_000;
  const unrealisticMinimum = Math.max(absoluteMinimum, Math.round(unrealisticDailyMinimum * Math.max(1, days)));

  if (days > 0 && budgetPerPerson < unrealisticMinimum) {
    return `Ngân sách này hơi thấp cho chuyến đi ${days} ngày. Bạn nên nâng lên khoảng ${fmtBudget(unrealisticMinimum)} / người để lịch trình thực tế hơn.`;
  }

  if (budgetPerPerson > absurdMaximum) {
    return `Ngân sách ${fmtBudget(budgetPerPerson)} / người đang quá cao so với chuyến đi ${days} ngày. Vui lòng kiểm tra lại, có thể bạn đã nhập nhầm đơn vị.`;
  }

  return "";
}

function getBudgetAdvisory({
  budgetPerPerson,
  days,
  destination,
}: {
  budgetPerPerson: number;
  days: number;
  destination?: Destination;
}) {
  if (budgetPerPerson <= 0 || days <= 0) return "";

  const longTripDiscount = days > 14 ? 0.75 : days > 7 ? 0.85 : 1;
  const suggestedBudget = Math.round(getMinimumDailyBudget(destination) * days * longTripDiscount);
  const warningThreshold = Math.round(suggestedBudget * 0.85);
  if (budgetPerPerson >= warningThreshold) return "";

  const destinationLabel = destination ? ` cho ${destination.name}` : "";
  return `Ngân sách ${fmtBudget(budgetPerPerson)} / người khá thấp${destinationLabel} trong ${days} ngày. VivuPlan vẫn sẽ thử lập lịch trình tiết kiệm, ưu tiên hoạt động chi phí thấp và sẽ báo nếu ước tính thực tế vượt ngân sách.`;
}

function toApiGroupType(group: string, travelers: number) {
  if (group === "solo" || travelers === 1) return "SOLO";
  if (group === "couple" || travelers === 2) return "COUPLE";
  if (group === "family" || group === "kids" || group === "seniors") return "FAMILY";
  return "FRIENDS";
}

function toApiTransport(outboundTransport: string, localTransport: string) {
  const value = outboundTransport && outboundTransport !== "ai" ? outboundTransport : localTransport;
  if (value === "personal-motorbike") return "PERSONAL_MOTORBIKE";
  if (value === "personal-car") return "PERSONAL_CAR";
  if (value === "rental-motorbike") return "RENTAL_MOTORBIKE";
  if (value === "rental-car") return "RENTAL_CAR";
  if (value === "taxi-grab") return "TAXI_GRAB";
  if (value === "bus") return "BUS";
  if (value === "plane") return "PLANE";
  if (value === "train") return "TRAIN";
  if (value === "walking") return "WALKING";
  return "MIXED";
}

function PlanContent() {
  const { destinations, destinationNames } = useDestinations();
  const params = useSearchParams();
  const router = useRouter();
  const [form, setForm] = useState({
    departure: params.get("departure") || "",
    destination: params.get("destination") || "",
    startDate: "",
    endDate: "",
    budget: 0,
    budgetMode: "perPerson",
    travelers: 0,
    style: "",
    group: "",
    outboundTransport: "",
    localTransport: "",
    mustVisit: "",
    avoid: "",
    notes: "",
  });
  const [generating, setGenerating] = useState(false);
  const [suggestingDestinations, setSuggestingDestinations] = useState(false);
  const [destinationSuggestions, setDestinationSuggestions] = useState<DestinationSuggestion[]>([]);
  const [showDestinationSuggestionDetails, setShowDestinationSuggestionDetails] = useState(false);
  const [destinationSuggestedByAi, setDestinationSuggestedByAi] = useState(false);
  const [destinationSuggestionModalOpen, setDestinationSuggestionModalOpen] = useState(false);
  const [destinationSuggestionError, setDestinationSuggestionError] = useState("");
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [suggestionElapsedSeconds, setSuggestionElapsedSeconds] = useState(0);
  const [error, setError] = useState("");
  const [purchaseOpen, setPurchaseOpen] = useState(false);
  const { wallet, refreshWallet } = useBilling();
  const auth = useAuth();
  const [focusedField, setFocusedField] = useState<"departure" | "destination" | null>(null);
  const blurTimer = useRef<number | null>(null);
  const destinationSuggestionRequestId = useRef(0);

  const image = useMemo(() => getDestinationImage(form.destination, destinations), [destinations, form.destination]);
  const destination = findDestinationByName(form.destination, destinations);
  const departureQuery = normalizeVietnameseSearch(form.departure);
  const destinationQuery = normalizeVietnameseSearch(form.destination);
  const departureMatches = departureQuery
    ? departureSuggestions.filter((item) => normalizeVietnameseSearch(item).includes(departureQuery))
    : departureSuggestions;
  const destinationMatches = destinationNames
    .filter((item) => !destinationQuery || normalizeVietnameseSearch(item).includes(destinationQuery));
  const computedDays = getTripDays(form.startDate, form.endDate);
  const computedNights = computedDays > 0 ? Math.max(0, computedDays - 1) : 0;
  const budgetPerPerson =
    form.budgetMode === "total" && form.travelers > 0 ? Math.round(form.budget / form.travelers) : form.budget;
  const budgetAdvisory = form.travelers > 0
    ? getBudgetAdvisory({
      budgetPerPerson,
      days: computedDays,
      destination,
    })
    : "";
  const todayInput = getTodayDateInput();
  const oneYearLaterInput = getOneYearLaterDateInput();
  const compatibleGroupOptions = getGroupOptions(form.travelers);
  const compatibleLocalTransportOptions = useMemo(() => localTransportOptions.filter((item) => {
    if (item.id === "rental-motorbike" && form.outboundTransport === "personal-motorbike") return false;
    if (item.id === "rental-car" && form.outboundTransport === "personal-car") return false;
    if (!("requiresOutbound" in item)) return true;
    return item.requiresOutbound === form.outboundTransport;
  }), [form.outboundTransport]);
  const groupSummary =
    form.travelers === 1
      ? "Đi một mình"
      : form.group
        ? optionLabel(groupOptions, form.group)
        : form.travelers > 1
          ? "Chưa chọn kiểu nhóm"
          : "Nhập số người trước";

  const generationStep =
    elapsedSeconds < 12
      ? "Đang phân tích điểm đến, ngày đi và ngân sách của bạn."
      : elapsedSeconds < 35
        ? "VivuPlan đang ghép các điểm đến, thời gian và nhịp di chuyển cho chuyến đi của bạn."
        : elapsedSeconds < 60
          ? "Đang cân đối chi phí, quán ăn và trải nghiệm để lịch trình dễ đi hơn."
          : "Chuyến đi này cần thêm một chút thời gian để VivuPlan sắp xếp kỹ hơn.";

  const destinationSuggestionStep =
    suggestionElapsedSeconds < 6
      ? "Đang đọc điểm xuất phát, thời gian và ngân sách chuyến đi."
      : suggestionElapsedSeconds < 14
        ? "Đang tìm những nơi hợp với gu du lịch của bạn."
        : "Đang chọn ra 3 gợi ý đáng để bạn cân nhắc.";

  useEffect(() => {
    if (!generating) return;
    const timer = window.setInterval(() => {
      setElapsedSeconds((current) => current + 1);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [generating]);

  useEffect(() => {
    if (!suggestingDestinations) return;
    const timer = window.setInterval(() => {
      setSuggestionElapsedSeconds((current) => current + 1);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [suggestingDestinations]);

  useEffect(() => {
    if (!form.localTransport) return;
    const stillAvailable = compatibleLocalTransportOptions.some((item) => item.id === form.localTransport);
    if (!stillAvailable) {
      setForm((prev) => ({ ...prev, localTransport: "" }));
    }
  }, [compatibleLocalTransportOptions, form.localTransport]);

  useEffect(() => {
    setDestinationSuggestedByAi(false);
    setDestinationSuggestions([]);
    setShowDestinationSuggestionDetails(false);
    setDestinationSuggestionError("");
    setDestinationSuggestionModalOpen(false);
    destinationSuggestionRequestId.current += 1;
    setSuggestingDestinations(false);
  }, [
    form.departure,
    form.startDate,
    form.endDate,
    form.budget,
    form.budgetMode,
    form.travelers,
    form.style,
    form.group,
    form.outboundTransport,
    form.localTransport,
    form.mustVisit,
    form.avoid,
    form.notes,
  ]);

  const focusField = (field: "departure" | "destination") => {
    if (blurTimer.current) {
      window.clearTimeout(blurTimer.current);
      blurTimer.current = null;
    }
    setFocusedField(field);
  };

  const closeSuggestionsSoon = () => {
    if (blurTimer.current) window.clearTimeout(blurTimer.current);
    blurTimer.current = window.setTimeout(() => {
      setFocusedField(null);
      blurTimer.current = null;
    }, 140);
  };

  const validatePlannerForm = () => {
    if (!form.departure.trim()) return "Vui lòng nhập điểm xuất phát.";
    if (form.departure.trim().length > DEPARTURE_MAX_LENGTH) return `Điểm xuất phát tối đa ${DEPARTURE_MAX_LENGTH} ký tự.`;
    if (form.destination.trim().length > DESTINATION_MAX_LENGTH) return `Điểm đến tối đa ${DESTINATION_MAX_LENGTH} ký tự.`;
    if (!form.startDate || !form.endDate || computedDays <= 0) return "Vui lòng chọn ngày đi và ngày về hợp lệ.";
    if (isBeforeToday(form.startDate)) return "Ngày đi không được ở trong quá khứ.";
    if (isAfterOneYear(form.startDate)) return "Ngày đi không được quá 1 năm kể từ hôm nay.";
    if (computedDays > MAX_TRIP_DAYS) return `VivuPlan hiện hỗ trợ lịch trình tối đa ${MAX_TRIP_DAYS} ngày cho mỗi điểm đến.`;
    if (form.budget <= 0) return "Vui lòng nhập ngân sách.";
    if (form.travelers < 1) return "Vui lòng nhập số người đi.";
    if (form.travelers > MAX_TRAVELERS) return `Số người tối đa hiện hỗ trợ là ${MAX_TRAVELERS}.`;

    const budgetValidationError = getBudgetHardBlockError({
      budgetPerPerson,
      days: computedDays,
    });
    if (budgetValidationError) return budgetValidationError;

    if (!form.outboundTransport) return "Vui lòng chọn phương tiện di chuyển đến điểm đến hoặc chọn Để AI chọn.";
    if (!form.localTransport) return "Vui lòng chọn phương tiện di chuyển trong chuyến đi hoặc chọn Để AI chọn.";
    if (form.mustVisit.trim().length > MUST_VISIT_MAX_LENGTH) return `Nơi muốn ghé tối đa ${MUST_VISIT_MAX_LENGTH} ký tự.`;
    if (form.avoid.trim().length > AVOID_MAX_LENGTH) return `Điều muốn tránh tối đa ${AVOID_MAX_LENGTH} ký tự.`;
    if (form.notes.trim().length > NOTES_MAX_LENGTH) return `Ghi chú tối đa ${NOTES_MAX_LENGTH} ký tự.`;
    return "";
  };

  const buildPlanningPayload = () => ({
    departure: form.departure.trim(),
    startDate: form.startDate,
    endDate: form.endDate,
    days: computedDays,
    budgetPerPerson,
    budgetTotal: form.budgetMode === "total" ? form.budget : undefined,
    budgetMode: form.budgetMode === "total" ? "TOTAL" : "PER_PERSON",
    travelerCount: form.travelers,
    style: (form.style || "relaxing").toUpperCase(),
    groupType: toApiGroupType(form.group, form.travelers),
    outboundTransport: toApiTransport(form.outboundTransport, ""),
    localTransport: toApiTransport("", form.localTransport),
    mustVisit: form.mustVisit.trim() || undefined,
    avoid: form.avoid.trim() || undefined,
    notes: form.notes.trim() || undefined,
  });

  const closeDestinationSuggestionModal = () => {
    if (suggestingDestinations) {
      destinationSuggestionRequestId.current += 1;
      setSuggestingDestinations(false);
    }
    setDestinationSuggestionModalOpen(false);
  };

  const selectDestinationSuggestion = (suggestion: DestinationSuggestion) => {
    setForm((prev) => ({ ...prev, destination: suggestion.name }));
    setDestinationSuggestedByAi(true);
    setDestinationSuggestionError("");
    setDestinationSuggestionModalOpen(false);
    setFocusedField(null);
  };

  const handleSuggestDestinations = async () => {
    setError("");
    setDestinationSuggestionError("");
    if (auth.loading) {
      setError("Vui lòng chờ một chút để VivuPlan kiểm tra phiên đăng nhập.");
      return;
    }
    if (!auth.isLoggedIn) {
      router.push("/login");
      return;
    }

    const validationError = validatePlannerForm();
    if (validationError) {
      setError(validationError);
      return;
    }

    if (destinationSuggestions.length > 0) {
      setDestinationSuggestionModalOpen(true);
      return;
    }

    const requestId = destinationSuggestionRequestId.current + 1;
    destinationSuggestionRequestId.current = requestId;
    setDestinationSuggestionModalOpen(true);
    setSuggestingDestinations(true);
    setSuggestionElapsedSeconds(0);
    try {
      const response = await tripApi.suggestDestinations(buildPlanningPayload());
      if (destinationSuggestionRequestId.current !== requestId) return;
      const suggestions = response.suggestions ?? [];
      if (suggestions.length === 0) {
        setDestinationSuggestionError("VivuPlan chưa tìm được điểm đến phù hợp. Bạn có thể thử lại hoặc nhập điểm đến thủ công.");
        return;
      }
      setDestinationSuggestions(suggestions);
      setShowDestinationSuggestionDetails(false);
      setDestinationSuggestedByAi(false);
    } catch (e) {
      if (destinationSuggestionRequestId.current !== requestId) return;
      if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
        setDestinationSuggestionModalOpen(false);
        router.push("/login");
      } else if (e instanceof ApiError && e.status === 402) {
        setDestinationSuggestionModalOpen(false);
        setError("Bạn cần có lượt tạo lịch trình để dùng gợi ý điểm đến bằng AI.");
        setPurchaseOpen(true);
      } else if (e instanceof ApiError && e.status === 429) {
        setDestinationSuggestionError(e.message || "Bạn đã yêu cầu gợi ý quá nhiều lần. Vui lòng thử lại sau ít phút.");
      } else {
        setDestinationSuggestionError(e instanceof Error ? e.message : "Chưa thể gợi ý điểm đến phù hợp. Vui lòng thử lại.");
      }
    } finally {
      if (destinationSuggestionRequestId.current === requestId) {
        setSuggestingDestinations(false);
      }
    }
  };

  const handleGenerate = async () => {
    setError("");
    if (auth.loading) {
      setError("Vui lòng chờ một chút để VivuPlan kiểm tra phiên đăng nhập.");
      return;
    }
    if (!auth.isLoggedIn) {
      router.push("/login");
      return;
    }
    const validationError = validatePlannerForm();
    if (validationError) {
      setError(validationError);
      return;
    }
    if (!form.destination.trim()) {
      await handleSuggestDestinations();
      return;
    }
    setGenerating(true);
    setElapsedSeconds(0);
    try {
      const finalDestination = form.destination.trim();
      const trip = await tripApi.generate({
        destination: finalDestination,
        ...buildPlanningPayload(),
        destinationSuggested: destinationSuggestedByAi,
      });
      const creationWarnings = trip.warnings?.filter((warning) => warning.trim().length > 0) ?? [];
      if (creationWarnings.length > 0 && typeof window !== "undefined") {
        window.sessionStorage.setItem(`vivuplan:trip:${trip.id}:warnings`, JSON.stringify(creationWarnings));
      }
      void refreshWallet();
      router.push(`/itinerary/${trip.id}`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 402) {
        setError("Bạn đã hết lượt tạo lịch trình bằng AI. Mua thêm lượt để tiếp tục nhé.");
        setPurchaseOpen(true);
      } else if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
        router.push("/login");
      } else {
        setError(e instanceof Error ? e.message : "Không thể tạo lịch trình. Hãy kiểm tra đăng nhập hoặc thử lại sau.");
      }
    } finally {
      setGenerating(false);
    }
  };

  return (
    <div
      className="planner-page"
      style={{
        backgroundImage: `linear-gradient(180deg, rgba(248,250,252,0.85), rgba(248,250,252,0.98)), url(${heroImages.vietnamCoast})`,
      }}
    >
      <Navbar />
      <main className="container planner-shell">
        <section className="planner-visual">
          <div className="planner-photo" style={{ backgroundImage: `url(${image})` }}>
            <Badge tone="glass">
              <MapPin size={13} /> {form.destination || "VivuPlan sẽ gợi ý điểm đến"}
            </Badge>
            <p style={{
              marginTop: "auto",
              color: "#fff",
              textShadow: "0 2px 4px rgba(0,0,0,0.5)",
              fontSize: "14px",
              fontWeight: "500",
              maxWidth: "80%"
            }}>
              {form.destination ? destination?.tag ?? "Tạo lịch trình thực tế với ngân sách và phong cách phù hợp." : "Để trống nếu bạn muốn VivuPlan gợi ý nơi phù hợp với thời gian và ngân sách."}
            </p>
          </div>

          <Card className="planner-preview">
            <div className="planner-route-head">
              <div>
                <span>Xem trước lịch trình</span>
                <h2>{form.departure || "Xuất phát"} → {form.destination || "AI chọn điểm đến"}</h2>
                <p className="planner-preview-meta">
                  {computedDays > 0 ? `${computedDays} ngày ${computedNights} đêm` : "Chưa chọn ngày"}
                  {" · "}
                  {form.travelers > 0 ? `${form.travelers} người` : "Chưa nhập số người"}
                  {" · "}
                  {form.budget > 0 ? fmtBudget(form.budget) : "Chưa nhập ngân sách"}
                </p>
              </div>
              <Route size={22} />
            </div>
            <div className="planner-route-list">
              {["Sáng", "Trưa", "Chiều", "Tối"].map((time, index) => (
                <div key={time}>
                  <span>{String(index + 1).padStart(2, "0")}</span>
                  <div>
                    <strong>{time}</strong>
                    <p>{index === 0 ? "Di chuyển" : index === 1 ? "Ăn uống địa phương" : index === 2 ? "Tham quan chính" : "Cà phê/chợ đêm"}</p>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </section>

        <section className="planner-panel">
          <div className="planner-heading">
            <h2>Thiết kế hành trình của riêng bạn</h2>
          </div>

          <Card className={`planner-form${generating ? " planner-form-generating" : ""}`}>
            <div className="field-group">
              <label>Điểm xuất phát</label>
              <div className="input-with-icon">
                <MapPin size={16} />
                <input
                  className="input"
                  value={form.departure}
                  maxLength={DEPARTURE_MAX_LENGTH}
                  onChange={(event) => setForm((prev) => ({ ...prev, departure: event.target.value }))}
                  onFocus={() => focusField("departure")}
                  onBlur={closeSuggestionsSoon}
                  placeholder="VD: Hà Nội, TP.HCM, Hải Phòng..."
                />
                {focusedField === "departure" && departureMatches.length > 0 && (
                  <div className="field-suggestions">
                    {departureMatches.map((item) => (
                      <button
                        key={item}
                        type="button"
                        onMouseDown={() => {
                          setForm((prev) => ({ ...prev, departure: item }));
                          setFocusedField(null);
                        }}
                      >
                        <MapPin size={13} /> {item}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <div className="field-group">
              <div className="field-label-row">
                <label>Điểm đến muốn đi <span className="optional-label">tùy chọn</span></label>
                <span className="group-summary">{form.destination.trim() ? "Theo điểm bạn chọn" : "VivuPlan gợi ý"}</span>
              </div>
              <div className="input-with-icon">
                <MapPin size={16} />
                <input
                  id="input-destination"
                  className="input"
                  value={form.destination}
                  maxLength={DESTINATION_MAX_LENGTH}
                  onChange={(event) => {
                    setForm((prev) => ({ ...prev, destination: event.target.value }));
                    setDestinationSuggestedByAi(false);
                    setDestinationSuggestions([]);
                  }}
                  onFocus={() => focusField("destination")}
                  onBlur={closeSuggestionsSoon}
                  placeholder="VD: Đà Lạt, Quy Nhơn... hoặc để trống để VivuPlan gợi ý"
                />
                {focusedField === "destination" && destinationMatches.length > 0 && (
                  <div className="field-suggestions">
                    {destinationMatches.map((item) => (
                      <button
                        key={item}
                        type="button"
                        onMouseDown={() => {
                          setForm((prev) => ({ ...prev, destination: item }));
                          setDestinationSuggestedByAi(false);
                          setDestinationSuggestions([]);
                          setFocusedField(null);
                        }}
                      >
                        <MapPin size={13} /> {item}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <p className="field-hint field-hint-icon">
                <Sparkles size={14} aria-hidden="true" />
                <span>Chưa biết đi đâu? Để trống điểm đến, VivuPlan sẽ gợi ý nơi phù hợp với thời gian, ngân sách và sở thích của bạn.</span>
              </p>
              {destinationSuggestedByAi && form.destination.trim() && (
                <div className="destination-ai-selected-note">
                  <span><CheckCircle2 size={14} /> Đã chọn từ gợi ý AI</span>
                  {destinationSuggestions.length > 0 && (
                    <button type="button" onClick={() => setDestinationSuggestionModalOpen(true)}>
                      Xem lại gợi ý
                    </button>
                  )}
                </div>
              )}
            </div>

            <div className="field-group planner-date-block">
              <div className="field-label-row">
                <label>Thời gian chuyến đi</label>
                <span className="duration-pill">
                  {computedDays > 0 ? `${computedDays} ngày ${computedNights} đêm` : "Chọn ngày đi và ngày về"}
                </span>
              </div>
              <div className="planner-date-row">
                <div className="input-with-icon">
                  <Clock size={16} />
                  <input
                    className="input"
                    type="date"
                    min={todayInput}
                    max={oneYearLaterInput}
                    value={form.startDate}
                    onChange={(event) => setForm((prev) => ({
                      ...prev,
                      startDate: event.target.value,
                      endDate: prev.endDate && prev.endDate < event.target.value ? "" : prev.endDate,
                    }))}
                    aria-label="Ngày đi"
                  />
                </div>

                <div className="input-with-icon">
                  <Clock size={16} />
                  <input
                    className="input"
                    type="date"
                    value={form.endDate}
                    min={form.startDate || todayInput}
                    onChange={(event) => setForm((prev) => ({ ...prev, endDate: event.target.value }))}
                    aria-label="Ngày về"
                  />
                </div>
              </div>
            </div>


            <div className="planner-money-row">
              <div className="field-group">
                <div className="field-label-row">
                  <label>Ngân sách</label>
                  <span className="group-summary budget-label-spacer" aria-hidden="true">Ngân sách</span>
                </div>
                <div className="budget-input-shell">
                  <Wallet size={16} />
                  <input
                    id="input-budget"
                    className="budget-input"
                    type="text"
                    inputMode="numeric"
                    value={formatVndInput(form.budget)}
                    onChange={(event) => setForm((prev) => ({ ...prev, budget: parseVndInput(event.target.value) }))}
                    placeholder="VD: 3.000.000"
                  />
                  <span className="budget-currency">₫</span>
                </div>
                <div className="budget-mode-row">
                  {[
                    { id: "perPerson", label: "Theo người" },
                    { id: "total", label: "Tổng nhóm" },
                  ].map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      className={form.budgetMode === item.id ? "active" : ""}
                      onClick={() => setForm((prev) => ({ ...prev, budgetMode: item.id }))}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
                <div className="budget-quick-row">
                  {[1_500_000, 3_000_000, 5_000_000, 8_000_000].map((value) => (
                    <button key={value} type="button" onClick={() => setForm((prev) => ({ ...prev, budget: value }))}>
                      {fmtBudget(value)}
                    </button>
                  ))}
                </div>
              </div>

              <div className="field-group">
                <div className="field-label-row">
                  <label>Số người & kiểu nhóm</label>
                  <span className="group-summary">{groupSummary}</span>
                </div>
                <div className="input-with-icon">
                  <Users size={16} />
                  <input
                    className="input"
                    type="number"
                    min={1}
                    max={MAX_TRAVELERS}
                    value={form.travelers || ""}
                    onChange={(event) => {
                      const travelers = Number(event.target.value);
                      setForm((prev) => ({
                        ...prev,
                        travelers,
                        group: getGroupOptions(travelers).some((item) => item.id === prev.group) ? prev.group : "",
                      }));
                    }}
                    placeholder="VD: 2, 4, 6..."
                  />
                </div>
                {compatibleGroupOptions.length > 0 && (
                  <div className="group-type-row">
                    {compatibleGroupOptions.map(({ id, label }) => (
                      <button
                        key={id}
                        type="button"
                        className={form.group === id ? "active" : ""}
                        onClick={() => setForm((prev) => ({ ...prev, group: prev.group === id ? "" : id }))}
                      >
                        {label}
                      </button>
                    ))}
                  </div>
                )}
                <p className="field-hint">
                  {budgetAdvisory
                    ? "AI sẽ ưu tiên phương án tiết kiệm và không ép chi phí xuống thấp hơn thực tế."
                    : form.budget > 0 && form.travelers > 0
                      ? `AI sẽ lập lịch trình trong khoảng ${fmtBudget(budgetPerPerson)} / người.`
                      : "Số người giúp AI ước tính phòng, ăn uống và phương án di chuyển."}
                </p>
              </div>
            </div>

            <div className="planner-two-col">
              <div className="field-group">
                <label>Di chuyển đến điểm đến</label>
                <div className="option-grid option-grid-transport">
                  {outboundTransportOptions.map(({ id, label, icon: Icon }) => (
                    <button
                      key={id}
                      type="button"
                      className={form.outboundTransport === id ? "active" : ""}
                      onClick={() => setForm((prev) => ({ ...prev, outboundTransport: id }))}
                    >
                      <Icon size={16} /> {label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="field-group">
                <label>Di chuyển trong chuyến đi</label>
                <div className="option-grid option-grid-transport">
                  {compatibleLocalTransportOptions.map(({ id, label, icon: Icon }) => (
                    <button
                      key={id}
                      type="button"
                      className={form.localTransport === id ? "active" : ""}
                      onClick={() => setForm((prev) => ({ ...prev, localTransport: id }))}
                    >
                      <Icon size={16} /> {label}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <div className="field-group">
              <label>Phong cách chính <span className="optional-label">tùy chọn</span></label>
              <div className="option-grid option-grid-four">
                {styleOptions.map(({ id, label, icon: Icon }) => (
                  <button
                    key={id}
                    type="button"
                    className={form.style === id ? "active" : ""}
                    onClick={() => setForm((prev) => ({ ...prev, style: prev.style === id ? "" : id }))}
                  >
                    <Icon size={17} /> {label}
                  </button>
                ))}
              </div>
            </div>

            <div className="planner-two-col">
              <div className="field-group">
                <label>Nơi muốn ghé <span className="optional-label">tùy chọn</span></label>
                <textarea
                  className="input textarea-compact"
                  value={form.mustVisit}
                  maxLength={MUST_VISIT_MAX_LENGTH}
                  onChange={(event) => setForm((prev) => ({ ...prev, mustVisit: event.target.value }))}
                  placeholder="VD: Đồi chè trái tim, thác Dải Yếm, rừng thông Bản Áng..."
                />
              </div>

              <div className="field-group">
                <label>Điều muốn tránh <span className="optional-label">tùy chọn</span></label>
                <textarea
                  className="input textarea-compact"
                  value={form.avoid}
                  maxLength={AVOID_MAX_LENGTH}
                  onChange={(event) => setForm((prev) => ({ ...prev, avoid: event.target.value }))}
                  placeholder="VD: tránh đi bộ nhiều, không ăn cay, không đi xe máy..."
                />
              </div>
            </div>

            <div className="field-group">
              <label>Ghi chú khác <span className="optional-label">tùy chọn</span></label>
              <textarea
                id="input-notes"
                className="input"
                value={form.notes}
                maxLength={NOTES_MAX_LENGTH}
                onChange={(event) => setForm((prev) => ({ ...prev, notes: event.target.value }))}
                placeholder="VD: Thích chilling, chụp ảnh, khám phá vẻ đẹp thiên nhiên, ăn những món ăn đặc sản địa phương, cần về trước 18h..."
              />
            </div>


            {error && <div className="form-error">{error}</div>}
            {!error && budgetAdvisory && <div className="form-warning">{budgetAdvisory}</div>}

            {generating && (
              <div className="planner-generation-status" role="status" aria-live="polite">
                <div className="spinner" />
                <div>
                  <strong>AI đang lập lịch trình cho bạn...</strong>
                  <p>{generationStep}</p>
                  <span>Quá trình này thường mất khoảng 30 giây đến 1 phút, bạn vui lòng chờ một chút nhé.</span>
                </div>
              </div>
            )}

            {wallet && (
              <div className="credit-inline-note">
                <Sparkles size={14} />
                <span>Còn <strong>{wallet.planCredits}</strong> lượt tạo lịch trình</span>
              </div>
            )}

            <Button id="btn-generate" onClick={handleGenerate} disabled={generating || suggestingDestinations} className="planner-submit">
              {generating ? (
                <>
                  <div className="spinner" style={{ borderColor: "rgba(255,255,255,0.3)", borderTopColor: "#fff" }} />
                  Đang tạo lịch trình...
                </>
              ) : suggestingDestinations ? (
                <>
                  <div className="spinner" style={{ borderColor: "rgba(255,255,255,0.3)", borderTopColor: "#fff" }} />
                  Đang gợi ý điểm đến...
                </>
              ) : !form.destination.trim() ? (
                <>
                  <Sparkles size={17} /> Gợi ý điểm đến <ArrowRight size={16} />
                </>
              ) : (
                <>
                  <Zap size={17} /> Tạo lịch trình thông minh <ArrowRight size={16} />
                </>
              )}
            </Button>
          </Card>
        </section>
      </main>
      {destinationSuggestionModalOpen && (
        <div className="destination-suggestion-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="destination-suggestion-title">
          <div className="destination-suggestion-modal-panel">
            <button
              type="button"
              className="destination-suggestion-modal-close"
              onClick={closeDestinationSuggestionModal}
              aria-label="Đóng gợi ý điểm đến"
            >
              <X size={18} />
            </button>

            <div className="destination-suggestion-modal-head">
              <div className="destination-suggestion-modal-icon">
                <Sparkles size={20} />
              </div>
              <div>
                <span>Gợi ý điểm đến</span>
                <h3 id="destination-suggestion-title">
                  {suggestingDestinations ? "Đang tìm điểm đến phù hợp" : destinationSuggestionError ? "Chưa thể gợi ý điểm đến" : "Chọn điểm đến cho chuyến đi"}
                </h3>
                <p>
                  {suggestingDestinations
                    ? "VivuPlan đang tìm những nơi hợp với thời gian, ngân sách và cảm hứng chuyến đi của bạn."
                    : destinationSuggestionError
                      ? "Bạn có thể thử lại sau ít phút, hoặc nhập nơi muốn đi để VivuPlan lên lịch trình ngay."
                      : "Chọn nơi bạn thấy hợp nhất, hoặc xem chi tiết lý do AI gợi ý để quyết định nhé."}
                </p>
              </div>
            </div>

            {suggestingDestinations ? (
              <div className="destination-suggestion-loading" role="status" aria-live="polite">
                <div className="destination-suggestion-loading-main">
                  <div className="spinner" />
                  <div>
                    <strong>AI đang gợi ý điểm đến...</strong>
                    <p>{destinationSuggestionStep}</p>
                  </div>
                </div>
                <div className="destination-suggestion-skeleton-grid" aria-hidden="true">
                  {[0, 1, 2].map((item) => (
                    <div className="destination-suggestion-skeleton-card" key={item}>
                      <span />
                      <strong />
                      <p />
                      <p />
                      <div />
                    </div>
                  ))}
                </div>
              </div>
            ) : destinationSuggestionError ? (
              <div className="destination-suggestion-modal-error">
                <p>{destinationSuggestionError}</p>
                <div className="destination-suggestion-modal-actions">
                  <button type="button" className="destination-suggestion-secondary" onClick={closeDestinationSuggestionModal}>
                    Nhập thủ công
                  </button>
                  <button type="button" className="destination-suggestion-primary" onClick={handleSuggestDestinations}>
                    Thử lại
                  </button>
                </div>
              </div>
            ) : (
              <>
                <div className="destination-suggestion-view-switch" aria-label="Chế độ xem gợi ý">
                  <button
                    type="button"
                    className={!showDestinationSuggestionDetails ? "is-active" : ""}
                    aria-pressed={!showDestinationSuggestionDetails}
                    onClick={() => setShowDestinationSuggestionDetails(false)}
                  >
                    Tóm tắt
                  </button>
                  <button
                    type="button"
                    className={showDestinationSuggestionDetails ? "is-active" : ""}
                    aria-pressed={showDestinationSuggestionDetails}
                    onClick={() => setShowDestinationSuggestionDetails(true)}
                  >
                    Chi tiết
                  </button>
                </div>
                <div className="destination-suggestion-modal-grid">
                  {destinationSuggestions.map((suggestion) => (
                    <article
                      key={`${suggestion.name}-${suggestion.region}`}
                      className={`destination-suggestion-modal-card${showDestinationSuggestionDetails ? " is-expanded" : ""}`}
                    >
                      <div className="destination-suggestion-card-top">
                        <span>{suggestion.region || "Điểm đến"}</span>
                        <span className="destination-suggestion-overall-badge">{suggestion.overallFit}</span>
                      </div>
                      <h4>{suggestion.name}</h4>
                      <p className="destination-suggestion-overall-note">{suggestion.overallNote}</p>
                      <dl className="destination-suggestion-fit-list">
                        <div>
                          <dt>Chi phí</dt>
                          <dd>{suggestion.budgetFit}</dd>
                          {showDestinationSuggestionDetails && <p>{suggestion.budgetNote}</p>}
                        </div>
                        <div>
                          <dt>Số ngày</dt>
                          <dd>{suggestion.durationFit}</dd>
                          {showDestinationSuggestionDetails && <p>{suggestion.durationNote}</p>}
                        </div>
                        <div>
                          <dt>Đường đi</dt>
                          <dd>{suggestion.travelFit}</dd>
                          {showDestinationSuggestionDetails && <p>{suggestion.travelNote}</p>}
                        </div>
                        <div>
                          <dt>Sở thích</dt>
                          <dd>{suggestion.styleFit}</dd>
                          {showDestinationSuggestionDetails && <p>{suggestion.styleNote}</p>}
                        </div>
                      </dl>
                      {showDestinationSuggestionDetails && (
                        <div className="destination-suggestion-reason">
                          <p>{suggestion.reason}</p>
                        </div>
                      )}
                      <button
                        type="button"
                        className="destination-suggestion-select-label"
                        onClick={() => selectDestinationSuggestion(suggestion)}
                      >
                        <CheckCircle2 size={15} /> Chọn điểm này
                      </button>
                    </article>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      )}
      <PurchaseModal
        open={purchaseOpen}
        reason="PLAN"
        onClose={() => setPurchaseOpen(false)}
      />
    </div>
  );
}

export default function PlanPage() {
  return (
    <Suspense
      fallback={
        <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}>
          <div className="spinner" />
        </div>
      }
    >
      <PlanContent />
    </Suspense>
  );
}
