"use client";
import { useState, useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import Navbar from "@/components/layout/Navbar";
import { tripApi, type TripResponse } from "@/lib/api";
import { Plus, MapPin, Clock, Wallet, Share2, Trash2, TrendingUp, Star, Zap, Eye } from "lucide-react";

const stats = [
  { label: "Chuyến đã lên kế hoạch", key: "total", icon: MapPin, color: "#FF6B35" },
  { label: "Ngày du lịch tổng", key: "days", icon: Clock, color: "#4ECDC4" },
  { label: "Ngân sách ước tính", key: "budget", icon: Wallet, color: "#FFE66D" },
  { label: "Điểm đến khám phá", key: "dests", icon: TrendingUp, color: "#FF6B9D" },
];

const statusMap: Record<string, { label: string; color: string; bg: string }> = {
  COMPLETED: { label: "Hoàn thành", color: "#4ECDC4", bg: "rgba(78,205,196,0.12)" },
  PLANNED:   { label: "Đã lên kế hoạch", color: "#FFE66D", bg: "rgba(255,230,109,0.12)" },
  DRAFT:     { label: "Nháp", color: "#9CA3AF", bg: "rgba(156,163,175,0.12)" },
};

const emojiMap: Record<string, string> = {
  "Đà Lạt": "🌸", "Hạ Long": "⛵", "Quy Nhơn": "🏖️", "Đà Nẵng": "🌉",
  "Phú Quốc": "🌴", "Sapa": "⛰️", "Nha Trang": "🐠", "Hội An": "🏮", "Huế": "👑",
};
const getEmoji = (dest: string) => emojiMap[dest] || "🗺️";

export default function DashboardPage() {
  const router = useRouter();
  const [trips, setTrips] = useState<TripResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [tab, setTab] = useState<"ALL" | "PLANNED" | "COMPLETED">("ALL");
  const [deleting, setDeleting] = useState<number | null>(null);

  useEffect(() => {
    const token = localStorage.getItem("vp_token");
    if (!token) { router.push("/login"); return; }

    tripApi.myTrips()
      .then(setTrips)
      .catch(() => setError("Không thể tải dữ liệu. Vui lòng thử lại."))
      .finally(() => setLoading(false));
  }, [router]);

  const filtered = tab === "ALL" ? trips : trips.filter((t) => t.status === tab);

  const computedStats = {
    total: trips.length.toString(),
    days: trips.reduce((s, t) => s + t.days, 0).toString(),
    budget: trips.length > 0 ? `${(trips.reduce((s, t) => s + t.budgetPerPerson, 0) / 1_000_000).toFixed(1)}tr` : "0",
    dests: new Set(trips.map((t) => t.destination)).size.toString(),
  };

  const handleDelete = async (id: number) => {
    if (!confirm("Bạn có chắc muốn xóa lịch trình này?")) return;
    setDeleting(id);
    try {
      await tripApi.deleteTrip(id);
      setTrips((prev) => prev.filter((t) => t.id !== id));
    } catch {
      alert("Xóa thất bại. Vui lòng thử lại.");
    } finally {
      setDeleting(null);
    }
  };

  const handleTogglePublic = async (id: number) => {
    try {
      const updated = await tripApi.toggleVisibility(id);
      setTrips((prev) => prev.map((t) => (t.id === id ? { ...t, isPublic: updated.isPublic } : t)));
    } catch { /* silent */ }
  };

  return (
    <div className="min-h-screen" style={{ background: "var(--brand-dark)" }}>
      <Navbar />
      <div className="pt-24 pb-16 px-4">
        <div className="max-w-5xl mx-auto">
          {/* Header */}
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
            <div>
              <h1 className="text-3xl font-bold mb-1" style={{ fontFamily: "var(--font-heading)", color: "var(--brand-text)" }}>
                Dashboard 👋
              </h1>
              <p className="text-sm" style={{ color: "var(--brand-text-muted)" }}>Quản lý các chuyến đi của bạn</p>
            </div>
            <Link href="/plan">
              <button id="btn-new-trip" className="btn-primary flex items-center gap-2 px-5 py-2.5">
                <Plus size={16} /> Lập kế hoạch mới
              </button>
            </Link>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
            {stats.map(({ label, key, icon: Icon, color }) => (
              <div key={key} className="rounded-2xl p-5" style={{ background: "var(--brand-surface)", border: "1px solid var(--brand-border)" }}>
                <div className="w-9 h-9 rounded-xl flex items-center justify-center mb-3" style={{ background: `${color}18` }}>
                  <Icon size={18} style={{ color }} />
                </div>
                <div className="text-2xl font-bold mb-0.5" style={{ color: "var(--brand-text)", fontFamily: "var(--font-heading)" }}>
                  {loading ? "–" : computedStats[key as keyof typeof computedStats]}
                </div>
                <div className="text-xs" style={{ color: "var(--brand-text-muted)" }}>{label}</div>
              </div>
            ))}
          </div>

          {/* Trips */}
          <div className="rounded-2xl overflow-hidden" style={{ background: "var(--brand-surface)", border: "1px solid var(--brand-border)" }}>
            {/* Tabs */}
            <div className="flex gap-1 p-4" style={{ borderBottom: "1px solid var(--brand-border)" }}>
              {(["ALL", "PLANNED", "COMPLETED"] as const).map((t) => (
                <button key={t} id={`tab-${t.toLowerCase()}`} onClick={() => setTab(t)}
                  className="px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200"
                  style={{
                    background: tab === t ? "rgba(255,107,53,0.15)" : "transparent",
                    color: tab === t ? "var(--brand-primary)" : "var(--brand-text-muted)",
                    border: tab === t ? "1px solid rgba(255,107,53,0.3)" : "1px solid transparent",
                  }}>
                  {t === "ALL" ? "Tất cả" : t === "PLANNED" ? "Đã lên kế hoạch" : "Hoàn thành"}
                </button>
              ))}
            </div>

            <div className="p-4 space-y-3">
              {loading && (
                <div className="py-12 text-center">
                  <div className="w-8 h-8 border-2 border-orange-500/30 border-t-orange-500 rounded-full animate-spin mx-auto mb-3" />
                  <p className="text-sm" style={{ color: "var(--brand-text-muted)" }}>Đang tải...</p>
                </div>
              )}
              {error && !loading && (
                <div className="py-8 text-center">
                  <p className="text-sm mb-3" style={{ color: "#ff6b6b" }}>{error}</p>
                  <button className="btn-secondary text-sm px-4 py-2" onClick={() => window.location.reload()}>Thử lại</button>
                </div>
              )}
              {!loading && !error && filtered.length === 0 && (
                <div className="py-12 text-center">
                  <div className="text-5xl mb-4">🗺️</div>
                  <p className="font-semibold mb-2" style={{ color: "var(--brand-text)" }}>Chưa có chuyến đi nào</p>
                  <p className="text-sm mb-4" style={{ color: "var(--brand-text-muted)" }}>Hãy lập kế hoạch chuyến đi đầu tiên của bạn</p>
                  <Link href="/plan">
                    <button className="btn-primary px-6 py-2.5 flex items-center gap-2 mx-auto">
                      <Zap size={15} /> Lập kế hoạch ngay
                    </button>
                  </Link>
                </div>
              )}
              {!loading && filtered.map((trip) => {
                const st = statusMap[trip.status] || statusMap["DRAFT"];
                return (
                  <div key={trip.id}
                    className="rounded-xl p-4 flex flex-col md:flex-row md:items-center gap-4 transition-all duration-200"
                    style={{ background: "rgba(255,255,255,0.03)", border: "1px solid var(--brand-border)" }}
                    onMouseEnter={(e) => (e.currentTarget.style.borderColor = "rgba(255,107,53,0.25)")}
                    onMouseLeave={(e) => (e.currentTarget.style.borderColor = "var(--brand-border)")}>
                    <div className="w-14 h-14 rounded-2xl flex items-center justify-center text-3xl shrink-0"
                      style={{ background: "rgba(255,255,255,0.05)" }}>
                      {getEmoji(trip.destination)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <h3 className="font-bold" style={{ color: "var(--brand-text)" }}>{trip.destination}</h3>
                        <span className="text-xs px-2 py-0.5 rounded-full font-medium" style={{ background: st.bg, color: st.color }}>{st.label}</span>
                        {trip.isPublic && <span className="text-xs px-2 py-0.5 rounded-full" style={{ background: "rgba(78,205,196,0.1)", color: "#4ECDC4" }}>Công khai</span>}
                      </div>
                      <div className="flex flex-wrap gap-3 text-xs" style={{ color: "var(--brand-text-muted)" }}>
                        <span className="flex items-center gap-1"><Clock size={10} />{trip.days}N{trip.days-1}Đ</span>
                        <span className="flex items-center gap-1"><Wallet size={10} />{(trip.budgetPerPerson/1000000).toFixed(1)}tr VND</span>
                        <span className="flex items-center gap-1"><Eye size={10} />{trip.viewCount} lượt xem</span>
                        <span className="flex items-center gap-1"><Star size={10} />Code: {trip.shareCode}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <Link href={`/itinerary/${trip.id}`}>
                        <button id={`btn-view-${trip.id}`} className="btn-secondary text-xs px-3 py-2 flex items-center gap-1.5">
                          <Eye size={12} /> Xem
                        </button>
                      </Link>
                      <button onClick={() => handleTogglePublic(trip.id)}
                        className="p-2 rounded-lg transition-colors hover:bg-white/5" style={{ color: trip.isPublic ? "#4ECDC4" : "var(--brand-text-dim)" }} title={trip.isPublic ? "Ẩn" : "Công khai"}>
                        <Share2 size={14} />
                      </button>
                      <button onClick={() => handleDelete(trip.id)} disabled={deleting === trip.id}
                        className="p-2 rounded-lg transition-colors hover:bg-red-900/20" style={{ color: "#ff6b6b" }}>
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* AI suggestion */}
          <div className="mt-6 rounded-2xl p-6 relative overflow-hidden" style={{ background: "linear-gradient(135deg, rgba(255,107,53,0.12), rgba(78,205,196,0.08))", border: "1px solid rgba(255,107,53,0.2)" }}>
            <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
              <div>
                <p className="text-xs font-semibold mb-1" style={{ color: "var(--brand-primary)" }}>✨ AI gợi ý cho bạn</p>
                <h3 className="text-lg font-bold mb-1" style={{ color: "var(--brand-text)" }}>Đi Nha Trang mùa hè này?</h3>
                <p className="text-sm" style={{ color: "var(--brand-text-muted)" }}>Dựa trên sở thích của bạn – biển, ngân sách vừa, phù hợp nhóm bạn</p>
              </div>
              <Link href="/plan?destination=Nha+Trang">
                <button className="btn-primary shrink-0 flex items-center gap-2 px-5 py-2.5">
                  <Zap size={15} /> Lên kế hoạch ngay
                </button>
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
