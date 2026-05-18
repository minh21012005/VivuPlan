"use client";

import { Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Navbar from "@/components/layout/Navbar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { tripApi } from "@/lib/api";
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
  { id: "car", label: "Ô tô cá nhân", icon: Car },
  { id: "motorbike", label: "Xe máy", icon: Bike },
  { id: "ai", label: "Để AI chọn", icon: Sparkles },
];

const localTransportOptions = [
  { id: "motorbike", label: "Thuê xe máy", icon: Bike },
  { id: "taxi", label: "Taxi/Grab", icon: Car },
  { id: "car", label: "Thuê ô tô", icon: Car },
  { id: "walking", label: "Đi bộ/kết hợp", icon: Navigation },
  { id: "ai", label: "Để AI chọn", icon: Sparkles },
];

const departureSuggestions = vietnamProvinces;
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
    return `Ngân sách ${fmtBudget(budgetPerPerson)} / người quá thấp cho chuyến đi ${days} ngày. Vui lòng nhập tối thiểu khoảng ${fmtBudget(unrealisticMinimum)} / người để AI có đủ cơ sở lập lịch trình thực tế.`;
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

function suggestDestination(form: { departure: string; budget: number; style: string; startDate: string; endDate: string }, destinations: Destination[]) {
  if (destinations.length === 0) return "";

  const days = getTripDays(form.startDate, form.endDate);
  const departure = normalizeVietnameseSearch(form.departure);
  const style = form.style || "relaxing";

  const scored = destinations.map((destination) => {
    const tags = destination.tags.join(" ");
    let score = destination.rating * 10 + (destination.featured ? 12 : 0);

    if ((style === "foodie" || style === "cultural") && /(food|culture|heritage|old-town|unesco)/.test(tags)) score += 28;
    if (style === "adventure" && /(mountain|adventure|cave|roadtrip|trekking|national-park)/.test(tags)) score += 30;
    if (style === "relaxing" && /(beach|island|resort|quiet|cool-weather)/.test(tags)) score += 24;

    if (form.budget >= 6_000_000 && /(island|resort|beach)/.test(tags)) score += 16;
    if (form.budget < 2_000_000 && (destination.estimatedBudgetMin ?? 0) <= 1_500_000) score += 10;
    if (days > 0 && days <= 3 && destination.recommendedDays.includes("1-2")) score += 8;
    if (days > 0 && days <= 4 && destination.recommendedDays.includes("2-3")) score += 6;
    if (departure.includes("ha noi") && destination.region === "Miền Bắc") score += 10;
    if ((departure.includes("tp.hcm") || departure.includes("ho chi minh") || departure.includes("sai gon")) && destination.region === "Miền Nam") score += 10;
    if (departure.includes("da nang") && destination.region === "Miền Trung") score += 10;

    return { destination, score };
  });

  return scored.sort((a, b) => b.score - a.score)[0]?.destination.name ?? "";
}

function toApiGroupType(group: string, travelers: number) {
  if (group === "solo" || travelers === 1) return "SOLO";
  if (group === "couple" || travelers === 2) return "COUPLE";
  if (group === "family" || group === "kids" || group === "seniors") return "FAMILY";
  return "FRIENDS";
}

