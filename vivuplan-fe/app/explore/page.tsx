"use client";
import { useState } from "react";
import Link from "next/link";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { Search, MapPin, Zap, Star, Clock, Filter } from "lucide-react";

const destinations = [
  { id: 1, name: "Đà Lạt", region: "Tây Nguyên", emoji: "🌸", desc: "Thành phố ngàn hoa với khí hậu mát mẻ quanh năm", tags: ["Cảnh đẹp", "Cà phê", "Hoa"], rating: 4.8, trips: 2341, budget: "2-5tr", color: "#FF6B9D" },
  { id: 2, name: "Hạ Long", region: "Miền Bắc", emoji: "⛵", desc: "Kỳ quan thiên nhiên thế giới với hàng nghìn đảo đá", tags: ["Biển", "Hang động", "Du thuyền"], rating: 4.7, trips: 1890, budget: "3-7tr", color: "#4ECDC4" },
  { id: 3, name: "Quy Nhơn", region: "Miền Trung", emoji: "🏖️", desc: "Bãi biển xanh trong vắt, ít đông đúc, giá cả hợp lý", tags: ["Biển", "Bình yên", "Hải sản"], rating: 4.6, trips: 1203, budget: "2-4tr", color: "#FFE66D" },
  { id: 4, name: "Đà Nẵng", region: "Miền Trung", emoji: "🌉", desc: "Thành phố đáng sống với bãi biển Mỹ Khê nổi tiếng", tags: ["Biển", "Ẩm thực", "Cầu Rồng"], rating: 4.7, trips: 3102, budget: "3-6tr", color: "#FF6B35" },
  { id: 5, name: "Phú Quốc", region: "Miền Nam", emoji: "🌴", desc: "Đảo ngọc thiên đường với nước biển trong xanh tuyệt đẹp", tags: ["Đảo", "Biển", "Cao cấp"], rating: 4.8, trips: 2750, budget: "4-10tr", color: "#6C63FF" },
  { id: 6, name: "Sapa", region: "Miền Bắc", emoji: "⛰️", desc: "Núi cao mây mù, ruộng bậc thang đẹp như tranh vẽ", tags: ["Núi", "Thiên nhiên", "Trekking"], rating: 4.6, trips: 1560, budget: "2-5tr", color: "#4ECDC4" },
  { id: 7, name: "Nha Trang", region: "Miền Trung", emoji: "🐠", desc: "Thiên đường lặn biển với hệ sinh thái san hô đặc sắc", tags: ["Biển", "Lặn biển", "Nightlife"], rating: 4.5, trips: 2890, budget: "3-7tr", color: "#FF6B35" },
  { id: 8, name: "Hội An", region: "Miền Trung", emoji: "🏮", desc: "Phố cổ đèn lồng huyền ảo, di sản văn hóa thế giới", tags: ["Văn hóa", "Ẩm thực", "Phố cổ"], rating: 4.9, trips: 3450, budget: "2-5tr", color: "#FFE66D" },
  { id: 9, name: "Huế", region: "Miền Trung", emoji: "👑", desc: "Cố đô với kiến trúc cung đình và ẩm thực hoàng gia", tags: ["Văn hóa", "Lịch sử", "Ẩm thực"], rating: 4.6, trips: 1230, budget: "2-4tr", color: "#FF6B9D" },
];

const regions = ["Tất cả", "Miền Bắc", "Miền Trung", "Miền Nam", "Tây Nguyên"];

