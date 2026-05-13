"use client";
import { useState, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Navbar from "@/components/layout/Navbar";
import { tripApi } from "@/lib/api";
import {
  MapPin, Clock, Wallet, Users, Car, Sliders, Zap,
  ChevronDown, ArrowRight, Sparkles, Mountain, Waves, Coffee, Moon
} from "lucide-react";

const popularDests = ["Đà Lạt", "Hạ Long", "Quy Nhơn", "Đà Nẵng", "Phú Quốc", "Sapa", "Nha Trang", "Huế", "Hội An", "Cần Thơ"];

const travelStyles = [
  { id: "adventure", label: "Phiêu lưu", icon: Mountain, color: "#FF6B35" },
  { id: "relaxing", label: "Nghỉ dưỡng", icon: Waves, color: "#4ECDC4" },
  { id: "cultural", label: "Văn hóa", icon: Coffee, color: "#FFE66D" },
  { id: "nightlife", label: "Khám phá đêm", icon: Moon, color: "#9B59B6" },
];

const groupTypes = [
  { id: "solo", label: "Một mình", emoji: "🧍" },
  { id: "couple", label: "Cặp đôi", emoji: "👫" },
  { id: "friends", label: "Nhóm bạn", emoji: "👯" },
  { id: "family", label: "Gia đình", emoji: "👨‍👩‍👧" },
];

const transports = [
  { id: "motorbike", label: "Xe máy", emoji: "🛵" },
  { id: "car", label: "Ô tô", emoji: "🚗" },
  { id: "bus", label: "Xe khách", emoji: "🚌" },
  { id: "mixed", label: "Kết hợp", emoji: "🔀" },
];

function PlanContent() {
  const params = useSearchParams();
  const router = useRouter();
  const [step, setStep] = useState(1);
  const [form, setForm] = useState({
    destination: params.get("destination") || "",
    days: 3,
    budget: 3000000,
    style: "relaxing",
    group: "friends",
    transport: "motorbike",
    notes: "",
  });
  const [generating, setGenerating] = useState(false);
  const [genError, setGenError] = useState("");
  const [destSearch, setDestSearch] = useState(form.destination);
  const [showSuggestions, setShowSuggestions] = useState(false);

  const filtered = popularDests.filter((d) => d.toLowerCase().includes(destSearch.toLowerCase()) && destSearch.length > 0);

  const formatBudget = (v: number) =>
    v >= 1000000 ? `${(v / 1000000).toFixed(1)}tr VND` : `${(v / 1000).toFixed(0)}k VND`;

  const handleGenerate = async () => {
    setGenError("");
    setGenerating(true);
    try {
      const res = await tripApi.generate({
        destination: form.destination,
        days: form.days,
        budgetPerPerson: form.budget,
        style: form.style.toUpperCase(),
        groupType: form.group.toUpperCase(),
        transport: form.transport.toUpperCase(),
        notes: form.notes || undefined,
      });
      router.push(`/itinerary/${res.id}`);
    } catch {
      // Fallback to demo if not authenticated or server down
      router.push("/itinerary/demo-1");
    } finally {
      setGenerating(false);
    }
  };

  const isStep1Valid = form.destination.trim().length > 0;

  return (
    <div className="min-h-screen" style={{ background: "var(--brand-dark)" }}>
      <Navbar />
      <div className="pt-24 pb-16 px-4">
        <div className="max-w-2xl mx-auto">
          {/* Header */}
          <div className="text-center mb-10">
            <div className="badge badge-orange mb-4 mx-auto inline-flex">
              <Sparkles size={12} /> AI Lập kế hoạch
            </div>
            <h1 className="text-4xl font-bold mb-2" style={{ fontFamily: "'Plus Jakarta Sans',sans-serif", color: "var(--brand-text)" }}>
              Kế hoạch chuyến đi của bạn
            </h1>
            <p className="text-gray-400">Điền thông tin, AI sẽ tạo lịch trình tối ưu trong vài giây</p>
          </div>

          {/* Progress */}
          <div className="flex items-center gap-2 mb-8">
            {[1, 2, 3].map((s) => (
              <div key={s} className="flex items-center gap-2 flex-1">
                <div
                  className="flex-1 h-1 rounded-full transition-all duration-500"
                  style={{ background: s <= step ? "var(--gradient-brand)" : "var(--brand-surface-3)" }}
                />
                <div
                  className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold transition-all duration-300 shrink-0"
                  style={{
                    background: s < step ? "var(--gradient-brand)" : s === step ? "rgba(255,107,53,0.2)" : "var(--brand-surface-3)",
                    border: s <= step ? "none" : "1px solid var(--brand-border)",
                    color: s <= step ? "white" : "var(--brand-text-dim)",
                  }}
                >
                  {s < step ? "✓" : s}
                </div>
              </div>
            ))}
          </div>

          {/* Card */}
          <div className="glass-strong rounded-2xl p-8">
            {/* STEP 1: Destination + Duration */}
            {step === 1 && (
              <div className="space-y-6 animate-fade-in">
                <h2 className="text-xl font-bold" style={{ color: "var(--brand-text)" }}>
                  <MapPin size={20} className="inline mr-2" style={{ color: "var(--brand-primary)" }} />
                  Bạn muốn đi đâu?
                </h2>

                {/* Destination input */}
                <div className="relative">
                  <label className="text-sm font-medium mb-2 block" style={{ color: "var(--brand-text-muted)" }}>Điểm đến *</label>
                  <div className="relative">
                    <input
                      id="input-destination"
                      type="text"
                      value={destSearch}
                      onChange={(e) => {
                        setDestSearch(e.target.value);
                        setForm((p) => ({ ...p, destination: e.target.value }));
                        setShowSuggestions(true);
                      }}
                      onFocus={() => setShowSuggestions(true)}
                      onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
                      placeholder="Nhập điểm đến (vd: Đà Lạt, Hạ Long...)"
                      className="input-field pl-10"
                    />
                    <MapPin size={16} className="absolute left-3 top-1/2 -translate-y-1/2" style={{ color: "var(--brand-primary)" }} />
                  </div>
                  {showSuggestions && filtered.length > 0 && (
                    <div
                      className="absolute top-full left-0 right-0 mt-1 rounded-xl overflow-hidden z-20 shadow-lg"
                      style={{ background: "var(--brand-surface-2)", border: "1px solid var(--brand-border)" }}
                    >
                      {filtered.map((d) => (
                        <button
                          key={d}
                          className="w-full text-left px-4 py-3 text-sm flex items-center gap-2 transition-colors hover:bg-white/5"
                          style={{ color: "var(--brand-text)" }}
                          onMouseDown={() => {
                            setDestSearch(d);
                            setForm((p) => ({ ...p, destination: d }));
                            setShowSuggestions(false);
                          }}
                        >
                          <MapPin size={13} style={{ color: "var(--brand-primary)" }} />
                          {d}
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                {/* Popular destinations quick pick */}
                <div>
                  <p className="text-xs mb-2" style={{ color: "var(--brand-text-dim)" }}>Điểm đến phổ biến:</p>
                  <div className="flex flex-wrap gap-2">
                    {popularDests.slice(0, 6).map((d) => (
                      <button
                        key={d}
                        onClick={() => { setDestSearch(d); setForm((p) => ({ ...p, destination: d })); }}
                        className="px-3 py-1.5 rounded-lg text-xs font-medium transition-all duration-200"
                        style={{
                          background: form.destination === d ? "rgba(255,107,53,0.2)" : "rgba(255,255,255,0.05)",
                          border: `1px solid ${form.destination === d ? "rgba(255,107,53,0.4)" : "var(--brand-border)"}`,
                          color: form.destination === d ? "var(--brand-primary)" : "var(--brand-text-muted)",
                        }}
                      >
                        {d}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Duration */}
                <div>
                  <label className="text-sm font-medium mb-2 block" style={{ color: "var(--brand-text-muted)" }}>
                    <Clock size={14} className="inline mr-1" />
                    Thời gian: <span className="font-bold" style={{ color: "var(--brand-primary)" }}>{form.days} ngày {form.days - 1} đêm</span>
                  </label>
                  <div className="flex gap-2">
                    {[2, 3, 4, 5, 7, 10].map((d) => (
                      <button
                        key={d}
                        onClick={() => setForm((p) => ({ ...p, days: d }))}
                        className="flex-1 py-2.5 rounded-xl text-sm font-semibold transition-all duration-200"
                        style={{
                          background: form.days === d ? "var(--gradient-brand)" : "rgba(255,255,255,0.05)",
                          border: `1px solid ${form.days === d ? "transparent" : "var(--brand-border)"}`,
                          color: form.days === d ? "white" : "var(--brand-text-muted)",
                        }}
                      >
                        {d}N
                      </button>
                    ))}
                  </div>
                </div>

                <button
                  id="btn-step1-next"
                  onClick={() => isStep1Valid && setStep(2)}
                  disabled={!isStep1Valid}
                  className="btn-primary w-full py-3 flex items-center justify-center gap-2"
                  style={{ opacity: isStep1Valid ? 1 : 0.4 }}
                >
                  Tiếp theo <ArrowRight size={16} />
                </button>
              </div>
            )}

            {/* STEP 2: Budget + Style + Group */}
            {step === 2 && (
              <div className="space-y-6 animate-fade-in">
                <h2 className="text-xl font-bold" style={{ color: "var(--brand-text)" }}>
                  <Sliders size={20} className="inline mr-2" style={{ color: "var(--brand-secondary)" }} />
                  Sở thích & ngân sách
                </h2>

                {/* Budget slider */}
                <div>
                  <label className="text-sm font-medium mb-1 block" style={{ color: "var(--brand-text-muted)" }}>
                    <Wallet size={14} className="inline mr-1" />
                    Ngân sách: <span className="font-bold" style={{ color: "var(--brand-primary)" }}>{formatBudget(form.budget)}</span>
                    <span className="ml-2 text-xs" style={{ color: "var(--brand-text-dim)" }}>/ người</span>
                  </label>
                  <input
                    id="input-budget"
                    type="range" min={500000} max={20000000} step={500000}
                    value={form.budget}
                    onChange={(e) => setForm((p) => ({ ...p, budget: Number(e.target.value) }))}
                    className="w-full mt-2"
                    style={{ accentColor: "var(--brand-primary)" }}
                  />
                  <div className="flex justify-between text-xs mt-1" style={{ color: "var(--brand-text-dim)" }}>
                    <span>500k (Tiết kiệm)</span><span>10tr (Tiêu chuẩn)</span><span>20tr+ (Cao cấp)</span>
                  </div>
                </div>

                {/* Travel Style */}
                <div>
                  <label className="text-sm font-medium mb-3 block" style={{ color: "var(--brand-text-muted)" }}>Phong cách du lịch</label>
                  <div className="grid grid-cols-2 gap-3">
                    {travelStyles.map(({ id, label, icon: Icon, color }) => (
                      <button
                        key={id}
                        onClick={() => setForm((p) => ({ ...p, style: id }))}
                        className="flex items-center gap-3 p-3 rounded-xl text-left transition-all duration-200"
                        style={{
                          background: form.style === id ? `${color}15` : "rgba(255,255,255,0.03)",
                          border: `1px solid ${form.style === id ? color + "50" : "var(--brand-border)"}`,
                          color: form.style === id ? color : "var(--brand-text-muted)",
                        }}
                      >
                        <Icon size={18} style={{ color: form.style === id ? color : "var(--brand-text-dim)" }} />
                        <span className="text-sm font-medium">{label}</span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Group type */}
                <div>
                  <label className="text-sm font-medium mb-3 block" style={{ color: "var(--brand-text-muted)" }}>
                    <Users size={14} className="inline mr-1" />
                    Loại nhóm
                  </label>
                  <div className="grid grid-cols-4 gap-2">
                    {groupTypes.map(({ id, label, emoji }) => (
                      <button
                        key={id}
                        onClick={() => setForm((p) => ({ ...p, group: id }))}
                        className="flex flex-col items-center gap-1.5 py-3 rounded-xl text-xs font-medium transition-all duration-200"
                        style={{
                          background: form.group === id ? "rgba(255,107,53,0.15)" : "rgba(255,255,255,0.03)",
                          border: `1px solid ${form.group === id ? "rgba(255,107,53,0.4)" : "var(--brand-border)"}`,
                          color: form.group === id ? "var(--brand-primary)" : "var(--brand-text-muted)",
                        }}
                      >
                        <span className="text-xl">{emoji}</span>
                        {label}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="flex gap-3">
                  <button onClick={() => setStep(1)} className="btn-secondary flex-1 py-3">← Quay lại</button>
                  <button id="btn-step2-next" onClick={() => setStep(3)} className="btn-primary flex-1 py-3 flex items-center justify-center gap-2">
                    Tiếp theo <ArrowRight size={16} />
                  </button>
                </div>
              </div>
            )}

            {/* STEP 3: Transport + Notes + Generate */}
            {step === 3 && (
              <div className="space-y-6 animate-fade-in">
                <h2 className="text-xl font-bold" style={{ color: "var(--brand-text)" }}>
                  <Car size={20} className="inline mr-2" style={{ color: "#FFE66D" }} />
                  Phương tiện & ghi chú
                </h2>

                <div>
                  <label className="text-sm font-medium mb-3 block" style={{ color: "var(--brand-text-muted)" }}>Phương tiện di chuyển</label>
                  <div className="grid grid-cols-2 gap-3">
                    {transports.map(({ id, label, emoji }) => (
                      <button
                        key={id}
                        onClick={() => setForm((p) => ({ ...p, transport: id }))}
                        className="flex items-center gap-3 p-3 rounded-xl transition-all duration-200"
                        style={{
                          background: form.transport === id ? "rgba(255,230,109,0.12)" : "rgba(255,255,255,0.03)",
                          border: `1px solid ${form.transport === id ? "rgba(255,230,109,0.4)" : "var(--brand-border)"}`,
                          color: form.transport === id ? "#FFE66D" : "var(--brand-text-muted)",
                        }}
                      >
                        <span className="text-2xl">{emoji}</span>
                        <span className="text-sm font-medium">{label}</span>
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="text-sm font-medium mb-2 block" style={{ color: "var(--brand-text-muted)" }}>
                    Yêu cầu thêm (không bắt buộc)
                  </label>
                  <textarea
                    id="input-notes"
                    value={form.notes}
                    onChange={(e) => setForm((p) => ({ ...p, notes: e.target.value }))}
                    placeholder="VD: Thích ăn đồ chay, cần địa điểm check-in đẹp, có trẻ nhỏ..."
                    rows={3}
                    className="input-field resize-none"
                  />
                </div>

                {/* Summary */}
                <div className="rounded-xl p-4 space-y-2" style={{ background: "rgba(255,107,53,0.07)", border: "1px solid rgba(255,107,53,0.2)" }}>
                  <p className="text-xs font-semibold mb-3" style={{ color: "var(--brand-primary)" }}>Tóm tắt kế hoạch</p>
                  {[
                    { label: "Điểm đến", value: form.destination },
                    { label: "Thời gian", value: `${form.days} ngày ${form.days - 1} đêm` },
                    { label: "Ngân sách", value: formatBudget(form.budget) + " / người" },
                    { label: "Phong cách", value: travelStyles.find((s) => s.id === form.style)?.label },
                    { label: "Nhóm", value: groupTypes.find((g) => g.id === form.group)?.label },
                    { label: "Phương tiện", value: transports.find((t) => t.id === form.transport)?.label },
                  ].map(({ label, value }) => (
                    <div key={label} className="flex justify-between text-sm">
                      <span style={{ color: "var(--brand-text-muted)" }}>{label}</span>
                      <span className="font-medium" style={{ color: "var(--brand-text)" }}>{value}</span>
                    </div>
                  ))}
                </div>

                {genError && (
                  <div className="p-3 rounded-xl text-sm" style={{ background: "rgba(255,107,107,0.1)", border: "1px solid rgba(255,107,107,0.2)", color: "#ff6b6b" }}>
                    {genError}
                  </div>
                )}

                <div className="flex gap-3">
                  <button onClick={() => setStep(2)} className="btn-secondary py-3 px-6">← Quay lại</button>
                  <button
                    id="btn-generate"
                    onClick={handleGenerate}
                    disabled={generating}
                    className="btn-primary flex-1 py-3 flex items-center justify-center gap-2"
                    style={{ fontSize: "16px" }}
                  >
                    {generating ? (
                      <>
                        <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                        AI đang tạo lịch trình...
                      </>
                    ) : (
                      <>
                        <Zap size={18} />
                        Tạo lịch trình với AI
                      </>
                    )}
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
      <div className="min-h-screen flex items-center justify-center" style={{ background: "var(--brand-dark)" }}>
        <div className="w-8 h-8 border-2 border-orange-500/30 border-t-orange-500 rounded-full animate-spin" />
      </div>
    }>
      <PlanContent />
    </Suspense>
  );
}
