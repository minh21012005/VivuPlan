"use client";

import { useEffect, useState } from "react";
import Navbar from "@/components/layout/Navbar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { tripApi, type TripResponse } from "@/lib/api";
import { getDestinationImage, heroImages } from "@/lib/travel-data";
import { BarChart2, Clock, Eye, MapPin, Plus, Share2, Sparkles, Trash2, Wallet } from "lucide-react";
import { useRouter } from "next/navigation";

const statusInfo: Record<string, { label: string; tone: "green" | "blue" | "gray" }> = {
  COMPLETED: { label: "Hoàn thành", tone: "green" },
  PLANNED: { label: "Kế hoạch", tone: "blue" },
  DRAFT: { label: "Nháp", tone: "gray" },
};

function fmtBudget(value: number) {
  return value >= 1_000_000 ? `${(value / 1_000_000).toFixed(1)}tr ₫` : `${Math.round(value / 1000)}k ₫`;
}

function fmtDateRange(trip: TripResponse) {
  if (!trip.startDate || !trip.endDate) return null;
  return `${new Date(`${trip.startDate}T00:00:00`).toLocaleDateString("vi-VN")} - ${new Date(`${trip.endDate}T00:00:00`).toLocaleDateString("vi-VN")}`;
}

export default function DashboardPage() {
  const router = useRouter();
  const [trips, setTrips] = useState<TripResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [tab, setTab] = useState<"ALL" | "PLANNED" | "COMPLETED">("ALL");
  const [deleting, setDeleting] = useState<number | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (!localStorage.getItem("vp_token")) {
        router.push("/login");
        return;
      }
      tripApi
        .myTrips()
        .then(setTrips)
        .catch(() => setError("Không thể tải dữ liệu"))
        .finally(() => setLoading(false));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [router]);

  const filtered = tab === "ALL" ? trips : trips.filter((trip) => trip.status === tab);
  const uniqueDests = new Set(trips.map((trip) => trip.destination)).size;
  const totalDays = trips.reduce((sum, trip) => sum + trip.days, 0);

  const handleDelete = async (id: number) => {
    if (!confirm("Xóa lịch trình này?")) return;
    setDeleting(id);
    try {
      await tripApi.deleteTrip(id);
      setTrips((prev) => prev.filter((trip) => trip.id !== id));
    } catch {
      alert("Xóa thất bại");
    } finally {
      setDeleting(null);
    }
  };

  const handleToggle = async (id: number) => {
    try {
      const updated = await tripApi.toggleVisibility(id);
      setTrips((prev) => prev.map((trip) => (trip.id === id ? { ...trip, isPublic: updated.isPublic } : trip)));
    } catch {
      // Keep the current card state if the backend rejects the request.
    }
  };

  return (
    <div className="trip-library-page">
      <Navbar />

      <section
        className="trip-library-hero"
        style={{
          backgroundImage: `linear-gradient(90deg, rgba(4,47,46,0.82), rgba(2,132,199,0.36)), url(${heroImages.hoiAn})`,
        }}
      >
        <div className="container">
          <Badge tone="glass">
            <Sparkles size={13} /> Trip library
          </Badge>
          <h1>Những chuyến đi của bạn</h1>
          <p>Lưu, xem lại, công khai hoặc tiếp tục tối ưu các lịch trình đã tạo.</p>
          <Button href="/plan">
            <Plus size={16} /> Tạo lịch trình mới
          </Button>
        </div>
      </section>

      <main className="container trip-library-main">
        <div className="dashboard-stat-grid">
          {[
            { label: "Tổng chuyến", value: loading ? "..." : trips.length, icon: MapPin },
            { label: "Ngày du lịch", value: loading ? "..." : totalDays, icon: Clock },
            { label: "Điểm đến", value: loading ? "..." : uniqueDests, icon: Wallet },
            { label: "Hoàn thành", value: loading ? "..." : trips.filter((trip) => trip.status === "COMPLETED").length, icon: BarChart2 },
          ].map(({ label, value, icon: Icon }) => (
            <Card key={label} className="dashboard-stat">
              <Icon size={18} />
              <div>
                <strong>{value}</strong>
                <span>{label}</span>
              </div>
            </Card>
          ))}
        </div>

        <div className="library-toolbar">
          <div>
            <h2>Thư viện lịch trình</h2>
            <p>{filtered.length} lịch trình đang hiển thị</p>
          </div>
          <div className="tab-bar library-tabs">
            {(["ALL", "PLANNED", "COMPLETED"] as const).map((item) => (
              <button key={item} onClick={() => setTab(item)} className={`tab-item${tab === item ? " active" : ""}`}>
                {item === "ALL" ? "Tất cả" : item === "PLANNED" ? "Kế hoạch" : "Hoàn thành"}
              </button>
            ))}
          </div>
        </div>

        {loading && (
          <Card className="library-state">
            <div className="spinner" />
            <p>Đang tải lịch trình...</p>
          </Card>
        )}

        {error && !loading && (
          <Card className="library-state">
            <p style={{ color: "#DC2626" }}>{error}</p>
            <Button variant="secondary" size="sm" onClick={() => window.location.reload()}>
              Thử lại
            </Button>
          </Card>
        )}

        {!loading && !error && filtered.length === 0 && (
          <Card className="library-empty">
            <div style={{ backgroundImage: `url(${heroImages.vietnamBay})` }} />
            <section>
              <Badge tone="teal">Chưa có lịch trình</Badge>
              <h2>Bắt đầu với chuyến đi đầu tiên</h2>
              <p>Chọn điểm đến, nhập ngân sách và để VivuPlan tạo itinerary thực tế cho bạn.</p>
              <Button href="/plan">
                <Sparkles size={15} /> Lập kế hoạch ngay
              </Button>
            </section>
          </Card>
        )}

        {!loading && !error && filtered.length > 0 && (
          <div className="trip-card-grid">
            {filtered.map((trip) => (
              <TripCard
                key={trip.id}
                trip={trip}
                deleting={deleting === trip.id}
                onDelete={() => handleDelete(trip.id)}
                onToggle={() => handleToggle(trip.id)}
              />
            ))}
          </div>
        )}
      </main>
    </div>
  );
}

