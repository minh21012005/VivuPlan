"use client";

import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  ArrowLeft,
  Calendar,
  CreditCard,
  Eye,
  Mail,
  MapPin,
  ShieldCheck,
  Ticket,
  User,
  Wallet,
} from "lucide-react";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { useRequireAuth } from "@/hooks/useRequireAuth";
import { adminApi, type AdminUserDetail } from "@/lib/api";

function isAdmin(user: { role?: string; roles?: string[] } | null | undefined) {
  return user?.role === "ADMIN" || user?.roles?.includes("ADMIN");
}

function formatDate(value?: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatCurrency(value?: number | null) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value ?? 0);
}

function statusLabel(status?: string) {
  const labels: Record<string, string> = {
    PENDING: "Đang chờ",
    PAID: "Đã thanh toán",
    UNDERPAID: "Thiếu tiền",
    EXPIRED: "Hết hạn",
    CANCELLED: "Đã hủy",
  };
  return status ? labels[status] ?? status : "-";
}

function providerLabel(provider?: string) {
  return provider === "GOOGLE" ? "Google" : "Email";
}

function StatCard({ label, value, icon }: { label: string; value: string | number; icon: ReactNode }) {
  return (
    <div className="admin-stat-card">
      <div className="admin-stat-icon">{icon}</div>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
    </div>
  );
}

export default function AdminUserDetailPage() {
  const params = useParams<{ id: string }>();
  const { user, loading: authLoading, authorized } = useRequireAuth((u) => !isAdmin(u));
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (authLoading || !authorized || !params.id) return;
    let cancelled = false;
    setLoading(true);
    setError("");

    adminApi.userDetail(Number(params.id))
      .then((data) => {
        if (!cancelled) setDetail(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Không thể tải chi tiết người dùng.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [authLoading, authorized, params.id]);

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
            <div className="admin-trip-detail-state">Đang tải chi tiết người dùng...</div>
          ) : error ? (
            <div className="admin-alert">{error}</div>
          ) : detail ? (
            <>
              <header className="admin-trip-detail-header">
                <div>
                  <div className="admin-eyebrow">
                    <ShieldCheck size={15} /> Chi tiết người dùng
                  </div>
                  <h1>{detail.user.name}</h1>
                </div>
                <div className="admin-trip-detail-status">
                  <span>{detail.user.emailVerified ? "Đã xác minh" : "Chưa xác minh"}</span>
                  <strong>{detail.user.role}</strong>
                </div>
              </header>

              <div className="admin-stats-grid">
                <StatCard label="Lịch trình" value={detail.totalTrips} icon={<MapPin size={18} />} />
                <StatCard label="Đơn đã thanh toán" value={detail.paidOrders} icon={<CreditCard size={18} />} />
                <StatCard label="Tổng đã trả" value={formatCurrency(detail.totalPaid)} icon={<Wallet size={18} />} />
                <StatCard label="Lượt còn lại" value={`${detail.wallet.planCredits} tạo · ${detail.wallet.editCredits} chỉnh`} icon={<Ticket size={18} />} />
              </div>

              <div className="admin-trip-detail-layout">
                <section className="admin-trip-main-column">
                  <div className="admin-panel">
                    <div className="admin-panel-header">
                      <div>
                        <h2>Lịch trình gần đây</h2>
                        <p>{detail.recentTrips.length} lịch trình mới nhất của người dùng</p>
                      </div>
                    </div>
                    <div className="admin-table-wrap">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>Điểm đến</th>
                            <th>Số ngày</th>
                            <th>Ngày tạo</th>
                            <th></th>
                          </tr>
                        </thead>
                        <tbody>
                          {detail.recentTrips.map((trip) => (
                            <tr key={trip.id}>
                              <td>
                                <strong>{trip.destination}</strong>
                                <span>{trip.departure ? `Từ ${trip.departure}` : `#${trip.id}`}</span>
                              </td>
                              <td>{trip.days}</td>
                              <td>{formatDate(trip.createdAt)}</td>
                              <td>
                                <Link href={`/admin/trips/${trip.id}`} className="admin-row-action">
                                  <Eye size={15} /> Xem
                                </Link>
                              </td>
                            </tr>
                          ))}
                          {detail.recentTrips.length === 0 && (
                            <tr>
                              <td colSpan={4}>Người dùng chưa tạo lịch trình.</td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>

                  <div className="admin-panel">
                    <div className="admin-panel-header">
                      <div>
                        <h2>Giao dịch gần đây</h2>
                        <p>{detail.recentOrders.length} đơn thanh toán mới nhất</p>
                      </div>
                    </div>
                    <div className="admin-table-wrap">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>Mã đơn</th>
                            <th>Gói</th>
                            <th>Số tiền</th>
                            <th>Trạng thái</th>
                            <th>Thời gian</th>
                          </tr>
                        </thead>
                        <tbody>
                          {detail.recentOrders.map((order) => (
                            <tr key={order.id}>
                              <td>
                                <strong>{order.orderCode}</strong>
                                <span>#{order.id}</span>
                              </td>
                              <td>{order.packageCode}</td>
                              <td>
                                <strong>{formatCurrency(order.amount)}</strong>
                                <span>{order.planCredits} tạo · {order.editCredits} chỉnh</span>
                              </td>
                              <td>{statusLabel(order.status)}</td>
                              <td>{formatDate(order.createdAt)}</td>
                            </tr>
                          ))}
                          {detail.recentOrders.length === 0 && (
                            <tr>
                              <td colSpan={5}>Người dùng chưa có giao dịch.</td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </section>

                <aside className="admin-trip-side-column">
                  <div className="admin-trip-side-card">
                    <div className="admin-trip-card-title">
                      <User size={17} /> Hồ sơ
                    </div>
                    <dl className="admin-trip-info-list">
                      <div><dt>ID</dt><dd>#{detail.user.id}</dd></div>
                      <div><dt>Tên</dt><dd>{detail.user.name}</dd></div>
                      <div><dt>Email</dt><dd>{detail.user.email}</dd></div>
                      <div><dt>Đăng nhập</dt><dd>{providerLabel(detail.user.provider)}</dd></div>
                      <div><dt>Trạng thái email</dt><dd>{detail.user.emailVerified ? "Đã xác minh" : "Chưa xác minh"}</dd></div>
                      <div><dt>Quyền</dt><dd>{detail.user.roles.join(", ")}</dd></div>
                      <div><dt>Ngày tạo</dt><dd>{formatDate(detail.user.createdAt)}</dd></div>
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
