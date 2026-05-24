"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { billingApi, type BillingOrder, type BillingPackage } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import { useBilling } from "@/hooks/useBilling";
import { CheckCircle2, Clock, CreditCard, QrCode, Sparkles, X } from "lucide-react";

type PurchaseReason = "PLAN" | "EDIT";

interface PurchaseModalProps {
  open: boolean;
  reason?: PurchaseReason;
  initialPackageCode?: string;
  onClose: () => void;
  onPaid?: () => void;
}

const fallbackPackages: BillingPackage[] = [
  { code: "PLAN_1", name: "Cơ bản", description: "1 lịch trình mới + 2 lần chỉnh ngày", amount: 10_000, planCredits: 1, editCredits: 2 },
  { code: "PLAN_3", name: "Tiêu chuẩn", description: "3 lịch trình mới + 9 lần chỉnh ngày", amount: 29_000, planCredits: 3, editCredits: 9, highlighted: true },
  { code: "PLAN_10", name: "Tiết kiệm", description: "10 lịch trình mới + 35 lần chỉnh ngày", amount: 89_000, planCredits: 10, editCredits: 35 },
];

function fmtVnd(value: number) {
  return `${value.toLocaleString("vi-VN")}đ`;
}

function secondsLeft(expiresAt?: string) {
  if (!expiresAt) return 0;
  return Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));
}

