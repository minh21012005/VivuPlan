"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import Navbar from "@/components/layout/Navbar";
import { PurchaseModal } from "@/components/billing/PurchaseModal";
import { billingApi, type BillingPackage, type BillingWallet } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import { Check, CreditCard, Sparkles, Zap } from "lucide-react";

const fallbackPackages: BillingPackage[] = [
  { code: "PLAN_1", name: "Gói một chuyến", description: "1 lượt tạo lịch trình + 2 lượt chỉnh ngày bằng AI", amount: 10_000, planCredits: 1, editCredits: 2 },
  { code: "PLAN_3", name: "Gói cuối tuần", description: "3 lượt tạo lịch trình + 9 lượt chỉnh ngày bằng AI", amount: 29_000, planCredits: 3, editCredits: 9, highlighted: true },
  { code: "PLAN_10", name: "Gói mê đi", description: "10 lượt tạo lịch trình + 35 lượt chỉnh ngày bằng AI", amount: 89_000, planCredits: 10, editCredits: 35 },
  { code: "EDIT_5", name: "Gói chỉnh ngày", description: "5 lượt chỉnh lại từng ngày bằng AI", amount: 5_000, planCredits: 0, editCredits: 5 },
];

function fmtVnd(value: number) {
  return `${value.toLocaleString("vi-VN")}đ`;
}

function packageCopy(item: BillingPackage) {
  const copy: Record<string, { name: string; description: string }> = {
    PLAN_1: {
      name: "Gói một chuyến",
      description: "Vừa đủ để tạo một lịch trình mới và chỉnh lại vài ngày nếu cần.",
    },
    PLAN_3: {
      name: "Gói cuối tuần",
      description: "Hợp với người hay so sánh vài điểm đến hoặc chuẩn bị nhiều chuyến ngắn.",
    },
    PLAN_10: {
      name: "Gói mê đi",
      description: "Dành cho người lập kế hoạch thường xuyên, nhóm bạn hoặc gia đình.",
    },
    EDIT_5: {
      name: "Gói chỉnh ngày",
      description: "Thêm lượt nhờ AI làm lại từng ngày, còn chỉnh tay thì luôn miễn phí.",
    },
  };
  return copy[item.code] ?? { name: item.name, description: item.description };
}

