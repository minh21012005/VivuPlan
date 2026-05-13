"use client";

import { Suspense, useMemo, useState } from "react";
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

const dayOptions = [2, 3, 4, 5, 7, 10];
const popular = destinations.map((item) => item.name);

function fmtBudget(value: number) {
  return value >= 1_000_000 ? `${(value / 1_000_000).toFixed(1)}tr ₫` : `${Math.round(value / 1000)}k ₫`;
}

function PlanContent() {
  const params = useSearchParams();
  const router = useRouter();
  const [form, setForm] = useState({
    destination: params.get("destination") || "Đà Lạt",
    days: 3,
    budget: 3_000_000,
    style: "relaxing",
    group: "friends",
    transport: "motorbike",
    notes: "",
  });
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState("");

  const image = useMemo(() => getDestinationImage(form.destination), [form.destination]);
  const destination = destinations.find((item) => item.name === form.destination);
  const selectedStyle = styleOptions.find((item) => item.id === form.style)?.label;
  const selectedGroup = groupOptions.find((item) => item.id === form.group)?.label;
  const selectedTransport = transportOptions.find((item) => item.id === form.transport)?.label;

  const handleGenerate = async () => {
    setError("");
    if (!form.destination.trim()) {
      setError("Vui lòng nhập điểm đến.");
      return;
    }
    setGenerating(true);
    try {
      const trip = await tripApi.generate({
        destination: form.destination.trim(),
        days: form.days,
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
        backgroundImage: `linear-gradient(180deg, rgba(246,251,250,0.88), rgba(246,251,250,0.98)), url(${heroImages.vietnamCoast})`,
      }}
    >
      <Navbar />
      <main className="container planner-shell">
        <section className="planner-visual">
          <div className="planner-photo" style={{ backgroundImage: `linear-gradient(180deg, rgba(4,47,46,0.08), rgba(4,47,46,0.58)), url(${image})` }}>
            <Badge tone="glass">
              <MapPin size={13} /> {form.destination || "Chọn điểm đến"}
            </Badge>
            <div>
              <h1>Kế hoạch {form.destination || "chuyến đi"} {form.days} ngày</h1>
              <p>{destination?.tag ?? "Tạo lịch trình thực tế với ngân sách và phong cách phù hợp."}</p>
            </div>
          </div>

          <Card className="planner-preview">
            <div className="planner-route-head">
              <div>
                <span>Itinerary preview</span>
                <h2>{form.days} ngày · {fmtBudget(form.budget)} / người</h2>
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
            <p>Điền các ràng buộc chính. VivuPlan sẽ tạo timeline, chi phí ước tính và gợi ý hoạt động từng ngày.</p>
          </div>

          <Card className="planner-form">
            <div className="field-group">
              <label>Điểm đến</label>
              <div className="input-with-icon">
                <MapPin size={16} />
                <input
                  id="input-destination"
                  className="input"
                  value={form.destination}
                  onChange={(event) => setForm((prev) => ({ ...prev, destination: event.target.value }))}
                  placeholder="VD: Đà Lạt, Quy Nhơn..."
                />
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

            <div className="planner-two-col">
              <div className="field-group">
                <label>Thời gian</label>
                <div className="segmented-grid">
                  {dayOptions.map((days) => (
                    <button
                      key={days}
                      type="button"
                      className={form.days === days ? "active" : ""}
                      onClick={() => setForm((prev) => ({ ...prev, days }))}
                    >
                      <Clock size={14} /> {days}N
                    </button>
                  ))}
                </div>
              </div>

              <div className="field-group">
                <label>Ngân sách / người</label>
                <div className="budget-display">
                  <Wallet size={16} /> {fmtBudget(form.budget)}
                </div>
                <input
                  id="input-budget"
                  type="range"
                  min={500_000}
                  max={20_000_000}
                  step={500_000}
                  value={form.budget}
                  onChange={(event) => setForm((prev) => ({ ...prev, budget: Number(event.target.value) }))}
                />
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

            <div className="planner-two-col">
              <div className="field-group">
                <label>Nhóm đi</label>
                <div className="option-grid compact">
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
                <div className="option-grid compact">
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
              <span>{selectedStyle}</span>
              <span>{selectedGroup}</span>
              <span>{selectedTransport}</span>
              <span>{form.days} ngày</span>
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
