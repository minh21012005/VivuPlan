"use client";

import { useMemo, useState } from "react";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { SectionHeader } from "@/components/ui/SectionHeader";
import { DestinationCard } from "@/components/travel/DestinationCard";
import { destinations, heroImages } from "@/lib/travel-data";
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
  Wallet,
} from "lucide-react";

const quickDestinations = ["Đà Lạt", "Hạ Long", "Quy Nhơn", "Đà Nẵng", "Phú Quốc"];

const stats = [
  { value: "30s", label: "Tạo itinerary", icon: Clock },
  { value: "3-10N", label: "Lịch trình phổ biến", icon: CalendarDays },
  { value: "4", label: "Nhóm ngân sách", icon: Wallet },
  { value: "VN", label: "Tối ưu cho du lịch nội địa", icon: Compass },
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
    title: "Nền cho dữ liệu xác minh",
    desc: "MVP dùng cấu trúc itinerary rõ ràng để tiến tới verified POI, map và route optimization.",
  },
];

const steps = [
  "Chọn điểm đến bạn đã muốn đi",
  "Nhập số ngày, ngân sách và kiểu nhóm",
  "Nhận itinerary từng ngày và tinh chỉnh theo ý bạn",
];

export default function HomePage() {
  const [destination, setDestination] = useState("Đà Lạt");
  const selected = useMemo(
    () => destinations.find((item) => item.name === destination) ?? destinations[0],
    [destination]
  );

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <Navbar />

      <section
        className="travel-hero"
        style={{
          backgroundImage: `linear-gradient(90deg, rgba(4, 47, 46, 0.78), rgba(4, 47, 46, 0.32)), url(${heroImages.vietnamBay})`,
        }}
      >
        <div className="container travel-hero-content">
          <Badge tone="teal" style={{ background: "rgba(230,255,251,0.94)", marginBottom: 18 }}>
            <Sparkles size={13} /> AI travel planner cho Việt Nam
          </Badge>
          <h1>Lên lịch trình du lịch Việt Nam trong vài phút</h1>
          <p>
            VivuPlan biến điểm đến đã chọn thành kế hoạch rõ ràng: đi đâu, lúc nào, tốn bao nhiêu và nên di chuyển ra sao.
          </p>

          <div className="hero-search">
            <div className="hero-search-field">
              <Search size={18} />
              <input
                className="input"
                value={destination}
                onChange={(event) => setDestination(event.target.value)}
                placeholder="Bạn muốn đi đâu? Ví dụ: Quy Nhơn"
              />
            </div>
            <Button href={`/plan?destination=${encodeURIComponent(destination)}`}>
              Tạo lịch trình <ArrowRight size={16} />
            </Button>
          </div>

          <div className="hero-chip-row">
            {quickDestinations.map((item) => (
              <button
                key={item}
                onClick={() => setDestination(item)}
                className={`badge hero-chip${item === destination ? " active" : ""}`}
              >
                <MapPin size={12} /> {item}
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="floating-stats">
        <div className="container">
          <Card className="stats-grid">
            {stats.map(({ value, label, icon: Icon }) => (
              <div key={label} className="stat-cell">
                <Icon size={18} />
                <div className="stat-value">{value}</div>
                <div className="stat-label">{label}</div>
              </div>
            ))}
          </Card>
        </div>
      </section>

      <section className="section">
        <div className="container split-grid">
          <div>
            <SectionHeader
              eyebrow="Cách VivuPlan hoạt động"
              title="Tập trung vào kế hoạch sau khi bạn đã biết muốn đi đâu"
              description="Sản phẩm ưu tiên flow cốt lõi: nhập điểm đến, nhận lịch trình, lưu chuyến đi và chia sẻ."
            />
            <div className="check-list">
              {steps.map((step) => (
                <span key={step}>
                  <CheckCircle2 size={18} /> {step}
                </span>
              ))}
            </div>
          </div>

          <div className="feature-stack">
            {features.map(({ icon: Icon, title, desc }) => (
              <Card key={title} hover className="feature-row">
                <div className="feature-icon">
                  <Icon size={22} />
                </div>
                <div>
                  <h3>{title}</h3>
                  <p>{desc}</p>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </section>

      <section className="section" style={{ background: "var(--surface)" }}>
        <div className="container">
          <SectionHeader
            eyebrow="Điểm đến nổi bật"
            title="Bắt đầu từ nơi bạn muốn đến"
            action={
              <Button href="/explore" variant="secondary">
                Khám phá thêm <ArrowRight size={15} />
              </Button>
            }
          />
          <div className="destination-grid compact">
            {destinations.slice(0, 4).map((item) => (
              <DestinationCard key={item.name} destination={item} />
            ))}
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div
            className="travel-banner"
            style={{
              backgroundImage: `linear-gradient(90deg, rgba(15,118,110,0.9), rgba(2,132,199,0.55)), url(${selected.image})`,
            }}
          >
            <Badge tone="glass" style={{ marginBottom: 18 }}>
              <Sparkles size={13} /> Gợi ý nhanh
            </Badge>
            <h2>Tạo chuyến đi {selected.name} cho cuối tuần này</h2>
            <p>Nhập ngân sách và phong cách du lịch, VivuPlan sẽ tạo lịch trình từng ngày với chi phí ước tính.</p>
            <Button
              href={`/plan?destination=${encodeURIComponent(selected.name)}`}
              style={{ background: "white", color: "var(--primary)", boxShadow: "none" }}
            >
              Bắt đầu với {selected.name} <ArrowRight size={16} />
            </Button>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
