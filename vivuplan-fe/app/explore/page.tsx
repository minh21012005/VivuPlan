"use client";
import { useState } from "react";
import Link from "next/link";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { Search, MapPin, Sparkles, Star, Clock, ArrowRight, Filter } from "lucide-react";

const regions = ["Tất cả", "Miền Bắc", "Miền Trung", "Miền Nam", "Tây Nguyên"];

const destinations = [
  { name: "Đà Lạt", region: "Tây Nguyên", emoji: "🌸", tag: "Thành phố hoa", days: "3–5 ngày", rating: 4.9, trips: 8420, desc: "Thành phố mộng mơ với khí hậu mát mẻ quanh năm, thác nước và vườn hoa tuyệt đẹp." },
  { name: "Hạ Long", region: "Miền Bắc", emoji: "⛵", tag: "Kỳ quan thế giới", days: "2–4 ngày", rating: 4.8, trips: 12300, desc: "Vịnh Hạ Long hùng vĩ với hàng nghìn đảo đá vôi, hang động và làng chài nổi." },
  { name: "Hội An", region: "Miền Trung", emoji: "🏮", tag: "Phố cổ đèn lồng", days: "2–3 ngày", rating: 4.9, trips: 9870, desc: "Phố cổ Hội An với kiến trúc cổ kính, đèn lồng lung linh và ẩm thực phong phú." },
  { name: "Phú Quốc", region: "Miền Nam", emoji: "🌴", tag: "Đảo ngọc", days: "3–5 ngày", rating: 4.7, trips: 11200, desc: "Đảo ngọc với bãi biển trắng mịn, nước biển trong xanh và hải sản tươi ngon." },
  { name: "Sapa", region: "Miền Bắc", emoji: "⛰️", tag: "Mây núi hùng vĩ", days: "3–4 ngày", rating: 4.8, trips: 7650, desc: "Thị trấn giữa mây với ruộng bậc thang hùng vĩ, văn hóa dân tộc độc đáo." },
  { name: "Nha Trang", region: "Miền Trung", emoji: "🐠", tag: "Thiên đường biển", days: "3–5 ngày", rating: 4.6, trips: 10500, desc: "Thành phố biển sôi động với bãi biển dài, lặn ngắm san hô và ẩm thực biển." },
  { name: "Đà Nẵng", region: "Miền Trung", emoji: "🌉", tag: "Thành phố đáng sống", days: "3–4 ngày", rating: 4.8, trips: 13400, desc: "Thành phố hiện đại với cầu Rồng, Bà Nà Hills và bãi biển Mỹ Khê tuyệt vời." },
  { name: "Huế", region: "Miền Trung", emoji: "👑", tag: "Cố đô lịch sử", days: "2–3 ngày", rating: 4.7, trips: 6800, desc: "Cố đô với Đại nội, lăng tẩm hoàng gia và ẩm thực cung đình độc đáo." },
  { name: "Quy Nhơn", region: "Miền Trung", emoji: "🏖️", tag: "Viên ngọc ẩn", days: "3–4 ngày", rating: 4.9, trips: 5300, desc: "Thành phố biển yên bình với bãi biển hoang sơ, tháp Chàm cổ kính." },
  { name: "Cần Thơ", region: "Miền Nam", emoji: "🚤", tag: "Miền Tây sông nước", days: "2–3 ngày", rating: 4.6, trips: 4200, desc: "Thủ phủ miền Tây với chợ nổi Cái Răng, vườn trái cây và sông nước hữu tình." },
];

const sorts = ["Phổ biến nhất", "Đánh giá cao nhất", "Chuyến đi nhiều nhất"];