function toApiTransport(outboundTransport: string, localTransport: string) {
  const value = outboundTransport && outboundTransport !== "ai" ? outboundTransport : localTransport;
  if (value === "motorbike") return "MOTORBIKE";
  if (value === "car" || value === "taxi") return "CAR";
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
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [error, setError] = useState("");
  const [focusedField, setFocusedField] = useState<"departure" | "destination" | null>(null);
  const blurTimer = useRef<number | null>(null);

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
        ? "AI đang sắp xếp lịch trình theo từng ngày và nhịp di chuyển."
        : elapsedSeconds < 60
          ? "Đang tinh chỉnh chi phí, địa điểm ăn uống và hoạt động phù hợp."
          : "Vẫn đang xử lý. Một số lịch trình dài có thể mất hơn 1 phút.";

  useEffect(() => {
    if (!generating) return;
    const timer = window.setInterval(() => {
      setElapsedSeconds((current) => current + 1);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [generating]);

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

  const handleGenerate = async () => {
    setError("");
    if (!form.departure.trim()) {
      setError("Vui lòng nhập điểm xuất phát.");
      return;
    }
    if (!form.startDate || !form.endDate || computedDays <= 0) {
      setError("Vui lòng chọn ngày đi và ngày về hợp lệ.");
      return;
    }
    if (isBeforeToday(form.startDate)) {
      setError("Ngày đi không được ở trong quá khứ.");
      return;
    }
    if (isAfterOneYear(form.startDate)) {
      setError("Ngày đi không được quá 1 năm kể từ hôm nay.");
      return;
    }
    if (computedDays > 30) {
      setError("MVP hiện hỗ trợ lịch trình tối đa 30 ngày.");
      return;
    }
    if (form.budget <= 0) {
      setError("Vui lòng nhập ngân sách.");
      return;
    }
    if (form.travelers < 1) {
      setError("Vui lòng nhập số người đi.");
      return;
    }
    if (form.travelers > 30) {
      setError("Số người tối đa hiện hỗ trợ là 30.");
      return;
    }
    const budgetValidationError = getBudgetHardBlockError({
      budgetPerPerson,
      days: computedDays,
    });
    if (budgetValidationError) {
      setError(budgetValidationError);
      return;
    }
    if (!form.outboundTransport) {
      setError("Vui lòng chọn phương tiện di chuyển đến điểm đến hoặc chọn Để AI chọn.");
      return;
    }
    if (!form.localTransport) {
      setError("Vui lòng chọn phương tiện di chuyển trong chuyến đi hoặc chọn Để AI chọn.");
      return;
    }

    setGenerating(true);
    setElapsedSeconds(0);
    try {
      const finalDestination = form.destination.trim() || suggestDestination({ ...form, budget: budgetPerPerson }, destinations);
      if (!finalDestination) {
        throw new Error("Không thể gợi ý điểm đến vì dữ liệu điểm đến chưa sẵn sàng. Vui lòng nhập điểm đến cụ thể hoặc thử lại.");
      }
      const planningNotes = [
        `Số người: ${form.travelers}`,
        `Ngân sách tối đa người dùng nhập: ${fmtBudget(form.budget)} ${form.budgetMode === "total" ? "tổng nhóm" : "mỗi người"}`,
        form.travelers === 1 ? "Thành phần nhóm: Một mình" : form.group ? `Thành phần nhóm: ${optionLabel(groupOptions, form.group)}` : "",
        form.outboundTransport ? `Di chuyển đến điểm đến: ${optionLabel(outboundTransportOptions, form.outboundTransport)}` : "Di chuyển đến điểm đến: để AI đề xuất",
        form.localTransport ? `Di chuyển trong chuyến đi: ${optionLabel(localTransportOptions, form.localTransport)}` : "Di chuyển trong chuyến đi: để AI đề xuất",
        budgetAdvisory ? `Lưu ý ngân sách: ${budgetAdvisory}` : "",
        form.mustVisit.trim() ? `Nơi muốn ghé: ${form.mustVisit.trim()}` : "",
        form.avoid.trim() ? `Điều muốn tránh: ${form.avoid.trim()}` : "",
        form.notes.trim(),
      ].filter(Boolean).join("\n");

      const trip = await tripApi.generate({
        destination: finalDestination,
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
        transport: toApiTransport(form.outboundTransport, form.localTransport),
        outboundTransport: toApiTransport(form.outboundTransport, ""),
        localTransport: toApiTransport("", form.localTransport),
        destinationSuggested: !form.destination.trim(),
        mustVisit: form.mustVisit.trim() || undefined,
        avoid: form.avoid.trim() || undefined,
        notes: planningNotes || undefined,
      });
      const creationWarnings = trip.warnings?.filter((warning) => warning.trim().length > 0) ?? [];
      if (creationWarnings.length > 0 && typeof window !== "undefined") {
        window.sessionStorage.setItem(`vivuplan:trip:${trip.id}:warnings`, JSON.stringify(creationWarnings));
      }
      router.push(`/itinerary/${trip.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không thể tạo lịch trình. Hãy kiểm tra đăng nhập hoặc backend.");
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
                  onChange={(event) => setForm((prev) => ({ ...prev, destination: event.target.value }))}
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
                          setFocusedField(null);
                        }}
                      >
                        <MapPin size={13} /> {item}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <p className="field-hint">Nếu chưa biết đi đâu, hãy để trống. VivuPlan sẽ gợi ý điểm đến dựa trên điểm xuất phát, thời gian, ngân sách và sở thích.</p>
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
                    max={30}
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
                  {localTransportOptions.map(({ id, label, icon: Icon }) => (
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
              <label>Phong cách du lịch <span className="optional-label">tùy chọn</span></label>
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
                  onChange={(event) => setForm((prev) => ({ ...prev, mustVisit: event.target.value }))}
                  placeholder="VD: Đồi chè trái tim, thác Dải Yếm, rừng thông Bản Áng..."
                />
              </div>

              <div className="field-group">
                <label>Điều muốn tránh <span className="optional-label">tùy chọn</span></label>
                <textarea
                  className="input textarea-compact"
                  value={form.avoid}
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
                onChange={(event) => setForm((prev) => ({ ...prev, notes: event.target.value }))}
                placeholder="VD: đi cùng người lớn tuổi, muốn lịch nhẹ nhàng, cần về trước 18h..."
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

            <Button id="btn-generate" onClick={handleGenerate} disabled={generating} className="planner-submit">
              {generating ? (
                <>
                  <div className="spinner" style={{ borderColor: "rgba(255,255,255,0.3)", borderTopColor: "#fff" }} />
                  Đang tạo lịch trình...
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
