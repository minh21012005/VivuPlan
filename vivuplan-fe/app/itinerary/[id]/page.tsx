"use client";
import { useState } from "react";
import Navbar from "@/components/layout/Navbar";
import {
  MapPin, Clock, Wallet, Share2, Download, Edit3, Plus, Trash2,
  ChevronDown, ChevronUp, Star, Navigation, Coffee, Utensils,
  Camera, Moon, Sun, Sunrise, RefreshCw, CheckCircle, ExternalLink
} from "lucide-react";

// Mock data - will be replaced by API
const mockItinerary = {
  id: "demo-1",
  destination: "Đà Lạt",
  days: 3,
  budget: { total: 3000000, breakdown: { transport: 600000, accommodation: 900000, food: 750000, activities: 750000 } },
  style: "Nghỉ dưỡng + Khám phá",
  group: "Nhóm bạn",
  schedule: [
    {
      day: 1, title: "Ngày 1 – Khám phá trung tâm",
      activities: [
        { time: "07:00", name: "Ăn sáng Bánh Mì Đà Lạt", type: "food", location: "35 Phan Đình Phùng", duration: "45 phút", cost: 30000, rating: 4.5, note: "Bánh mì nổi tiếng, ngon và rẻ" },
        { time: "08:30", name: "Hồ Xuân Hương", type: "attraction", location: "Trung tâm thành phố", duration: "1 giờ", cost: 0, rating: 4.3, note: "Đi bộ quanh hồ, chụp ảnh" },
        { time: "10:00", name: "Chợ Đà Lạt", type: "attraction", location: "Nguyễn Thị Minh Khai", duration: "1.5 giờ", cost: 50000, rating: 4.2, note: "Mua đặc sản, thử đồ ăn vặt" },
        { time: "12:00", name: "Cơm trưa Nhà hàng Tùng", type: "food", location: "6 Khu Hòa Bình", duration: "1 giờ", cost: 80000, rating: 4.6, note: "Cơm gà nổi tiếng Đà Lạt" },
        { time: "14:00", name: "Vườn hoa thành phố", type: "attraction", location: "Phù Đổng Thiên Vương", duration: "1.5 giờ", cost: 30000, rating: 4.4, note: "Check-in đẹp, nhiều hoa" },
        { time: "16:30", name: "Café Tùng cà phê", type: "cafe", location: "6 Khu Hòa Bình", duration: "1 giờ", cost: 40000, rating: 4.7, note: "Cà phê cổ điển nổi tiếng nhất Đà Lạt" },
        { time: "19:00", name: "Ăn tối Bún bò Huế Bà Tư", type: "food", location: "Trần Phú", duration: "45 phút", cost: 60000, rating: 4.3, note: "Đặc sản miền Trung tại Đà Lạt" },
        { time: "20:30", name: "Chợ đêm Đà Lạt", type: "attraction", location: "Nguyễn Thị Minh Khai", duration: "1.5 giờ", cost: 100000, rating: 4.5, note: "Mua sắm, thử đồ ăn đêm" },
      ],
    },
    {
      day: 2, title: "Ngày 2 – Thiên nhiên & cà phê",
      activities: [
        { time: "05:30", name: "Ngắm bình minh Langbiang", type: "attraction", location: "Lạc Dương, cách TT 12km", duration: "2.5 giờ", cost: 50000, rating: 4.8, note: "Phải đi sớm để bắt kịp bình minh" },
        { time: "09:00", name: "Ăn sáng tại chân núi", type: "food", location: "Khu vực Langbiang", duration: "45 phút", cost: 40000, rating: 4.0, note: "Cháo gà nóng hổi" },
        { time: "10:30", name: "Datanla thác nước", type: "attraction", location: "Đường 3 tháng 2", duration: "2 giờ", cost: 80000, rating: 4.5, note: "Bao gồm cáp treo và xe trượt" },
        { time: "13:00", name: "Cơm trưa Lẩu gà lá é", type: "food", location: "Phan Chu Trinh", duration: "1 giờ", cost: 120000, rating: 4.7, note: "Đặc sản nổi tiếng không thể bỏ qua" },
        { time: "15:00", name: "Cà phê Mê Linh – view đồi chè", type: "cafe", location: "Cầu Đất, 35km từ TT", duration: "2 giờ", cost: 60000, rating: 4.9, note: "View đẹp nhất Đà Lạt, book trước" },
        { time: "19:00", name: "Ăn tối Bánh tráng nướng", type: "food", location: "Hẻm 2 Phan Bội Châu", duration: "1 giờ", cost: 50000, rating: 4.6, note: "Pizza Đà Lạt huyền thoại" },
      ],
    },
    {
      day: 3, title: "Ngày 3 – Làng hoa & về xuôi",
      activities: [
        { time: "07:30", name: "Làng hoa Vạn Thành", type: "attraction", location: "Thái Phiên", duration: "1.5 giờ", cost: 20000, rating: 4.4, note: "Đẹp nhất buổi sáng sớm" },
        { time: "09:30", name: "Ăn sáng Bánh căn Đà Lạt", type: "food", location: "Nguyễn Công Trứ", duration: "45 phút", cost: 35000, rating: 4.6, note: "Đặc sản buổi sáng không thể bỏ" },
        { time: "11:00", name: "Tu viện Domaine de Marie", type: "attraction", location: "Ngô Quyền", duration: "1 giờ", cost: 0, rating: 4.5, note: "Kiến trúc cổ kính, yên bình" },
        { time: "12:30", name: "Cơm trưa + mua đặc sản về", type: "food", location: "Chợ Đà Lạt", duration: "1.5 giờ", cost: 100000, rating: 4.4, note: "Mua atisô, mứt, trà về làm quà" },
        { time: "14:30", name: "Trả phòng & lên xe về", type: "transport", location: "Bến xe Đà Lạt", duration: "", cost: 200000, rating: 0, note: "Xe giường nằm về TP.HCM/Hà Nội" },
      ],
    },
  ],
};

