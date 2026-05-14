"use client";

import { type FormEvent, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Navbar from "@/components/layout/Navbar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { tripApi, type ActivityMutationRequest, type ActivityResponse, type TripResponse } from "@/lib/api";
import { getDestinationImage } from "@/lib/travel-data";
import {
  AlertCircle,
  Camera,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Coffee,
  Copy,
  Edit3,
  ExternalLink,
  MapPin,
  Navigation,
  Plus,
  Save,
  Share2,
  Star,
  Trash2,
  Utensils,
  Wallet,
  X,
} from "lucide-react";

const typeConfig: Record<string, { icon: typeof Coffee; color: string; bg: string; label: string }> = {
  FOOD: { icon: Utensils, color: "#0F9F9C", bg: "#E6FFFB", label: "Ăn uống" },
  CAFE: { icon: Coffee, color: "#0284C7", bg: "#E0F2FE", label: "Cà phê" },
  ATTRACTION: { icon: Camera, color: "#22C55E", bg: "#F0FDF4", label: "Địa điểm" },
  ACTIVITY: { icon: Camera, color: "#22C55E", bg: "#F0FDF4", label: "Hoạt động" },
  TRANSPORT: { icon: Navigation, color: "#6366F1", bg: "#EEF2FF", label: "Di chuyển" },
  ACCOMMODATION: { icon: MapPin, color: "#A855F7", bg: "#FAF5FF", label: "Lưu trú" },
};

const fmtCost = (value: number) => {
  if (!value) return "Miễn phí";
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}tr ₫`;
  return `${Math.round(value / 1000)}k ₫`;
};

const styleLabel: Record<string, string> = {
  ADVENTURE: "Phiêu lưu",
  RELAXING: "Nghỉ dưỡng",
  CULTURAL: "Văn hóa",
  NIGHTLIFE: "Khám phá đêm",
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

export default function ItineraryPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [trip, setTrip] = useState<TripResponse | null>(null);
  const [activeDay, setActiveDay] = useState(0);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  const [editor, setEditor] = useState<{ mode: "add" | "edit"; dayNumber: number; activity?: ActivityResponse } | null>(null);
  const [savingActivity, setSavingActivity] = useState(false);
  const [activityError, setActivityError] = useState("");

  useEffect(() => {
    let cancelled = false;

    async function loadTrip() {
      const id = params.id;
      setLoading(true);
      setError("");
      try {
        const data = await (/^\d+$/.test(id) ? tripApi.getTrip(id) : tripApi.getByShareCode(id));
        if (cancelled) return;
        setTrip(data);
        setActiveDay(0);
      } catch (e) {
        if (cancelled) return;
        if (!localStorage.getItem("vp_token")) {
          router.push("/login");
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
  }, [params.id, router]);

  const image = useMemo(() => getDestinationImage(trip?.destination), [trip]);

  const day = trip?.schedule?.[activeDay];
  const dayTotal = day?.activities?.reduce((sum, activity) => sum + activity.estimatedCost, 0) ?? 0;
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

  const copyShareLink = async () => {
    if (!trip?.shareCode) return;
    await navigator.clipboard.writeText(`${window.location.origin}/itinerary/${trip.shareCode}`);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1400);
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
    if (!window.confirm(`Xóa hoạt động "${activity.name}"?`)) return;
    setActivityError("");
    try {
      const updated = await tripApi.deleteActivity(trip.id, activity.id);
      setTrip(updated);
      setExpanded(null);
    } catch (e) {
      setActivityError(e instanceof Error ? e.message : "Không thể xóa hoạt động");
    }
  };

  if (loading) {
    return (
      <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
        <Navbar />
        <div style={{ minHeight: "70vh", display: "grid", placeItems: "center" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 12, color: "var(--text-3)" }}>
            <div className="spinner" /> Đang tải lịch trình...
          </div>
        </div>
      </div>
    );
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
            <Button onClick={() => router.push("/dashboard")}>Quay về dashboard</Button>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <Navbar />

      <section
        style={{
          paddingTop: 64,
          backgroundImage: `linear-gradient(90deg, rgba(4,47,46,0.84), rgba(4,47,46,0.28)), url(${image})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          color: "white",
        }}
      >
        <div className="container" style={{ paddingTop: 54, paddingBottom: 42 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 20, alignItems: "end", flexWrap: "wrap" }}>
            <div>
              <Badge tone="glass" style={{ marginBottom: 14 }}>
                <MapPin size={13} /> {trip.destination}
              </Badge>
              <h1 style={{ color: "white", fontSize: "clamp(30px, 5vw, 52px)", fontWeight: 900, marginBottom: 12 }}>
                Lịch trình {trip.destination} {trip.days} ngày
              </h1>
              <div style={{ display: "flex", gap: 16, flexWrap: "wrap", color: "rgba(255,255,255,0.9)" }}>
                <span>{trip.departure || "Điểm xuất phát"} → {trip.destination}</span>
                {trip.startDate && trip.endDate && <span>{fmtDate(trip.startDate)} - {fmtDate(trip.endDate)}</span>}
                <span>{trip.days} ngày {trip.days - 1} đêm</span>
                <span>{fmtCost(trip.budgetPerPerson)} / người</span>
                <span>{styleLabel[trip.style] ?? trip.style}</span>
                <span>{groupLabel[trip.groupType] ?? trip.groupType}</span>
              </div>
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              <Button variant="secondary" size="sm" onClick={copyShareLink}>
                {copied ? <CheckCircle2 size={14} /> : <Copy size={14} />}
                {copied ? "Đã copy" : "Copy link"}
              </Button>
              <Button variant="secondary" size="sm" onClick={() => tripApi.toggleVisibility(trip.id).then(setTrip)}>
                <Share2 size={14} /> {trip.isPublic ? "Đang công khai" : "Công khai"}
              </Button>
            </div>
          </div>
        </div>
      </section>

      <main className="container" style={{ paddingTop: 30, paddingBottom: 80 }}>
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 320px", gap: 24 }} className="itinerary-grid">
          <section>
            <div className="itinerary-day-toolbar">
              <div style={{ display: "flex", gap: 8, overflowX: "auto", minWidth: 0 }} className="no-scrollbar">
              {trip.schedule?.map((item, index) => (
                <button
                  key={item.day}
                  onClick={() => {
                    setActiveDay(index);
                    setExpanded(null);
                  }}
                  className={activeDay === index ? "btn btn-primary btn-sm" : "btn btn-secondary btn-sm"}
                >
                  Ngày {item.day}
                </button>
                ))}
              </div>
              <Button variant="primary" size="sm" onClick={() => setEditor({ mode: "add", dayNumber: day.day })}>
                <Plus size={13} /> Thêm hoạt động
              </Button>
            </div>

            <Card style={{ padding: 20, marginBottom: 18 }}>
              <div>
                <h2 style={{ fontSize: 20, marginBottom: 4 }}>{day.title}</h2>
                <p style={{ color: "var(--text-3)", fontSize: 14 }}>
                  {day.activities.length} hoạt động · Chi phí trong ngày khoảng {fmtCost(dayTotal)}
                </p>
              </div>
            </Card>

            {activityError && !editor && (
              <div style={{ marginBottom: 12, padding: "10px 12px", borderRadius: "var(--r-md)", background: "#FEF2F2", color: "#B91C1C", fontSize: 13 }}>
                {activityError}
              </div>
            )}

            <div style={{ position: "relative" }}>
              <div style={{ position: "absolute", left: 22, top: 18, bottom: 18, width: 2, background: "linear-gradient(to bottom, var(--primary), var(--border))", borderRadius: 99 }} />
              <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                {day.activities.map((activity, index) => (
                  <ActivityItem
                    key={activity.id ?? `${activity.time}-${activity.name}`}
                    activity={activity}
                    expanded={expanded === `${activeDay}-${index}`}
                    onToggle={() => setExpanded(expanded === `${activeDay}-${index}` ? null : `${activeDay}-${index}`)}
                    onEdit={() => {
                      setActivityError("");
                      setEditor({ mode: "edit", dayNumber: day.day, activity });
                    }}
                    onDelete={() => void deleteActivity(activity)}
                  />
                ))}
              </div>
            </div>
          </section>

          <aside style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <Card className="map-preview-card" style={{ overflow: "hidden" }}>
              <div style={{ height: 150, backgroundImage: `linear-gradient(180deg, rgba(15,159,156,0.1), rgba(15,159,156,0.32)), url(${image})`, backgroundSize: "cover", backgroundPosition: "center" }} />
              <div style={{ padding: 18 }}>
                <h3 style={{ fontSize: 16, marginBottom: 8, display: "flex", alignItems: "center", gap: 8 }}>
                  <Navigation size={16} style={{ color: "var(--primary)" }} /> Tuyến trong ngày
                </h3>
                <p style={{ color: "var(--text-3)", fontSize: 13, lineHeight: 1.6, marginBottom: 14 }}>
                  {day.activities.slice(0, 3).map((activity) => activity.name).join(" → ")}
                </p>
                <Button
                  variant="secondary"
                  size="sm"
                  href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${trip.destination} ${day.activities[0]?.name ?? ""}`)}`}
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

            <Card style={{ padding: 22 }}>
              <h3 style={{ fontSize: 16, marginBottom: 14 }}>Thông tin chuyến đi</h3>
              {[
                ["Điểm đến", trip.destination],
                ["Xuất phát", trip.departure || "Chưa có"],
                ["Ngày đi", fmtDate(trip.startDate) || "Chưa có"],
                ["Ngày về", fmtDate(trip.endDate) || "Chưa có"],
                ["Thời gian", `${trip.days} ngày`],
                ["Phong cách", styleLabel[trip.style] ?? trip.style],
                ["Nhóm", groupLabel[trip.groupType] ?? trip.groupType],
                ["Trạng thái", trip.status],
              ].map(([label, value]) => (
                <div key={label} style={{ display: "flex", justifyContent: "space-between", gap: 12, padding: "10px 0", borderBottom: "1px solid var(--divider)", fontSize: 13 }}>
                  <span style={{ color: "var(--text-3)" }}>{label}</span>
                  <strong style={{ textAlign: "right" }}>{value}</strong>
                </div>
              ))}
            </Card>
          </aside>
        </div>
      </main>

      {editor && (
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
    </div>
  );
}

