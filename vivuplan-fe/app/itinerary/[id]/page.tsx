"use client";
import { useState } from "react";
import Navbar from "@/components/layout/Navbar";
import {
  MapPin, Clock, Wallet, Share2, Download, ChevronDown, ChevronUp,
  Star, Navigation, Coffee, Utensils, Camera, RefreshCw, Plus, ExternalLink
} from "lucide-react";

const mockItinerary = {
  id: "demo-1",
  destination: "Đà Lạt",
  days: 3,
  budget: { total: 3_000_000, breakdown: { transport: 600_000, accommodation: 900_000, food: 750_000, activities: 750_000 } },
  style: "Nghỉ dưỡng + Khám phá",
  group: "Nhóm bạn",
  schedule: [
    {
      day: 1, title: "Ngày 1 – Khám phá trung tâm",
      activities: [
        { time: "07:00", name: "Ăn sáng Bánh Mì Đà Lạt", type: "food", location: "35 Phan Đình Phùng", duration: "45 phút", cost: 30_000, rating: 4.5, note: "Bánh mì nổi tiếng, ngon và rẻ" },
        { time: "08:30", name: "Hồ Xuân Hương", type: "attraction", location: "Trung tâm thành phố", duration: "1 giờ", cost: 0, rating: 4.3, note: "Đi bộ quanh hồ, chụp ảnh" },
        { time: "10:00", name: "Chợ Đà Lạt", type: "attraction", location: "Nguyễn Thị Minh Khai", duration: "1.5 giờ", cost: 50_000, rating: 4.2, note: "Mua đặc sản, thử đồ ăn vặt" },
        { time: "12:00", name: "Cơm trưa Nhà hàng Tùng", type: "food", location: "6 Khu Hòa Bình", duration: "1 giờ", cost: 80_000, rating: 4.6, note: "Cơm gà nổi tiếng Đà Lạt" },
        { time: "14:00", name: "Vườn hoa thành phố", type: "attraction", location: "Phù Đổng Thiên Vương", duration: "1.5 giờ", cost: 30_000, rating: 4.4, note: "Check-in đẹp, nhiều hoa" },
        { time: "16:30", name: "Café Tùng", type: "cafe", location: "6 Khu Hòa Bình", duration: "1 giờ", cost: 40_000, rating: 4.7, note: "Cà phê cổ điển nổi tiếng nhất Đà Lạt" },
        { time: "19:00", name: "Bún bò Huế Bà Tư", type: "food", location: "Trần Phú", duration: "45 phút", cost: 60_000, rating: 4.3, note: "Đặc sản miền Trung tại Đà Lạt" },
        { time: "20:30", name: "Chợ đêm Đà Lạt", type: "attraction", location: "Nguyễn Thị Minh Khai", duration: "1.5 giờ", cost: 100_000, rating: 4.5, note: "Mua sắm, thử đồ ăn đêm" },
      ],
    },
    {
      day: 2, title: "Ngày 2 – Thiên nhiên & cà phê",
      activities: [
        { time: "05:30", name: "Ngắm bình minh Langbiang", type: "attraction", location: "Lạc Dương, 12km", duration: "2.5 giờ", cost: 50_000, rating: 4.8, note: "Đi sớm để bắt kịp bình minh" },
        { time: "09:00", name: "Ăn sáng tại chân núi", type: "food", location: "Khu vực Langbiang", duration: "45 phút", cost: 40_000, rating: 4.0, note: "Cháo gà nóng hổi" },
        { time: "10:30", name: "Datanla thác nước", type: "attraction", location: "Đường 3 tháng 2", duration: "2 giờ", cost: 80_000, rating: 4.5, note: "Cáp treo và xe trượt" },
        { time: "13:00", name: "Lẩu gà lá é", type: "food", location: "Phan Chu Trinh", duration: "1 giờ", cost: 120_000, rating: 4.7, note: "Đặc sản không thể bỏ qua" },
        { time: "15:00", name: "Cà phê Mê Linh – view đồi chè", type: "cafe", location: "Cầu Đất, 35km", duration: "2 giờ", cost: 60_000, rating: 4.9, note: "View đẹp nhất Đà Lạt, book trước" },
        { time: "19:00", name: "Bánh tráng nướng", type: "food", location: "Hẻm 2 Phan Bội Châu", duration: "1 giờ", cost: 50_000, rating: 4.6, note: "Pizza Đà Lạt huyền thoại" },
      ],
    },
    {
      day: 3, title: "Ngày 3 – Làng hoa & về xuôi",
      activities: [
        { time: "07:30", name: "Làng hoa Vạn Thành", type: "attraction", location: "Thái Phiên", duration: "1.5 giờ", cost: 20_000, rating: 4.4, note: "Đẹp nhất buổi sáng sớm" },
        { time: "09:30", name: "Bánh căn Đà Lạt", type: "food", location: "Nguyễn Công Trứ", duration: "45 phút", cost: 35_000, rating: 4.6, note: "Đặc sản buổi sáng" },
        { time: "11:00", name: "Tu viện Domaine de Marie", type: "attraction", location: "Ngô Quyền", duration: "1 giờ", cost: 0, rating: 4.5, note: "Kiến trúc cổ kính, yên bình" },
        { time: "12:30", name: "Cơm trưa + mua đặc sản về", type: "food", location: "Chợ Đà Lạt", duration: "1.5 giờ", cost: 100_000, rating: 4.4, note: "Mua atisô, mứt, trà" },
        { time: "14:30", name: "Về nhà", type: "transport", location: "Bến xe Đà Lạt", duration: "", cost: 200_000, rating: 0, note: "Xe giường nằm" },
      ],
    },
  ],
};

