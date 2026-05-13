"use client";
import { useState, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Navbar from "@/components/layout/Navbar";
import { tripApi } from "@/lib/api";
import {
  MapPin, Clock, Wallet, Users, Car, Sparkles,
  Mountain, Waves, Coffee, Moon, ArrowRight, Zap
} from "lucide-react";

const popular = ["Đà Lạt", "Hạ Long", "Quy Nhơn", "Đà Nẵng", "Phú Quốc", "Sapa", "Nha Trang", "Hội An"];

const styles = [
  { id: "adventure", label: "Phiêu lưu", icon: Mountain, color: "#22C55E", bg: "#F0FDF4" },
  { id: "relaxing", label: "Nghỉ dưỡng", icon: Waves, color: "#0EA5E9", bg: "#F0F9FF" },
  { id: "cultural", label: "Văn hóa", icon: Coffee, color: "#8B5CF6", bg: "#F5F3FF" },
  { id: "nightlife", label: "Khám phá đêm", icon: Moon, color: "#EC4899", bg: "#FDF2F8" },
];

const groups = [
  { id: "solo", label: "Một mình", emoji: "🧍" },
  { id: "couple", label: "Cặp đôi", emoji: "👫" },
  { id: "friends", label: "Nhóm bạn", emoji: "👯" },
  { id: "family", label: "Gia đình", emoji: "👨‍👩‍👧" },
];

const transports = [
  { id: "motorbike", label: "Xe máy", emoji: "🛵" },
  { id: "car", label: "Ô tô", emoji: "🚗" },
  { id: "bus", label: "Xe khách", emoji: "🚌" },
  { id: "mixed", label: "Kết hợp", emoji: "✈️" },
];

const steps = ["Điểm đến & Thời gian", "Ngân sách & Phong cách", "Phương tiện & Hoàn tất"];

function PlanContent() {
  const params = useSearchParams();
  const router = useRouter();
  const [step, setStep] = useState(1);
  const [form, setForm] = useState({
    destination: params.get("destination") || "",
    days: 3,
    budget: 3_000_000,
    style: "relaxing",
    group: "friends",
    transport: "motorbike",
    notes: "",
  });
  const [generating, setGenerating] = useState(false);
  const [genError, setGenError] = useState("");
  const [destSearch, setDestSearch] = useState(form.destination);
  const [showSugg, setShowSugg] = useState(false);

  const filtered = popular.filter((d) => d.toLowerCase().includes(destSearch.toLowerCase()) && destSearch.length > 0);
  const fmt = (v: number) => v >= 1_000_000 ? `${(v / 1_000_000).toFixed(1)}tr ₫` : `${(v / 1_000).toFixed(0)}k ₫`;
  const isStep1Valid = form.destination.trim().length > 0;

  const handleGenerate = async () => {
    setGenError(""); setGenerating(true);
    try {
      const res = await tripApi.generate({
        destination: form.destination, days: form.days, budgetPerPerson: form.budget,
        style: form.style.toUpperCase(), groupType: form.group.toUpperCase(),
        transport: form.transport.toUpperCase(), notes: form.notes || undefined,
      });
      router.push(`/itinerary/${res.id}`);
    } catch { router.push("/itinerary/demo-1"); }
    finally { setGenerating(false); }
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        background:
          "linear-gradient(180deg, rgba(246,251,250,0.88) 0%, rgba(246,251,250,0.98) 330px, var(--bg) 100%), url(https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1800&q=85)",
        backgroundSize: "cover",
        backgroundPosition: "center top",
        backgroundAttachment: "fixed",
      }}
    >
      <Navbar />
      <div style={{ paddingTop: "88px", paddingBottom: "80px" }}>
        <div className="container" style={{ maxWidth: "680px" }}>

          {/* Header */}
          <div style={{ textAlign: "center", marginBottom: "40px" }}>
            <div className="badge badge-teal" style={{ display: "inline-flex", marginBottom: "14px" }}>
              <Sparkles size={13} /> AI lập kế hoạch
            </div>
            <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "clamp(28px,4vw,40px)", fontWeight: 800, color: "var(--text)", marginBottom: "10px" }}>
              Kế hoạch chuyến đi của bạn
            </h1>
            <p style={{ fontSize: "15px", color: "var(--text-3)" }}>Điền thông tin, AI tạo lịch trình tối ưu trong 30 giây</p>
          </div>

          {/* Step indicator */}
          <div style={{ display: "flex", alignItems: "center", marginBottom: "32px", gap: "0" }}>
            {steps.map((label, i) => {
              const s = i + 1;
              const done = step > s; const active = step === s;
              return (
                <div key={s} style={{ display: "flex", alignItems: "center", flex: s < 3 ? "1 1 0" : "0 0 auto" }}>
                  <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "6px" }}>
                    <div style={{
                      width: 36, height: 36, borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center",
                      fontSize: "13px", fontWeight: 700, transition: "all 0.2s",
                      background: done ? "#10B981" : active ? "var(--primary)" : "var(--surface)",
                      color: done || active ? "white" : "var(--text-4)",
                      border: `2px solid ${done ? "#10B981" : active ? "var(--primary)" : "var(--border)"}`,
                      boxShadow: active ? "0 0 0 4px rgba(15,159,156,0.15)" : "none",
                    }}>{done ? "✓" : s}</div>
                    <span style={{ fontSize: "11px", fontWeight: 500, color: active ? "var(--primary)" : "var(--text-4)", whiteSpace: "nowrap" }}>{label}</span>
                  </div>
                  {s < 3 && <div style={{ flex: 1, height: "2px", background: done ? "#10B981" : "var(--border)", margin: "0 8px", marginBottom: "20px", transition: "background 0.3s" }} />}
                </div>
              );
            })}
          </div>

          {/* Card */}
          <div className="card" style={{ padding: "36px" }}>

            {/* STEP 1 */}
            {step === 1 && (
              <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
                <div>
                  <h2 style={{ fontSize: "18px", fontFamily: "var(--font-heading)", color: "var(--text)", marginBottom: "4px", display: "flex", alignItems: "center", gap: "8px" }}>
                    <MapPin size={18} style={{ color: "var(--primary)" }} /> Bạn muốn đi đâu?
                  </h2>
                  <p style={{ fontSize: "13px", color: "var(--text-4)" }}>Chọn điểm đến và thời gian cho chuyến đi</p>
                </div>

                {/* Destination */}
                <div style={{ position: "relative" }}>
                  <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "8px" }}>Điểm đến <span style={{ color: "var(--primary)" }}>*</span></label>
                  <div style={{ position: "relative" }}>
                    <MapPin size={15} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: "var(--primary)" }} />
                    <input id="input-destination" type="text" value={destSearch}
                      onChange={(e) => { setDestSearch(e.target.value); setForm((p) => ({ ...p, destination: e.target.value })); setShowSugg(true); }}
                      onFocus={() => setShowSugg(true)} onBlur={() => setTimeout(() => setShowSugg(false), 150)}
                      placeholder="Nhập điểm đến (vd: Đà Lạt, Hạ Long...)" className="input" style={{ paddingLeft: "36px" }} />
                  </div>
                  {showSugg && filtered.length > 0 && (
                    <div style={{ position: "absolute", top: "calc(100% + 6px)", left: 0, right: 0, background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "var(--r-lg)", boxShadow: "var(--shadow-lg)", zIndex: 20, overflow: "hidden" }}>
                      {filtered.map((d) => (
                        <button key={d} onMouseDown={() => { setDestSearch(d); setForm((p) => ({ ...p, destination: d })); setShowSugg(false); }}
                          style={{ width: "100%", textAlign: "left", padding: "11px 14px", background: "none", border: "none", cursor: "pointer", fontSize: "14px", color: "var(--text-2)", display: "flex", alignItems: "center", gap: "8px" }}
                          onMouseEnter={(e) => e.currentTarget.style.background = "var(--surface-2)"}
                          onMouseLeave={(e) => e.currentTarget.style.background = "none"}>
                          <MapPin size={13} style={{ color: "var(--primary)" }} /> {d}
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                {/* Quick picks */}
                <div>
                  <p style={{ fontSize: "12px", fontWeight: 600, color: "var(--text-4)", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "10px" }}>Phổ biến</p>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
                    {popular.map((d) => (
                      <button key={d} onClick={() => { setDestSearch(d); setForm((p) => ({ ...p, destination: d })); }}
                        style={{
                          padding: "7px 14px", borderRadius: "var(--r-full)", fontSize: "13px", fontWeight: 500, cursor: "pointer",
                          background: form.destination === d ? "var(--primary-light)" : "var(--surface-2)",
                          color: form.destination === d ? "var(--primary)" : "var(--text-3)",
                          border: `1.5px solid ${form.destination === d ? "var(--primary-muted)" : "transparent"}`,
                          transition: "all 0.15s",
                        }}>
                        {d}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Duration */}
                <div>
                  <label style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "10px" }}>
                    <Clock size={14} style={{ color: "var(--text-4)" }} /> Thời gian:{" "}
                    <span style={{ color: "var(--primary)" }}>{form.days} ngày {form.days - 1} đêm</span>
                  </label>
                  <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
                    {[2, 3, 4, 5, 7, 10].map((d) => (
                      <button key={d} onClick={() => setForm((p) => ({ ...p, days: d }))}
                        style={{
                          width: "60px", padding: "10px 0", borderRadius: "var(--r-lg)", fontSize: "14px", fontWeight: 600, cursor: "pointer",
                          background: form.days === d ? "var(--primary)" : "var(--surface-2)",
                          color: form.days === d ? "white" : "var(--text-3)",
                          border: `2px solid ${form.days === d ? "var(--primary)" : "transparent"}`,
                          boxShadow: form.days === d ? "var(--shadow-brand)" : "none",
                          transition: "all 0.15s",
                        }}>
                        {d}N
                      </button>
                    ))}
                  </div>
                </div>

                <button id="btn-step1-next" onClick={() => isStep1Valid && setStep(2)} disabled={!isStep1Valid} className="btn btn-primary" style={{ justifyContent: "center", padding: "13px", opacity: isStep1Valid ? 1 : 0.45 }}>
                  Tiếp theo <ArrowRight size={16} />
                </button>
              </div>
            )}

            {/* STEP 2 */}
            {step === 2 && (
              <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
                <div>
                  <h2 style={{ fontSize: "18px", fontFamily: "var(--font-heading)", color: "var(--text)", marginBottom: "4px", display: "flex", alignItems: "center", gap: "8px" }}>
                    <Wallet size={18} style={{ color: "#8B5CF6" }} /> Ngân sách & Phong cách
                  </h2>
                  <p style={{ fontSize: "13px", color: "var(--text-4)" }}>Cho AI biết sở thích của bạn</p>
                </div>

                {/* Budget slider */}
                <div>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px" }}>
                    <label style={{ fontSize: "13px", fontWeight: 600, color: "var(--text-2)", display: "flex", alignItems: "center", gap: "6px" }}>
                      <Wallet size={13} style={{ color: "var(--text-4)" }} /> Ngân sách / người
                    </label>
                    <span style={{ fontSize: "15px", fontWeight: 700, color: "var(--primary)" }}>{fmt(form.budget)}</span>
                  </div>
                  <input id="input-budget" type="range" min={500_000} max={20_000_000} step={500_000} value={form.budget}
                    onChange={(e) => setForm((p) => ({ ...p, budget: Number(e.target.value) }))}
                    style={{ width: "100%", accentColor: "var(--primary)", cursor: "pointer" }} />
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", color: "var(--text-4)", marginTop: "4px" }}>
                    <span>500k (Tiết kiệm)</span><span>10tr (Tiêu chuẩn)</span><span>20tr+ (Cao cấp)</span>
                  </div>
                </div>

                {/* Style */}
                <div>
                  <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "10px" }}>Phong cách du lịch</label>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px" }}>
                    {styles.map(({ id, label, icon: Icon, color, bg }) => (
                      <button key={id} onClick={() => setForm((p) => ({ ...p, style: id }))}
                        style={{
                          display: "flex", alignItems: "center", gap: "12px", padding: "14px", borderRadius: "var(--r-lg)", cursor: "pointer",
                          background: form.style === id ? bg : "var(--surface-2)",
                          border: `2px solid ${form.style === id ? color : "transparent"}`,
                          transition: "all 0.15s",
                        }}>
                        <div style={{ width: 36, height: 36, borderRadius: "var(--r-md)", background: form.style === id ? `${color}20` : "var(--surface)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                          <Icon size={18} style={{ color: form.style === id ? color : "var(--text-4)" }} />
                        </div>
                        <span style={{ fontSize: "14px", fontWeight: 600, color: form.style === id ? color : "var(--text-3)" }}>{label}</span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Group */}
                <div>
                  <label style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "10px" }}>
                    <Users size={13} style={{ color: "var(--text-4)" }} /> Loại nhóm
                  </label>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: "8px" }}>
                    {groups.map(({ id, label, emoji }) => (
                      <button key={id} onClick={() => setForm((p) => ({ ...p, group: id }))}
                        style={{
                          display: "flex", flexDirection: "column", alignItems: "center", gap: "6px",
                          padding: "14px 8px", borderRadius: "var(--r-lg)", cursor: "pointer",
                          background: form.group === id ? "var(--primary-light)" : "var(--surface-2)",
                          border: `2px solid ${form.group === id ? "var(--primary)" : "transparent"}`,
                          transition: "all 0.15s",
                        }}>
                        <span style={{ fontSize: "22px" }}>{emoji}</span>
                        <span style={{ fontSize: "12px", fontWeight: 600, color: form.group === id ? "var(--primary)" : "var(--text-3)" }}>{label}</span>
                      </button>
                    ))}
                  </div>
                </div>

                <div style={{ display: "flex", gap: "10px" }}>
                  <button onClick={() => setStep(1)} className="btn btn-secondary" style={{ padding: "11px 20px" }}>← Quay lại</button>
                  <button id="btn-step2-next" onClick={() => setStep(3)} className="btn btn-primary" style={{ flex: 1, justifyContent: "center" }}>Tiếp theo <ArrowRight size={16} /></button>
                </div>
              </div>
            )}

            {/* STEP 3 */}
            {step === 3 && (
              <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
                <div>
                  <h2 style={{ fontSize: "18px", fontFamily: "var(--font-heading)", color: "var(--text)", marginBottom: "4px", display: "flex", alignItems: "center", gap: "8px" }}>
                    <Car size={18} style={{ color: "#10B981" }} /> Phương tiện & Hoàn tất
                  </h2>
                  <p style={{ fontSize: "13px", color: "var(--text-4)" }}>Bước cuối — AI sẽ tạo lịch trình ngay sau đây</p>
                </div>

                {/* Transport */}
                <div>
                  <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "10px" }}>Phương tiện di chuyển</label>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px" }}>
                    {transports.map(({ id, label, emoji }) => (
                      <button key={id} onClick={() => setForm((p) => ({ ...p, transport: id }))}
                        style={{
                          display: "flex", alignItems: "center", gap: "12px", padding: "14px", borderRadius: "var(--r-lg)", cursor: "pointer",
                          background: form.transport === id ? "#F0FDF4" : "var(--surface-2)",
                          border: `2px solid ${form.transport === id ? "#10B981" : "transparent"}`,
                          transition: "all 0.15s",
                        }}>
                        <span style={{ fontSize: "24px" }}>{emoji}</span>
                        <span style={{ fontSize: "14px", fontWeight: 600, color: form.transport === id ? "#10B981" : "var(--text-3)" }}>{label}</span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Notes */}
                <div>
                  <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "8px" }}>Ghi chú thêm <span style={{ fontWeight: 400, color: "var(--text-4)" }}>(không bắt buộc)</span></label>
                  <textarea id="input-notes" value={form.notes} onChange={(e) => setForm((p) => ({ ...p, notes: e.target.value }))}
                    placeholder="VD: thích ăn chay, cần check-in đẹp, có trẻ em..." className="input" style={{ resize: "none", minHeight: "88px" }} />
                </div>

                {/* Summary */}
                <div style={{ background: "var(--surface-2)", borderRadius: "var(--r-lg)", padding: "16px 20px", display: "flex", flexDirection: "column", gap: "10px" }}>
                  <p style={{ fontSize: "12px", fontWeight: 700, color: "var(--text-4)", textTransform: "uppercase", letterSpacing: "0.06em" }}>Tóm tắt kế hoạch</p>
                  {[
                    { label: "Điểm đến", value: form.destination },
                    { label: "Thời gian", value: `${form.days} ngày ${form.days - 1} đêm` },
                    { label: "Ngân sách", value: `${fmt(form.budget)} / người` },
                    { label: "Phong cách", value: styles.find((s) => s.id === form.style)?.label },
                    { label: "Nhóm", value: groups.find((g) => g.id === form.group)?.label },
                    { label: "Phương tiện", value: transports.find((t) => t.id === form.transport)?.label },
                  ].map(({ label, value }) => (
                    <div key={label} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: "14px" }}>
                      <span style={{ color: "var(--text-3)" }}>{label}</span>
                      <span style={{ fontWeight: 600, color: "var(--text)" }}>{value}</span>
                    </div>
                  ))}
                </div>

                {genError && (
                  <div style={{ padding: "12px 14px", borderRadius: "var(--r-lg)", background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626", fontSize: "14px" }}>{genError}</div>
                )}

                <div style={{ display: "flex", gap: "10px" }}>
                  <button onClick={() => setStep(2)} className="btn btn-secondary" style={{ padding: "11px 20px" }}>← Quay lại</button>
                  <button id="btn-generate" onClick={handleGenerate} disabled={generating} className="btn btn-primary" style={{ flex: 1, justifyContent: "center", padding: "13px", fontSize: "15px" }}>
                    {generating ? <><div className="spinner" style={{ borderTopColor: "white", borderColor: "rgba(255,255,255,0.3)" }} /> AI đang tạo lịch trình...</>
                      : <><Zap size={17} /> Tạo lịch trình với AI</>}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function PlanPage() {
  return (
    <Suspense fallback={
      <div style={{ minHeight: "100vh", background: "var(--bg)", display: "flex", alignItems: "center", justifyContent: "center" }}>
        <div className="spinner" />
      </div>
    }>
      <PlanContent />
    </Suspense>
  );
}
