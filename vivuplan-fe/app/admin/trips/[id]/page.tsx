"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  AlertTriangle,
  ArrowLeft,
  Calendar,
  Clock,
  ExternalLink,
  Eye,
  ListChecks,
  MapPin,
  Navigation,
  ShieldCheck,
  Sparkles,
  Star,
  Users,
  Wallet,
} from "lucide-react";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { useRequireAuth } from "@/hooks/useRequireAuth";
import { adminApi, type ActivityResponse, type AdminUserSummary, type TripResponse } from "@/lib/api";

function isAdmin(user: { role?: string; roles?: string[] } | null | undefined) {
  return user?.role === "ADMIN" || user?.roles?.includes("ADMIN");
}

function formatCurrency(value?: number | null) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value ?? 0);
}

function formatDate(value?: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(`${value}T00:00:00`));
}

function formatDateTime(value?: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function labelFromEnum(value?: string | null) {
  if (!value) return "-";
  const labels: Record<string, string> = {
    ADVENTURE: "Phiêu lưu",
    RELAXING: "Nghỉ dưỡng",
    CULTURAL: "Văn hóa",
    FOODIE: "Ẩm thực",
    SOLO: "Một mình",
    COUPLE: "Cặp đôi",
    FRIENDS: "Nhóm bạn",
    FAMILY: "Gia đình",
    CAR: "Ô tô",
    MOTORBIKE: "Xe máy",
    BUS: "Xe khách",
    TRAIN: "Tàu hỏa",
    PLANE: "Máy bay",
    FLIGHT: "Máy bay",
    MIXED: "Linh hoạt",
    WALKING: "Đi bộ",
    PLANNED: "Đã tạo",
    DRAFT: "Bản nháp",
    COMPLETED: "Hoàn thành",
  };
  return labels[value] ?? value;
}

function transportLabel(value?: string | null) {
  if (value === "MIXED") return "Để AI chọn";
  return labelFromEnum(value);
}

function activityTypeLabel(value?: string | null) {
  const labels: Record<string, string> = {
    FOOD: "Ăn uống",
    CAFE: "Cà phê",
    ATTRACTION: "Điểm đến",
    ACTIVITY: "Hoạt động",
    TRANSPORT: "Di chuyển",
    ACCOMMODATION: "Lưu trú",
  };
  return value ? labels[value] ?? value : "Hoạt động";
}

function budgetTarget(trip: TripResponse) {
  if (trip.budgetMode === "TOTAL" && trip.budgetTotal) return trip.budgetTotal;
  return trip.budgetPerPerson * Math.max(1, trip.travelerCount ?? 1);
}

function mapsUrl(activity: ActivityResponse) {
  if (activity.latitude != null && activity.longitude != null) {
    return `https://www.google.com/maps/search/?api=1&query=${activity.latitude},${activity.longitude}`;
  }
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${activity.name} ${activity.location}`)}`;
}

function MetaCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="admin-trip-meta-card">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function ActivityRow({ activity }: { activity: ActivityResponse }) {
  return (
    <article className="admin-trip-detail-activity">
      <div className="admin-trip-activity-clock">
        <Clock size={14} />
        <span>{activity.time || "--:--"}</span>
      </div>
      <div className="admin-trip-activity-main">
        <div className="admin-trip-activity-title-row">
          <div>
            <span className="admin-trip-activity-type">{activityTypeLabel(activity.type)}</span>
            <h4>{activity.name}</h4>
          </div>
          <strong>{formatCurrency(activity.estimatedCost)}</strong>
        </div>
        <div className="admin-trip-activity-info">
          <span><MapPin size={13} /> {activity.location || "Chưa có địa điểm"}</span>
          <span><Clock size={13} /> {activity.duration || "Chưa rõ thời lượng"}</span>
          {activity.rating > 0 && <span><Star size={13} /> {activity.rating.toFixed(1)}</span>}
        </div>
        {activity.note && <p>{activity.note}</p>}
        {activity.costEstimateMessage && (
          <div className="admin-trip-inline-warning">{activity.costEstimateMessage}</div>
        )}
        <div className="admin-trip-activity-debug">
          <span>Thứ tự: {activity.sortOrder}</span>
          <span>Loại: {activity.type}</span>
          <span>Cost status: {activity.costEstimateStatus || "OK"}</span>
          {activity.latitude != null && activity.longitude != null && (
            <span>Toạ độ: {activity.latitude.toFixed(5)}, {activity.longitude.toFixed(5)}</span>
          )}
          {activity.googlePlaceId && <span>Google Place ID: {activity.googlePlaceId}</span>}
          <a href={mapsUrl(activity)} target="_blank" rel="noreferrer">
            Google Maps <ExternalLink size={12} />
          </a>
        </div>
      </div>
    </article>
  );
}

export default function AdminTripDetailPage() {
  const params = useParams<{ id: string }>();
  const { user, loading: authLoading, authorized } = useRequireAuth((u) => !isAdmin(u));
  const [trip, setTrip] = useState<TripResponse | null>(null);
  const [owner, setOwner] = useState<AdminUserSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (authLoading || !authorized || !params.id) return;
    let cancelled = false;
    setLoading(true);
    setError("");

    adminApi.tripDetail(Number(params.id))
      .then((data) => {
        if (cancelled) return;
        setTrip(data.trip);
        setOwner(data.user);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Không thể tải chi tiết lịch trình.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [authLoading, authorized, params.id]);

  const totals = useMemo(() => {
    const activities = trip?.schedule?.flatMap((day) => day.activities ?? []) ?? [];
    return {
      activities: activities.length,
      estimated: activities.reduce((sum, activity) => sum + Math.max(0, activity.estimatedCost ?? 0), 0),
    };
  }, [trip]);

  if (authLoading || !authorized || !user) return null;

  return (
    <>
      <Navbar />
      <main className="admin-trip-detail-page">
        <section className="container admin-trip-detail-shell">
          <Link href="/admin" className="admin-trip-back-link">
            <ArrowLeft size={16} /> Quay lại quản trị
          </Link>

          {loading ? (
            <div className="admin-trip-detail-state">Đang tải chi tiết lịch trình...</div>
          ) : error ? (
            <div className="admin-alert">{error}</div>
          ) : trip ? (
            <>
              <header className="admin-trip-detail-header">
                <div>
                  <div className="admin-eyebrow">
                    <ShieldCheck size={15} /> Kiểm tra lịch trình
                  </div>
                  <h1>{trip.destination}</h1>
                  <div className="admin-trip-detail-subtitle">
                    {trip.departure && <span><Navigation size={15} /> Từ {trip.departure}</span>}
                    <span><Calendar size={15} /> {formatDate(trip.startDate)} - {formatDate(trip.endDate)}</span>
                    <span><Eye size={15} /> {trip.isPublic ? "Công khai" : "Riêng tư"}</span>
                  </div>
                </div>
                <div className="admin-trip-detail-status">
                  <span>{labelFromEnum(trip.status)}</span>
                  <strong>#{trip.id}</strong>
                </div>
              </header>

              <div className="admin-trip-meta-grid">
                <MetaCard label="Số ngày" value={`${trip.days} ngày`} />
                <MetaCard label="Số hoạt động" value={totals.activities} />
                <MetaCard label="Ngân sách mục tiêu" value={formatCurrency(budgetTarget(trip))} />
                <MetaCard label="Ước tính hiện tại" value={formatCurrency(trip.budget?.total ?? totals.estimated)} />
                <MetaCard label="Số người" value={trip.travelerCount ?? 1} />
                <MetaCard label="Ngày tạo" value={formatDateTime(trip.createdAt)} />
              </div>

              <div className="admin-trip-detail-layout">
                <section className="admin-trip-main-column">
                  {trip.warnings?.length ? (
                    <div className="admin-trip-quality-card">
                      <div className="admin-trip-card-title">
                        <AlertTriangle size={17} /> Cảnh báo cần kiểm tra
                      </div>
                      <ul className="admin-trip-warning-list">
                        {trip.warnings.map((warning) => (
                          <li key={warning}>{warning}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}

                  {trip.requestFulfillment?.items?.length ? (
                    <div className="admin-trip-quality-card">
                      <div className="admin-trip-card-title">
                        <ListChecks size={17} /> Mức độ đáp ứng yêu cầu
                      </div>
                      <div className="admin-trip-request-list">
                        {trip.requestFulfillment.items.map((item, index) => (
                          <div key={`${item.requestedText}-${index}`} className="admin-trip-request-item">
                            <strong>{item.requestedText || "Yêu cầu của user"}</strong>
                            <span>{item.status || "UNCLEAR"}</span>
                            {item.userMessage && <p>{item.userMessage}</p>}
                          </div>
                        ))}
                      </div>
                    </div>
                  ) : null}

                  <div className="admin-trip-schedule">
                    {trip.schedule?.length ? (
                      trip.schedule.map((day) => (
                        <section key={day.day} className="admin-trip-detail-day">
                          <div className="admin-trip-day-title">
                            <span>Ngày {day.day}</span>
                            <h2>{day.title}</h2>
                            {day.summary && <p>{day.summary}</p>}
                          </div>
                          <div className="admin-trip-activity-list">
                            {day.activities?.map((activity) => (
                              <ActivityRow key={activity.id ?? `${day.day}-${activity.sortOrder}-${activity.name}`} activity={activity} />
                            ))}
                          </div>
                        </section>
                      ))
                    ) : (
                      <div className="admin-trip-detail-state">Lịch trình này chưa có nội dung chi tiết.</div>
                    )}
                  </div>
                </section>

                <aside className="admin-trip-side-column">
                  <div className="admin-trip-side-card">
                    <div className="admin-trip-card-title">
                      <Users size={17} /> Người tạo
                    </div>
                    <dl className="admin-trip-info-list">
                      <div><dt>Tên</dt><dd>{owner?.name || "-"}</dd></div>
                      <div><dt>Email</dt><dd>{owner?.email || "-"}</dd></div>
                    </dl>
                  </div>

                  <div className="admin-trip-side-card">
                    <div className="admin-trip-card-title">
                      <Sparkles size={17} /> Thông tin chuyến đi
                    </div>
                    <dl className="admin-trip-info-list">
                      <div><dt>Nơi đi</dt><dd>{trip.departure || "-"}</dd></div>
                      <div><dt>Điểm đến</dt><dd>{trip.destination}</dd></div>
                      <div><dt>Ngày đi</dt><dd>{formatDate(trip.startDate)}</dd></div>
                      <div><dt>Ngày về</dt><dd>{formatDate(trip.endDate)}</dd></div>
                      <div><dt>Phong cách</dt><dd>{labelFromEnum(trip.style)}</dd></div>
                      <div><dt>Nhóm đi</dt><dd>{labelFromEnum(trip.groupType)}</dd></div>
                      <div><dt>Di chuyển chính</dt><dd>{transportLabel(trip.outboundTransport || trip.transport)}</dd></div>
                      <div><dt>Di chuyển tại nơi đến</dt><dd>{transportLabel(trip.localTransport || trip.transport)}</dd></div>
                      <div><dt>Điểm đến được gợi ý</dt><dd>{trip.destinationSuggested ? "Có" : "Không"}</dd></div>
                      <div><dt>Hiển thị</dt><dd>{trip.isPublic ? "Công khai" : "Riêng tư"}</dd></div>
                      <div><dt>Mã chia sẻ</dt><dd>{trip.shareCode || "-"}</dd></div>
                      <div><dt>Lượt xem</dt><dd>{trip.viewCount}</dd></div>
                    </dl>
                  </div>

                  <div className="admin-trip-side-card">
                    <div className="admin-trip-card-title">
                      <Wallet size={17} /> Ngân sách
                    </div>
                    <dl className="admin-trip-info-list">
                      <div><dt>Ngân sách mục tiêu</dt><dd>{formatCurrency(budgetTarget(trip))}</dd></div>
                      <div><dt>Ước tính hiện tại</dt><dd>{formatCurrency(trip.budget?.total ?? totals.estimated)}</dd></div>
                      <div><dt>Di chuyển</dt><dd>{formatCurrency(trip.budget?.transport)}</dd></div>
                      <div><dt>Lưu trú</dt><dd>{formatCurrency(trip.budget?.accommodation)}</dd></div>
                      <div><dt>Ăn uống</dt><dd>{formatCurrency(trip.budget?.food)}</dd></div>
                      <div><dt>Tham quan</dt><dd>{formatCurrency(trip.budget?.activities)}</dd></div>
                    </dl>
                  </div>

                  <div className="admin-trip-side-card">
                    <div className="admin-trip-card-title">
                      <Users size={17} /> Ràng buộc từ user
                    </div>
                    <dl className="admin-trip-info-list">
                      <div><dt>Muốn ghé</dt><dd>{trip.mustVisit || "-"}</dd></div>
                      <div><dt>Muốn tránh</dt><dd>{trip.avoid || "-"}</dd></div>
                      <div><dt>Ghi chú thêm</dt><dd>{trip.notes || "-"}</dd></div>
                    </dl>
                  </div>
                </aside>
              </div>
            </>
          ) : null}
        </section>
      </main>
      <Footer />
    </>
  );
}
