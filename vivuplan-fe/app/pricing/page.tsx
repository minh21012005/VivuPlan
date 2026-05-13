import Link from "next/link";
import Navbar from "@/components/layout/Navbar";
import { Check, Zap, Star, ArrowRight } from "lucide-react";

const plans = [
  {
    name: "Miễn phí",
    price: "0",
    unit: "mãi mãi",
    desc: "Hoàn hảo để bắt đầu khám phá",
    color: "#10B981",
    badgeClass: "badge-green",
    features: [
      "3 lịch trình AI mỗi tháng",
      "Xem điểm đến phổ biến",
      "Lưu tối đa 2 lịch trình",
      "Chia sẻ công khai",
    ],
    cta: "Dùng miễn phí",
    href: "/register",
    popular: false,
  },
  {
    name: "Pro",
    price: "99.000",
    unit: "/ tháng",
    desc: "Dành cho người yêu du lịch thực sự",
    color: "#F97316",
    badgeClass: "badge-orange",
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
    unit: "/ tháng",
    desc: "Cho nhóm du lịch & doanh nghiệp",
    color: "#8B5CF6",
    badgeClass: "badge-purple",
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
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <Navbar />

      {/* Hero */}
      <section style={{ paddingTop: "96px", paddingBottom: "56px", background: "linear-gradient(135deg, #FFF7ED 0%, #F0F9FF 100%)", borderBottom: "1px solid var(--border)", textAlign: "center" }}>
        <div className="container">
          <div className="badge badge-orange" style={{ display: "inline-flex", marginBottom: "16px" }}>
            <Star size={13} fill="currentColor" /> Bảng giá
          </div>
          <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "clamp(30px,4vw,48px)", fontWeight: 900, color: "var(--text)", marginBottom: "14px" }}>
            Đơn giản, <span className="gradient-text">minh bạch</span>
          </h1>
          <p style={{ fontSize: "17px", color: "var(--text-3)", maxWidth: "480px", margin: "0 auto" }}>
            Bắt đầu miễn phí, nâng cấp khi bạn cần thêm sức mạnh AI
          </p>
        </div>
      </section>

      {/* Plans */}
      <section style={{ padding: "56px 0 80px" }}>
        <div className="container">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(1,1fr)", gap: "20px", maxWidth: "960px", margin: "0 auto" }} className="md:grid-cols-3">
            {plans.map((plan) => (
              <div key={plan.name} style={{
                background: "var(--surface)", border: `2px solid ${plan.popular ? plan.color : "var(--border)"}`,
                borderRadius: "var(--r-2xl)", padding: "32px 28px",
                position: "relative",
                boxShadow: plan.popular ? `0 12px 40px rgba(249,115,22,0.15)` : "var(--shadow-sm)",
                transform: plan.popular ? "scale(1.03)" : "scale(1)",
              }}>
                {plan.popular && (
                  <div style={{
                    position: "absolute", top: "-14px", left: "50%", transform: "translateX(-50%)",
                    background: "linear-gradient(135deg, #F97316, #FB923C)", color: "white",
                    fontSize: "12px", fontWeight: 700, padding: "4px 16px", borderRadius: "var(--r-full)",
                    whiteSpace: "nowrap",
                  }}>
                    ⭐ Phổ biến nhất
                  </div>
                )}

                {/* Plan header */}
                <div style={{ marginBottom: "24px" }}>
                  <span className={`badge ${plan.badgeClass}`} style={{ marginBottom: "12px", display: "inline-flex" }}>{plan.name}</span>
                  <p style={{ fontSize: "13px", color: "var(--text-3)", marginBottom: "16px" }}>{plan.desc}</p>
                  <div style={{ display: "flex", alignItems: "flex-end", gap: "4px" }}>
                    <span style={{ fontFamily: "var(--font-heading)", fontSize: "36px", fontWeight: 900, color: plan.color, lineHeight: 1 }}>
                      {plan.price === "0" ? "0" : plan.price}
                    </span>
                    <span style={{ fontSize: "13px", color: "var(--text-3)", marginBottom: "4px" }}>
                      đ {plan.unit}
                    </span>
                  </div>
                </div>

                {/* Features */}
                <ul style={{ listStyle: "none", display: "flex", flexDirection: "column", gap: "10px", marginBottom: "28px" }}>
                  {plan.features.map((f) => (
                    <li key={f} style={{ display: "flex", alignItems: "flex-start", gap: "10px", fontSize: "14px", color: "var(--text-2)" }}>
                      <div style={{ width: 20, height: 20, borderRadius: "50%", flexShrink: 0, marginTop: "1px", background: `${plan.color}15`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                        <Check size={11} style={{ color: plan.color }} />
                      </div>
                      {f}
                    </li>
                  ))}
                </ul>

                {/* CTA */}
                <Link href={plan.href} style={{ textDecoration: "none", display: "block" }}>
                  <button
                    id={`btn-plan-${plan.name.toLowerCase()}`}
                    style={{
                      width: "100%", padding: "12px", borderRadius: "var(--r-lg)",
                      fontSize: "14px", fontWeight: 700, cursor: "pointer",
                      display: "flex", alignItems: "center", justifyContent: "center", gap: "8px",
                      background: plan.popular ? plan.color : `${plan.color}12`,
                      color: plan.popular ? "white" : plan.color,
                      border: `2px solid ${plan.popular ? plan.color : `${plan.color}30`}`,
                      boxShadow: plan.popular ? `0 4px 16px rgba(249,115,22,0.35)` : "none",
                      transition: "all 0.18s",
                    }}>
                    {plan.popular && <Zap size={15} />}
                    {plan.cta} <ArrowRight size={14} />
                  </button>
                </Link>
              </div>
            ))}
          </div>

          {/* FAQ note */}
          <p style={{ textAlign: "center", fontSize: "14px", color: "var(--text-4)", marginTop: "48px" }}>
            Có câu hỏi?{" "}
            <Link href="/contact" style={{ color: "var(--primary)", textDecoration: "none", fontWeight: 600 }}>
              Liên hệ với chúng tôi
            </Link>
          </p>
        </div>
      </section>
    </div>
  );
}
