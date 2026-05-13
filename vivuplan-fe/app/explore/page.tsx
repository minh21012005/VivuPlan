"use client";

import { useMemo, useState } from "react";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { Button } from "@/components/ui/Button";
import { DestinationCard } from "@/components/travel/DestinationCard";
import { destinations, heroImages } from "@/lib/travel-data";
import { Filter, Search } from "lucide-react";

const regions = ["Tất cả", "Miền Bắc", "Miền Trung", "Miền Nam", "Tây Nguyên"];

export default function ExplorePage() {
  const [search, setSearch] = useState("");
  const [region, setRegion] = useState("Tất cả");
  const [sort, setSort] = useState("popular");

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();

    return destinations
      .filter((destination) => {
        const matchRegion = region === "Tất cả" || destination.region === region;
        const matchSearch =
          !keyword ||
          `${destination.name} ${destination.desc} ${destination.tag} ${destination.region}`.toLowerCase().includes(keyword);
        return matchRegion && matchSearch;
      })
      .sort((a, b) => (sort === "rating" ? b.rating - a.rating : b.trips - a.trips));
  }, [region, search, sort]);

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <Navbar />

      <section
        className="travel-hero"
        style={{
          minHeight: 460,
          backgroundImage: `linear-gradient(to right, rgba(15, 23, 42, 0.8) 0%, rgba(15, 23, 42, 0.4) 50%, rgba(15, 23, 42, 0.1) 100%), url(${heroImages.vietnamCoast})`,
          display: "flex",
          alignItems: "center"
        }}
      >
        <div className="container travel-hero-content">
          <h1 style={{
            fontSize: "clamp(32px, 5vw, 56px)",
            maxWidth: "900px",
            lineHeight: 1.1,
            marginBottom: "20px",
            fontWeight: 800,
            letterSpacing: "-0.03em",
            textShadow: "0 2px 10px rgba(0,0,0,0.2)"
          }}>
            Khám phá điểm đến, kiến tạo mọi hành trình
          </h1>
          <p style={{
            fontSize: "19px",
            maxWidth: "700px",
            opacity: 0.95,
            lineHeight: 1.6,
            fontWeight: 400,
            letterSpacing: "-0.01em"
          }}>
            Tìm cảm hứng cho chuyến đi tiếp theo của bạn và để VivuPlan lo liệu phần lịch trình chi tiết.
          </p>
        </div>
      </section>

      <section className="explore-filter">
        <div className="container explore-filter-inner">
          <div className="filter-group">
            <div className="filter-search">
              <Search size={16} />
              <input
                className="input"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Tìm Đà Lạt, biển, phố cổ..."
              />
            </div>
            {regions.map((item) => (
              <Button
                key={item}
                type="button"
                size="sm"
                variant={region === item ? "primary" : "secondary"}
                onClick={() => setRegion(item)}
              >
                {item}
              </Button>
            ))}
          </div>

          <label style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <Filter size={15} style={{ color: "var(--text-4)" }} />
            <select className="input" value={sort} onChange={(event) => setSort(event.target.value)} style={{ width: 170, padding: "8px 12px" }}>
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
          <div className="destination-grid">
            {filtered.map((destination) => (
              <DestinationCard key={destination.name} destination={destination} />
            ))}
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