const activityTypeOptions = [
  { value: "FOOD", label: "Ăn uống" },
  { value: "CAFE", label: "Cà phê" },
  { value: "ATTRACTION", label: "Địa điểm" },
  { value: "ACTIVITY", label: "Hoạt động" },
  { value: "TRANSPORT", label: "Di chuyển" },
  { value: "ACCOMMODATION", label: "Lưu trú" },
];

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
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onCancel();
      }}
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
  expanded,
  onToggle,
  onEdit,
  onDelete,
}: {
  activity: ActivityResponse;
  expanded: boolean;
  onToggle: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const cfg = typeConfig[activity.type] ?? typeConfig.ATTRACTION;
  const Icon = cfg.icon;
  const mapUrl =
    activity.latitude && activity.longitude
      ? `https://www.google.com/maps/search/?api=1&query=${activity.latitude},${activity.longitude}`
      : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${activity.name} ${activity.location}`)}`;

  return (
    <div style={{ display: "flex", gap: 16, alignItems: "flex-start", paddingLeft: 8 }}>
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
      <article className="card" style={{ flex: 1, overflow: "hidden" }}>
        <button
          onClick={onToggle}
          style={{ width: "100%", background: "transparent", border: "none", cursor: "pointer", padding: 16, display: "flex", gap: 12, alignItems: "center", textAlign: "left" }}
        >
          <span style={{ fontSize: 12, fontWeight: 800, color: cfg.color, background: cfg.bg, padding: "4px 9px", borderRadius: 999, whiteSpace: "nowrap" }}>
            {activity.time}
          </span>
          <div style={{ minWidth: 0, flex: 1 }}>
            <h3 style={{ fontSize: 15, marginBottom: 4, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{activity.name}</h3>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 12, color: "var(--text-4)", fontSize: 12 }}>
              <span>{activity.duration}</span>
              <span style={{ fontWeight: 700, color: activity.estimatedCost ? "var(--text-2)" : "var(--accent)" }}>{fmtCost(activity.estimatedCost)}</span>
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
              <a href={mapUrl} target="_blank" rel="noreferrer" className="btn btn-secondary btn-sm">
                <ExternalLink size={12} /> Mở bản đồ
              </a>
              <button type="button" className="btn btn-secondary btn-sm" onClick={onEdit}>
                <Edit3 size={12} /> Sửa
              </button>
              <button type="button" className="btn btn-secondary btn-sm" onClick={onDelete} style={{ color: "#B91C1C" }}>
                <Trash2 size={12} /> Xóa
              </button>
              <span className="badge badge-teal">{cfg.label}</span>
            </div>
          </div>
        )}
      </article>
    </div>
  );
}
