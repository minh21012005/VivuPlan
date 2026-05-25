"use client";

import { useEffect, useState } from "react";
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
  Lock,
  MapPin,
  Search,
  ShieldCheck,
  Unlock,
  Users,
  X,
} from "lucide-react";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { useRequireAuth } from "@/hooks/useRequireAuth";
import {
  adminApi,
  type AdminStats,
  type AdminTripSummary,
  type AdminTransactionStatus,
  type AdminTransactionSummary,
  type AdminUserSummary,
  type PageResponse,
} from "@/lib/api";

type AdminTab = "users" | "trips" | "transactions";

const pageSize = 10;

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

const transactionStatusLabels: Record<AdminTransactionStatus, string> = {
  PENDING: "Đang chờ",
  PAID: "Đã thanh toán",
  UNDERPAID: "Thiếu tiền",
  EXPIRED: "Hết hạn",
  CANCELLED: "Đã hủy",
};

const transactionStatusOptions = Object.keys(transactionStatusLabels) as AdminTransactionStatus[];

function transactionStatusLabel(status: string) {
  return transactionStatusLabels[status as AdminTransactionStatus] ?? status;
}

function transactionStatusTone(status: string): "neutral" | "success" | "admin" | "warning" {
  if (status === "PAID") return "success";
  if (status === "UNDERPAID") return "warning";
  if (status === "PENDING") return "admin";
  return "neutral";
}

