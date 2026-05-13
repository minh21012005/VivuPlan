"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Navbar from "@/components/layout/Navbar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { tripApi, type ActivityResponse, type TripResponse } from "@/lib/api";
import { getDestinationImage } from "@/lib/travel-data";
import {
  AlertCircle,
  Camera,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Coffee,
  Copy,
  ExternalLink,
  MapPin,
  Navigation,
  RefreshCw,
  Share2,
  Star,
  Utensils,
  Wallet,
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
            <div style={{ display: "flex", gap: 8, marginBottom: 18, overflowX: "auto" }} className="no-scrollbar">
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

            <Card style={{ padding: 20, marginBottom: 18, display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center" }}>
              <div>
                <h2 style={{ fontSize: 20, marginBottom: 4 }}>{day.title}</h2>
                <p style={{ color: "var(--text-3)", fontSize: 14 }}>
                  {day.activities.length} hoạt động · Chi phí trong ngày khoảng {fmtCost(dayTotal)}
                </p>
              </div>
              <Button variant="secondary" size="sm" title="Tính năng tái tạo từng ngày sẽ được nối API ở bước tiếp theo">
                <RefreshCw size={13} /> Tạo lại ngày
              </Button>
            </Card>

            <div style={{ position: "relative" }}>
              <div style={{ position: "absolute", left: 22, top: 18, bottom: 18, width: 2, background: "linear-gradient(to bottom, var(--primary), var(--border))", borderRadius: 99 }} />
              <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                {day.activities.map((activity, index) => (
                  <ActivityItem
                    key={activity.id ?? `${activity.time}-${activity.name}`}
                    activity={activity}
                    expanded={expanded === `${activeDay}-${index}`}
                    onToggle={() => setExpanded(expanded === `${activeDay}-${index}` ? null : `${activeDay}-${index}`)}
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
              <p style={{ color: "var(--text-4)", fontSize: 12, marginBottom: 18 }}>VND / người · toàn chuyến</p>
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
    </div>
  );
}

function ActivityItem({ activity, expanded, onToggle }: { activity: ActivityResponse; expanded: boolean; onToggle: () => void }) {
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
              <span className="badge badge-teal">{cfg.label}</span>
            </div>
          </div>
        )}
      </article>
    </div>
  );
}
