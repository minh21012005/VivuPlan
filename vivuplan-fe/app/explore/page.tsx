"use client";

import { useMemo, useState } from "react";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { DestinationCard } from "@/components/travel/DestinationCard";
import { destinations, heroImages } from "@/lib/travel-data";
import { Filter, MapPin, Search } from "lucide-react";

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
          minHeight: 430,
          backgroundImage: `linear-gradient(90deg, rgba(4,47,46,0.78), rgba(2,132,199,0.34)), url(${heroImages.vietnamCoast})`,
        }}
      >
        <div className="container travel-hero-content" style={{ paddingTop: 72, paddingBottom: 72 }}>
          <Badge tone="glass" style={{ marginBottom: 16 }}>
            <MapPin size={13} /> Khám phá Việt Nam
          </Badge>
          <h1 style={{ fontSize: "clamp(34px, 5vw, 58px)" }}>Chọn cảm hứng, VivuPlan lo phần lịch trình</h1>
          <p>Dành cho lúc bạn cần gợi ý điểm đến trước khi bắt đầu lập kế hoạch chi tiết.</p>
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
