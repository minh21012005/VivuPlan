"use client";
import { useState, useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import Navbar from "@/components/layout/Navbar";
import { tripApi, type TripResponse } from "@/lib/api";
import { Plus, MapPin, Clock, Wallet, Share2, Trash2, TrendingUp, Sparkles, Eye, BarChart2 } from "lucide-react";

const statusInfo: Record<string, { label: string; badge: string }> = {
  COMPLETED: { label: "Hoàn thành", badge: "badge-green" },
  PLANNED:   { label: "Kế hoạch",   badge: "badge-blue" },
  DRAFT:     { label: "Nháp",       badge: "badge-gray" },
};

const emojiMap: Record<string, string> = {
  "Đà Lạt":"🌸","Hạ Long":"⛵","Quy Nhơn":"🏖️","Đà Nẵng":"🌉",
  "Phú Quốc":"🌴","Sapa":"⛰️","Nha Trang":"🐠","Hội An":"🏮","Huế":"👑","Cần Thơ":"🚤",
};
const getEmoji = (d: string) => emojiMap[d] ?? "🗺️";
const fmtBudget = (v: number) => v >= 1_000_000 ? `${(v/1_000_000).toFixed(1)}tr ₫` : `${(v/1_000).toFixed(0)}k ₫`;

export default function DashboardPage() {
  const router = useRouter();
  const [trips, setTrips] = useState<TripResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [tab, setTab] = useState<"ALL"|"PLANNED"|"COMPLETED">("ALL");
  const [deleting, setDeleting] = useState<number|null>(null);

  useEffect(() => {
    if (!localStorage.getItem("vp_token")) { router.push("/login"); return; }
    tripApi.myTrips().then(setTrips).catch(() => setError("Không thể tải dữ liệu")).finally(() => setLoading(false));
  }, [router]);

  const filtered = tab === "ALL" ? trips : trips.filter((t) => t.status === tab);
  const uniqueDests = new Set(trips.map((t) => t.destination)).size;
  const totalDays   = trips.reduce((s, t) => s + t.days, 0);

  const handleDelete = async (id: number) => {
    if (!confirm("Xóa lịch trình này?")) return;
    setDeleting(id);
    try { await tripApi.deleteTrip(id); setTrips((p) => p.filter((t) => t.id !== id)); }
    catch { alert("Xóa thất bại"); }
    finally { setDeleting(null); }
  };

  const handleToggle = async (id: number) => {
    try { const u = await tripApi.toggleVisibility(id); setTrips((p) => p.map((t) => t.id === id ? { ...t, isPublic: u.isPublic } : t)); }
    catch { /* silent */ }
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <Navbar />
      <div style={{ paddingTop: "88px", paddingBottom: "80px" }}>
        <div className="container">

          {/* Page header */}
          <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", flexWrap: "wrap", gap: "16px", marginBottom: "32px" }}>
            <div>
              <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "28px", fontWeight: 800, color: "var(--text)", marginBottom: "4px" }}>Dashboard</h1>
              <p style={{ fontSize: "14px", color: "var(--text-3)" }}>Quản lý và theo dõi tất cả chuyến đi của bạn</p>
            </div>
            <Link href="/plan" className="btn btn-primary" style={{ textDecoration: "none" }}>
              <Plus size={16} /> Lịch trình mới
            </Link>
          </div>

          {/* Stats cards */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(2,1fr)", gap: "16px", marginBottom: "32px" }} className="md:grid-cols-4">
            {[
              { label: "Tổng chuyến đi", value: loading ? "–" : trips.length, icon: MapPin, color: "#0F9F9C", bg: "#E6FFFB" },
              { label: "Ngày du lịch",   value: loading ? "–" : totalDays,   icon: Clock, color: "#0EA5E9", bg: "#F0F9FF" },
              { label: "Điểm đến",       value: loading ? "–" : uniqueDests, icon: TrendingUp, color: "#8B5CF6", bg: "#F5F3FF" },
              { label: "Lịch trình hoàn thành", value: loading ? "–" : trips.filter(t=>t.status==="COMPLETED").length, icon: BarChart2, color: "#10B981", bg: "#F0FDF4" },
            ].map(({ label, value, icon: Icon, color, bg }) => (
              <div key={label} className="card" style={{ padding: "20px 22px", display: "flex", alignItems: "center", gap: "16px" }}>
                <div style={{ width: 44, height: 44, borderRadius: "var(--r-lg)", background: bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                  <Icon size={20} style={{ color }} />
                </div>
                <div>
                  <div style={{ fontFamily: "var(--font-heading)", fontSize: "22px", fontWeight: 800, color: "var(--text)", lineHeight: 1 }}>{value}</div>
                  <div style={{ fontSize: "12px", color: "var(--text-3)", marginTop: "3px" }}>{label}</div>
                </div>
              </div>
            ))}
          </div>

          {/* Trips panel */}
          <div className="card" style={{ overflow: "hidden" }}>
            {/* Tabs */}
            <div style={{ padding: "16px 20px", borderBottom: "1px solid var(--border)", display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "12px" }}>
              <h2 style={{ fontSize: "16px", fontWeight: 700, color: "var(--text)" }}>Chuyến đi của bạn</h2>
              <div className="tab-bar" style={{ minWidth: "300px" }}>
                {(["ALL","PLANNED","COMPLETED"] as const).map((t) => (
                  <button key={t} id={`tab-${t.toLowerCase()}`} onClick={() => setTab(t)} className={`tab-item${tab===t?" active":""}`}>
                    {t==="ALL"?"Tất cả":t==="PLANNED"?"Kế hoạch":"Hoàn thành"}
                  </button>
                ))}
              </div>
            </div>

            <div style={{ padding: "16px 20px", minHeight: "200px" }}>
              {loading && (
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "60px 0", gap: "12px" }}>
                  <div className="spinner" />
                  <p style={{ fontSize: "14px", color: "var(--text-3)" }}>Đang tải...</p>
                </div>
              )}
              {error && !loading && (
                <div style={{ textAlign: "center", padding: "60px 0" }}>
                  <p style={{ color: "#DC2626", fontSize: "14px", marginBottom: "12px" }}>{error}</p>
                  <button className="btn btn-secondary btn-sm" onClick={() => window.location.reload()}>Thử lại</button>
                </div>
              )}
              {!loading && !error && filtered.length === 0 && (
                <div style={{ textAlign: "center", padding: "60px 0" }}>
                  <div style={{ fontSize: "52px", marginBottom: "16px" }}>🗺️</div>
                  <h3 style={{ fontSize: "16px", color: "var(--text)", marginBottom: "8px" }}>Chưa có chuyến đi nào</h3>
                  <p style={{ fontSize: "14px", color: "var(--text-3)", marginBottom: "20px" }}>Hãy tạo lịch trình AI đầu tiên của bạn!</p>
                  <Link href="/plan" className="btn btn-primary btn-sm" style={{ textDecoration: "none", display: "inline-flex" }}>
                    <Sparkles size={14} /> Lập kế hoạch ngay
                  </Link>
                </div>
              )}
              <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                {!loading && filtered.map((trip) => {
                  const st = statusInfo[trip.status] ?? statusInfo["DRAFT"];
                  return (
                    <div key={trip.id} style={{
                      display: "flex", alignItems: "center", gap: "16px", padding: "16px",
                      borderRadius: "var(--r-lg)", border: "1.5px solid var(--border)",
                      background: "var(--surface)", transition: "all 0.15s",
                    }}
                    onMouseEnter={(e) => { e.currentTarget.style.borderColor = "var(--primary-muted)"; e.currentTarget.style.boxShadow = "var(--shadow-sm)"; }}
                    onMouseLeave={(e) => { e.currentTarget.style.borderColor = "var(--border)"; e.currentTarget.style.boxShadow = "none"; }}>
                      <div style={{ width: 52, height: 52, borderRadius: "var(--r-lg)", background: "var(--primary-light)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: "28px", flexShrink: 0 }}>
                        {getEmoji(trip.destination)}
                      </div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "5px", flexWrap: "wrap" }}>
                          <span style={{ fontWeight: 700, fontSize: "15px", color: "var(--text)" }}>{trip.destination}</span>
                          <span className={`badge ${st.badge}`} style={{ fontSize: "11px" }}>{st.label}</span>
                          {trip.isPublic && <span className="badge badge-teal" style={{ fontSize: "11px" }}>Công khai</span>}
                        </div>
                        <div style={{ display: "flex", gap: "16px", flexWrap: "wrap" }}>
                          <span style={{ display: "flex", alignItems: "center", gap: "4px", fontSize: "12px", color: "var(--text-4)" }}>
                            <Clock size={11} style={{ color: "var(--primary)" }} /> {trip.days}N{trip.days-1}Đ
                          </span>
                          <span style={{ display: "flex", alignItems: "center", gap: "4px", fontSize: "12px", color: "var(--text-4)" }}>
                            <Wallet size={11} style={{ color: "var(--primary)" }} /> {fmtBudget(trip.budgetPerPerson)}/người
                          </span>
                          <span style={{ display: "flex", alignItems: "center", gap: "4px", fontSize: "12px", color: "var(--text-4)" }}>
                            <Eye size={11} /> {trip.viewCount} lượt xem
                          </span>
                        </div>
                      </div>
                      <div style={{ display: "flex", gap: "6px", flexShrink: 0 }}>
                        <Link href={`/itinerary/${trip.id}`} className="btn btn-secondary btn-sm" style={{ textDecoration: "none" }}>
                          <Eye size={13} /> Xem
                        </Link>
                        <button onClick={() => handleToggle(trip.id)} className="btn btn-ghost btn-icon"
                          title={trip.isPublic ? "Ẩn" : "Công khai"} style={{ color: trip.isPublic ? "#0D9488" : "var(--text-4)" }}>
                          <Share2 size={15} />
                        </button>
                        <button onClick={() => handleDelete(trip.id)} disabled={deleting === trip.id}
                          className="btn btn-ghost btn-icon" style={{ color: "#DC2626" }}>
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>

          {/* AI suggestion banner */}
          <div style={{ marginTop: "24px", padding: "28px 32px", borderRadius: "var(--r-xl)", background: "linear-gradient(135deg, #E6FFFB, #E0F2FE)", border: "1px solid #99F6E4", display: "flex", alignItems: "center", justifyContent: "space-between", gap: "20px", flexWrap: "wrap" }}>
            <div>
              <div className="badge badge-teal" style={{ display: "inline-flex", marginBottom: "10px" }}>
                <Sparkles size={12} /> AI gợi ý
              </div>
              <h3 style={{ fontSize: "17px", fontFamily: "var(--font-heading)", color: "var(--text)", marginBottom: "6px" }}>
                Thử khám phá Nha Trang mùa hè này?
              </h3>
              <p style={{ fontSize: "13px", color: "var(--text-3)" }}>Phù hợp với sở thích du lịch biển · 4 ngày · Từ 3tr/người</p>
            </div>
            <Link href="/plan?destination=Nha+Trang" className="btn btn-primary" style={{ textDecoration: "none", flexShrink: 0 }}>
              <Sparkles size={15} /> Lên kế hoạch ngay
            </Link>
          </div>

        </div>
      </div>
    </div>
  );
}