function fmtCountdown(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function packageCopy(item: BillingPackage) {
  const copy: Record<string, { name: string; description: string }> = {
    PLAN_1: {
      name: "Cơ bản",
      description: "1 lịch trình mới, kèm 2 lần chỉnh ngày.",
    },
    PLAN_3: {
      name: "Tiêu chuẩn",
      description: "3 lịch trình mới, kèm 9 lần chỉnh ngày.",
    },
    PLAN_10: {
      name: "Tiết kiệm",
      description: "10 lịch trình mới, kèm 35 lần chỉnh ngày.",
    },
  };
  return copy[item.code] ?? { name: item.name, description: item.description };
}

function statusLabel(status: BillingOrder["status"]) {
  const labels: Record<BillingOrder["status"], string> = {
    PENDING: "Đang chờ thanh toán",
    PAID: "Đã thanh toán",
    UNDERPAID: "Chưa đủ số tiền",
    EXPIRED: "Đã hết hạn",
    CANCELLED: "Đã hủy",
  };
  return labels[status];
}

export function PurchaseModal({ open, reason = "PLAN", initialPackageCode, onClose, onPaid }: PurchaseModalProps) {
  const router = useRouter();
  const { isLoggedIn, loading: authLoading } = useAuth();
  const { wallet, refreshWallet } = useBilling();
  const [packages, setPackages] = useState<BillingPackage[]>(fallbackPackages);
  const [selectedCode, setSelectedCode] = useState(initialPackageCode ?? "PLAN_3");
  const [order, setOrder] = useState<BillingOrder | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [remaining, setRemaining] = useState(0);
  const [pickerManuallyOpened, setPickerManuallyOpened] = useState(false);
  const autoCreatedCodeRef = useRef<string | null>(null);

  const recommended = useMemo(() => new Set(["PLAN_1", "PLAN_3", "PLAN_10"]), []);

  const visiblePackages = useMemo(() => {
    const list = packages.length ? packages : fallbackPackages;
    return [...list].sort((a, b) => {
      const ar = recommended.has(a.code) ? 0 : 1;
      const br = recommended.has(b.code) ? 0 : 1;
      return ar - br || a.amount - b.amount;
    });
  }, [packages, recommended]);
  const packagePickerVisible = !initialPackageCode || pickerManuallyOpened;

  const createOrder = useCallback(async (packageCode: string) => {
    if (!isLoggedIn) {
      router.push("/login");
      return;
    }
    setSelectedCode(packageCode);
    setPickerManuallyOpened(false);
    setLoading(true);
    setMessage("");
    try {
      const nextOrder = await billingApi.createOrder(packageCode);
      setOrder(nextOrder);
      setRemaining(secondsLeft(nextOrder.expiresAt));
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Không thể tạo mã thanh toán. Vui lòng thử lại.");
      autoCreatedCodeRef.current = null;
      setPickerManuallyOpened(true);
    } finally {
      setLoading(false);
    }
  }, [isLoggedIn, router]);

  useEffect(() => {
    if (!open) return;
    setOrder(null);
    setMessage("");
    setSelectedCode(initialPackageCode ?? "PLAN_3");
    setPickerManuallyOpened(false);
    billingApi.packages()
      .then(setPackages)
      .catch(() => setPackages(fallbackPackages));
  }, [open, reason, initialPackageCode, isLoggedIn]);

  useEffect(() => {
    if (open) return;
    autoCreatedCodeRef.current = null;
  }, [open]);

  useEffect(() => {
    if (!open || !initialPackageCode || authLoading || !isLoggedIn || packagePickerVisible) return;
    if (autoCreatedCodeRef.current === initialPackageCode) return;
    autoCreatedCodeRef.current = initialPackageCode;
    void createOrder(initialPackageCode);
  }, [open, initialPackageCode, authLoading, isLoggedIn, packagePickerVisible, createOrder]);

  useEffect(() => {
    if (!order || order.status !== "PENDING") return;
    setRemaining(secondsLeft(order.expiresAt));
    const countdown = window.setInterval(() => {
      setRemaining(secondsLeft(order.expiresAt));
    }, 1000);
    const poll = window.setInterval(async () => {
      try {
        const latest = await billingApi.getOrder(order.orderCode);
        setOrder(latest);
        if (latest.status === "PAID") {
          await refreshWallet();
          setMessage("Thanh toán thành công. Lượt mới đã được cộng vào tài khoản của bạn.");
          onPaid?.();
        }
        if (latest.status === "UNDERPAID") {
          setMessage("Giao dịch chưa đủ số tiền. Vui lòng liên hệ hỗ trợ để được xử lý.");
        }
        if (latest.status === "EXPIRED") {
          setMessage("Mã thanh toán đã hết hạn. Bạn có thể chọn lại gói và tạo mã mới.");
        }
      } catch {
        // Keep polling quietly while the modal is open.
      }
    }, 2500);
    return () => {
      window.clearInterval(countdown);
      window.clearInterval(poll);
    };
  }, [order, onPaid, refreshWallet]);

  if (!open) return null;

  const chooseDifferentPackage = async () => {
    const currentOrder = order;
    setPickerManuallyOpened(true);
    setOrder(null);
    autoCreatedCodeRef.current = initialPackageCode ?? null;
    if (currentOrder?.status === "PENDING") {
      await billingApi.cancelOrder(currentOrder.orderCode).catch(() => undefined);
    }
  };

  return (
    <div style={{
      position: "fixed",
      inset: 0,
      zIndex: 10000,
      background: "rgba(15, 23, 42, 0.48)",
      display: "grid",
      placeItems: "center",
      padding: 16,
    }}>
      <div role="dialog" aria-modal="true" style={{
        width: "min(920px, 100%)",
        maxHeight: "92vh",
        overflow: "auto",
        background: "var(--surface)",
        borderRadius: 16,
        boxShadow: "0 24px 80px rgba(15,23,42,0.28)",
        border: "1px solid var(--border)",
      }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "20px 22px", borderBottom: "1px solid var(--border)" }}>
          <div>
            <h2 style={{ margin: 0, fontSize: 22, fontWeight: 800, color: "var(--text)" }}>
              {order ? "Hoàn tất thanh toán" : reason === "EDIT" ? "Thêm lượt chỉnh ngày" : "Chọn gói cho chuyến đi"}
            </h2>
            <p style={{ margin: "6px 0 0", color: "var(--text-3)", fontSize: 14 }}>
              {order
                ? "Quét mã và chuyển khoản đúng nội dung để VivuPlan tự cộng lượt vào tài khoản của bạn."
                : "Chọn gói phù hợp, quét mã để thanh toán và VivuPlan sẽ tự cộng lượt vào tài khoản của bạn."}
            </p>
          </div>
          <button type="button" onClick={onClose} aria-label="Đóng" style={{ border: 0, background: "transparent", cursor: "pointer", color: "var(--text-3)" }}>
            <X size={22} />
          </button>
        </div>

        <div style={{ padding: 22 }}>
          {wallet && (
            <div style={{
              display: "flex",
              gap: 12,
              flexWrap: "wrap",
              marginBottom: 18,
              color: "var(--text-2)",
              fontSize: 14,
            }}>
              <span className="badge badge-teal"><Sparkles size={13} /> Còn {wallet.planCredits} lượt tạo</span>
              <span className="badge badge-blue"><CreditCard size={13} /> Còn {wallet.editCredits} lượt chỉnh AI</span>
            </div>
          )}

          {!order && !packagePickerVisible && (
            <div style={{ display: "grid", placeItems: "center", minHeight: 280, color: "var(--text-3)", textAlign: "center", gap: 12 }}>
              <Clock size={34} />
              <div>
                <strong style={{ display: "block", color: "var(--text)", fontSize: 16, marginBottom: 4 }}>Đang tạo mã thanh toán</strong>
                <span>VivuPlan đang chuẩn bị mã QR cho gói bạn đã chọn.</span>
              </div>
            </div>
          )}

          {!order && packagePickerVisible && (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 14 }}>
              {visiblePackages.map((item) => {
                const active = selectedCode === item.code;
                return (
                  <button
                    key={item.code}
                    type="button"
                    onClick={() => void createOrder(item.code)}
                    disabled={loading || authLoading}
                    style={{
                      textAlign: "left",
                      padding: 16,
                      borderRadius: 12,
                      border: `2px solid ${active || item.highlighted ? "var(--primary)" : "var(--border)"}`,
                      background: item.highlighted ? "rgba(15,159,156,0.08)" : "var(--surface)",
                      cursor: "pointer",
                      minHeight: 158,
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "flex-start" }}>
                      <strong style={{ fontSize: 16, color: "var(--text)" }}>{packageCopy(item).name}</strong>
                      {item.highlighted && <span className="badge badge-green">Phù hợp nhất</span>}
                    </div>
                    <div style={{ fontSize: 28, fontWeight: 900, color: "var(--primary)", margin: "14px 0 8px" }}>{fmtVnd(item.amount)}</div>
                    <p style={{ margin: 0, color: "var(--text-3)", fontSize: 13, lineHeight: 1.5 }}>{packageCopy(item).description}</p>
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 12 }}>
                      {item.planCredits > 0 && <span className="badge badge-teal">+{item.planCredits} lịch trình</span>}
                      {item.editCredits > 0 && <span className="badge badge-blue">+{item.editCredits} lần chỉnh</span>}
                    </div>
                  </button>
                );
              })}
            </div>
          )}

          {order && (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: 22, alignItems: "start" }}>
              <div style={{ border: "1px solid var(--border)", borderRadius: 12, padding: 14, display: "grid", placeItems: "center", minHeight: 280 }}>
                {order.qrUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={order.qrUrl} alt={`QR thanh toán ${order.orderCode}`} style={{ width: "100%", maxWidth: 280, height: "auto" }} />
                ) : (
                  <div style={{ display: "grid", placeItems: "center", gap: 10, color: "var(--text-3)", textAlign: "center" }}>
                    <QrCode size={64} />
                    <span>Chưa cấu hình ảnh QR thanh toán.</span>
                  </div>
                )}
              </div>
              <div>
                <div className="badge badge-teal" style={{ marginBottom: 12 }}>
                  {order.status === "PAID" ? <CheckCircle2 size={14} /> : <Clock size={14} />} {statusLabel(order.status)}
                </div>
                <h3 style={{ fontSize: 24, margin: "0 0 12px", color: "var(--text)" }}>Chuyển khoản {fmtVnd(order.amount)}</h3>
                <div style={{ display: "grid", gap: 10, fontSize: 14, color: "var(--text-2)" }}>
                  <div><strong>Nội dung chuyển khoản:</strong> <code style={{ fontSize: 16 }}>{order.orderCode}</code></div>
                  <div><strong>Gói này thêm:</strong> +{order.planCredits} lượt tạo, +{order.editCredits} lượt chỉnh AI</div>
                  <div><strong>Hết hạn sau:</strong> {fmtCountdown(remaining)}</div>
                </div>
                <p style={{ marginTop: 16, color: "var(--text-3)", lineHeight: 1.6, fontSize: 14 }}>
                  Sau khi ngân hàng ghi nhận giao dịch, VivuPlan sẽ tự cập nhật số lượt của bạn. Nếu vừa chuyển khoản xong mà chưa thấy đổi, hãy đợi thêm vài giây.
                </p>
                {message && (
                  <div style={{ marginTop: 14, padding: "12px 14px", borderRadius: 10, background: order.status === "PAID" ? "#F0FDF4" : "#FFF7ED", color: order.status === "PAID" ? "#15803D" : "#9A3412", fontSize: 14 }}>
                    {message}
                  </div>
                )}
                <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginTop: 18 }}>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => void chooseDifferentPackage()}
                  >
                    Chọn gói khác
                  </button>
                  <button type="button" className="btn btn-primary" onClick={onClose}>Đóng</button>
                </div>
              </div>
            </div>
          )}

          {message && !order && (
            <div style={{ marginTop: 14, padding: "12px 14px", borderRadius: 10, background: "#FEF2F2", color: "#B91C1C", fontSize: 14 }}>
              {message}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