const typeConfig: Record<string, { icon: typeof Coffee; color: string; bg: string }> = {
  food:       { icon: Utensils,   color: "#FF6B35", bg: "rgba(255,107,53,0.12)" },
  cafe:       { icon: Coffee,     color: "#4ECDC4", bg: "rgba(78,205,196,0.12)" },
  attraction: { icon: Camera,     color: "#FFE66D", bg: "rgba(255,230,109,0.12)" },
  transport:  { icon: Navigation, color: "#9B59B6", bg: "rgba(155,89,182,0.12)" },
};

export default function ItineraryPage() {
  const [activeDay, setActiveDay] = useState(0);
  const [expandedActivity, setExpandedActivity] = useState<string | null>(null);
  const [showBudget, setShowBudget] = useState(false);

  const it = mockItinerary;
  const day = it.schedule[activeDay];
  const dayTotal = day.activities.reduce((s, a) => s + a.cost, 0);

  const budgetPct = (v: number) => Math.round((v / it.budget.total) * 100);

  return (
    <div className="min-h-screen" style={{ background: "var(--brand-dark)" }}>
      <Navbar />
      <div className="pt-20 pb-16">
        {/* Hero banner */}
        <div
          className="relative overflow-hidden"
          style={{
            background: "linear-gradient(135deg, rgba(255,107,53,0.15) 0%, rgba(13,27,42,1) 60%)",
            borderBottom: "1px solid var(--brand-border)",
          }}
        >
          <div className="max-w-6xl mx-auto px-6 py-10">
            <div className="flex flex-col md:flex-row md:items-start justify-between gap-6">
              <div>
                <div className="badge badge-orange mb-3 inline-flex">
                  <CheckCircle size={11} /> Lịch trình đã được tối ưu
                </div>
                <h1 className="text-3xl md:text-4xl font-bold mb-2" style={{ fontFamily: "'Plus Jakarta Sans',sans-serif", color: "var(--brand-text)" }}>
                  🌸 {it.destination} · {it.days}N{it.days - 1}Đ
                </h1>
                <div className="flex flex-wrap gap-3 text-sm" style={{ color: "var(--brand-text-muted)" }}>
                  <span className="flex items-center gap-1.5"><Clock size={13} /> {it.days} ngày</span>
                  <span className="flex items-center gap-1.5"><Wallet size={13} /> {(it.budget.total / 1000000).toFixed(1)}tr VND/người</span>
                  <span className="flex items-center gap-1.5"><MapPin size={13} /> {it.style}</span>
                  <span>{it.group}</span>
                </div>
              </div>
              <div className="flex gap-2 shrink-0">
                <button id="btn-edit-trip" className="btn-secondary flex items-center gap-1.5 text-sm px-4 py-2.5">
                  <Edit3 size={14} /> Chỉnh sửa
                </button>
                <button id="btn-share-trip" className="btn-secondary flex items-center gap-1.5 text-sm px-4 py-2.5">
                  <Share2 size={14} /> Chia sẻ
                </button>
                <button id="btn-export-trip" className="btn-primary flex items-center gap-1.5 text-sm px-4 py-2.5">
                  <Download size={14} /> Xuất PDF
                </button>
              </div>
            </div>
          </div>
        </div>

        <div className="max-w-6xl mx-auto px-6 py-8">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* LEFT: Schedule */}
            <div className="lg:col-span-2 space-y-5">
              {/* Day tabs */}
              <div className="flex gap-2 overflow-x-auto no-scrollbar">
                {it.schedule.map((d, i) => (
                  <button
                    key={i}
                    id={`btn-day-${i + 1}`}
                    onClick={() => setActiveDay(i)}
                    className="shrink-0 px-5 py-2.5 rounded-xl text-sm font-semibold transition-all duration-200"
                    style={{
                      background: activeDay === i ? "var(--gradient-brand)" : "var(--brand-surface)",
                      border: `1px solid ${activeDay === i ? "transparent" : "var(--brand-border)"}`,
                      color: activeDay === i ? "white" : "var(--brand-text-muted)",
                    }}
                  >
                    Ngày {d.day}
                  </button>
                ))}
              </div>

              {/* Day header */}
              <div className="rounded-xl px-5 py-4 flex items-center justify-between" style={{ background: "var(--brand-surface)", border: "1px solid var(--brand-border)" }}>
                <div>
                  <h2 className="font-bold text-lg" style={{ color: "var(--brand-text)" }}>{day.title}</h2>
                  <p className="text-sm" style={{ color: "var(--brand-text-muted)" }}>{day.activities.length} hoạt động · Chi phí ~{(dayTotal / 1000).toFixed(0)}k VND</p>
                </div>
                <button id="btn-regen-day" className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg transition-all" style={{ background: "rgba(255,107,53,0.1)", color: "var(--brand-primary)", border: "1px solid rgba(255,107,53,0.2)" }}>
                  <RefreshCw size={12} /> Tạo lại
                </button>
              </div>

              {/* Timeline */}
              <div className="relative">
                <div className="absolute left-[23px] top-4 bottom-4 w-0.5" style={{ background: "linear-gradient(to bottom, var(--brand-primary), transparent)" }} />
                <div className="space-y-3 pl-12">
                  {day.activities.map((act, i) => {
                    const cfg = typeConfig[act.type] || typeConfig.attraction;
                    const key = `${activeDay}-${i}`;
                    const expanded = expandedActivity === key;
                    return (
                      <div key={key} className="relative">
                        {/* Dot */}
                        <div
                          className="absolute -left-12 top-4 w-5 h-5 rounded-full flex items-center justify-center"
                          style={{ background: cfg.bg, border: `2px solid ${cfg.color}` }}
                        >
                          <cfg.icon size={10} style={{ color: cfg.color }} />
                        </div>
                        <div
                          className="rounded-xl overflow-hidden cursor-pointer transition-all duration-200"
                          style={{ background: "var(--brand-surface)", border: `1px solid ${expanded ? cfg.color + "40" : "var(--brand-border)"}` }}
                          onClick={() => setExpandedActivity(expanded ? null : key)}
                        >
                          <div className="px-4 py-3.5 flex items-start gap-3">
                            <div className="shrink-0 text-right" style={{ width: "48px" }}>
                              <span className="text-xs font-bold" style={{ color: cfg.color }}>{act.time}</span>
                            </div>
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center justify-between gap-2">
                                <h3 className="font-semibold text-sm" style={{ color: "var(--brand-text)" }}>{act.name}</h3>
                                <div className="flex items-center gap-2 shrink-0">
                                  {act.rating > 0 && (
                                    <span className="flex items-center gap-0.5 text-xs" style={{ color: "#FFE66D" }}>
                                      <Star size={10} fill="#FFE66D" />{act.rating}
                                    </span>
                                  )}
                                  {expanded ? <ChevronUp size={14} style={{ color: "var(--brand-text-dim)" }} /> : <ChevronDown size={14} style={{ color: "var(--brand-text-dim)" }} />}
                                </div>
                              </div>
                              <div className="flex flex-wrap gap-3 mt-1 text-xs" style={{ color: "var(--brand-text-dim)" }}>
                                {act.duration && <span className="flex items-center gap-1"><Clock size={10} />{act.duration}</span>}
                                {act.cost > 0 && <span className="flex items-center gap-1"><Wallet size={10} />{(act.cost / 1000).toFixed(0)}k VND</span>}
                              </div>
                            </div>
                          </div>

                          {/* Expanded */}
                          {expanded && (
                            <div className="px-4 pb-4 pt-0 border-t" style={{ borderColor: "var(--brand-border)" }}>
                              <div className="mt-3 space-y-2.5">
                                <div className="flex items-start gap-2 text-sm">
                                  <MapPin size={13} className="mt-0.5 shrink-0" style={{ color: "var(--brand-primary)" }} />
                                  <span style={{ color: "var(--brand-text-muted)" }}>{act.location}</span>
                                </div>
                                {act.note && (
                                  <div className="rounded-lg px-3 py-2 text-sm" style={{ background: "rgba(255,255,255,0.04)", color: "var(--brand-text-muted)" }}>
                                    💡 {act.note}
                                  </div>
                                )}
                                <div className="flex gap-2 pt-1">
                                  <button className="flex items-center gap-1 text-xs px-3 py-1.5 rounded-lg transition-all hover:bg-white/5" style={{ color: "var(--brand-text-dim)", border: "1px solid var(--brand-border)" }}>
                                    <ExternalLink size={11} /> Google Maps
                                  </button>
                                  <button className="flex items-center gap-1 text-xs px-3 py-1.5 rounded-lg transition-all hover:bg-red-900/20" style={{ color: "#ff6b6b", border: "1px solid rgba(255,107,107,0.2)" }}>
                                    <Trash2 size={11} /> Xóa
                                  </button>
                                  <button className="flex items-center gap-1 text-xs px-3 py-1.5 rounded-lg transition-all" style={{ color: "var(--brand-primary)", border: "1px solid rgba(255,107,53,0.3)", background: "rgba(255,107,53,0.08)" }}>
                                    <RefreshCw size={11} /> Thay thế
                                  </button>
                                </div>
                              </div>
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                  {/* Add activity */}
                  <div className="relative">
                    <div className="absolute -left-12 top-3 w-5 h-5 rounded-full flex items-center justify-center" style={{ background: "var(--brand-surface-3)" }}>
                      <Plus size={10} style={{ color: "var(--brand-text-dim)" }} />
                    </div>
                    <button
                      id="btn-add-activity"
                      className="w-full rounded-xl py-3 text-sm font-medium transition-all duration-200 flex items-center justify-center gap-2"
                      style={{ background: "rgba(255,255,255,0.03)", border: "1px dashed var(--brand-border)", color: "var(--brand-text-dim)" }}
                      onMouseEnter={(e) => { e.currentTarget.style.borderColor = "rgba(255,107,53,0.4)"; e.currentTarget.style.color = "var(--brand-primary)"; }}
                      onMouseLeave={(e) => { e.currentTarget.style.borderColor = "var(--brand-border)"; e.currentTarget.style.color = "var(--brand-text-dim)"; }}
                    >
                      <Plus size={15} /> Thêm hoạt động
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* RIGHT: Sidebar */}
            <div className="space-y-5">
              {/* Budget card */}
              <div className="rounded-2xl p-5" style={{ background: "var(--brand-surface)", border: "1px solid var(--brand-border)" }}>
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-bold" style={{ color: "var(--brand-text)" }}>💰 Ngân sách</h3>
                  <button onClick={() => setShowBudget(!showBudget)} className="text-xs" style={{ color: "var(--brand-primary)" }}>
                    {showBudget ? "Thu gọn" : "Chi tiết"}
                  </button>
                </div>
                <div className="text-3xl font-bold mb-1" style={{ color: "var(--brand-text)", fontFamily: "'Plus Jakarta Sans',sans-serif" }}>
                  {(it.budget.total / 1000000).toFixed(1)}tr
                </div>
                <p className="text-xs mb-4" style={{ color: "var(--brand-text-muted)" }}>VND / người · Ước tính toàn chuyến</p>

                {showBudget && (
                  <div className="space-y-3 mt-4 pt-4" style={{ borderTop: "1px solid var(--brand-border)" }}>
                    {Object.entries({ "Vận chuyển 🚗": it.budget.breakdown.transport, "Lưu trú 🏨": it.budget.breakdown.accommodation, "Ăn uống 🍜": it.budget.breakdown.food, "Hoạt động 🎭": it.budget.breakdown.activities }).map(([label, val]) => (
                      <div key={label}>
                        <div className="flex justify-between text-sm mb-1">
                          <span style={{ color: "var(--brand-text-muted)" }}>{label}</span>
                          <span className="font-medium" style={{ color: "var(--brand-text)" }}>{(val / 1000).toFixed(0)}k</span>
                        </div>
                        <div className="h-1.5 rounded-full overflow-hidden" style={{ background: "var(--brand-surface-3)" }}>
                          <div className="h-full rounded-full" style={{ width: `${budgetPct(val)}%`, background: "var(--gradient-brand)" }} />
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Quick tips */}
              <div className="rounded-2xl p-5" style={{ background: "var(--brand-surface)", border: "1px solid var(--brand-border)" }}>
                <h3 className="font-bold mb-3" style={{ color: "var(--brand-text)" }}>💡 Tips AI</h3>
                <div className="space-y-3 text-sm" style={{ color: "var(--brand-text-muted)" }}>
                  {["Đặt phòng trước 1–2 tuần để có giá tốt", "Mua vé Langbiang online sẽ rẻ hơn 10%", "Tránh đi chợ đêm vào thứ 7 vì rất đông", "Thời tiết Đà Lạt mát quanh năm, mang áo ấm"].map((tip) => (
                    <div key={tip} className="flex items-start gap-2">
                      <CheckCircle size={13} className="mt-0.5 shrink-0" style={{ color: "var(--brand-secondary)" }} />
                      {tip}
                    </div>
                  ))}
                </div>
              </div>

              {/* Save / New plan */}
              <div className="space-y-2">
                <button id="btn-save-itinerary" className="btn-primary w-full py-3 flex items-center justify-center gap-2">
                  <CheckCircle size={16} /> Lưu lịch trình
                </button>
                <button id="btn-new-plan" onClick={() => window.location.href = "/plan"} className="btn-secondary w-full py-3 flex items-center justify-center gap-2">
                  <Plus size={16} /> Lập kế hoạch mới
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
