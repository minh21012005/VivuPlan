"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import {
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  Clock,
  Compass,
  MapPin,
  Route,
  Search,
  ShieldCheck,
  Sparkles,
  Star,
  Wallet,
} from "lucide-react";

const heroImage =
  "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1800&q=85";

const destinations = [
  {
    name: "Đà Lạt",
    tag: "Rừng thông, cà phê và khí hậu mát",
    days: "3-4 ngày",
    image: "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=900&q=80",
  },
  {
    name: "Hạ Long",
    tag: "Vịnh biển, du thuyền và hang động",
    days: "2-3 ngày",
    image: "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=900&q=80",
  },
  {
    name: "Hội An",
    tag: "Phố cổ, ẩm thực và đèn lồng",
    days: "2-3 ngày",
    image: "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=900&q=80",
  },
  {
    name: "Phú Quốc",
    tag: "Biển xanh, hoàng hôn và nghỉ dưỡng",
    days: "3-5 ngày",
    image: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=80",
  },
];

const features = [
  {
    icon: Route,
    title: "Lịch trình thực tế",
    desc: "Sắp xếp hoạt động theo thời gian, khu vực và nhịp di chuyển hợp lý cho từng ngày.",
  },
  {
    icon: Wallet,
    title: "Ngân sách rõ ràng",
    desc: "Ước tính chi phí ăn uống, lưu trú, di chuyển và tham quan theo tổng ngân sách của bạn.",
  },
  {
    icon: ShieldCheck,
    title: "Giảm gợi ý ảo",
    desc: "MVP ưu tiên cấu trúc dữ liệu rõ ràng để tiến tới verified POI và Google Maps trong giai đoạn tiếp theo.",
  },
];

const steps = [
  "Chọn điểm đến",
  "Nhập số ngày, ngân sách, nhóm đi",
  "Nhận itinerary và chỉnh sửa theo ý bạn",
];

