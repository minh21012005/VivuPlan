"use client";

import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import Link from "next/link";
import {
  BarChart3,
  ChevronsLeft,
  ChevronsRight,
  CreditCard,
  ChevronLeft,
  ChevronRight,
  Eye,
  MapPin,
  Search,
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
  type AdminTransactionSummary,
  type AdminUserSummary,
  type PageResponse,
} from "@/lib/api";

type AdminTab = "users" | "trips" | "transactions";

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

function StatusBadge({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "success" | "admin" | "warning" }) {
  return <span className={`admin-badge admin-badge-${tone}`}>{children}</span>;
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

function Pagination({
  page,
  totalPages,
  loading,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  loading: boolean;
  onPageChange: (page: number) => void;
}) {
  const lastPage = Math.max(totalPages - 1, 0);
  const pages = Array.from(
    { length: Math.min(5, totalPages) },
    (_, index) => {
      const start = Math.min(Math.max(page - 2, 0), Math.max(totalPages - 5, 0));
      return start + index;
    }
  );
  return (
    <div className="admin-pagination">
      <button type="button" className="admin-page-btn admin-page-btn-icon" disabled={loading || page <= 0} onClick={() => onPageChange(0)} aria-label="Trang đầu">
        <ChevronsLeft size={15} />
      </button>
      <button type="button" className="admin-page-btn admin-page-btn-icon" disabled={loading || page <= 0} onClick={() => onPageChange(Math.max(0, page - 1))} aria-label="Trang trước">
        <ChevronLeft size={15} />
      </button>
      <div className="admin-page-numbers">
        {pages.map((pageNumber) => (
          <button
            key={pageNumber}
            type="button"
            className={`admin-page-btn${pageNumber === page ? " active" : ""}`}
            disabled={loading || pageNumber === page}
            onClick={() => onPageChange(pageNumber)}
          >
            {pageNumber + 1}
          </button>
        ))}
      </div>
      <button type="button" className="admin-page-btn admin-page-btn-icon" disabled={loading || page >= lastPage} onClick={() => onPageChange(Math.min(lastPage, page + 1))} aria-label="Trang sau">
        <ChevronRight size={15} />
      </button>
      <button type="button" className="admin-page-btn admin-page-btn-icon" disabled={loading || page >= lastPage} onClick={() => onPageChange(lastPage)} aria-label="Trang cuối">
        <ChevronsRight size={15} />
      </button>
    </div>
  );
}

export default function AdminPage() {
  const { user, loading: authLoading, authorized } = useRequireAuth((u) => !isAdmin(u));
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [users, setUsers] = useState<PageResponse<AdminUserSummary> | null>(null);
  const [trips, setTrips] = useState<PageResponse<AdminTripSummary> | null>(null);
  const [transactions, setTransactions] = useState<PageResponse<AdminTransactionSummary> | null>(null);
  const [activeTab, setActiveTab] = useState<AdminTab>("users");
  const [userPage, setUserPage] = useState(0);
  const [tripPage, setTripPage] = useState(0);
  const [transactionPage, setTransactionPage] = useState(0);
  const [userSearch, setUserSearch] = useState("");
  const [userRoleFilter, setUserRoleFilter] = useState<"ALL" | "USER" | "ADMIN">("ALL");
  const [userProviderFilter, setUserProviderFilter] = useState<"ALL" | "LOCAL" | "GOOGLE">("ALL");
  const [tripSearch, setTripSearch] = useState("");
  const [transactionSearch, setTransactionSearch] = useState("");
  const [transactionStatusFilter, setTransactionStatusFilter] = useState<"ALL" | "PENDING" | "PAID" | "UNDERPAID" | "EXPIRED" | "CANCELLED">("ALL");
  const [loading, setLoading] = useState(true);
  const [savingUserId, setSavingUserId] = useState<number | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    setUserPage(0);
  }, [userSearch, userRoleFilter, userProviderFilter]);

  useEffect(() => {
    setTripPage(0);
  }, [tripSearch]);

  useEffect(() => {
    setTransactionPage(0);
  }, [transactionSearch, transactionStatusFilter]);

  useEffect(() => {
    if (authLoading || !authorized) return;
    let cancelled = false;
    setLoading(true);
    setError("");

    Promise.all([
      adminApi.stats(),
      adminApi.users(userPage, pageSize, {
        q: userSearch,
        role: userRoleFilter,
        provider: userProviderFilter,
      }),
      adminApi.trips(tripPage, pageSize, {
        q: tripSearch,
      }),
      adminApi.transactions(transactionPage, pageSize, {
        q: transactionSearch,
        status: transactionStatusFilter,
      }),
    ])
      .then(([nextStats, nextUsers, nextTrips, nextTransactions]) => {
        if (cancelled) return;
        setStats(nextStats);
        setUsers(nextUsers);
        setTrips(nextTrips);
        setTransactions(nextTransactions);
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
  }, [
    authLoading,
    authorized,
    userPage,
    tripPage,
    transactionPage,
    userSearch,
    userRoleFilter,
    userProviderFilter,
    tripSearch,
    transactionSearch,
    transactionStatusFilter,
  ]);

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
              <h1>Bảng điều khiển quản trị</h1>
            </div>
            <Link href="/" className="btn btn-secondary">
              Về trang chủ
            </Link>
          </div>

          {error && <div className="admin-alert">{error}</div>}

          <div
            className="admin-stats-grid"
            aria-busy={loading}
            style={{ gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))" }}
          >
            <StatCard label="Người dùng" value={stats?.totalUsers ?? "-"} icon={<Users size={18} />} />
            <StatCard label="Admin" value={stats?.adminUsers ?? "-"} icon={<ShieldCheck size={18} />} />
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
            <button
              type="button"
              className={activeTab === "transactions" ? "active" : ""}
              onClick={() => setActiveTab("transactions")}
            >
              Giao dịch
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

              <div className="admin-filter-bar">
                <label className="admin-search-field">
                  <Search size={15} />
                  <input
                    value={userSearch}
                    onChange={(e) => setUserSearch(e.target.value)}
                    placeholder="Tìm theo tên hoặc email"
                  />
                </label>
                <select className="admin-filter-select" value={userRoleFilter} onChange={(e) => setUserRoleFilter(e.target.value as typeof userRoleFilter)}>
                  <option value="ALL">Tất cả quyền</option>
                  <option value="USER">User</option>
                  <option value="ADMIN">Admin</option>
                </select>
                <select className="admin-filter-select" value={userProviderFilter} onChange={(e) => setUserProviderFilter(e.target.value as typeof userProviderFilter)}>
                  <option value="ALL">Tất cả đăng nhập</option>
                  <option value="LOCAL">Email</option>
                  <option value="GOOGLE">Google</option>
                </select>
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
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {users?.content.map((item) => (
                      <tr key={item.id}>
                        <td>
                          <strong>{item.name}</strong>
                          <span>{item.email}</span>
                        </td>
                        <td>
                          <StatusBadge tone={item.provider === "GOOGLE" ? "neutral" : "success"}>
                            {item.provider === "GOOGLE" ? "Google" : "Email"}
                          </StatusBadge>
                        </td>
                        <td>
                          <StatusBadge tone={item.emailVerified ? "success" : "warning"}>
                            {item.emailVerified ? "Đã xác minh" : "Chưa xác minh"}
                          </StatusBadge>
                        </td>
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
                        <td>
                          <Link
                            href={`/admin/users/${item.id}`}
                            className="admin-row-action"
                            aria-label={`Xem người dùng ${item.email}`}
                          >
                            <Eye size={15} /> Xem
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <Pagination page={userPage} totalPages={users?.totalPages ?? 1} loading={loading} onPageChange={setUserPage} />
            </section>
          ) : activeTab === "trips" ? (
            <section className="admin-panel">
              <div className="admin-panel-header">
                <div>
                  <h2>Lịch trình</h2>
                  <p>{trips?.totalElements ?? 0} lịch trình đã được tạo</p>
                </div>
              </div>

              <div className="admin-filter-bar">
                <label className="admin-search-field">
                  <Search size={15} />
                  <input
                    value={tripSearch}
                    onChange={(e) => setTripSearch(e.target.value)}
                    placeholder="Tìm theo điểm đến, nơi đi hoặc email"
                  />
                </label>
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Điểm đến</th>
                      <th>Người tạo</th>
                      <th>Số ngày</th>
                      <th>Ngày tạo</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {trips?.content.map((item) => (
                      <tr key={item.id}>
                        <td>
                          <strong>{item.destination}</strong>
                          <span><MapPin size={12} /> {item.departure ? `Từ ${item.departure}` : `#${item.id}`}</span>
                        </td>
                        <td>{item.userEmail}</td>
                        <td>{item.days}</td>
                        <td>{formatDate(item.createdAt)}</td>
                        <td>
                          <Link
                            href={`/admin/trips/${item.id}`}
                            className="admin-row-action"
                            aria-label={`Xem lịch trình ${item.destination}`}
                          >
                            <Eye size={15} /> Xem
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <Pagination page={tripPage} totalPages={trips?.totalPages ?? 1} loading={loading} onPageChange={setTripPage} />
            </section>
          ) : (
            <section className="admin-panel">
              <div className="admin-panel-header">
                <div>
                  <h2>Giao dịch</h2>
                  <p>{transactions?.totalElements ?? 0} đơn thanh toán đã được tạo</p>
                </div>
              </div>

              <div className="admin-filter-bar">
                <label className="admin-search-field">
                  <Search size={15} />
                  <input
                    value={transactionSearch}
                    onChange={(e) => setTransactionSearch(e.target.value)}
                    placeholder="Tìm theo mã đơn, email hoặc mã gói"
                  />
                </label>
                <select className="admin-filter-select" value={transactionStatusFilter} onChange={(e) => setTransactionStatusFilter(e.target.value as typeof transactionStatusFilter)}>
                  <option value="ALL">Tất cả trạng thái</option>
                  <option value="PENDING">Đang chờ</option>
                  <option value="PAID">Đã thanh toán</option>
                  <option value="UNDERPAID">Thiếu tiền</option>
                  <option value="EXPIRED">Hết hạn</option>
                  <option value="CANCELLED">Đã hủy</option>
                </select>
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table admin-transaction-table">
                  <thead>
                    <tr>
                      <th>Mã đơn</th>
                      <th>Người mua</th>
                      <th>Gói</th>
                      <th>Số tiền</th>
                      <th>Lượt cộng</th>
                      <th>Trạng thái</th>
                      <th>Thời gian</th>
                    </tr>
                  </thead>
                  <tbody>
                    {transactions?.content.map((item) => {
                      const paid = item.paidAmount ?? 0;
                      const statusTone =
                        item.status === "PAID" ? "success"
                          : item.status === "UNDERPAID" ? "warning"
                            : item.status === "PENDING" ? "admin"
                              : "neutral";
                      const statusLabel =
                        item.status === "PAID" ? "Đã thanh toán"
                          : item.status === "PENDING" ? "Đang chờ"
                            : item.status === "UNDERPAID" ? "Thiếu tiền"
                              : item.status === "EXPIRED" ? "Hết hạn"
                                : item.status === "CANCELLED" ? "Đã hủy"
                                  : item.status;
                      return (
                        <tr key={item.id}>
                          <td>
                            <strong>{item.orderCode}</strong>
                            <span>#{item.id}</span>
                          </td>
                          <td>{item.userEmail}</td>
                          <td>{item.packageCode}</td>
                          <td>
                            <strong>{formatCurrency(item.amount)}</strong>
                            <span>{paid > 0 ? `Đã nhận ${formatCurrency(paid)}` : "Chưa nhận tiền"}</span>
                          </td>
                          <td>{item.planCredits} lịch trình · {item.editCredits} chỉnh AI</td>
                          <td><StatusBadge tone={statusTone}>{statusLabel}</StatusBadge></td>
                          <td>
                            <strong>{formatDate(item.createdAt)}</strong>
                            <span>{item.paidAt ? `Thanh toán ${formatDate(item.paidAt)}` : `Hết hạn ${formatDate(item.expiresAt)}`}</span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <Pagination page={transactionPage} totalPages={transactions?.totalPages ?? 1} loading={loading} onPageChange={setTransactionPage} />
            </section>
          )}
        </section>
      </main>
      <Footer />
    </>
  );
}
