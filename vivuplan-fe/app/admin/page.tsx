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
import { useRequireAuth } from "@/hooks/useRequireAuth";
import {
  adminApi,
  type AdminAiCostDaily,
  type AdminAiCostSummary,
  type AdminAiOperation,
  type AdminAiStatus,
  type AdminAiUsageEvent,
  type AdminStats,
  type AdminTripSummary,
  type AdminTransactionStatus,
  type AdminTransactionSummary,
  type AdminUserSummary,
  type PageResponse,
} from "@/lib/api";

type AdminTab = "users" | "trips" | "transactions" | "ai-cost";
type AiRangeKey = "today" | "7d" | "30d" | "custom";

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

function formatUsd(value: number) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 4,
  }).format(value || 0);
}

function formatNumber(value: number) {
  return new Intl.NumberFormat("vi-VN").format(value || 0);
}

function formatPercent(value: number) {
  return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 }).format(value || 0)}%`;
}

function formatDurationMs(value: number) {
  if (!value) return "-";
  if (value < 1000) return `${formatNumber(value)} ms`;
  return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 }).format(value / 1000)} giây`;
}

function toIsoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function aiDateRange(range: AiRangeKey, customFrom?: string, customTo?: string) {
  if (range === "custom" && customFrom && customTo) {
    return customFrom <= customTo
      ? { from: customFrom, to: customTo }
      : { from: customTo, to: customFrom };
  }
  const today = new Date();
  const from = new Date(today);
  if (range === "today") {
    return { from: toIsoDate(today), to: toIsoDate(today) };
  }
  if (range === "7d") {
    from.setDate(today.getDate() - 6);
    return { from: toIsoDate(from), to: toIsoDate(today) };
  }
  from.setDate(today.getDate() - 29);
  return { from: toIsoDate(from), to: toIsoDate(today) };
}