export default function ExplorePage() {
  const [search, setSearch] = useState("");
  const [region, setRegion] = useState("Tất cả");
  const [sort, setSort] = useState<"rating" | "trips" | "budget">("trips");

  const filtered = destinations
    .filter((d) => {
      const matchSearch = d.name.toLowerCase().includes(search.toLowerCase()) || d.tags.some((t) => t.toLowerCase().includes(search.toLowerCase()));
      const matchRegion = region === "Tất cả" || d.region === region;
      return matchSearch && matchRegion;
    })
    .sort((a, b) => {
      if (sort === "rating") return b.rating - a.rating;
      if (sort === "trips") return b.trips - a.trips;
      return 0;
    });

  return (
    <div className="min-h-screen" style={{ background: "var(--brand-dark)" }}>
      <Navbar />
      <div className="pt-24 pb-16 px-4">
        <div className="max-w-6xl mx-auto">
          {/* Header */}
          <div className="text-center mb-10">
            <div className="badge badge-teal mb-4 mx-auto inline-flex">🗺️ Khám phá</div>
            <h1 className="text-4xl md:text-5xl font-bold mb-3" style={{ fontFamily: "'Plus Jakarta Sans',sans-serif", color: "var(--brand-text)" }}>
              Chọn điểm đến, <span className="gradient-text">AI lo lịch trình</span>
            </h1>
            <p className="text-gray-400 text-lg max-w-xl mx-auto">
              Khám phá các điểm đến Việt Nam và lập kế hoạch ngay với AI
            </p>
          </div>

          {/* Search + Filter */}
          <div className="flex flex-col md:flex-row gap-3 mb-8">
            <div className="relative flex-1">
              <Search size={16} className="absolute left-4 top-1/2 -translate-y-1/2" style={{ color: "var(--brand-text-dim)" }} />
              <input
                id="input-explore-search"
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Tìm điểm đến hoặc trải nghiệm..."
                className="input-field pl-11"
              />
            </div>
            <select
              id="select-region"
              value={region}
              onChange={(e) => setRegion(e.target.value)}
              className="input-field md:w-44"
            >
              {regions.map((r) => <option key={r} value={r}>{r}</option>)}
            </select>
            <select
              id="select-sort"
              value={sort}
              onChange={(e) => setSort(e.target.value as typeof sort)}
              className="input-field md:w-44"
            >
              <option value="trips">Phổ biến nhất</option>
              <option value="rating">Đánh giá cao nhất</option>
            </select>
          </div>

          {/* Region pills */}
          <div className="flex gap-2 overflow-x-auto no-scrollbar mb-8">
            {regions.map((r) => (
              <button
                key={r}
                onClick={() => setRegion(r)}
                className="shrink-0 px-4 py-2 rounded-full text-sm font-medium transition-all duration-200"
                style={{
                  background: region === r ? "var(--gradient-brand)" : "rgba(255,255,255,0.05)",
                  border: `1px solid ${region === r ? "transparent" : "var(--brand-border)"}`,
                  color: region === r ? "white" : "var(--brand-text-muted)",
                }}
              >
                {r}
              </button>
            ))}
          </div>

          {/* Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {filtered.map((dest) => (
              <div
                key={dest.id}
                className="rounded-2xl overflow-hidden group transition-all duration-300"
                style={{ background: "var(--brand-surface)", border: "1px solid var(--brand-border)" }}
                onMouseEnter={(e) => { e.currentTarget.style.transform = "translateY(-4px)"; e.currentTarget.style.borderColor = dest.color + "50"; e.currentTarget.style.boxShadow = `0 12px 40px ${dest.color}20`; }}
                onMouseLeave={(e) => { e.currentTarget.style.transform = "translateY(0)"; e.currentTarget.style.borderColor = "var(--brand-border)"; e.currentTarget.style.boxShadow = "none"; }}
              >
                {/* Card top */}
                <div className="h-36 flex items-center justify-center relative" style={{ background: `linear-gradient(135deg, ${dest.color}20, ${dest.color}08)` }}>
                  <span className="text-6xl">{dest.emoji}</span>
                  <div className="absolute top-3 right-3 flex items-center gap-1 px-2 py-1 rounded-full text-xs font-semibold" style={{ background: "rgba(0,0,0,0.4)", color: "#FFE66D" }}>
                    <Star size={10} fill="#FFE66D" />{dest.rating}
                  </div>
                  <div className="absolute top-3 left-3 text-xs px-2 py-1 rounded-full font-medium" style={{ background: "rgba(0,0,0,0.4)", color: "rgba(255,255,255,0.7)" }}>
                    {dest.region}
                  </div>
                </div>

                {/* Card body */}
                <div className="p-5">
                  <h3 className="text-xl font-bold mb-1" style={{ color: "var(--brand-text)", fontFamily: "'Plus Jakarta Sans',sans-serif" }}>{dest.name}</h3>
                  <p className="text-sm mb-3 line-clamp-2" style={{ color: "var(--brand-text-muted)" }}>{dest.desc}</p>

                  <div className="flex flex-wrap gap-1.5 mb-4">
                    {dest.tags.map((tag) => (
                      <span key={tag} className="text-xs px-2 py-0.5 rounded-full" style={{ background: `${dest.color}15`, color: dest.color }}>
                        {tag}
                      </span>
                    ))}
                  </div>

                  <div className="flex items-center justify-between text-xs mb-4" style={{ color: "var(--brand-text-dim)" }}>
                    <span className="flex items-center gap-1"><Clock size={10} />{dest.budget}</span>
                    <span>{dest.trips.toLocaleString()} lịch trình</span>
                  </div>

                  <Link href={`/plan?destination=${encodeURIComponent(dest.name)}`}>
                    <button
                      id={`btn-plan-${dest.id}`}
                      className="w-full py-2.5 rounded-xl text-sm font-semibold flex items-center justify-center gap-2 transition-all duration-200"
                      style={{ background: `${dest.color}20`, color: dest.color, border: `1px solid ${dest.color}30` }}
                      onMouseEnter={(e) => { e.currentTarget.style.background = `${dest.color}30`; }}
                      onMouseLeave={(e) => { e.currentTarget.style.background = `${dest.color}20`; }}
                    >
                      <Zap size={14} /> Lập kế hoạch ngay
                    </button>
                  </Link>
                </div>
              </div>
            ))}
          </div>

          {filtered.length === 0 && (
            <div className="text-center py-16">
              <div className="text-5xl mb-4">🔍</div>
              <p className="text-lg font-semibold mb-2" style={{ color: "var(--brand-text)" }}>Không tìm thấy điểm đến</p>
              <p className="text-sm" style={{ color: "var(--brand-text-muted)" }}>Thử tìm kiếm khác hoặc xóa bộ lọc</p>
            </div>
          )}
        </div>
      </div>
      <Footer />
    </div>
  );
}