export default function HomePage() {
  const [destination, setDestination] = useState("Đà Lạt");
  const selected = useMemo(
    () => destinations.find((d) => d.name === destination) ?? destinations[0],
    [destination]
  );

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <Navbar />

      <section
        style={{
          minHeight: "min(760px, 100vh)",
          paddingTop: "64px",
          position: "relative",
          overflow: "hidden",
          backgroundImage: `linear-gradient(90deg, rgba(4, 47, 46, 0.78), rgba(4, 47, 46, 0.32)), url(${heroImage})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          color: "white",
        }}
      >
        <div className="container" style={{ paddingTop: "96px", paddingBottom: "56px" }}>
          <div style={{ maxWidth: "760px" }}>
            <div className="badge badge-teal" style={{ background: "rgba(230,255,251,0.94)", marginBottom: 18 }}>
              <Sparkles size={13} /> AI travel planner cho Việt Nam
            </div>
            <h1
              style={{
                color: "white",
                fontSize: "clamp(42px, 7vw, 76px)",
                fontWeight: 900,
                maxWidth: "780px",
                letterSpacing: 0,
                marginBottom: 20,
              }}
            >
              Lên lịch trình du lịch Việt Nam trong vài phút
            </h1>
            <p style={{ fontSize: 18, lineHeight: 1.75, maxWidth: 620, color: "rgba(255,255,255,0.88)", marginBottom: 30 }}>
              VivuPlan giúp bạn biến điểm đến đã chọn thành một kế hoạch rõ ràng: đi đâu, lúc nào, tốn bao nhiêu và nên di chuyển ra sao.
            </p>

            <div
              style={{
                display: "grid",
                gridTemplateColumns: "1fr auto",
                gap: 10,
                maxWidth: 680,
                background: "rgba(255,255,255,0.94)",
                padding: 10,
                borderRadius: "var(--r-xl)",
                boxShadow: "0 20px 60px rgba(15,23,42,0.24)",
              }}
            >
              <div style={{ position: "relative" }}>
                <Search size={18} style={{ position: "absolute", left: 14, top: "50%", transform: "translateY(-50%)", color: "var(--primary)" }} />
                <input
                  className="input"
                  value={destination}
                  onChange={(e) => setDestination(e.target.value)}
                  placeholder="Bạn muốn đi đâu? Ví dụ: Quy Nhơn"
                  style={{ height: 48, paddingLeft: 44, border: "none", background: "transparent", boxShadow: "none" }}
                />
              </div>
              <Link href={`/plan?destination=${encodeURIComponent(destination)}`} className="btn btn-primary" style={{ minHeight: 48 }}>
                Tạo lịch trình <ArrowRight size={16} />
              </Link>
            </div>

            <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 16 }}>
              {["Đà Lạt", "Hạ Long", "Quy Nhơn", "Đà Nẵng", "Phú Quốc"].map((item) => (
                <button
                  key={item}
                  onClick={() => setDestination(item)}
                  className="badge"
                  style={{
                    cursor: "pointer",
                    background: item === destination ? "white" : "rgba(255,255,255,0.18)",
                    border: "1px solid rgba(255,255,255,0.36)",
                    color: item === destination ? "var(--primary)" : "white",
                  }}
                >
                  <MapPin size={12} /> {item}
                </button>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section style={{ marginTop: "-58px", position: "relative", zIndex: 2 }}>
        <div className="container">
          <div
            className="card home-stats-grid"
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(4, 1fr)",
              gap: 0,
              overflow: "hidden",
            }}
          >
            {[
              { value: "30s", label: "Tạo itinerary", icon: Clock },
              { value: "3-10N", label: "Lịch trình phổ biến", icon: CalendarDays },
              { value: "4", label: "Nhóm ngân sách", icon: Wallet },
              { value: "VN", label: "Tối ưu cho du lịch nội địa", icon: Compass },
            ].map(({ value, label, icon: Icon }) => (
              <div key={label} style={{ padding: "24px 20px", borderRight: "1px solid var(--border)" }}>
                <Icon size={18} style={{ color: "var(--primary)", marginBottom: 10 }} />
                <div style={{ fontFamily: "var(--font-heading)", fontSize: 26, fontWeight: 850, color: "var(--text)" }}>{value}</div>
                <div style={{ fontSize: 13, color: "var(--text-3)" }}>{label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="home-split-grid" style={{ display: "grid", gridTemplateColumns: "0.9fr 1.1fr", gap: 44, alignItems: "center" }}>
            <div>
              <div className="badge badge-blue" style={{ marginBottom: 14 }}>Cách VivuPlan hoạt động</div>
              <h2 style={{ fontSize: "clamp(30px, 4vw, 44px)", marginBottom: 16 }}>
                Tập trung vào kế hoạch sau khi bạn đã biết muốn đi đâu
              </h2>
              <p style={{ color: "var(--text-3)", fontSize: 16, lineHeight: 1.75, marginBottom: 24 }}>
                MVP hiện ưu tiên flow cốt lõi: nhập điểm đến, nhận lịch trình, lưu chuyến đi và chia sẻ. Đây là nền để bổ sung POI database, route optimization và editing sâu hơn.
              </p>
              <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                {steps.map((step) => (
                  <div key={step} style={{ display: "flex", gap: 10, alignItems: "center", color: "var(--text-2)" }}>
                    <CheckCircle2 size={18} style={{ color: "var(--accent)", flexShrink: 0 }} /> {step}
                  </div>
                ))}
              </div>
            </div>
            <div style={{ display: "grid", gap: 16 }}>
              {features.map(({ icon: Icon, title, desc }) => (
                <div key={title} className="card card-hover" style={{ padding: 24, display: "flex", gap: 18 }}>
                  <div style={{ width: 48, height: 48, borderRadius: "var(--r-lg)", background: "var(--primary-light)", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Icon size={22} style={{ color: "var(--primary)" }} />
                  </div>
                  <div>
                    <h3 style={{ fontSize: 17, marginBottom: 6 }}>{title}</h3>
                    <p style={{ color: "var(--text-3)", fontSize: 14, lineHeight: 1.7 }}>{desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="section" style={{ background: "var(--surface)" }}>
        <div className="container">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "end", gap: 20, marginBottom: 28 }}>
            <div>
              <div className="badge badge-teal" style={{ marginBottom: 12 }}>Điểm đến nổi bật</div>
              <h2 style={{ fontSize: "clamp(28px, 4vw, 42px)" }}>Bắt đầu từ nơi bạn muốn đến</h2>
            </div>
            <Link href="/explore" className="btn btn-secondary">
              Khám phá thêm <ArrowRight size={15} />
            </Link>
          </div>
          <div className="home-destination-grid" style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 18 }}>
            {destinations.map((dest) => (
              <Link key={dest.name} href={`/plan?destination=${encodeURIComponent(dest.name)}`} style={{ textDecoration: "none" }}>
                <article className="card card-hover" style={{ overflow: "hidden", height: "100%" }}>
                  <div style={{ height: 190, backgroundImage: `url(${dest.image})`, backgroundSize: "cover", backgroundPosition: "center" }} />
                  <div style={{ padding: 18 }}>
                    <h3 style={{ fontSize: 18, marginBottom: 6 }}>{dest.name}</h3>
                    <p style={{ color: "var(--text-3)", fontSize: 13, minHeight: 42 }}>{dest.tag}</p>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 16 }}>
                      <span className="badge badge-blue"><Clock size={12} /> {dest.days}</span>
                      <span style={{ color: "var(--primary)", fontWeight: 700, fontSize: 13 }}>Lên kế hoạch</span>
                    </div>
                  </div>
                </article>
              </Link>
            ))}
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div
            style={{
              borderRadius: "var(--r-2xl)",
              minHeight: 320,
              padding: "56px 40px",
              backgroundImage: `linear-gradient(90deg, rgba(15,118,110,0.9), rgba(2,132,199,0.55)), url(${selected.image})`,
              backgroundSize: "cover",
              backgroundPosition: "center",
              color: "white",
            }}
          >
            <div style={{ maxWidth: 560 }}>
              <div className="badge" style={{ background: "rgba(255,255,255,0.18)", color: "white", border: "1px solid rgba(255,255,255,0.3)", marginBottom: 18 }}>
                <Star size={13} fill="currentColor" /> Gợi ý nhanh
              </div>
              <h2 style={{ color: "white", fontSize: "clamp(30px, 4vw, 46px)", marginBottom: 14 }}>
                Tạo chuyến đi {selected.name} cho cuối tuần này
              </h2>
              <p style={{ color: "rgba(255,255,255,0.86)", fontSize: 16, lineHeight: 1.7, marginBottom: 24 }}>
                Nhập ngân sách và phong cách du lịch, VivuPlan sẽ tạo lịch trình từng ngày với chi phí ước tính.
              </p>
              <Link href={`/plan?destination=${encodeURIComponent(selected.name)}`} className="btn btn-primary" style={{ background: "white", color: "var(--primary)", boxShadow: "none" }}>
                Bắt đầu với {selected.name} <ArrowRight size={16} />
              </Link>
            </div>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
