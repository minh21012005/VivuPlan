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
  Lightbulb,
  MapPin,
  Mountain,
  Route,
  Sparkles,
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
  { id: "solo", label: "Một mình" },
  { id: "couple", label: "Cặp đôi" },
  { id: "friends", label: "Nhóm bạn" },
  { id: "family", label: "Gia đình" },
];

const transportOptions = [
  { id: "motorbike", label: "Xe máy", icon: Bike },
  { id: "car", label: "Ô tô", icon: Car },
  { id: "bus", label: "Xe khách", icon: Bus },
  { id: "mixed", label: "Kết hợp", icon: Route },
];

const popular = destinations.map((item) => item.name);
const departureSuggestions = ["Hà Nội", "TP.HCM", "Đà Nẵng", "Hải Phòng", "Cần Thơ", "Nha Trang", "Huế", "Vinh"];

function fmtBudget(value: number) {
  return value >= 1_000_000 ? `${(value / 1_000_000).toFixed(1)}tr ₫` : `${Math.round(value / 1000)}k ₫`;
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

function suggestDestination(form: { departure: string; budget: number; style: string; startDate: string; endDate: string }) {
  const days = getTripDays(form.startDate, form.endDate);
  if (form.style === "foodie" || form.style === "cultural") return days <= 3 ? "Hội An" : "Đà Nẵng";
  if (form.style === "adventure") return form.departure.toLowerCase().includes("hà nội") ? "Sapa" : "Đà Lạt";
  if (form.budget >= 6_000_000) return "Phú Quốc";
  if (days <= 3) return form.departure.toLowerCase().includes("hà nội") ? "Hạ Long" : "Quy Nhơn";
  return "Đà Lạt";
}

function PlanContent() {
  const params = useSearchParams();
  const router = useRouter();
  const [form, setForm] = useState({
    mode: params.get("mode") === "suggest" ? "suggest" : "known",
    departure: params.get("departure") || "",
    destination: params.get("destination") || "",
    startDate: "",
    endDate: "",
    days: 0,
    budget: 0,
    style: "",
    group: "",
    transport: "",
    notes: "",
  });
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState("");
  const [focusedField, setFocusedField] = useState<"departure" | "destination" | null>(null);
  const blurTimer = useRef<number | null>(null);

  const image = useMemo(() => getDestinationImage(form.destination), [form.destination]);
  const destination = destinations.find((item) => item.name === form.destination);
  const selectedStyle = styleOptions.find((item) => item.id === form.style)?.label || "Chưa chọn";
  const selectedGroup = groupOptions.find((item) => item.id === form.group)?.label || "Chưa chọn";
  const selectedTransport = transportOptions.find((item) => item.id === form.transport)?.label || "Chưa chọn";
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
    const finalDestination = form.mode === "suggest" ? suggestDestination(form) : form.destination.trim();
    if (!finalDestination) {
      setError("Vui lòng nhập điểm đến.");
      return;
    }
    if (!form.startDate || !form.endDate || computedDays <= 0) {
      setError("Vui lòng chọn ngày đi và ngày về hợp lệ.");
      return;
    }
    if (form.budget < 500_000) {
      setError("Vui lòng nhập ngân sách tối thiểu 500.000₫ / người.");
      return;
    }
    if (!form.style) {
      setError("Vui lòng chọn phong cách du lịch.");
      return;
    }
    if (!form.group) {
      setError("Vui lòng chọn nhóm đi.");
      return;
    }
    if (!form.transport) {
      setError("Vui lòng chọn phương tiện.");
      return;
    }

    setGenerating(true);
    try {
      const trip = await tripApi.generate({
        destination: finalDestination,
        departure: form.departure.trim(),
        startDate: form.startDate,
        endDate: form.endDate,
        days: computedDays,
        budgetPerPerson: form.budget,
        style: form.style.toUpperCase(),
        groupType: form.group.toUpperCase(),
        transport: form.transport.toUpperCase(),
        notes: form.notes || undefined,
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
          <div className="planner-photo" style={{ backgroundImage: `linear-gradient(180deg, rgba(15,23,42,0.1), rgba(15,23,42,0.6)), url(${image})` }}>
            <Badge tone="glass">
              <MapPin size={13} /> {form.mode === "suggest" ? "AI sẽ gợi ý điểm đến" : form.destination || "Chọn điểm đến"}
            </Badge>
            <div>
              <h1>{form.mode === "suggest" ? "Gợi ý chuyến đi" : `Kế hoạch ${form.destination || "chuyến đi"}`} {computedDays || "..."} ngày</h1>
              <p>{form.mode === "suggest" ? "Nhập điểm xuất phát và ràng buộc, VivuPlan sẽ chọn điểm đến phù hợp trước khi lập itinerary." : destination?.tag ?? "Tạo lịch trình thực tế với ngân sách và phong cách phù hợp."}</p>
            </div>
          </div>

          <Card className="planner-preview">
            <div className="planner-route-head">
              <div>
                <span>Itinerary preview</span>
                <h2>{form.departure || "Điểm đi"} → {form.mode === "suggest" ? "AI chọn điểm đến" : form.destination || "Điểm đến"}</h2>
              </div>
              <Route size={22} />
            </div>
            <div className="planner-route-list">
              {["Sáng", "Trưa", "Chiều", "Tối"].map((time, index) => (
                <div key={time}>
                  <span>{String(index + 1).padStart(2, "0")}</span>
                  <div>
                    <strong>{time}</strong>
                    <p>{index === 0 ? "Di chuyển gọn tuyến" : index === 1 ? "Ăn uống địa phương" : index === 2 ? "Tham quan chính" : "Cà phê/chợ đêm"}</p>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </section>

        <section className="planner-panel">
          <div className="planner-heading">
            <Badge tone="teal">
              <Sparkles size={13} /> AI planner
            </Badge>
            <h2>Lập lịch trình thực tế cho điểm đến bạn đã chọn</h2>
            <p>Nhập điểm xuất phát và các ràng buộc chính. Bạn có thể chọn điểm đến trước hoặc để AI gợi ý điểm đến phù hợp.</p>
          </div>

          <Card className="planner-form">
            <div className="planner-mode-switch">
              {[
                { id: "known", label: "Tôi đã biết điểm đến", icon: MapPin },
                { id: "suggest", label: "Gợi ý điểm đến bằng AI", icon: Lightbulb },
              ].map(({ id, label, icon: Icon }) => (
                <button
                  key={id}
                  type="button"
                  className={form.mode === id ? "active" : ""}
                  onClick={() => setForm((prev) => ({ ...prev, mode: id }))}
                >
                  <Icon size={16} /> {label}
                </button>
              ))}
            </div>

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

            {form.mode === "known" ? (
            <div className="field-group">
              <label>Điểm đến</label>
              <div className="input-with-icon">
                <MapPin size={16} />
                <input
                  id="input-destination"
                  className="input"
                  value={form.destination}
                  onChange={(event) => setForm((prev) => ({ ...prev, destination: event.target.value }))}
                  onFocus={() => focusField("destination")}
                  onBlur={closeSuggestionsSoon}
                  placeholder="VD: Đà Lạt, Quy Nhơn..."
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
              <div className="chip-row">
                {popular.slice(0, 8).map((item) => (
                  <button
                    key={item}
                    type="button"
                    className={`choice-chip${form.destination === item ? " active" : ""}`}
                    onClick={() => setForm((prev) => ({ ...prev, destination: item }))}
                  >
                    {item}
                  </button>
                ))}
              </div>
            </div>
            ) : (
              <div className="suggestion-panel">
                <Badge tone="blue">AI inspiration</Badge>
                <h3>Chưa cần chọn điểm đến ngay</h3>
                <p>VivuPlan sẽ dựa trên điểm xuất phát, số ngày, ngân sách và phong cách để chọn một điểm đến phù hợp trong dữ liệu MVP.</p>
                <div>
                  {destinations.slice(0, 4).map((item) => (
                    <span key={item.name}>{item.name}</span>
                  ))}
                </div>
              </div>
            )}

            <div className="planner-two-col">
              <div className="field-group">
                <label>Ngày đi</label>
                <div className="input-with-icon">
                  <Clock size={16} />
                  <input
                    className="input"
                    type="date"
                    value={form.startDate}
                    onChange={(event) => setForm((prev) => ({ ...prev, startDate: event.target.value }))}
                  />
                </div>
              </div>

              <div className="field-group">
                <label>Ngày về</label>
                <div className="input-with-icon">
                  <Clock size={16} />
                  <input
                    className="input"
                    type="date"
                    value={form.endDate}
                    min={form.startDate || undefined}
                    onChange={(event) => setForm((prev) => ({ ...prev, endDate: event.target.value }))}
                  />
                </div>
              </div>
            </div>

            <div style={{ textAlign: "center", marginTop: "-8px" }}>
              <div className="trip-duration-pill">
                {computedDays > 0 ? `${computedDays} ngày ${computedNights} đêm` : "Chọn ngày đi và ngày về"}
              </div>
            </div>

            <div className="planner-two-col">
              <div className="field-group">
                <label>Ngân sách / người</label>
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
                <div className="budget-quick-row">
                  {[1_500_000, 3_000_000, 5_000_000, 8_000_000].map((value) => (
                    <button key={value} type="button" onClick={() => setForm((prev) => ({ ...prev, budget: value }))}>
                      {fmtBudget(value)}
                    </button>
                  ))}
                </div>
              </div>

              <div className="field-group">
                <label>Phong cách du lịch</label>
                <div className="option-grid">
                  {styleOptions.map(({ id, label, icon: Icon }) => (
                    <button
                      key={id}
                      type="button"
                      className={form.style === id ? "active" : ""}
                      onClick={() => setForm((prev) => ({ ...prev, style: id }))}
                    >
                      <Icon size={17} /> {label}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <div className="planner-two-col">
              <div className="field-group">
                <label>Nhóm đi</label>
                <div className="option-grid">
                  {groupOptions.map(({ id, label }) => (
                    <button
                      key={id}
                      type="button"
                      className={form.group === id ? "active" : ""}
                      onClick={() => setForm((prev) => ({ ...prev, group: id }))}
                    >
                      <Users size={16} /> {label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="field-group">
                <label>Phương tiện</label>
                <div className="option-grid">
                  {transportOptions.map(({ id, label, icon: Icon }) => (
                    <button
                      key={id}
                      type="button"
                      className={form.transport === id ? "active" : ""}
                      onClick={() => setForm((prev) => ({ ...prev, transport: id }))}
                    >
                      <Icon size={16} /> {label}
                    </button>
                  ))}
                </div>
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

            <div className="planner-summary">
              <span>Đi từ {form.departure || "..."}</span>
              <span>{form.mode === "suggest" ? "AI gợi ý điểm đến" : form.destination || "..."}</span>
              <span>Phong cách: {selectedStyle}</span>
              <span>Nhóm: {selectedGroup}</span>
              <span>Di chuyển: {selectedTransport}</span>
              <span>{computedDays > 0 ? `${computedDays} ngày ${computedNights} đêm` : "Chưa chọn ngày"}</span>
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
                  <Zap size={17} /> Tạo lịch trình với AI <ArrowRight size={16} />
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
