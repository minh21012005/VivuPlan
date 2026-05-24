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
];

function fmtVnd(value: number) {
  return value.toLocaleString("vi-VN");
}

function packageCopy(item: BillingPackage) {
  const copy: Record<string, { name: string; eyebrow: string; bestFor: string }> = {
    PLAN_1: {
      name: "Cơ bản",
      eyebrow: "Cho một chuyến đi",
      bestFor: "Phù hợp khi bạn cần lên lịch trình cho một chuyến cụ thể.",
    },
    PLAN_3: {
      name: "Tiêu chuẩn",
      eyebrow: "Phổ biến",
      bestFor: "Dành cho vài chuyến ngắn hoặc khi bạn muốn có thêm lựa chọn.",
    },
    PLAN_10: {
      name: "Tiết kiệm",
      eyebrow: "Giá tốt nhất",
      bestFor: "Tối ưu cho người thường xuyên lên kế hoạch du lịch.",
    },
  };
  return copy[item.code] ?? { name: item.name, eyebrow: "Gói lịch trình", bestFor: "Tạo lịch trình bằng AI." };
}

export default function PricingPage() {
  const router = useRouter();
  const { isLoggedIn, loading: authLoading } = useAuth();
  const [packages, setPackages] = useState<BillingPackage[]>(fallbackPackages);
  const [wallet, setWallet] = useState<BillingWallet | null>(null);
  const [purchaseOpen, setPurchaseOpen] = useState(false);

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

  const startPurchase = () => {
    if (!isLoggedIn && !authLoading) {
      router.push("/login");
      return;
    }
    setPurchaseOpen(true);
  };

  return (
    <div className="pricing-page">
      <Navbar />

      <section className="pricing-hero">
        <div className="container pricing-hero-inner">
          <div className="pricing-hero-copy">
            <span className="badge badge-teal pricing-hero-badge">
              <Sparkles size={13} /> Gói lập lịch trình
            </span>
            <h1>Chọn gói cho chuyến đi tiếp theo</h1>
            <p>
              Tạo lịch trình chi tiết trong vài phút, rồi chỉnh lại từng ngày nếu
              muốn đổi nhịp đi, ngân sách hoặc trải nghiệm. Bắt đầu miễn phí và
              chỉ mua thêm khi bạn cần lên chuyến mới.
            </p>
          </div>

          <div className="pricing-balance-panel">
            {wallet ? (
              <>
                <div className="pricing-balance-title">Số lượt hiện có</div>
                <div className="pricing-balance-grid">
                  <div className="pricing-balance-item">
                    <span><Zap size={13} /> Tạo lịch trình</span>
                    <strong>{wallet.planCredits}</strong>
                  </div>
                  <div className="pricing-balance-item">
                    <span><CreditCard size={13} /> Chỉnh ngày</span>
                    <strong>{wallet.editCredits}</strong>
                  </div>
                </div>
              </>
            ) : (
              <p>
                Tài khoản mới được tặng sẵn lượt dùng thử để bạn trải nghiệm trước
                khi mua thêm.
              </p>
            )}
          </div>
        </div>
      </section>

      <section className="pricing-plans">
        <div className="container">
          <div className="pricing-grid">
            <article className="pricing-card">
              <div className="pricing-card-head">
                <div>
                  <span className="pricing-card-eyebrow">Bắt đầu</span>
                  <h2>Dùng thử</h2>
                </div>
                <span className="pricing-pill">Miễn phí</span>
              </div>

              <div className="pricing-price">
                <strong>0<span>đ</span></strong>
                <span>Dành cho tài khoản mới</span>
              </div>

              <Link href={isLoggedIn ? "/plan" : "/register"} className="btn btn-secondary pricing-card-cta">
                {isLoggedIn ? "Tạo lịch trình" : "Đăng ký miễn phí"}
              </Link>

              <div className="pricing-divider" />
              <div className="pricing-includes-title">Bao gồm</div>

              <ul className="pricing-benefits">
                <li><Check size={14} /> 1 lịch trình mới</li>
                <li><Check size={14} /> 1 lần chỉnh ngày bằng AI</li>
                <li><Check size={14} /> Đầy đủ tính năng trong lịch trình</li>
              </ul>
            </article>

            {packages.map((item) => {
              const copy = packageCopy(item);
              return (
                <article key={item.code} className={item.highlighted ? "pricing-card pricing-card-featured" : "pricing-card"}>
                  <div className="pricing-card-head">
                    <div>
                      <span className="pricing-card-eyebrow">{copy.eyebrow}</span>
                      <h2>{copy.name}</h2>
                    </div>
                    {item.highlighted && <span className="pricing-pill pricing-pill-primary">Phù hợp nhất</span>}
                  </div>

                  <div className="pricing-price">
                    <strong>{fmtVnd(item.amount)}<span>đ</span></strong>
                    <span>Thanh toán một lần</span>
                  </div>

                  <button
                    type="button"
                    className={item.highlighted ? "btn btn-primary pricing-card-cta" : "btn btn-secondary pricing-card-cta"}
                    onClick={startPurchase}
                  >
                    Chọn gói
                  </button>

                  <div className="pricing-divider" />
                  <div className="pricing-includes-title">Bao gồm</div>

                  <ul className="pricing-benefits">
                    <li><Check size={14} /> {item.planCredits} lịch trình mới</li>
                    <li><Check size={14} /> {item.editCredits} lần chỉnh ngày bằng AI</li>
                    <li><Check size={14} /> Chỉ trừ lượt khi AI tạo thành công</li>
                  </ul>
                </article>
              );
            })}
          </div>
        </div>
      </section>

      <PurchaseModal
        open={purchaseOpen}
        reason="PLAN"
        onClose={() => setPurchaseOpen(false)}
        onPaid={refreshWallet}
      />
    </div>
  );
}
