"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { ArrowRight, Clock, Filter, MapPin, Search, Sparkles, Star } from "lucide-react";

const heroImage = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1800&q=85";

const regions = ["Tất cả", "Miền Bắc", "Miền Trung", "Miền Nam", "Tây Nguyên"];

const destinations = [
  {
    name: "Đà Lạt",
    region: "Tây Nguyên",
    tag: "Thành phố hoa",
    days: "3-5 ngày",
    rating: 4.9,
    trips: 8420,
    image: "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=900&q=80",
    desc: "Khí hậu mát, rừng thông, cà phê view đồi và những cung đường nhẹ nhàng cho nhóm bạn.",
  },
  {
    name: "Hạ Long",
    region: "Miền Bắc",
    tag: "Kỳ quan biển đảo",
    days: "2-4 ngày",
    rating: 4.8,
    trips: 12300,
    image: "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=900&q=80",
    desc: "Vịnh biển, du thuyền, hang động và lịch trình phù hợp cho gia đình hoặc cặp đôi.",
  },
  {
    name: "Hội An",
    region: "Miền Trung",
    tag: "Phố cổ đèn lồng",
    days: "2-3 ngày",
    rating: 4.9,
    trips: 9870,
    image: "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=900&q=80",
    desc: "Phố cổ, ẩm thực địa phương, biển An Bàng và nhịp đi bộ thư thái.",
  },
  {
    name: "Phú Quốc",
    region: "Miền Nam",
    tag: "Đảo ngọc",
    days: "3-5 ngày",
    rating: 4.7,
    trips: 11200,
    image: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=80",
    desc: "Biển xanh, hoàng hôn, hải sản và các resort phù hợp nghỉ dưỡng.",
  },
  {
    name: "Sapa",
    region: "Miền Bắc",
    tag: "Mây núi Tây Bắc",
    days: "3-4 ngày",
    rating: 4.8,
    trips: 7650,
    image: "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=900&q=80",
    desc: "Ruộng bậc thang, bản làng, trekking nhẹ và trải nghiệm khí hậu vùng cao.",
  },
  {
    name: "Nha Trang",
    region: "Miền Trung",
    tag: "Thiên đường biển",
    days: "3-5 ngày",
    rating: 4.6,
    trips: 10500,
    image: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=80",
    desc: "Bãi biển dài, đảo gần bờ, hải sản và các hoạt động biển dễ sắp lịch.",
  },
  {
    name: "Đà Nẵng",
    region: "Miền Trung",
    tag: "Thành phố biển",
    days: "3-4 ngày",
    rating: 4.8,
    trips: 13400,
    image: "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=900&q=80",
    desc: "Biển Mỹ Khê, Sơn Trà, Bà Nà và lịch trình dễ kết hợp Hội An.",
  },
  {
    name: "Quy Nhơn",
    region: "Miền Trung",
    tag: "Biển yên bình",
    days: "3-4 ngày",
    rating: 4.9,
    trips: 5300,
    image: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=80",
    desc: "Kỳ Co, Eo Gió, tháp Chăm và nhịp đi biển thoải mái hơn các điểm quá đông.",
  },
];