function TripCard({
  trip,
  deleting,
  onDelete,
  onToggle,
}: {
  trip: TripResponse;
  deleting: boolean;
  onDelete: () => void;
  onToggle: () => void;
}) {
  const status = statusInfo[trip.status] ?? statusInfo.DRAFT;

  return (
    <article className="trip-card">
      <div className="trip-card-media" style={{ backgroundImage: `linear-gradient(180deg, rgba(0,0,0,0.04), rgba(0,0,0,0.46)), url(${getDestinationImage(trip.destination)})` }}>
        <div>
          <Badge tone={status.tone}>{status.label}</Badge>
          {trip.isPublic && <Badge tone="teal">Công khai</Badge>}
        </div>
        <h3>{trip.destination}</h3>
      </div>
      <div className="trip-card-body">
        <p className="trip-route-text">{trip.departure || "Điểm xuất phát"} → {trip.destination}</p>
        <div className="trip-meta-grid">
          {fmtDateRange(trip) && (
            <span>
              <Clock size={13} /> {fmtDateRange(trip)}
            </span>
          )}
          <span>
            <Clock size={13} /> {trip.days}N{trip.days - 1}Đ
          </span>
          <span>
            <Wallet size={13} /> {fmtBudget(trip.budgetPerPerson)}
          </span>
          <span>
            <Eye size={13} /> {trip.viewCount} lượt xem
          </span>
        </div>
        <div className="trip-card-actions">
          <Button href={`/itinerary/${trip.id}`} variant="secondary" size="sm">
            <Eye size={13} /> Xem
          </Button>
          <Button type="button" variant="ghost" size="icon" onClick={onToggle} title={trip.isPublic ? "Ẩn lịch trình" : "Công khai lịch trình"}>
            <Share2 size={15} />
          </Button>
          <Button type="button" variant="ghost" size="icon" onClick={onDelete} disabled={deleting} title="Xóa lịch trình">
            <Trash2 size={15} />
          </Button>
        </div>
      </div>
    </article>
  );
}
