"use client";

import { useEffect, useRef, useState } from "react";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { SectionHeader } from "@/components/ui/SectionHeader";
import { DestinationCard } from "@/components/travel/DestinationCard";
import { destinations, heroImages } from "@/lib/travel-data";
import {
  ArrowRight,
  CheckCircle2,
  MapPin,
  Route,
  Search,
  ShieldCheck,
  Wallet,
} from "lucide-react";

const quickDestinations = ["Đà Lạt", "Hạ Long", "Quy Nhơn", "Đà Nẵng", "Phú Quốc"];
const departureSuggestions = ["Hà Nội", "TP.HCM", "Đà Nẵng", "Hải Phòng", "Cần Thơ", "Nha Trang", "Huế", "Vinh"];

function normalizeSearch(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
}

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
    title: "Địa điểm đã xác thực",
    desc: "Mọi điểm đến và lộ trình đều được kiểm tra tính thực tế, giúp bạn an tâm tận hưởng hành trình.",
  },
];

const steps = [
  "Nhập điểm xuất phát và ràng buộc chuyến đi",
  "Chọn điểm đến hoặc để AI gợi ý",
  "Nhận lịch trình chi tiết từng ngày và tinh chỉnh theo ý bạn",
];

export default function HomePage() {
  const [departure, setDeparture] = useState("");
  const [destination, setDestination] = useState("");
  const [focusedField, setFocusedField] = useState<"departure" | "destination" | null>(null);
  const [pendingHref, setPendingHref] = useState<string | null>(null);
  const blurTimer = useRef<number | null>(null);
  const navigationResetTimer = useRef<number | null>(null);
  const departureQuery = normalizeSearch(departure);
  const destinationQuery = normalizeSearch(destination);
  const departureMatches = departureQuery
    ? departureSuggestions.filter((item) => normalizeSearch(item).includes(departureQuery)).slice(0, 6)
    : departureSuggestions.slice(0, 6);
  const destinationMatches = destinations
    .map((item) => item.name)
    .filter((item) => !destinationQuery || normalizeSearch(item).includes(destinationQuery))
    .slice(0, 6);
  const planHref = `/plan?departure=${encodeURIComponent(departure)}&destination=${encodeURIComponent(destination)}`;

  useEffect(() => {
    return () => {
      if (blurTimer.current) window.clearTimeout(blurTimer.current);
      if (navigationResetTimer.current) window.clearTimeout(navigationResetTimer.current);
    };
  }, []);

  const focusField = (field: "departure" | "destination") => {
    if (blurTimer.current) {
      window.clearTimeout(blurTimer.current);
      blurTimer.current = null;
    }
    setFocusedField(field);
  };

  const closeSuggestionsSoon = () => {
    if (blurTimer.current) window.clearTimeout(blurTimer.current);
    blurTimer.current = window.setTimeout(() => {
      setFocusedField(null);
      blurTimer.current = null;
    }, 140);
  };

  const beginNavigation = (href: string) => {
    if (pendingHref) return;
    setPendingHref(href);
    setFocusedField(null);
    if (navigationResetTimer.current) window.clearTimeout(navigationResetTimer.current);
    navigationResetTimer.current = window.setTimeout(() => {
      setPendingHref((current) => (current === href ? null : current));
    }, 12000);
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <Navbar />

      <section
        className="travel-hero"
        style={{
          minHeight: "min(620px, 90vh)",
          display: "flex",
          alignItems: "center",
          backgroundImage: `linear-gradient(rgba(15, 23, 42, 0.6), rgba(15, 23, 42, 0.2)), url(${heroImages.vietnamBay})`,
        }}
      >
        <div className="container" style={{ display: "flex", justifyContent: "center", width: "100%" }}>
          <div className="travel-hero-content" style={{ maxWidth: "1100px", width: "100%", textAlign: "center", display: "flex", flexDirection: "column", alignItems: "center" }}>
          <h1>Lập kế hoạch du lịch <br /> Thông minh hơn cùng AI</h1>
          <p>
            VivuPlan giúp bạn biến mọi ý tưởng thành một hành trình chi tiết: <br /> đi đâu, lúc nào và tốn bao nhiêu – chỉ trong vài giây.
          </p>

          <div className="hero-search hero-planner" style={{ width: "100%" }}>
            <div className="hero-search-field">
              <MapPin size={18} />
              <input
                className="input"
                value={departure}
                onChange={(event) => setDeparture(event.target.value)}
                onFocus={() => focusField("departure")}
                onBlur={closeSuggestionsSoon}
                placeholder="Đi từ đâu? (VD: Hà Nội)"
              />
              {focusedField === "departure" && departureMatches.length > 0 && (
                <div className="hero-suggestions">
                  {departureMatches.map((item) => (
                    <button key={item} type="button" onMouseDown={() => { setDeparture(item); setFocusedField(null); }}>
                      <MapPin size={13} /> {item}
                    </button>
                  ))}
                </div>
              )}
            </div>
            <div className="hero-search-field">
              <Search size={18} />
              <input
                className="input"
                value={destination}
                onChange={(event) => setDestination(event.target.value)}
                onFocus={() => focusField("destination")}
                onBlur={closeSuggestionsSoon}
                placeholder="Bạn muốn đi đâu? (VD: Quy Nhơn)"
              />
              {focusedField === "destination" && destinationMatches.length > 0 && (
                <div className="hero-suggestions">
                  {destinationMatches.map((item) => (
                    <button key={item} type="button" onMouseDown={() => { setDestination(item); setFocusedField(null); }}>
                      <MapPin size={13} /> {item}
                    </button>
                  ))}
                </div>
              )}
            </div>
            <Button
              href={planHref}
              className="home-loading-link"
              aria-busy={pendingHref === planHref}
              aria-disabled={pendingHref !== null}
              onClick={(event) => {
                if (pendingHref) {
                  event.preventDefault();
                  return;
                }
                if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
                beginNavigation(planHref);
              }}
            >
              {pendingHref === planHref ? <span className="spinner spinner-inline spinner-on-primary" /> : null}
              {pendingHref === planHref ? "Đang mở..." : "Tạo lịch trình"} {pendingHref === planHref ? null : <ArrowRight size={16} />}
            </Button>
          </div>
          <div className="hero-chip-row">
            {quickDestinations.map((item) => (
              <span key={item} className="badge hero-chip">
                <MapPin size={12} /> {item}
              </span>
            ))}
          </div>
        </div>
      </div>
    </section>

      <section className="section">
        <div className="container split-grid">
          <div>
            <SectionHeader
              eyebrow="Cách VivuPlan hoạt động"
              title="Linh hoạt giữa chọn điểm đến và gợi ý bằng AI"
              description="Bạn có thể nhập điểm đến cụ thể, hoặc chỉ cung cấp điểm xuất phát, thời gian, ngân sách để VivuPlan gợi ý điểm đến phù hợp."
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
            title="Điểm đến hàng đầu"
            action={
              <Button
                href="/explore"
                variant="secondary"
                className="home-loading-link"
                aria-busy={pendingHref === "/explore"}
                aria-disabled={pendingHref !== null}
                onClick={(event) => {
                  if (pendingHref) {
                    event.preventDefault();
                    return;
                  }
                  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
                  beginNavigation("/explore");
                }}
              >
                {pendingHref === "/explore" ? <span className="spinner spinner-inline" /> : null}
                {pendingHref === "/explore" ? "Đang mở..." : "Khám phá thêm"} {pendingHref === "/explore" ? null : <ArrowRight size={15} />}
              </Button>
            }
          />
          <div className="destination-grid compact">
            {destinations.slice(0, 4).map((item) => {
              const href = `/plan?destination=${encodeURIComponent(item.name)}`;
              return (
                <DestinationCard
                  key={item.name}
                  destination={item}
                  loading={pendingHref === href}
                  disabled={pendingHref !== null}
                  onNavigate={() => beginNavigation(href)}
                />
              );
            })}
          </div>
        </div>
      </section>


      <Footer />
    </div>
  );
}