const typeConfig: Record<string, { icon: typeof Coffee; color: string; bg: string; label: string }> = {
  food:       { icon: Utensils,   color: "#F97316", bg: "#FFF7ED", label: "Ăn uống" },
  cafe:       { icon: Coffee,     color: "#0EA5E9", bg: "#F0F9FF", label: "Café" },
  attraction: { icon: Camera,     color: "#8B5CF6", bg: "#F5F3FF", label: "Địa điểm" },
  transport:  { icon: Navigation, color: "#10B981", bg: "#F0FDF4", label: "Di chuyển" },
};

const fmtCost = (v: number) => v === 0 ? "Miễn phí" : v >= 1_000_000 ? `${(v/1_000_000).toFixed(1)}tr ₫` : `${(v/1_000).toFixed(0)}k ₫`;

export default function ItineraryPage() {
  const [activeDay, setActiveDay] = useState(0);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [showBudget, setShowBudget] = useState(false);

  const it = mockItinerary;
  const day = it.schedule[activeDay];
  const dayTotal = day.activities.reduce((s, a) => s + a.cost, 0);
  const budgetPct = (v: number) => Math.min(100, Math.round((v / it.budget.total) * 100));

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <Navbar />

      {/* Hero banner */}
      <div style={{ paddingTop: "64px", background: "linear-gradient(135deg, #FFF7ED 0%, #F0F9FF 100%)", borderBottom: "1px solid var(--border)" }}>
        <div className="container" style={{ paddingTop: "36px", paddingBottom: "36px" }}>
          <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", flexWrap: "wrap", gap: "20px" }}>
            <div>
              <div className="badge badge-orange" style={{ display: "inline-flex", marginBottom: "12px" }}>🌸 {it.destination}</div>
              <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "clamp(24px,4vw,36px)", fontWeight: 800, color: "var(--text)", marginBottom: "10px" }}>
                Lịch trình {it.destination} {it.days} ngày
              </h1>
              <div style={{ display: "flex", flexWrap: "wrap", gap: "16px" }}>
                {[
                  { icon: Clock,    text: `${it.days}N${it.days-1}Đ` },
                  { icon: Wallet,   text: fmtCost(it.budget.total) + "/người" },
                  { icon: MapPin,   text: it.style },
                ].map(({ icon: Icon, text }) => (
                  <span key={text} style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "14px", color: "var(--text-3)" }}>
                    <Icon size={14} style={{ color: "var(--primary)" }} /> {text}
                  </span>
                ))}
              </div>
            </div>
            <div style={{ display: "flex", gap: "8px" }}>
              <button className="btn btn-secondary btn-sm"><Share2 size={14} /> Chia sẻ</button>
              <button className="btn btn-secondary btn-sm"><Download size={14} /> Tải PDF</button>
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="container" style={{ paddingTop: "32px", paddingBottom: "80px" }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: "24px" }} className="lg:grid-cols-3">

          {/* Main: timeline */}
          <div style={{ gridColumn: "1 / -1" }} className="lg:col-span-2">

            {/* Day tabs */}
            <div style={{ display: "flex", gap: "8px", marginBottom: "20px", overflowX: "auto" }} className="no-scrollbar">
              {it.schedule.map((s, i) => (
                <button key={i} id={`btn-day-${i+1}`} onClick={() => setActiveDay(i)}
                  style={{
                    padding: "9px 20px", borderRadius: "var(--r-full)", fontSize: "13px", fontWeight: 600,
                    whiteSpace: "nowrap", cursor: "pointer", flexShrink: 0,
                    background: activeDay === i ? "var(--primary)" : "var(--surface)",
                    color: activeDay === i ? "white" : "var(--text-3)",
                    border: `1.5px solid ${activeDay === i ? "var(--primary)" : "var(--border)"}`,
                    boxShadow: activeDay === i ? "var(--shadow-brand)" : "var(--shadow-xs)",
                    transition: "all 0.15s",
                  }}>
                  Ngày {i + 1}
                </button>
              ))}
            </div>

            {/* Day header */}
            <div className="card" style={{ padding: "18px 20px", marginBottom: "16px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              <div>
                <h2 style={{ fontSize: "16px", fontWeight: 700, color: "var(--text)", marginBottom: "2px" }}>{day.title}</h2>
                <p style={{ fontSize: "13px", color: "var(--text-3)" }}>{day.activities.length} hoạt động · Chi phí ~{fmtCost(dayTotal)}</p>
              </div>
              <button id="btn-regen-day" className="btn btn-secondary btn-sm">
                <RefreshCw size={13} /> Tạo lại ngày này
              </button>
            </div>

            {/* Activities timeline */}
            <div style={{ position: "relative", paddingLeft: "0" }}>
              {/* Vertical line */}
              <div style={{ position: "absolute", left: "22px", top: "20px", bottom: "20px", width: "2px", background: "linear-gradient(to bottom, var(--primary), var(--border))", borderRadius: "99px" }} />

              <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                {day.activities.map((act, idx) => {
                  const cfg = typeConfig[act.type] ?? typeConfig["attraction"];
                  const Icon = cfg.icon;
                  const isExp = expanded === `${activeDay}-${idx}`;
                  return (
                    <div key={idx} style={{ display: "flex", gap: "16px", alignItems: "flex-start", paddingLeft: "8px" }}>
                      {/* Timeline dot */}
                      <div style={{
                        width: 30, height: 30, borderRadius: "50%", flexShrink: 0,
                        background: cfg.bg, border: `2px solid ${cfg.color}`,
                        display: "flex", alignItems: "center", justifyContent: "center",
                        zIndex: 1, marginTop: "10px",
                      }}>
                        <Icon size={13} style={{ color: cfg.color }} />
                      </div>

                      {/* Card */}
                      <div className="card" style={{ flex: 1, overflow: "hidden", transition: "box-shadow 0.15s" }}
                        onMouseEnter={(e) => e.currentTarget.style.boxShadow = "var(--shadow-md)"}
                        onMouseLeave={(e) => e.currentTarget.style.boxShadow = "var(--shadow-sm)"}>
                        <button onClick={() => setExpanded(isExp ? null : `${activeDay}-${idx}`)}
                          style={{ width: "100%", padding: "14px 16px", background: "none", border: "none", cursor: "pointer", display: "flex", alignItems: "center", gap: "12px", textAlign: "left" }}>
                          {/* Time badge */}
                          <span style={{ fontSize: "11px", fontWeight: 700, color: cfg.color, background: cfg.bg, padding: "3px 8px", borderRadius: "var(--r-full)", whiteSpace: "nowrap", flexShrink: 0 }}>
                            {act.time}
                          </span>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <h3 style={{ fontSize: "14px", fontWeight: 700, color: "var(--text)", marginBottom: "3px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{act.name}</h3>
                            <div style={{ display: "flex", gap: "12px", flexWrap: "wrap" }}>
                              <span style={{ fontSize: "12px", color: "var(--text-4)" }}>{act.duration}</span>
                              <span style={{ fontSize: "12px", fontWeight: 600, color: act.cost === 0 ? "#10B981" : "var(--text-3)" }}>{fmtCost(act.cost)}</span>
                              {act.rating > 0 && (
                                <span style={{ display: "flex", alignItems: "center", gap: "3px", fontSize: "12px", color: "var(--text-4)" }}>
                                  <Star size={10} fill="#F97316" color="#F97316" /> {act.rating}
                                </span>
                              )}
                            </div>
                          </div>
                          {isExp ? <ChevronUp size={15} style={{ color: "var(--text-4)", flexShrink: 0 }} /> : <ChevronDown size={15} style={{ color: "var(--text-4)", flexShrink: 0 }} />}
                        </button>

                        {isExp && (
                          <div style={{ padding: "0 16px 16px", borderTop: "1px solid var(--divider)" }}>
                            <div style={{ display: "flex", alignItems: "flex-start", gap: "6px", marginTop: "12px", marginBottom: "10px" }}>
                              <MapPin size={13} style={{ color: "var(--primary)", marginTop: "2px", flexShrink: 0 }} />
                              <span style={{ fontSize: "13px", color: "var(--text-3)" }}>{act.location}</span>
                            </div>
                            {act.note && (
                              <div style={{ background: "var(--surface-2)", borderRadius: "var(--r-md)", padding: "10px 12px", fontSize: "13px", color: "var(--text-3)", lineHeight: 1.6, marginBottom: "12px" }}>
                                💡 {act.note}
                              </div>
                            )}
                            <div style={{ display: "flex", gap: "8px" }}>
                              <button className="btn btn-secondary btn-sm"><ExternalLink size={12} /> Bản đồ</button>
                              <span className={`badge ${typeConfig[act.type]?.label ? "badge-orange" : "badge-gray"}`} style={{ fontSize: "11px" }}>{cfg.label}</span>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}

                {/* Add activity button */}
                <div style={{ paddingLeft: "8px", display: "flex", gap: "16px" }}>
                  <div style={{ width: 30, height: 30, flexShrink: 0 }} />
                  <button className="btn btn-secondary btn-sm" style={{ justifyContent: "center", borderStyle: "dashed" }}>
                    <Plus size={13} /> Thêm hoạt động
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* Sidebar */}
          <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>

            {/* Budget card */}
            <div className="card" style={{ padding: "20px" }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
                <h3 style={{ fontSize: "15px", fontWeight: 700, color: "var(--text)" }}>💰 Ngân sách</h3>
                <button onClick={() => setShowBudget(!showBudget)} style={{ fontSize: "12px", color: "var(--primary)", background: "none", border: "none", cursor: "pointer" }}>
                  {showBudget ? "Ẩn bớt" : "Xem chi tiết"}
                </button>
              </div>
              <div style={{ fontFamily: "var(--font-heading)", fontSize: "28px", fontWeight: 800, color: "var(--text)", marginBottom: "4px" }}>
                {fmtCost(it.budget.total)}
              </div>
              <p style={{ fontSize: "12px", color: "var(--text-4)", marginBottom: "16px" }}>VND / người · Ước tính toàn chuyến</p>

              {showBudget && (
                <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                  {Object.entries({
                    "🚌 Di chuyển": it.budget.breakdown.transport,
                    "🏨 Lưu trú":   it.budget.breakdown.accommodation,
                    "🍜 Ăn uống":   it.budget.breakdown.food,
                    "🎡 Tham quan": it.budget.breakdown.activities,
                  }).map(([label, val]) => (
                    <div key={label}>
                      <div style={{ display: "flex", justifyContent: "space-between", fontSize: "13px", marginBottom: "5px" }}>
                        <span style={{ color: "var(--text-3)" }}>{label}</span>
                        <span style={{ fontWeight: 600, color: "var(--text)" }}>{fmtCost(val)}</span>
                      </div>
                      <div style={{ height: 6, borderRadius: "99px", background: "var(--surface-2)" }}>
                        <div style={{ height: "100%", width: `${budgetPct(val)}%`, borderRadius: "99px", background: "linear-gradient(90deg, var(--primary), #FB923C)", transition: "width 0.5s ease" }} />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Trip info */}
            <div className="card" style={{ padding: "20px" }}>
              <h3 style={{ fontSize: "15px", fontWeight: 700, color: "var(--text)", marginBottom: "14px" }}>📋 Thông tin chuyến đi</h3>
              <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                {[
                  { label: "Điểm đến", value: it.destination },
                  { label: "Thời gian", value: `${it.days}N${it.days-1}Đ` },
                  { label: "Phong cách", value: it.style },
                  { label: "Nhóm", value: it.group },
                ].map(({ label, value }) => (
                  <div key={label} style={{ display: "flex", justifyContent: "space-between", fontSize: "13px", paddingBottom: "10px", borderBottom: "1px solid var(--divider)" }}>
                    <span style={{ color: "var(--text-3)" }}>{label}</span>
                    <span style={{ fontWeight: 600, color: "var(--text)", textAlign: "right" }}>{value}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Share */}
            <div className="card" style={{ padding: "20px", background: "linear-gradient(135deg, #FFF7ED, #F0F9FF)", border: "1px solid #FED7AA" }}>
              <h3 style={{ fontSize: "15px", fontWeight: 700, color: "var(--text)", marginBottom: "8px" }}>🔗 Chia sẻ lịch trình</h3>
              <p style={{ fontSize: "13px", color: "var(--text-3)", marginBottom: "14px" }}>Gửi link cho bạn bè cùng xem lịch trình này</p>
              <button className="btn btn-primary btn-sm" style={{ width: "100%", justifyContent: "center" }}>
                <Share2 size={13} /> Sao chép liên kết
              </button>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