function packageLabel(packageCode?: string) {
  const labels: Record<string, string> = {
    PLAN_BASIC: "Gói cơ bản",
    PLAN_STANDARD: "Gói tiêu chuẩn",
    PLAN_SAVING: "Gói tiết kiệm",
  };
  return packageCode ? labels[packageCode] ?? packageCode : "-";
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

const aiOperationLabels: Record<AdminAiOperation, string> = {
  PLAN_GENERATION: "Tạo lịch trình",
  DAY_REGENERATION: "Chỉnh ngày",
  DESTINATION_SUGGESTION: "Gợi ý điểm đến",
};

const aiStatusLabels: Record<AdminAiStatus, string> = {
  SUCCESS: "Thành công",
  INVALID_RESPONSE: "Response không hợp lệ",
  HTTP_ERROR: "Lỗi HTTP",
  PARSE_ERROR: "Lỗi parse",
  FAILED: "Thất bại",
};

const aiOperationOptions = Object.keys(aiOperationLabels) as AdminAiOperation[];
const aiStatusOptions = Object.keys(aiStatusLabels) as AdminAiStatus[];

function aiOperationLabel(operation?: string) {
  return operation ? aiOperationLabels[operation as AdminAiOperation] ?? operation : "-";
}

function aiStatusLabel(status?: string) {
  return status ? aiStatusLabels[status as AdminAiStatus] ?? status : "-";
}

function aiStatusTone(status?: string): "neutral" | "success" | "admin" | "warning" {
  if (status === "SUCCESS") return "success";
  if (status === "INVALID_RESPONSE") return "warning";
  if (status === "HTTP_ERROR" || status === "PARSE_ERROR" || status === "FAILED") return "neutral";
  return "admin";
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
  note,
}: {
  label: string;
  value: string | number;
  icon: ReactNode;
  note?: string;
}) {
  return (
    <div className="admin-stat-card">
      <div className="admin-stat-icon">{icon}</div>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
        {note && <small>{note}</small>}
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
                <dd>{packageLabel(transaction.packageCode)}</dd>
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
              <div>
                <span>Gợi ý AI</span>
                <strong>{transaction.suggestionCredits}</strong>
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

function AiUsageDrawer({
  event,
  onClose,
}: {
  event: AdminAiUsageEvent;
  onClose: () => void;
}) {
  return (
    <div className="admin-drawer-backdrop">
      <aside className="admin-drawer-panel" aria-label="Chi tiết AI call">
        <div className="admin-drawer-header">
          <div>
            <span>Chi tiết AI call</span>
            <h3>{aiOperationLabel(event.operation)}</h3>
            <div className="admin-drawer-header-meta">
              <StatusBadge tone={aiStatusTone(event.status)}>{aiStatusLabel(event.status)}</StatusBadge>
              <span>Attempt #{event.attemptNumber}</span>
            </div>
          </div>
          <button type="button" className="admin-drawer-close" onClick={onClose} aria-label="Đóng">
            <X size={18} />
          </button>
        </div>

        <div className="admin-drawer-body">
          <section className="admin-drawer-section">
            <h4>Định danh</h4>
            <dl className="admin-drawer-list">
              <div>
                <dt>Request ID</dt>
                <dd>{event.requestId || "-"}</dd>
              </div>
              <div>
                <dt>Thời gian</dt>
                <dd>{formatDate(event.createdAt)}</dd>
              </div>
              <div>
                <dt>User</dt>
                <dd>{event.userEmail || "-"}</dd>
              </div>
              <div>
                <dt>Trip</dt>
                <dd>
                  {event.tripId ? (
                    <Link href={`/admin/trips/${event.tripId}`} className="admin-drawer-link">
                      #{event.tripId}
                    </Link>
                  ) : "-"}
                </dd>
              </div>
            </dl>
          </section>

          <section className="admin-drawer-section">
            <h4>Model & runtime</h4>
            <dl className="admin-drawer-list">
              <div>
                <dt>Model</dt>
                <dd>{event.model || "-"}</dd>
              </div>
              <div>
                <dt>Finish reason</dt>
                <dd>{event.finishReason || "-"}</dd>
              </div>
              <div>
                <dt>Thời gian xử lý</dt>
                <dd>{event.durationMs !== undefined ? `${formatNumber(event.durationMs)} ms` : "-"}</dd>
              </div>
            </dl>
          </section>

          <section className="admin-drawer-section">
            <h4>Token</h4>
            <div className="admin-drawer-credit-grid admin-ai-token-grid">
              <div>
                <span>Input</span>
                <strong>{formatNumber(event.promptTokens)}</strong>
              </div>
              <div>
                <span>Output</span>
                <strong>{formatNumber(event.outputTokens)}</strong>
              </div>
              <div>
                <span>Thinking</span>
                <strong>{formatNumber(event.thinkingTokens)}</strong>
              </div>
              <div>
                <span>Tổng</span>
                <strong>{formatNumber(event.totalTokens)}</strong>
              </div>
            </div>
          </section>

          <section className="admin-drawer-section">
            <h4>Chi phí</h4>
            <dl className="admin-drawer-list">
              <div>
                <dt>Ước tính VND</dt>
                <dd>{formatCurrency(event.estimatedCostVnd)}</dd>
              </div>
              <div>
                <dt>Ước tính USD</dt>
                <dd>{formatUsd(event.estimatedCostUsd)}</dd>
              </div>
              <div>
                <dt>Max output</dt>
                <dd>{event.maxOutputTokens ? formatNumber(event.maxOutputTokens) : "-"}</dd>
              </div>
              <div>
                <dt>Thinking budget</dt>
                <dd>{event.thinkingBudget ? formatNumber(event.thinkingBudget) : "-"}</dd>
              </div>
            </dl>
          </section>

          {(event.errorCode || event.errorMessage) && (
            <section className="admin-drawer-section">
              <h4>Lỗi ngắn</h4>
              <dl className="admin-drawer-list">
                <div>
                  <dt>Mã lỗi</dt>
                  <dd>{event.errorCode || "-"}</dd>
                </div>
                <div>
                  <dt>Nội dung</dt>
                  <dd>{event.errorMessage || "-"}</dd>
                </div>
              </dl>
            </section>
          )}

          {event.errorDetail && event.errorDetail !== event.errorMessage && (
            <section className="admin-drawer-section">
              <h4>Chi tiết lỗi đầy đủ</h4>
              <p style={{ fontSize: 13, color: "var(--admin-text-secondary, #64748b)", lineHeight: 1.6, margin: 0, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
                {event.errorDetail}
              </p>
            </section>
          )}

          {event.rawResponseSnippet && (
            <section className="admin-drawer-section">
              <h4>Raw AI Response <span style={{ fontWeight: 400, fontSize: 12, color: "var(--admin-text-secondary, #94a3b8)" }}>(full response)</span></h4>
              <pre style={{
                fontSize: 11,
                lineHeight: 1.5,
                background: "var(--admin-surface-2, #0f172a)",
                color: "var(--admin-text-code, #94a3b8)",
                padding: "12px 14px",
                borderRadius: 8,
                overflowX: "auto",
                overflowY: "auto",
                maxHeight: 340,
                margin: 0,
                whiteSpace: "pre-wrap",
                wordBreak: "break-all",
              }}>
                {event.rawResponseSnippet}
              </pre>
            </section>
          )}
        </div>
      </aside>
    </div>
  );
}

function AiCostDashboard({
  summary,
  daily,
  events,
  loading,
  range,
  operation,
  status,
  search,
  page,
  customFrom,
  customTo,
  onRangeChange,
  onCustomFromChange,
  onCustomToChange,
  onOperationChange,
  onStatusChange,
  onSearchChange,
  onPageChange,
  onSelectEvent,
}: {
  summary: AdminAiCostSummary | null;
  daily: AdminAiCostDaily[];
  events: PageResponse<AdminAiUsageEvent> | null;
  loading: boolean;
  range: AiRangeKey;
  operation: "ALL" | AdminAiOperation;
  status: "ALL" | AdminAiStatus;
  search: string;
  page: number;
  customFrom: string;
  customTo: string;
  onRangeChange: (range: AiRangeKey) => void;
  onCustomFromChange: (value: string) => void;
  onCustomToChange: (value: string) => void;
  onOperationChange: (operation: "ALL" | AdminAiOperation) => void;
  onStatusChange: (status: "ALL" | AdminAiStatus) => void;
  onSearchChange: (value: string) => void;
  onPageChange: (page: number) => void;
  onSelectEvent: (event: AdminAiUsageEvent) => void;
}) {
  const maxDailyCost = Math.max(...daily.map((item) => item.totalCostVnd), 1);
  const dailyNewestFirst = [...daily].sort((left, right) => right.date.localeCompare(left.date));
  const totalUsageTokens = Math.max(
    (summary?.promptTokens ?? 0) + (summary?.outputTokens ?? 0) + (summary?.thinkingTokens ?? 0),
    1,
  );

  return (
    <section className="admin-panel admin-ai-panel">
      <div className="admin-panel-header">
        <div>
          <h2>AI chi phí</h2>
        </div>
      </div>

      <div className="admin-filter-bar admin-ai-filter-bar">
        <div className="admin-ai-range">
          {[
            ["today", "Hôm nay"],
            ["7d", "7 ngày"],
            ["30d", "30 ngày"],
            ["custom", "Tùy chọn"],
          ].map(([value, label]) => (
            <button
              key={value}
              type="button"
              className={range === value ? "active" : ""}
              onClick={() => onRangeChange(value as AiRangeKey)}
            >
              {label}
            </button>
          ))}
        </div>
        {range === "custom" && (
          <div className="admin-ai-date-range">
            <input
              type="date"
              value={customFrom}
              onChange={(event) => onCustomFromChange(event.target.value)}
              aria-label="Từ ngày"
            />
            <span>đến</span>
            <input
              type="date"
              value={customTo}
              onChange={(event) => onCustomToChange(event.target.value)}
              aria-label="Đến ngày"
            />
          </div>
        )}
        <select className="admin-filter-select" value={operation} onChange={(event) => onOperationChange(event.target.value as "ALL" | AdminAiOperation)}>
          <option value="ALL">Tất cả luồng</option>
          {aiOperationOptions.map((item) => (
            <option key={item} value={item}>{aiOperationLabel(item)}</option>
          ))}
        </select>
        <select className="admin-filter-select" value={status} onChange={(event) => onStatusChange(event.target.value as "ALL" | AdminAiStatus)}>
          <option value="ALL">Tất cả trạng thái</option>
          {aiStatusOptions.map((item) => (
            <option key={item} value={item}>{aiStatusLabel(item)}</option>
          ))}
        </select>
      </div>

      <div className="admin-ai-overview">
        <StatCard label="Chi phí khoảng chọn" value={summary ? formatCurrency(summary.totalCostVnd) : "-"} icon={<CreditCard size={18} />} />
        <StatCard
          label="Requests"
          value={summary ? formatNumber(summary.requests) : "-"}
          note={summary ? `${formatNumber(summary.attempts)} attempts` : undefined}
          icon={<BarChart3 size={18} />}
        />
        <StatCard label="Retry rate" value={summary ? formatPercent(summary.retryRate) : "-"} icon={<BarChart3 size={18} />} />
        <StatCard label="Error rate" value={summary ? formatPercent(summary.errorRate) : "-"} icon={<BarChart3 size={18} />} />
        <StatCard label="Avg latency" value={summary ? formatDurationMs(summary.avgDurationMs) : "-"} icon={<BarChart3 size={18} />} />
      </div>

      <div className="admin-ai-grid admin-ai-grid-balanced">
        <section className="admin-ai-card">
          <div className="admin-ai-card-header">
            <h3>Chi phí theo ngày</h3>
            <span>{daily.length} ngày</span>
          </div>
          <div className="admin-ai-trend">
            {daily.length === 0 ? (
              <p className="admin-ai-empty">Chưa có dữ liệu trong khoảng này.</p>
            ) : dailyNewestFirst.map((item) => (
              <div className="admin-ai-trend-row" key={item.date}>
                <span>{item.date}</span>
                <div>
                  <i style={{ width: `${Math.max(3, (item.totalCostVnd / maxDailyCost) * 100)}%` }} />
                </div>
                <strong>{formatCurrency(item.totalCostVnd)}</strong>
              </div>
            ))}
          </div>
        </section>

        <section className="admin-ai-card">
          <div className="admin-ai-card-header">
            <h3>Token usage</h3>
            <span>{formatNumber(summary?.totalTokens ?? 0)} token</span>
          </div>
          <div className="admin-ai-token-breakdown">
            {[
              ["Input", summary?.promptTokens ?? 0],
              ["Output", summary?.outputTokens ?? 0],
              ["Thinking", summary?.thinkingTokens ?? 0],
            ].map(([label, value]) => (
              <div key={label as string}>
                <div>
                  <span>{label}</span>
                  <strong>{formatNumber(value as number)}</strong>
                </div>
                <i style={{ width: `${Math.max(2, ((value as number) / totalUsageTokens) * 100)}%` }} />
              </div>
            ))}
          </div>
        </section>

        <section className="admin-ai-card">
          <div className="admin-ai-card-header">
            <h3>Avg cost</h3>
            <span>Mỗi operation</span>
          </div>
          <div className="admin-ai-average-list">
            {(summary?.averageCosts ?? []).length === 0 ? (
              <p className="admin-ai-empty">Chưa có operation thành công.</p>
            ) : summary?.averageCosts.map((item) => (
              <div key={item.operation}>
                <span>{aiOperationLabel(item.operation)}</span>
                <strong>{formatCurrency(item.avgCostVnd)}</strong>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="admin-ai-grid admin-ai-grid-health">
        <section className="admin-ai-card">
          <div className="admin-ai-card-header">
            <h3>Sức khỏe theo luồng</h3>
            <span>{summary?.operationHealth.length ?? 0} luồng</span>
          </div>
          <div className="admin-ai-health-list">
            {(summary?.operationHealth ?? []).length === 0 ? (
              <p className="admin-ai-empty">Chưa có dữ liệu.</p>
            ) : summary?.operationHealth.map((item) => (
              <div key={item.operation} className="admin-ai-health-item">
                <div>
                  <strong>{aiOperationLabel(item.operation)}</strong>
                  <span>{formatNumber(item.requests)} requests · {formatNumber(item.attempts)} attempts</span>
                </div>
                <dl>
                  <div>
                    <dt>Cost</dt>
                    <dd>{formatCurrency(item.totalCostVnd)}</dd>
                  </div>
                  <div>
                    <dt>Retry</dt>
                    <dd>{formatPercent(item.retryRate)}</dd>
                  </div>
                  <div>
                    <dt>Lỗi</dt>
                    <dd>{formatPercent(item.errorRate)}</dd>
                  </div>
                  <div>
                    <dt>Avg</dt>
                    <dd>{formatDurationMs(item.avgDurationMs)}</dd>
                  </div>
                  <div>
                    <dt>Max</dt>
                    <dd>{formatDurationMs(item.maxDurationMs)}</dd>
                  </div>
                </dl>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="admin-filter-bar">
        <label className="admin-search-field">
          <Search size={15} />
          <input
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="Tìm theo email hoặc request ID"
          />
        </label>
      </div>

      <div className="admin-table-wrap">
        <table className="admin-table admin-ai-table">
          <thead>
            <tr>
              <th>STT</th>
              <th>Thời gian</th>
              <th>Luồng</th>
              <th>User</th>
              <th>Cost</th>
              <th>Trạng thái</th>
              <th>Hành động</th>
            </tr>
          </thead>
          <tbody>
            {events?.content.map((item, index) => (
              <tr key={item.id}>
                <td>{rowNumber(page, index)}</td>
                <td>{formatDate(item.createdAt)}</td>
                <td>
                  <strong>{aiOperationLabel(item.operation)}</strong>
                  <span>Attempt #{item.attemptNumber}</span>
                </td>
                <td>{item.userEmail || "-"}</td>
                <td>
                  <strong>{formatCurrency(item.estimatedCostVnd)}</strong>
                </td>
                <td><StatusBadge tone={aiStatusTone(item.status)}>{aiStatusLabel(item.status)}</StatusBadge></td>
                <td>
                  <button type="button" className="admin-row-action" onClick={() => onSelectEvent(item)}>
                    <Eye size={15} /> Xem
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Pagination page={page} totalPages={events?.totalPages ?? 1} loading={loading} onPageChange={onPageChange} />
    </section>
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
  const [aiCostSummary, setAiCostSummary] = useState<AdminAiCostSummary | null>(null);
  const [aiCostDaily, setAiCostDaily] = useState<AdminAiCostDaily[]>([]);
  const [aiCostEvents, setAiCostEvents] = useState<PageResponse<AdminAiUsageEvent> | null>(null);
  const [selectedTransaction, setSelectedTransaction] = useState<AdminTransactionSummary | null>(null);
  const [selectedAiEvent, setSelectedAiEvent] = useState<AdminAiUsageEvent | null>(null);
  const [activeTab, setActiveTab] = useState<AdminTab>("users");
  const [userPage, setUserPage] = useState(0);
  const [tripPage, setTripPage] = useState(0);
  const [transactionPage, setTransactionPage] = useState(0);
  const [aiCostPage, setAiCostPage] = useState(0);
  const [aiCostRange, setAiCostRange] = useState<AiRangeKey>("7d");
  const defaultAiRange = aiDateRange("7d");
  const [aiCustomFrom, setAiCustomFrom] = useState(defaultAiRange.from);
  const [aiCustomTo, setAiCustomTo] = useState(defaultAiRange.to);
  const [aiOperationFilter, setAiOperationFilter] = useState<"ALL" | AdminAiOperation>("ALL");
  const [aiStatusFilter, setAiStatusFilter] = useState<"ALL" | AdminAiStatus>("ALL");
  const [aiCostSearch, setAiCostSearch] = useState("");
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
    if (authLoading || !authorized) return;
    let cancelled = false;
    queueMicrotask(() => {
      if (cancelled) return;
      setLoading(true);
      setError("");
    });
    const aiRange = aiDateRange(aiCostRange, aiCustomFrom, aiCustomTo);
    const aiFilters = {
      ...aiRange,
      operation: aiOperationFilter,
      status: aiStatusFilter,
    };

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
      adminApi.aiCostSummary(aiFilters),
      adminApi.aiCostDaily(aiFilters),
      adminApi.aiCostEvents(aiCostPage, pageSize, {
        ...aiFilters,
        q: aiCostSearch,
      }),
    ])
      .then(([nextStats, nextUsers, nextTrips, nextTransactions, nextAiSummary, nextAiDaily, nextAiEvents]) => {
        if (cancelled) return;
        setStats(nextStats);
        setUsers(nextUsers);
        setTrips(nextTrips);
        setTransactions(nextTransactions);
        setAiCostSummary(nextAiSummary);
        setAiCostDaily(nextAiDaily);
        setAiCostEvents(nextAiEvents);
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
    aiCostRange,
    aiCustomFrom,
    aiCustomTo,
    aiCostPage,
    aiCostSearch,
    aiOperationFilter,
    aiStatusFilter,
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
            <button
              type="button"
              className={activeTab === "ai-cost" ? "active" : ""}
              onClick={() => setActiveTab("ai-cost")}
            >
              AI chi phí
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
                    onChange={(e) => {
                      setUserPage(0);
                      setUserSearch(e.target.value);
                    }}
                    placeholder="Tìm theo tên hoặc email"
                  />
                </label>
                <select
                  className="admin-filter-select"
                  value={userRoleFilter}
                  onChange={(e) => {
                    setUserPage(0);
                    setUserRoleFilter(e.target.value as typeof userRoleFilter);
                  }}
                >
                  <option value="ALL">Tất cả quyền</option>
                  <option value="USER">User</option>
                  <option value="ADMIN">Admin</option>
                </select>
                <select
                  className="admin-filter-select"
                  value={userProviderFilter}
                  onChange={(e) => {
                    setUserPage(0);
                    setUserProviderFilter(e.target.value as typeof userProviderFilter);
                  }}
                >
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
                    onChange={(e) => {
                      setTripPage(0);
                      setTripSearch(e.target.value);
                    }}
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
          ) : activeTab === "transactions" ? (
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
                    onChange={(e) => {
                      setTransactionPage(0);
                      setTransactionSearch(e.target.value);
                    }}
                    placeholder="Tìm theo mã đơn, email hoặc mã gói"
                  />
                </label>
                <select
                  className="admin-filter-select"
                  value={transactionStatusFilter}
                  onChange={(e) => {
                    setTransactionPage(0);
                    setTransactionStatusFilter(e.target.value as typeof transactionStatusFilter);
                  }}
                >
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
          ) : (
            <AiCostDashboard
              summary={aiCostSummary}
              daily={aiCostDaily}
              events={aiCostEvents}
              loading={loading}
              range={aiCostRange}
              operation={aiOperationFilter}
              status={aiStatusFilter}
              search={aiCostSearch}
              page={aiCostPage}
              customFrom={aiCustomFrom}
              customTo={aiCustomTo}
              onRangeChange={(value) => {
                setAiCostPage(0);
                setAiCostRange(value);
              }}
              onCustomFromChange={(value) => {
                setAiCostPage(0);
                setAiCustomFrom(value);
              }}
              onCustomToChange={(value) => {
                setAiCostPage(0);
                setAiCustomTo(value);
              }}
              onOperationChange={(value) => {
                setAiCostPage(0);
                setAiOperationFilter(value);
              }}
              onStatusChange={(value) => {
                setAiCostPage(0);
                setAiStatusFilter(value);
              }}
              onSearchChange={(value) => {
                setAiCostPage(0);
                setAiCostSearch(value);
              }}
              onPageChange={setAiCostPage}
              onSelectEvent={setSelectedAiEvent}
            />
          )}
        </section>
      </main>
      {selectedTransaction && (
        <TransactionDrawer
          transaction={selectedTransaction}
          onClose={() => setSelectedTransaction(null)}
        />
      )}
      {selectedAiEvent && (
        <AiUsageDrawer
          event={selectedAiEvent}
          onClose={() => setSelectedAiEvent(null)}
        />
      )}
    </>
  );
}