export default function PricingPage() {
  const router = useRouter();
  const { isLoggedIn, loading: authLoading } = useAuth();
  const [packages, setPackages] = useState<BillingPackage[]>(fallbackPackages);
  const [wallet, setWallet] = useState<BillingWallet | null>(null);
  const [purchaseOpen, setPurchaseOpen] = useState(false);
  const [purchaseReason, setPurchaseReason] = useState<"PLAN" | "EDIT">("PLAN");

  const refreshWallet = () => {
    if (!isLoggedIn) {
      setWallet(null);
      return;
    }
    billingApi.me()
      .then((data) => setWallet(data.wallet))
      .catch(() => setWallet(null));
  };

  useEffect(() => {
    billingApi.packages()
      .then(setPackages)
      .catch(() => setPackages(fallbackPackages));
  }, []);

  useEffect(() => {
    refreshWallet();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn]);

  const startPurchase = (code: string) => {
    if (!isLoggedIn && !authLoading) {
      router.push("/login");
      return;
    }
    setPurchaseReason(code === "EDIT_5" ? "EDIT" : "PLAN");
    setPurchaseOpen(true);
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <Navbar />

      <section style={{ paddingTop: 96, paddingBottom: 34, background: "#F8FAFC", borderBottom: "1px solid var(--border)" }}>
        <div className="container" style={{ display: "grid", gap: 16 }}>
          <span className="badge badge-teal" style={{ width: "fit-content" }}>
            <Sparkles size={13} /> Lượt AI
          </span>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 20, alignItems: "end", flexWrap: "wrap" }}>
            <div>
              <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "clamp(30px,4vw,48px)", fontWeight: 900, color: "var(--text)", margin: "0 0 10px" }}>
                Mua lượt AI khi bạn cần
              </h1>
              <p style={{ fontSize: 16, color: "var(--text-3)", maxWidth: 640, margin: 0, lineHeight: 1.65 }}>
                VivuPlan không chia tính năng thành nhiều tầng khó hiểu. Bạn chỉ mua lượt để tạo lịch trình mới hoặc nhờ AI chỉnh lại từng ngày; thời tiết, ngân sách, chia sẻ và chỉnh tay vẫn dùng bình thường trong lịch trình.
              </p>
            </div>
            {wallet && (
              <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                <span className="badge badge-teal"><Zap size={13} /> Còn {wallet.planCredits} lượt tạo</span>
                <span className="badge badge-blue"><CreditCard size={13} /> Còn {wallet.editCredits} lượt chỉnh AI</span>
              </div>
            )}
          </div>
        </div>
      </section>

      <section style={{ padding: "38px 0 80px" }}>
        <div className="container">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(230px, 1fr))", gap: 18 }}>
            <div style={{
              background: "var(--surface)",
              border: "1px solid var(--border)",
              borderRadius: 12,
              padding: 22,
              boxShadow: "var(--shadow-sm)",
            }}>
              <span className="badge badge-green">Dùng thử</span>
              <h2 style={{ fontSize: 24, margin: "16px 0 6px", color: "var(--text)" }}>0đ</h2>
              <p style={{ color: "var(--text-3)", fontSize: 14, lineHeight: 1.55, minHeight: 44 }}>
                Tài khoản mới có sẵn 1 lượt tạo lịch trình và 1 lượt chỉnh ngày bằng AI.
              </p>
              <ul style={{ listStyle: "none", padding: 0, display: "grid", gap: 10, margin: "18px 0 22px", color: "var(--text-2)", fontSize: 14 }}>
                <li><Check size={14} /> Tạo 1 lịch trình bằng AI</li>
                <li><Check size={14} /> Chỉnh lại 1 ngày bằng AI</li>
                <li><Check size={14} /> Các tính năng trong lịch trình vẫn mở đầy đủ</li>
              </ul>
              <Link href={isLoggedIn ? "/plan" : "/register"} className="btn btn-secondary" style={{ width: "100%", justifyContent: "center" }}>
                {isLoggedIn ? "Tạo lịch trình" : "Đăng ký miễn phí"}
              </Link>
            </div>

            {packages.map((item) => (
              <div key={item.code} style={{
                background: "var(--surface)",
                border: `2px solid ${item.highlighted ? "var(--primary)" : "var(--border)"}`,
                borderRadius: 12,
                padding: 22,
                boxShadow: item.highlighted ? "0 12px 34px rgba(15,159,156,0.16)" : "var(--shadow-sm)",
                position: "relative",
              }}>
                {item.highlighted && <span className="badge badge-teal" style={{ position: "absolute", top: 14, right: 14 }}>Phù hợp nhất</span>}
                <span className="badge badge-blue">{item.planCredits > 0 ? "Tạo lịch trình" : "Chỉnh ngày"}</span>
                <h2 style={{ fontSize: 24, margin: "16px 0 6px", color: "var(--text)" }}>{fmtVnd(item.amount)}</h2>
                <h3 style={{ fontSize: 18, margin: "0 0 8px", color: "var(--text)" }}>{packageCopy(item).name}</h3>
                <p style={{ color: "var(--text-3)", fontSize: 14, lineHeight: 1.55, minHeight: 54 }}>{packageCopy(item).description}</p>
                <ul style={{ listStyle: "none", padding: 0, display: "grid", gap: 10, margin: "18px 0 22px", color: "var(--text-2)", fontSize: 14 }}>
                  {item.planCredits > 0 && <li><Check size={14} /> {item.planCredits} lượt tạo lịch trình</li>}
                  {item.editCredits > 0 && <li><Check size={14} /> {item.editCredits} lượt chỉnh ngày bằng AI</li>}
                  <li><Check size={14} /> Chỉ trừ lượt khi AI tạo xong</li>
                </ul>
                <button type="button" className={item.highlighted ? "btn btn-primary" : "btn btn-secondary"} style={{ width: "100%", justifyContent: "center" }} onClick={() => startPurchase(item.code)}>
                  Mua gói này
                </button>
              </div>
            ))}
          </div>
        </div>
      </section>

      <PurchaseModal
        open={purchaseOpen}
        reason={purchaseReason}
        onClose={() => setPurchaseOpen(false)}
        onPaid={refreshWallet}
      />
    </div>
  );
}
