"use client";

import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import Link from "next/link";
import {
  BarChart3,
  CheckCircle2,
  CreditCard,
  LayoutDashboard,
  ShieldCheck,
  Users,
} from "lucide-react";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { useRequireAuth } from "@/hooks/useRequireAuth";
import {
  adminApi,
  type AdminStats,
  type AdminTripSummary,
  type AdminUserSummary,
  type PageResponse,
} from "@/lib/api";

type AdminTab = "users" | "trips";

const pageSize = 12;

function formatDate(value?: string) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);
}

function isAdmin(user: { role?: string; roles?: string[] } | null | undefined) {
  return user?.role === "ADMIN" || user?.roles?.includes("ADMIN");
}

function StatCard({
  label,
  value,
  icon,
}: {
  label: string;
  value: string | number;
  icon: ReactNode;
}) {
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

export default function AdminPage() {
  const { user, loading: authLoading, authorized } = useRequireAuth((u) => !isAdmin(u));
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [users, setUsers] = useState<PageResponse<AdminUserSummary> | null>(null);
  const [trips, setTrips] = useState<PageResponse<AdminTripSummary> | null>(null);
  const [activeTab, setActiveTab] = useState<AdminTab>("users");
  const [userPage, setUserPage] = useState(0);
  const [tripPage, setTripPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [savingUserId, setSavingUserId] = useState<number | null>(null);
  const [error, setError] = useState("");

  const bootstrapCopy = useMemo(
    () => "Admin đầu tiên được cấp quyền bằng email trùng ADMIN_BOOTSTRAP_EMAIL trong cấu hình backend.",
    []
  );

  useEffect(() => {
    if (authLoading || !authorized) return;
    let cancelled = false;
    setLoading(true);
    setError("");

    Promise.all([
      adminApi.stats(),
      adminApi.users(userPage, pageSize),
      adminApi.trips(tripPage, pageSize),
    ])
      .then(([nextStats, nextUsers, nextTrips]) => {
        if (cancelled) return;
        setStats(nextStats);
        setUsers(nextUsers);
        setTrips(nextTrips);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : "Không thể tải dữ liệu quản trị.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [authLoading, authorized, userPage, tripPage]);

  const handleRoleChange = async (adminUser: AdminUserSummary, role: "USER" | "ADMIN") => {
    if (adminUser.role === role) return;
    setSavingUserId(adminUser.id);
    setError("");
    try {
      const updated = await adminApi.updateUserRole(adminUser.id, role);
      setUsers((current) => current
        ? {
            ...current,
            content: current.content.map((item) => item.id === updated.id ? updated : item),
          }
        : current);
      setStats(await adminApi.stats());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể cập nhật quyền người dùng.");
    } finally {
      setSavingUserId(null);
    }
  };

  if (authLoading || !authorized || !user) return null;

  return (
    <>
      <Navbar />
      <main className="admin-page">
        <section className="container admin-shell">
          <div className="admin-header">
            <div>
              <div className="admin-eyebrow">
                <ShieldCheck size={15} /> Quản trị hệ thống
              </div>
              <h1>Admin Dashboard</h1>
              <p>{bootstrapCopy}</p>
            </div>
            <Link href="/" className="btn btn-secondary">
              Về trang chủ
            </Link>
          </div>

          {error && <div className="admin-alert">{error}</div>}

          <div className="admin-stats-grid" aria-busy={loading}>
            <StatCard label="Người dùng" value={stats?.totalUsers ?? "-"} icon={<Users size={18} />} />
            <StatCard label="Admin" value={stats?.adminUsers ?? "-"} icon={<ShieldCheck size={18} />} />
            <StatCard label="Lịch trình" value={stats?.totalTrips ?? "-"} icon={<LayoutDashboard size={18} />} />
            <StatCard label="Lịch trình công khai" value={stats?.publicTrips ?? "-"} icon={<CheckCircle2 size={18} />} />
            <StatCard label="Đơn đã thanh toán" value={stats?.paidOrders ?? "-"} icon={<CreditCard size={18} />} />
            <StatCard label="Doanh thu" value={stats ? formatCurrency(stats.totalRevenue) : "-"} icon={<BarChart3 size={18} />} />
          </div>

          <div className="admin-tabs" role="tablist" aria-label="Khu vực quản trị">
            <button
              type="button"
              className={activeTab === "users" ? "active" : ""}
              onClick={() => setActiveTab("users")}
            >
              Người dùng
            </button>
            <button
              type="button"
              className={activeTab === "trips" ? "active" : ""}
              onClick={() => setActiveTab("trips")}
            >
              Lịch trình
            </button>
          </div>

          {activeTab === "users" ? (
            <section className="admin-panel">
              <div className="admin-panel-header">
                <div>
                  <h2>Người dùng</h2>
                  <p>{users?.totalElements ?? 0} tài khoản trong hệ thống</p>
                </div>
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Người dùng</th>
                      <th>Đăng nhập</th>
                      <th>Trạng thái</th>
                      <th>Ngày tạo</th>
                      <th>Quyền</th>
                    </tr>
                  </thead>
                  <tbody>
                    {users?.content.map((item) => (
                      <tr key={item.id}>
                        <td>
                          <strong>{item.name}</strong>
                          <span>{item.email}</span>
                        </td>
                        <td>{item.provider === "GOOGLE" ? "Google" : "Email"}</td>
                        <td>{item.emailVerified ? "Đã xác minh" : "Chưa xác minh"}</td>
                        <td>{formatDate(item.createdAt)}</td>
                        <td>
                          <select
                            className="admin-role-select"
                            value={item.role === "ADMIN" ? "ADMIN" : "USER"}
                            disabled={savingUserId === item.id}
                            onChange={(e) => handleRoleChange(item, e.target.value as "USER" | "ADMIN")}
                          >
                            <option value="USER">User</option>
                            <option value="ADMIN">Admin</option>
                          </select>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="admin-pagination">
                <button type="button" className="btn btn-secondary" disabled={userPage <= 0 || loading} onClick={() => setUserPage((p) => Math.max(0, p - 1))}>
                  Trước
                </button>
                <span>Trang {userPage + 1} / {Math.max(users?.totalPages ?? 1, 1)}</span>
                <button type="button" className="btn btn-secondary" disabled={loading || userPage + 1 >= (users?.totalPages ?? 1)} onClick={() => setUserPage((p) => p + 1)}>
                  Sau
                </button>
              </div>
            </section>
          ) : (
            <section className="admin-panel">
              <div className="admin-panel-header">
                <div>
                  <h2>Lịch trình</h2>
                  <p>{trips?.totalElements ?? 0} lịch trình đã được tạo</p>
                </div>
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Điểm đến</th>
                      <th>Người tạo</th>
                      <th>Số ngày</th>
                      <th>Trạng thái</th>
                      <th>Lượt xem</th>
                      <th>Ngày tạo</th>
                    </tr>
                  </thead>
                  <tbody>
                    {trips?.content.map((item) => (
                      <tr key={item.id}>
                        <td>
                          <strong>{item.destination}</strong>
                          <span>{item.departure ? `Từ ${item.departure}` : `#${item.id}`}</span>
                        </td>
                        <td>{item.userEmail}</td>
                        <td>{item.days}</td>
                        <td>{item.isPublic ? "Công khai" : item.status}</td>
                        <td>{item.viewCount}</td>
                        <td>{formatDate(item.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="admin-pagination">
                <button type="button" className="btn btn-secondary" disabled={tripPage <= 0 || loading} onClick={() => setTripPage((p) => Math.max(0, p - 1))}>
                  Trước
                </button>
                <span>Trang {tripPage + 1} / {Math.max(trips?.totalPages ?? 1, 1)}</span>
                <button type="button" className="btn btn-secondary" disabled={loading || tripPage + 1 >= (trips?.totalPages ?? 1)} onClick={() => setTripPage((p) => p + 1)}>
                  Sau
                </button>
              </div>
            </section>
          )}
        </section>
      </main>
      <Footer />
    </>
  );
}
