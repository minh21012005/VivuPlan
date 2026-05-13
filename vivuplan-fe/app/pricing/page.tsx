"use client";
import Link from "next/link";
import Navbar from "@/components/layout/Navbar";
import { Check, Zap, Star } from "lucide-react";

const plans = [
  {
    name: "Miễn phí",
    price: "0",
    period: "",
    description: "Hoàn hảo để bắt đầu khám phá",
    color: "#4ECDC4",
    features: [
      "3 lịch trình AI mỗi tháng",
      "Xem điểm đến phổ biến",
      "Tính năng cơ bản",
      "Lưu 2 lịch trình",
    ],
    cta: "Dùng miễn phí",
    href: "/register",
    popular: false,
  },
  {
    name: "Pro",
    price: "99.000",
    period: "/ tháng",
    description: "Dành cho người yêu du lịch thực sự",
    color: "#FF6B35",
    features: [
      "Tạo không giới hạn lịch trình AI",
      "Tối ưu tuyến đường nâng cao",
      "Ước tính ngân sách chi tiết",
      "Chia sẻ & cộng tác nhóm",
      "Xuất PDF lịch trình",
      "Ưu tiên hỗ trợ",
    ],
    cta: "Dùng thử 7 ngày",
    href: "/register?plan=pro",
    popular: true,
  },
  {
    name: "Team",
    price: "299.000",
    period: "/ tháng",
    description: "Cho nhóm du lịch & doanh nghiệp",
    color: "#FFE66D",
    features: [
      "Tất cả tính năng Pro",
      "Tới 10 thành viên",
      "Quản lý chuyến đi nhóm",
      "Phân chia chi phí thông minh",
      "API Access",
      "Hỗ trợ ưu tiên 24/7",
    ],
    cta: "Liên hệ tư vấn",
    href: "/contact",
    popular: false,
  },
];

export default function PricingPage() {
  return (
    <div className="min-h-screen" style={{ background: "var(--brand-dark)" }}>
      <Navbar />
      <div className="pt-28 pb-20 px-4">
        <div className="max-w-5xl mx-auto">
          {/* Header */}
          <div className="text-center mb-16">
            <div
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-xs font-semibold mb-5"
              style={{
                background: "rgba(255,107,53,0.12)",
                border: "1px solid rgba(255,107,53,0.25)",
                color: "var(--brand-primary)",
              }}
            >
              <Star size={12} fill="currentColor" /> Bảng giá
            </div>
            <h1
              className="text-4xl md:text-5xl font-black mb-4"
              style={{ fontFamily: "var(--font-heading)", color: "var(--brand-text)" }}
            >
              Đơn giản, minh bạch
            </h1>
            <p className="text-lg max-w-xl mx-auto" style={{ color: "var(--brand-text-muted)" }}>
              Bắt đầu miễn phí, nâng cấp khi bạn cần thêm sức mạnh AI
            </p>
          </div>

          {/* Plans */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 items-start">
            {plans.map((plan) => (
              <div
                key={plan.name}
                className="rounded-2xl p-7 relative transition-all duration-300"
                style={{
                  background: plan.popular ? `linear-gradient(135deg, rgba(255,107,53,0.08), rgba(78,205,196,0.05))` : "var(--brand-surface)",
                  border: `1px solid ${plan.popular ? "rgba(255,107,53,0.35)" : "var(--brand-border)"}`,
                  transform: plan.popular ? "scale(1.03)" : "scale(1)",
                  boxShadow: plan.popular ? "0 20px 60px rgba(255,107,53,0.15)" : "none",
                }}
              >
                {plan.popular && (
                  <div
                    className="absolute -top-3.5 left-1/2 -translate-x-1/2 px-4 py-1 rounded-full text-xs font-bold"
                    style={{ background: "linear-gradient(135deg, #FF6B35, #FF8C42)", color: "white" }}
                  >
                    Phổ biến nhất
                  </div>
                )}

                <div className="mb-6">
                  <h3
                    className="text-lg font-bold mb-1"
                    style={{ color: "var(--brand-text)", fontFamily: "var(--font-heading)" }}
                  >
                    {plan.name}
                  </h3>
                  <p className="text-sm mb-4" style={{ color: "var(--brand-text-muted)" }}>
                    {plan.description}
                  </p>
                  <div className="flex items-end gap-1">
                    <span
                      className="text-4xl font-black"
                      style={{ color: plan.color, fontFamily: "var(--font-heading)" }}
                    >
                      {plan.price}
                    </span>
                    {plan.price !== "0" && (
                      <span className="text-sm mb-1" style={{ color: "var(--brand-text-muted)" }}>
                        đ{plan.period}
                      </span>
                    )}
                    {plan.price === "0" && (
                      <span className="text-sm mb-1" style={{ color: "var(--brand-text-muted)" }}>
                        đ mãi mãi
                      </span>
                    )}
                  </div>
                </div>

                <ul className="space-y-3 mb-8">
                  {plan.features.map((f) => (
                    <li key={f} className="flex items-start gap-2.5 text-sm" style={{ color: "var(--brand-text-muted)" }}>
                      <Check size={15} className="mt-0.5 shrink-0" style={{ color: plan.color }} />
                      {f}
                    </li>
                  ))}
                </ul>

                <Link href={plan.href}>
                  <button
                    id={`btn-plan-${plan.name.toLowerCase()}`}
                    className="w-full py-3 rounded-xl font-semibold text-sm flex items-center justify-center gap-2 transition-all duration-200"
                    style={
                      plan.popular
                        ? { background: "linear-gradient(135deg, #FF6B35, #FF8C42)", color: "white" }
                        : {
                            background: `${plan.color}15`,
                            border: `1px solid ${plan.color}30`,
                            color: plan.color,
                          }
                    }
                  >
                    {plan.popular && <Zap size={14} />}
                    {plan.cta}
                  </button>
                </Link>
              </div>
            ))}
          </div>

          {/* FAQ hint */}
          <p className="text-center text-sm mt-12" style={{ color: "var(--brand-text-dim)" }}>
            Có câu hỏi?{" "}
            <Link href="/contact" className="hover:text-orange-400 transition-colors" style={{ color: "var(--brand-primary)" }}>
              Liên hệ với chúng tôi
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