export default function ExplorePage() {
  const [search, setSearch] = useState("");
  const [region, setRegion] = useState("Tất cả");
  const [sort, setSort] = useState("popular");

  const filtered = useMemo(() => {
    return destinations
      .filter((d) => {
        const matchRegion = region === "Tất cả" || d.region === region;
        const keyword = search.trim().toLowerCase();
        const matchSearch = !keyword || `${d.name} ${d.desc} ${d.tag}`.toLowerCase().includes(keyword);
        return matchRegion && matchSearch;
      })
      .sort((a, b) => (sort === "rating" ? b.rating - a.rating : b.trips - a.trips));
  }, [region, search, sort]);

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <Navbar />

      <section
        style={{
          paddingTop: 64,
          backgroundImage: `linear-gradient(90deg, rgba(4,47,46,0.78), rgba(2,132,199,0.34)), url(${heroImage})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          color: "white",
        }}
      >
        <div className="container" style={{ paddingTop: 72, paddingBottom: 72 }}>
          <div style={{ maxWidth: 680 }}>
            <div className="badge" style={{ background: "rgba(255,255,255,0.18)", color: "white", border: "1px solid rgba(255,255,255,0.28)", marginBottom: 16 }}>
              <MapPin size={13} /> Khám phá Việt Nam
            </div>
            <h1 style={{ color: "white", fontSize: "clamp(34px, 5vw, 58px)", fontWeight: 900, marginBottom: 14 }}>
              Chọn cảm hứng, VivuPlan lo phần lịch trình
            </h1>
            <p style={{ color: "rgba(255,255,255,0.86)", fontSize: 17, lineHeight: 1.75 }}>
              Dành cho lúc bạn cần gợi ý điểm đến trước khi bắt đầu lập kế hoạch chi tiết.
            </p>
          </div>
        </div>
      </section>

      <section style={{ position: "sticky", top: 64, zIndex: 40, background: "rgba(255,255,255,0.94)", backdropFilter: "blur(14px)", borderBottom: "1px solid var(--border)" }}>
        <div className="container" style={{ paddingTop: 16, paddingBottom: 16, display: "grid", gridTemplateColumns: "1fr auto", gap: 14, alignItems: "center" }}>
          <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
            <div style={{ position: "relative", minWidth: 260, flex: "1 1 280px" }}>
              <Search size={16} style={{ position: "absolute", left: 13, top: "50%", transform: "translateY(-50%)", color: "var(--primary)" }} />
              <input className="input" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Tìm Đà Lạt, biển, phố cổ..." style={{ paddingLeft: 38 }} />
            </div>
            {regions.map((item) => (
              <button key={item} onClick={() => setRegion(item)} className={region === item ? "btn btn-primary btn-sm" : "btn btn-secondary btn-sm"}>
                {item}
              </button>
            ))}
          </div>
          <label style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <Filter size={15} style={{ color: "var(--text-4)" }} />
            <select className="input" value={sort} onChange={(e) => setSort(e.target.value)} style={{ width: 170, padding: "8px 12px" }}>
              <option value="popular">Phổ biến nhất</option>
              <option value="rating">Đánh giá cao</option>
            </select>
          </label>
        </div>
      </section>

      <section style={{ padding: "40px 0 84px" }}>
        <div className="container">
          <p style={{ color: "var(--text-3)", marginBottom: 22 }}>
            Hiển thị <strong style={{ color: "var(--text)" }}>{filtered.length}</strong> điểm đến
          </p>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 22 }} className="destination-grid">
            {filtered.map((dest) => (
              <article key={dest.name} className="card card-hover" style={{ overflow: "hidden" }}>
                <div style={{ height: 220, backgroundImage: `linear-gradient(180deg, rgba(0,0,0,0.05), rgba(0,0,0,0.35)), url(${dest.image})`, backgroundSize: "cover", backgroundPosition: "center", position: "relative" }}>
                  <span className="badge" style={{ position: "absolute", left: 14, top: 14, background: "rgba(255,255,255,0.92)", color: "var(--primary)" }}>
                    {dest.region}
                  </span>
                </div>
                <div style={{ padding: 20 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: 12, marginBottom: 8 }}>
                    <div>
                      <h2 style={{ fontSize: 20, marginBottom: 4 }}>{dest.name}</h2>
                      <span className="badge badge-teal">{dest.tag}</span>
                    </div>
                    <span style={{ display: "flex", alignItems: "center", gap: 4, fontWeight: 800, color: "var(--text)" }}>
                      <Star size={14} fill="#FBBF24" color="#FBBF24" /> {dest.rating}
                    </span>
                  </div>
                  <p style={{ color: "var(--text-3)", fontSize: 14, lineHeight: 1.7, minHeight: 72 }}>{dest.desc}</p>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 18 }}>
                    <span style={{ color: "var(--text-4)", fontSize: 13, display: "flex", alignItems: "center", gap: 5 }}>
                      <Clock size={13} style={{ color: "var(--primary)" }} /> {dest.days}
                    </span>
                    <Link href={`/plan?destination=${encodeURIComponent(dest.name)}`} className="btn btn-primary btn-sm">
                      <Sparkles size={13} /> Lên kế hoạch <ArrowRight size={13} />
                    </Link>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
