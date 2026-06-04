"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import Navbar from "@/components/layout/Navbar";
import { PurchaseModal } from "@/components/billing/PurchaseModal";
import { billingApi, type BillingPackage } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import { useBilling } from "@/hooks/useBilling";
import { Check, CreditCard, Sparkles, Zap } from "lucide-react";

const fallbackPackages: BillingPackage[] = [
  { code: "PLAN_BASIC", name: "Gói cơ bản", description: "2 lượt lập lịch trình, 2 lượt chỉnh sửa ngày và 3 lượt gợi ý điểm đến phù hợp", amount: 10_000, planCredits: 2, editCredits: 2, suggestionCredits: 3 },
  { code: "PLAN_STANDARD", name: "Gói tiêu chuẩn", description: "5 lượt lập lịch trình, 5 lượt chỉnh sửa ngày và 8 lượt gợi ý điểm đến phù hợp", amount: 19_000, planCredits: 5, editCredits: 5, suggestionCredits: 8, highlighted: true },
  { code: "PLAN_SAVING", name: "Gói tiết kiệm", description: "12 lượt lập lịch trình, 12 lượt chỉnh sửa ngày và 20 lượt gợi ý điểm đến phù hợp", amount: 39_000, planCredits: 12, editCredits: 12, suggestionCredits: 20 },
];

function fmtVnd(value: number) {
  return value.toLocaleString("vi-VN");
}

function packageCopy(item: BillingPackage) {
  const copy: Record<string, { name: string; eyebrow: string; bestFor: string }> = {
    PLAN_BASIC: {
      name: "Cơ bản",
      eyebrow: "Linh hoạt",
      bestFor: "Phù hợp khi bạn muốn lên vài phương án đầu tiên.",
    },
    PLAN_STANDARD: {
      name: "Tiêu chuẩn",
      eyebrow: "Phổ biến",
      bestFor: "Dành cho nhiều chuyến ngắn hoặc nhóm cần thêm lựa chọn.",
    },
    PLAN_SAVING: {
      name: "Tiết kiệm",
      eyebrow: "Giá tốt nhất",
      bestFor: "Tối ưu cho người thường xuyên lên kế hoạch du lịch.",
    },
  };
  return copy[item.code] ?? { name: item.name, eyebrow: "Gói lịch trình", bestFor: "Lập lịch trình cho chuyến đi tiếp theo." };
}

export default function PricingPage() {
  const router = useRouter();
  const { isLoggedIn, loading: authLoading } = useAuth();
  const { wallet } = useBilling();
  const [packages, setPackages] = useState<BillingPackage[]>(fallbackPackages);
  const [purchaseOpen, setPurchaseOpen] = useState(false);
  const [purchasePackageCode, setPurchasePackageCode] = useState<string | undefined>();

  useEffect(() => {
    billingApi.packages()
      .then(setPackages)
      .catch(() => setPackages(fallbackPackages));
  }, []);

  const startPurchase = (packageCode: string) => {
    if (authLoading) return;
    if (!isLoggedIn && !authLoading) {
      router.push("/login");
      return;
    }
    setPurchasePackageCode(packageCode);
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
              muốn đổi nhịp đi, ngân sách hoặc trải nghiệm.
            </p>
          </div>

          <div className="pricing-balance-panel">
            {wallet ? (
              <>
                <div className="pricing-balance-title">Số lượt hiện có</div>
                <div className="pricing-balance-grid">
                  <div className="pricing-balance-item">
                    <span><Zap size={13} /> Lập lịch</span>
                    <strong>{wallet.planCredits}</strong>
                  </div>
                  <div className="pricing-balance-item">
                    <span><CreditCard size={13} /> Chỉnh sửa</span>
                    <strong>{wallet.editCredits}</strong>
                  </div>
                  <div className="pricing-balance-item">
                    <span><Sparkles size={13} /> Gợi ý</span>
                    <strong>{wallet.suggestionCredits}</strong>
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
                <li><Check size={14} /> 1 lượt lập lịch trình</li>
                <li><Check size={14} /> 1 lượt chỉnh sửa ngày</li>
                <li><Check size={14} /> 1 lượt gợi ý điểm đến phù hợp</li>
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
                    disabled={authLoading}
                    onClick={() => startPurchase(item.code)}
                  >
                    Chọn gói
                  </button>

                  <div className="pricing-divider" />
                  <div className="pricing-includes-title">Bao gồm</div>

                  <ul className="pricing-benefits">
                    <li><Check size={14} /> {item.planCredits} lượt lập lịch trình</li>
                    <li><Check size={14} /> {item.editCredits} lượt chỉnh sửa ngày</li>
                    <li><Check size={14} /> {item.suggestionCredits} lượt gợi ý điểm đến phù hợp</li>
                    <li><Check size={14} /> Chỉ trừ lượt khi AI xử lý thành công</li>
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
        initialPackageCode={purchasePackageCode}
        onClose={() => setPurchaseOpen(false)}
      />
    </div>
  );
}