function rowNumber(page: number, index: number) {
  return page * pageSize + index + 1;
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

function TransactionDrawer({
  transaction,
  onClose,
}: {
  transaction: AdminTransactionSummary;
  onClose: () => void;
}) {
  const paidAmount = transaction.paidAmount ?? 0;

  return (
    <div className="admin-drawer-backdrop" onClick={(event) => event.target === event.currentTarget && onClose()}>
      <aside className="admin-drawer-panel" aria-label="Chi tiết giao dịch">
        <div className="admin-drawer-header">
          <div>
            <span>Chi tiết giao dịch</span>
            <h3>{transaction.orderCode}</h3>
            <div className="admin-drawer-header-meta">
              <StatusBadge tone={transactionStatusTone(transaction.status)}>
                {transactionStatusLabel(transaction.status)}
              </StatusBadge>
              <span>#{transaction.id}</span>
            </div>
          </div>
          <button type="button" className="admin-drawer-close" onClick={onClose} aria-label="Đóng">
            <X size={18} />
          </button>
        </div>

        <div className="admin-drawer-body">
          <section className="admin-drawer-section">
            <h4>Người mua</h4>
            <dl className="admin-drawer-list">
              <div>
                <dt>Email</dt>
                <dd>{transaction.userEmail}</dd>
              </div>
              <div>
                <dt>Tài khoản</dt>
                <dd>
                  <Link href={`/admin/users/${transaction.userId}`} className="admin-drawer-link">
                    Xem người dùng
                  </Link>
                </dd>
              </div>
            </dl>
          </section>

          <section className="admin-drawer-section">
            <h4>Thanh toán</h4>
            <dl className="admin-drawer-list">
              <div>
                <dt>Gói</dt>
                <dd>{transaction.packageCode}</dd>
              </div>
              <div>
                <dt>Số tiền cần thanh toán</dt>
                <dd>{formatCurrency(transaction.amount)}</dd>
              </div>
              {paidAmount > 0 && (
                <div>
                  <dt>Số tiền đã nhận</dt>
                  <dd>{formatCurrency(paidAmount)}</dd>
                </div>
              )}
            </dl>
          </section>

          <section className="admin-drawer-section">
            <h4>Lượt được cộng</h4>
            <div className="admin-drawer-credit-grid">
              <div>
                <span>Lịch trình</span>
                <strong>{transaction.planCredits}</strong>
              </div>
              <div>
                <span>Chỉnh AI</span>
                <strong>{transaction.editCredits}</strong>
              </div>
            </div>
          </section>

          <section className="admin-drawer-section">
            <h4>Thời gian</h4>
            <dl className="admin-drawer-list">
              <div>
                <dt>Tạo đơn</dt>
                <dd>{formatDate(transaction.createdAt)}</dd>
              </div>
              {transaction.paidAt && (
                <div>
                  <dt>Thanh toán</dt>
                  <dd>{formatDate(transaction.paidAt)}</dd>
                </div>
              )}
              {transaction.expiresAt && (
                <div>
                  <dt>Hết hạn</dt>
                  <dd>{formatDate(transaction.expiresAt)}</dd>
                </div>
              )}
            </dl>
          </section>
        </div>
      </aside>
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
  const [selectedTransaction, setSelectedTransaction] = useState<AdminTransactionSummary | null>(null);
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
  const [lockingUserId, setLockingUserId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [pendingRoleChange, setPendingRoleChange] = useState<{ user: AdminUserSummary; role: "USER" | "ADMIN"; placement: "top-left" | "top-right" } | null>(null);
  const [pendingLockChange, setPendingLockChange] = useState<{ user: AdminUserSummary; locked: boolean; placement: "top-left" | "top-right" } | null>(null);

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

  const requestRoleChange = (adminUser: AdminUserSummary, role: "USER" | "ADMIN", trigger: HTMLElement) => {
    if (adminUser.role === role) return;
    const rect = trigger.getBoundingClientRect();
    const placement = rect.left < window.innerWidth / 2 ? "top-left" : "top-right";
    setPendingLockChange(null);
    setPendingRoleChange({ user: adminUser, role, placement });
  };

  const handleRoleChange = async (adminUser: AdminUserSummary, role: "USER" | "ADMIN") => {
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
      setPendingRoleChange(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể cập nhật quyền người dùng.");
    } finally {
      setSavingUserId(null);
    }
  };

  const requestLockChange = (adminUser: AdminUserSummary, locked: boolean, trigger: HTMLElement) => {
    if (adminUser.accountLocked === locked) return;
    const rect = trigger.getBoundingClientRect();
    const placement = rect.left < window.innerWidth / 2 ? "top-left" : "top-right";
    setPendingRoleChange(null);
    setPendingLockChange({ user: adminUser, locked, placement });
  };

  const handleLockChange = async (adminUser: AdminUserSummary, locked: boolean) => {
    setLockingUserId(adminUser.id);
    setError("");
    try {
      const updated = await adminApi.updateUserLock(adminUser.id, locked);
      setUsers((current) => current
        ? {
          ...current,
          content: current.content.map((item) => item.id === updated.id ? updated : item),
        }
        : current);
      setStats(await adminApi.stats());
      setPendingLockChange(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể cập nhật trạng thái tài khoản.");
    } finally {
      setLockingUserId(null);
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
                      <th>STT</th>
                      <th>Người dùng</th>
                      <th>Đăng nhập</th>
                      <th>Ngày tạo</th>
                      <th>Quyền</th>
                      <th>Hành động</th>
                    </tr>
                  </thead>
                  <tbody>
                    {users?.content.map((item, index) => (
                      <tr key={item.id}>
                        <td>{rowNumber(userPage, index)}</td>
                        <td>
                          <strong>{item.name}</strong>
                          <span>{item.email}</span>
                        </td>
                        <td>
                          <StatusBadge tone={item.provider === "GOOGLE" ? "neutral" : "success"}>
                            {item.provider === "GOOGLE" ? "Google" : "Email"}
                          </StatusBadge>
                        </td>
                        <td>{formatDate(item.createdAt)}</td>
                        <td>
                          <div className="admin-role-control">
                            <select
                              className="admin-role-select"
                              value={item.role === "ADMIN" ? "ADMIN" : "USER"}
                              disabled={savingUserId === item.id}
                              onChange={(e) => requestRoleChange(item, e.target.value as "USER" | "ADMIN", e.currentTarget)}
                            >
                              <option value="USER">User</option>
                              <option value="ADMIN">Admin</option>
                            </select>
                            {pendingRoleChange?.user.id === item.id && (
                              <div className={`admin-popconfirm admin-popconfirm-action admin-popconfirm-${pendingRoleChange.placement}`} role="dialog" aria-label="Xác nhận đổi quyền">
                                <strong>Đổi quyền người dùng?</strong>
                                <p>
                                  {item.email} sẽ được chuyển sang {pendingRoleChange.role === "ADMIN" ? "Admin" : "User"}.
                                </p>
                                <div>
                                  <button type="button" onClick={() => setPendingRoleChange(null)}>Hủy</button>
                                  <button
                                    type="button"
                                    className="primary"
                                    disabled={savingUserId === item.id}
                                    onClick={() => handleRoleChange(pendingRoleChange.user, pendingRoleChange.role)}
                                  >
                                    Xác nhận
                                  </button>
                                </div>
                              </div>
                            )}
                          </div>
                        </td>
                        <td>
                          <div className="admin-row-actions">
                            <Link
                              href={`/admin/users/${item.id}`}
                              className="admin-row-action"
                              aria-label={`Xem người dùng ${item.email}`}
                            >
                              <Eye size={15} /> Xem
                            </Link>
                            <div className="admin-action-control">
                              <button
                                type="button"
                                className={`admin-row-action ${item.accountLocked ? "admin-row-action-success" : "admin-row-action-danger"}`}
                                disabled={lockingUserId === item.id}
                                onClick={(event) => requestLockChange(item, !item.accountLocked, event.currentTarget)}
                                aria-label={`${item.accountLocked ? "Mở khóa" : "Khóa"} tài khoản ${item.email}`}
                              >
                                {item.accountLocked ? <Unlock size={15} /> : <Lock size={15} />}
                                {item.accountLocked ? "Mở khóa" : "Khóa"}
                              </button>
                              {pendingLockChange?.user.id === item.id && (
                                <div className={`admin-popconfirm admin-popconfirm-action admin-popconfirm-${pendingLockChange.placement}`} role="dialog" aria-label="Xác nhận khóa tài khoản">
                                  <strong>{pendingLockChange.locked ? "Khóa tài khoản này?" : "Mở khóa tài khoản này?"}</strong>
                                  <p>
                                    {pendingLockChange.locked
                                      ? `${item.email} sẽ không thể đăng nhập hoặc tiếp tục sử dụng hệ thống.`
                                      : `${item.email} sẽ có thể đăng nhập lại bình thường.`}
                                  </p>
                                  <div>
                                    <button type="button" onClick={() => setPendingLockChange(null)}>Hủy</button>
                                    <button
                                      type="button"
                                      className="primary"
                                      disabled={lockingUserId === item.id}
                                      onClick={() => handleLockChange(pendingLockChange.user, pendingLockChange.locked)}
                                    >
                                      Xác nhận
                                    </button>
                                  </div>
                                </div>
                              )}
                            </div>
                          </div>
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
                      <th>STT</th>
                      <th>Điểm đến</th>
                      <th>Người tạo</th>
                      <th>Số ngày</th>
                      <th>Ngày tạo</th>
                      <th>Hành động</th>
                    </tr>
                  </thead>
                  <tbody>
                    {trips?.content.map((item, index) => (
                      <tr key={item.id}>
                        <td>{rowNumber(tripPage, index)}</td>
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
                  {transactionStatusOptions.map((status) => (
                    <option key={status} value={status}>{transactionStatusLabel(status)}</option>
                  ))}
                </select>
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table admin-transaction-table">
                  <thead>
                    <tr>
                      <th>STT</th>
                      <th>Mã đơn</th>
                      <th>Người mua</th>
                      <th>Số tiền</th>
                      <th>Trạng thái</th>
                      <th>Hành động</th>
                    </tr>
                  </thead>
                  <tbody>
                    {transactions?.content.map((item, index) => {
                      return (
                        <tr key={item.id}>
                          <td>{rowNumber(transactionPage, index)}</td>
                          <td>
                            <strong>{item.orderCode}</strong>
                          </td>
                          <td>{item.userEmail}</td>
                          <td>
                            <strong>{formatCurrency(item.amount)}</strong>
                          </td>
                          <td><StatusBadge tone={transactionStatusTone(item.status)}>{transactionStatusLabel(item.status)}</StatusBadge></td>
                          <td>
                            <button
                              type="button"
                              className="admin-row-action"
                              aria-label={`Xem giao dịch ${item.orderCode}`}
                              onClick={(event) => {
                                event.stopPropagation();
                                setSelectedTransaction(item);
                              }}
                            >
                              <Eye size={15} /> Xem
                            </button>
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
      {selectedTransaction && (
        <TransactionDrawer
          transaction={selectedTransaction}
          onClose={() => setSelectedTransaction(null)}
        />
      )}
    </>
  );
}
