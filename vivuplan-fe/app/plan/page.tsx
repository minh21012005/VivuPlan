"use client";

import { Suspense, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Navbar from "@/components/layout/Navbar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { tripApi } from "@/lib/api";
import { destinations, getDestinationImage, heroImages } from "@/lib/travel-data";
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

const departureSuggestions = ["Hà Nội", "TP.HCM", "Đà Nẵng", "Hải Phòng", "Cần Thơ", "Nha Trang", "Huế", "Vinh"];
function fmtBudget(value: number) {
  return value >= 1_000_000 ? `${(value / 1_000_000).toFixed(1)}tr ₫` : `${Math.round(value / 1000)}k ₫`;
}

function optionLabel(options: Array<{ id: string; label: string }>, id: string) {
  return options.find((item) => item.id === id)?.label ?? id;
}

function getGroupOptions(travelers: number) {
  if (travelers <= 1) return [];
  if (travelers === 2) return groupOptions.filter((item) => ["couple", "friends", "family", "kids", "seniors"].includes(item.id));
  return groupOptions.filter((item) => item.id !== "couple");
}

function normalizeSearch(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
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

function isBeforeToday(value: string) {
  if (!value) return false;
  const selected = new Date(`${value}T00:00:00`);
  const today = new Date(`${getTodayDateInput()}T00:00:00`);
  return selected < today;
}

function suggestDestination(form: { departure: string; budget: number; style: string; startDate: string; endDate: string }) {
  const days = getTripDays(form.startDate, form.endDate);
  if (form.style === "foodie" || form.style === "cultural") return days <= 3 ? "Hội An" : "Đà Nẵng";
  if (form.style === "adventure") return form.departure.toLowerCase().includes("hà nội") ? "Sapa" : "Đà Lạt";
  if (form.budget >= 6_000_000) return "Phú Quốc";
  if (days <= 3) return form.departure.toLowerCase().includes("hà nội") ? "Hạ Long" : "Quy Nhơn";
  return "Đà Lạt";
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
  const [error, setError] = useState("");
  const [focusedField, setFocusedField] = useState<"departure" | "destination" | null>(null);
  const blurTimer = useRef<number | null>(null);

  const image = useMemo(() => getDestinationImage(form.destination), [form.destination]);
  const destination = destinations.find((item) => item.name === form.destination);
  const departureQuery = normalizeSearch(form.departure);
  const destinationQuery = normalizeSearch(form.destination);
  const departureMatches = departureQuery
    ? departureSuggestions.filter((item) => normalizeSearch(item).includes(departureQuery)).slice(0, 6)
    : departureSuggestions.slice(0, 6);
  const destinationMatches = destinations
    .map((item) => item.name)
    .filter((item) => !destinationQuery || normalizeSearch(item).includes(destinationQuery))
    .slice(0, 6);
  const computedDays = getTripDays(form.startDate, form.endDate);
  const computedNights = computedDays > 0 ? Math.max(0, computedDays - 1) : 0;
  const budgetPerPerson =
    form.budgetMode === "total" && form.travelers > 0 ? Math.round(form.budget / form.travelers) : form.budget;
  const todayInput = getTodayDateInput();
  const compatibleGroupOptions = getGroupOptions(form.travelers);
  const groupSummary =
    form.travelers === 1
      ? "Đi một mình"
      : form.group
        ? optionLabel(groupOptions, form.group)
        : form.travelers > 1
          ? "Chưa chọn kiểu nhóm"
          : "Nhập số người trước";

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
    if (computedDays > 30) {
      setError("MVP hiện hỗ trợ lịch trình tối đa 30 ngày.");
      return;
    }
    if (form.budget < 500_000) {
      setError("Vui lòng nhập ngân sách tối thiểu 500.000₫.");
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
    if (budgetPerPerson < 500_000) {
      setError("Ngân sách sau khi chia theo số người cần tối thiểu 500.000₫ / người.");
      return;
    }

    setGenerating(true);
    try {
      const finalDestination = form.destination.trim() || suggestDestination({ ...form, budget: budgetPerPerson });
      const planningNotes = [
        `Số người: ${form.travelers}`,
        `Ngân sách người dùng nhập: ${fmtBudget(form.budget)} ${form.budgetMode === "total" ? "tổng nhóm" : "mỗi người"}`,
        form.travelers === 1 ? "Thành phần nhóm: Một mình" : form.group ? `Thành phần nhóm: ${optionLabel(groupOptions, form.group)}` : "",
        form.outboundTransport ? `Di chuyển đến điểm đến: ${optionLabel(outboundTransportOptions, form.outboundTransport)}` : "Di chuyển đến điểm đến: để AI đề xuất",
        form.localTransport ? `Di chuyển trong chuyến đi: ${optionLabel(localTransportOptions, form.localTransport)}` : "Di chuyển trong chuyến đi: để AI đề xuất",
        form.mustVisit.trim() ? `Địa điểm muốn ghé: ${form.mustVisit.trim()}` : "",
        form.avoid.trim() ? `Điều cần tránh: ${form.avoid.trim()}` : "",
        form.notes.trim(),
      ].filter(Boolean).join("\n");

      const trip = await tripApi.generate({
        destination: finalDestination,
        departure: form.departure.trim(),
        startDate: form.startDate,
        endDate: form.endDate,
        days: computedDays,
        budgetPerPerson,
        style: (form.style || "relaxing").toUpperCase(),
        groupType: toApiGroupType(form.group, form.travelers),
        transport: toApiTransport(form.outboundTransport, form.localTransport),
        notes: planningNotes || undefined,
      });
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
              <MapPin size={13} /> {form.destination || "AI sẽ chọn điểm đến"}
            </Badge>
            <p style={{
              marginTop: "auto",
              color: "#fff",
              textShadow: "0 2px 4px rgba(0,0,0,0.5)",
              fontSize: "14px",
              fontWeight: "500",
              maxWidth: "80%"
            }}>
              {form.destination ? destination?.tag ?? "Tạo lịch trình thực tế với ngân sách và phong cách phù hợp." : "Bạn có thể bỏ trống điểm đến, VivuPlan sẽ chọn nơi phù hợp với thời gian và ngân sách."}
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

          <Card className="planner-form">
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
                <label>Điểm đến <span className="optional-label">tùy chọn</span></label>
                <span className="group-summary">{form.destination.trim() ? "Theo điểm bạn chọn" : "Để AI chọn"}</span>
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
                  placeholder="VD: Đà Lạt, Quy Nhơn... hoặc bỏ trống để AI gợi ý"
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
              <p className="field-hint">Nếu chưa biết đi đâu, cứ để trống. AI sẽ chọn điểm đến dựa trên điểm xuất phát, thời gian, ngân sách và sở thích.</p>
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
                <label>Ngân sách</label>
                <div className="input-with-icon">
                  <Wallet size={16} />
                  <input
                    id="input-budget"
                    className="input"
                    type="number"
                    min={500000}
                    step={100000}
                    value={form.budget || ""}
                    onChange={(event) => setForm((prev) => ({ ...prev, budget: Number(event.target.value) }))}
                    placeholder="VD: 3000000"
                  />
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
                  {form.budget > 0 && form.travelers > 0
                    ? `AI sẽ tính khoảng ${fmtBudget(budgetPerPerson)} / người.`
                    : "Số người giúp AI ước tính phòng, ăn uống và phương án di chuyển."}
                </p>
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
                <label>Di chuyển đến điểm đến <span className="optional-label">tùy chọn</span></label>
                <div className="option-grid option-grid-transport">
                  {outboundTransportOptions.map(({ id, label, icon: Icon }) => (
                    <button
                      key={id}
                      type="button"
                      className={form.outboundTransport === id ? "active" : ""}
                      onClick={() => setForm((prev) => ({ ...prev, outboundTransport: prev.outboundTransport === id ? "" : id }))}
                    >
                      <Icon size={16} /> {label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="field-group">
                <label>Di chuyển trong chuyến đi <span className="optional-label">tùy chọn</span></label>
                <div className="option-grid option-grid-transport">
                  {localTransportOptions.map(({ id, label, icon: Icon }) => (
                    <button
                      key={id}
                      type="button"
                      className={form.localTransport === id ? "active" : ""}
                      onClick={() => setForm((prev) => ({ ...prev, localTransport: prev.localTransport === id ? "" : id }))}
                    >
                      <Icon size={16} /> {label}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <div className="planner-two-col">
              <div className="field-group">
                <label>Địa điểm muốn ghé <span className="optional-label">tùy chọn</span></label>
                <textarea
                  className="input textarea-compact"
                  value={form.mustVisit}
                  onChange={(event) => setForm((prev) => ({ ...prev, mustVisit: event.target.value }))}
                  placeholder="VD: Langbiang, chợ đêm, quán cà phê view đẹp..."
                />
              </div>

              <div className="field-group">
                <label>Điều cần tránh <span className="optional-label">tùy chọn</span></label>
                <textarea
                  className="input textarea-compact"
                  value={form.avoid}
                  onChange={(event) => setForm((prev) => ({ ...prev, avoid: event.target.value }))}
                  placeholder="VD: không trekking, không dậy quá sớm, không lịch quá dày..."
                />
              </div>
            </div>

            <div className="field-group">
              <label>Ghi chú thêm</label>
              <textarea
                id="input-notes"
                className="input"
                value={form.notes}
                onChange={(event) => setForm((prev) => ({ ...prev, notes: event.target.value }))}
                placeholder="VD: thích ăn chay, có trẻ em, cần lịch nhẹ, muốn nhiều quán cà phê..."
              />
            </div>


            {error && <div className="form-error">{error}</div>}

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