export default function ExplorePage() {
  const [search, setSearch] = useState("");
  const [region, setRegion] = useState("Tất cả");
  const [sort, setSort] = useState("Phổ biến nhất");

  const filtered = destinations
    .filter((d) => (region === "Tất cả" || d.region === region) && (search === "" || d.name.toLowerCase().includes(search.toLowerCase()) || d.desc.toLowerCase().includes(search.toLowerCase())))
    .sort((a, b) => sort === "Đánh giá cao nhất" ? b.rating - a.rating : sort === "Chuyến đi nhiều nhất" ? b.trips - a.trips : b.trips - a.trips);

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <Navbar />

      {/* Hero */}
      <section style={{ paddingTop: "96px", paddingBottom: "48px", background: "linear-gradient(135deg, #FFF7ED 0%, #F0F9FF 100%)", borderBottom: "1px solid var(--border)" }}>
        <div className="container" style={{ textAlign: "center" }}>
          <div className="badge badge-blue" style={{ display: "inline-flex", marginBottom: "16px" }}>
            <MapPin size={13} /> Khám phá Việt Nam
          </div>
          <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "clamp(28px,4vw,44px)", fontWeight: 800, color: "var(--text)", marginBottom: "12px" }}>
            Điểm đến <span className="gradient-text">tuyệt vời</span> đang chờ bạn
          </h1>
          <p style={{ fontSize: "16px", color: "var(--text-3)", marginBottom: "32px", maxWidth: "480px", margin: "0 auto 32px" }}>
            Khám phá hơn 200 điểm đến trên khắp Việt Nam và tạo lịch trình AI ngay lập tức
          </p>

          {/* Search bar */}
          <div style={{ maxWidth: "520px", margin: "0 auto", position: "relative" }}>
            <Search size={17} style={{ position: "absolute", left: "16px", top: "50%", transform: "translateY(-50%)", color: "var(--text-4)" }} />
            <input id="input-search" type="text" value={search} onChange={(e) => setSearch(e.target.value)}
              placeholder="Tìm điểm đến..." className="input" style={{ paddingLeft: "44px", paddingRight: "16px", fontSize: "15px", boxShadow: "var(--shadow-md)" }} />
          </div>
        </div>
      </section>

      {/* Filters */}
      <section style={{ padding: "20px 0", background: "var(--surface)", borderBottom: "1px solid var(--border)", position: "sticky", top: "64px", zIndex: 40 }}>
        <div className="container" style={{ display: "flex", gap: "12px", alignItems: "center", flexWrap: "wrap", justifyContent: "space-between" }}>
          <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
            {regions.map((r) => (
              <button key={r} onClick={() => setRegion(r)}
                style={{
                  padding: "7px 16px", borderRadius: "var(--r-full)", fontSize: "13px", fontWeight: 500, cursor: "pointer",
                  background: region === r ? "var(--primary)" : "var(--surface-2)",
                  color: region === r ? "white" : "var(--text-3)",
                  border: `1.5px solid ${region === r ? "var(--primary)" : "transparent"}`,
                  boxShadow: region === r ? "var(--shadow-brand)" : "none",
                  transition: "all 0.15s",
                }}>
                {r}
              </button>
            ))}
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <Filter size={14} style={{ color: "var(--text-4)" }} />
            <select value={sort} onChange={(e) => setSort(e.target.value)} className="input" style={{ width: "auto", padding: "7px 12px", fontSize: "13px" }}>
              {sorts.map((s) => <option key={s}>{s}</option>)}
            </select>
          </div>
        </div>
      </section>

      {/* Grid */}
      <section style={{ padding: "40px 0 80px" }}>
        <div className="container">
          <p style={{ fontSize: "13px", color: "var(--text-4)", marginBottom: "24px" }}>
            Hiển thị <strong style={{ color: "var(--text-2)" }}>{filtered.length}</strong> điểm đến
          </p>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(1,1fr)", gap: "20px" }} className="sm:grid-cols-2 lg:grid-cols-3">
            {filtered.map((dest) => (
              <div key={dest.name} className="card card-hover" style={{ overflow: "hidden" }}>
                {/* Card header */}
                <div style={{ padding: "28px 24px 20px", background: "linear-gradient(135deg, #FFF7ED, #F0F9FF)" }}>
                  <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "14px" }}>
                      <div style={{ width: 56, height: 56, borderRadius: "var(--r-lg)", background: "var(--surface)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: "32px", boxShadow: "var(--shadow-sm)" }}>
                        {dest.emoji}
                      </div>
                      <div>
                        <h3 style={{ fontSize: "17px", fontFamily: "var(--font-heading)", color: "var(--text)", marginBottom: "3px" }}>{dest.name}</h3>
                        <span className="badge badge-orange" style={{ fontSize: "11px" }}>{dest.tag}</span>
                      </div>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: "4px", flexShrink: 0 }}>
                      <Star size={13} fill="#F97316" color="#F97316" />
                      <span style={{ fontSize: "13px", fontWeight: 700, color: "var(--text)" }}>{dest.rating}</span>
                    </div>
                  </div>
                </div>

                {/* Card body */}
                <div style={{ padding: "16px 24px 20px" }}>
                  <p style={{ fontSize: "13px", color: "var(--text-3)", lineHeight: "1.65", marginBottom: "16px" }}>{dest.desc}</p>
                  <div style={{ display: "flex", gap: "16px", marginBottom: "18px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "5px", fontSize: "12px", color: "var(--text-4)" }}>
                      <Clock size={12} style={{ color: "var(--primary)" }} /> {dest.days}
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: "5px", fontSize: "12px", color: "var(--text-4)" }}>
                      <Sparkles size={12} style={{ color: "var(--primary)" }} /> {dest.trips.toLocaleString()} lịch trình
                    </div>
                    <div style={{ fontSize: "12px", color: "var(--text-4)" }}>📍 {dest.region}</div>
                  </div>
                  <Link href={`/plan?destination=${encodeURIComponent(dest.name)}`}
                    className="btn btn-primary btn-sm" style={{ width: "100%", justifyContent: "center", textDecoration: "none" }}>
                    <Sparkles size={13} /> Lên kế hoạch ngay <ArrowRight size={13} />
                  </Link>
                </div>
              </div>
            ))}
          </div>

          {filtered.length === 0 && (
            <div style={{ textAlign: "center", padding: "80px 0" }}>
              <div style={{ fontSize: "60px", marginBottom: "16px" }}>🔍</div>
              <h3 style={{ fontSize: "18px", color: "var(--text)", marginBottom: "8px" }}>Không tìm thấy điểm đến</h3>
              <p style={{ fontSize: "14px", color: "var(--text-3)" }}>Thử tìm kiếm với từ khóa khác</p>
            </div>
          )}
        </div>
      </section>

      <Footer />
    </div>
  );
}
