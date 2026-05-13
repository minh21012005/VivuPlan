"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { MapPin, Compass, ChevronRight, Star, Users, Clock, Zap, ArrowRight, Play, CheckCircle, Globe, TrendingUp, Map } from "lucide-react";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";

const destinations = [
  { name: "Đà Lạt", emoji: "🌸", tagline: "Thành phố ngàn hoa", color: "#FF6B9D", image: "bg-gradient-to-br from-pink-900/50 to-purple-900/50" },
  { name: "Hạ Long", emoji: "⛵", tagline: "Kỳ quan thiên nhiên", color: "#4ECDC4", image: "bg-gradient-to-br from-teal-900/50 to-blue-900/50" },
  { name: "Quy Nhơn", emoji: "🏖️", tagline: "Biển xanh cát trắng", color: "#FFE66D", image: "bg-gradient-to-br from-yellow-900/50 to-orange-900/50" },
  { name: "Đà Nẵng", emoji: "🌉", tagline: "Thành phố đáng sống", color: "#FF6B35", image: "bg-gradient-to-br from-orange-900/50 to-red-900/50" },
  { name: "Phú Quốc", emoji: "🌴", tagline: "Đảo ngọc thiên đường", color: "#6C63FF", image: "bg-gradient-to-br from-violet-900/50 to-indigo-900/50" },
  { name: "Sapa", emoji: "⛰️", tagline: "Núi mây bồng bềnh", color: "#4ECDC4", image: "bg-gradient-to-br from-emerald-900/50 to-teal-900/50" },
];

const steps = [
  { step: "01", title: "Chọn điểm đến", desc: "Bạn đã biết muốn đi đâu – hãy cho chúng tôi biết", icon: MapPin, color: "#FF6B35" },
  { step: "02", title: "Nhập yêu cầu", desc: "Thời gian, ngân sách, phong cách du lịch của bạn", icon: Compass, color: "#4ECDC4" },
  { step: "03", title: "AI tạo lịch trình", desc: "Hệ thống tối ưu hóa lịch trình thực tế trong vài giây", icon: Zap, color: "#FFE66D" },
  { step: "04", title: "Chỉnh sửa & chia sẻ", desc: "Tùy chỉnh linh hoạt và chia sẻ với bạn bè", icon: Users, color: "#FF6B9D" },
];

const stats = [
  { value: "10,000+", label: "Lịch trình đã tạo" },
  { value: "50+", label: "Điểm đến Việt Nam" },
  { value: "< 2 phút", label: "Thời gian tạo lịch trình" },
  { value: "4.9★", label: "Đánh giá người dùng" },
];

const features = [
  {
    icon: Zap,
    title: "AI Tạo Lịch Trình Thực Tế",
    desc: "Không hallucination. Lịch trình được tối ưu dựa trên dữ liệu địa điểm thực tế của Việt Nam.",
    color: "#FF6B35",
    tag: "Cốt lõi",
  },
  {
    icon: Map,
    title: "Tối Ưu Tuyến Đường",
    desc: "Sắp xếp thứ tự địa điểm thông minh, tiết kiệm thời gian di chuyển và giảm mệt mỏi.",
    color: "#4ECDC4",
    tag: "Độc quyền",
  },
  {
    icon: TrendingUp,
    title: "Quản Lý Ngân Sách",
    desc: "Ước tính chi phí thực tế: ăn uống, di chuyển, lưu trú, hoạt động – theo từng hạng mức.",
    color: "#FFE66D",
    tag: "Hữu ích",
  },
  {
    icon: Globe,
    title: "Chỉnh Sửa Linh Hoạt",
    desc: "Thêm, xóa, sắp xếp lại địa điểm. Regenerate từng phần mà không mất toàn bộ kế hoạch.",
    color: "#FF6B9D",
    tag: "Quan trọng",
  },
];

const testimonials = [
  { name: "Minh Anh", age: 24, avatar: "MA", text: "VivuPlan giúp mình lên kế hoạch chuyến Đà Lạt 4 ngày chỉ trong 3 phút. Lịch trình rất thực tế, không bị nhồi nhét.", trip: "Đà Lạt 4N3Đ" },
  { name: "Thanh Hà", age: 28, avatar: "TH", text: "Tính năng tối ưu tuyến đường cực hay! Đi Hạ Long mà không bị chạy ngược chạy xuôi như trước.", trip: "Hạ Long 3N2Đ" },
  { name: "Quốc Bảo", age: 22, avatar: "QB", text: "Mình dùng để plan chuyến nhóm 8 người đi Quy Nhơn. Chia sẻ lịch trình cho cả nhóm super tiện!", trip: "Quy Nhơn 5N4Đ" },
];

export default function HomePage() {
  const [currentDestIndex, setCurrentDestIndex] = useState(0);
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    setIsVisible(true);
    const interval = setInterval(() => {
      setCurrentDestIndex((prev) => (prev + 1) % destinations.length);
    }, 3000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="min-h-screen" style={{ background: "var(--brand-dark)" }}>
      <Navbar />

      {/* Hero Section */}
      <section className="relative min-h-screen flex items-center justify-center overflow-hidden pt-20">
        {/* Background */}
        <div className="absolute inset-0">
          <div
            className="absolute inset-0"
            style={{
              background: "radial-gradient(ellipse at 20% 50%, rgba(255,107,53,0.1) 0%, transparent 60%), radial-gradient(ellipse at 80% 20%, rgba(78,205,196,0.08) 0%, transparent 60%)",
            }}
          />
          {/* Grid pattern */}
          <div
            className="absolute inset-0 opacity-[0.03]"
            style={{
              backgroundImage: "linear-gradient(rgba(255,255,255,0.5) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.5) 1px, transparent 1px)",
              backgroundSize: "60px 60px",
            }}
          />
        </div>

        <div className="relative z-10 max-w-7xl mx-auto px-6 py-20 text-center">
          {/* Badge */}
          <div
            className={`inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium mb-8 transition-all duration-700 ${isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}
            style={{
              background: "rgba(255, 107, 53, 0.12)",
              border: "1px solid rgba(255, 107, 53, 0.3)",
              color: "#FF6B35",
            }}
          >
            <span className="relative flex h-2 w-2">
              <span
                className="animate-ping absolute inline-flex h-full w-full rounded-full opacity-75"
                style={{ background: "#FF6B35" }}
              />
              <span className="relative inline-flex rounded-full h-2 w-2" style={{ background: "#FF6B35" }} />
            </span>
            AI Du Lịch Việt Nam – Lập kế hoạch thông minh
          </div>

          {/* Headline */}
          <h1
            className={`text-5xl md:text-7xl font-bold leading-tight mb-6 transition-all duration-700 delay-100 ${isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}
            style={{ fontFamily: "'Plus Jakarta Sans', sans-serif" }}
          >
            <span style={{ color: "var(--brand-text)" }}>Lên kế hoạch </span>
            <span
              style={{
                background: "linear-gradient(135deg, #FF6B35, #FF8C42, #FFE66D)",
                WebkitBackgroundClip: "text",
                WebkitTextFillColor: "transparent",
                backgroundClip: "text",
              }}
            >
              {destinations[currentDestIndex].name}
            </span>
            <br />
            <span style={{ color: "var(--brand-text)" }}>cực nhanh với AI</span>
          </h1>

          <p
            className={`text-xl text-gray-400 max-w-2xl mx-auto mb-10 leading-relaxed transition-all duration-700 delay-200 ${isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}
          >
            Bạn đã biết muốn đi đâu. Chúng tôi giúp bạn{" "}
            <strong style={{ color: "var(--brand-text)" }}>lập kế hoạch hoàn hảo</strong> – lịch trình tối ưu, ngân sách thực tế, địa điểm được cá nhân hóa.
          </p>

          {/* CTA Buttons */}
          <div
            className={`flex flex-col sm:flex-row gap-4 justify-center items-center mb-16 transition-all duration-700 delay-300 ${isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}
          >
            <Link href="/plan" id="hero-cta-plan">
              <button
                className="btn-primary flex items-center gap-2 text-base px-8 py-4"
                style={{ borderRadius: "14px", fontSize: "16px" }}
              >
                <Zap size={18} />
                Lập kế hoạch ngay
                <ArrowRight size={16} />
              </button>
            </Link>
            <Link href="/explore" id="hero-cta-explore">
              <button
                className="btn-secondary flex items-center gap-2 text-base px-8 py-4"
                style={{ borderRadius: "14px", fontSize: "16px" }}
              >
                <Play size={16} />
                Xem demo
              </button>
            </Link>
          </div>

          {/* Stats */}
          <div
            className={`grid grid-cols-2 md:grid-cols-4 gap-6 max-w-3xl mx-auto transition-all duration-700 delay-400 ${isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"}`}
          >
            {stats.map((stat) => (
              <div
                key={stat.label}
                className="glass rounded-2xl p-4 text-center"
              >
                <div className="text-2xl font-bold gradient-text mb-1">{stat.value}</div>
                <div className="text-xs text-gray-400">{stat.label}</div>
              </div>
            ))}
          </div>
        </div>

        {/* Scroll indicator */}
        <div className="absolute bottom-8 left-1/2 -translate-x-1/2 flex flex-col items-center gap-2 animate-float">
          <span className="text-xs text-gray-500">Khám phá thêm</span>
          <ChevronRight size={20} style={{ color: "var(--brand-primary)", transform: "rotate(90deg)" }} />
        </div>
      </section>

      {/* Destinations Section */}
      <section className="py-24 px-6">
        <div className="max-w-7xl mx-auto">
          <div className="text-center mb-16">
            <div className="badge badge-orange mb-4 mx-auto inline-flex">🗺️ Điểm đến hot</div>
            <h2 className="text-4xl md:text-5xl font-bold mb-4" style={{ fontFamily: "'Plus Jakarta Sans', sans-serif" }}>
              <span style={{ color: "var(--brand-text)" }}>Đi đâu? Chúng tôi </span>
              <span className="gradient-text">tối ưu cho bạn</span>
            </h2>
            <p className="text-gray-400 text-lg max-w-xl mx-auto">
              Từ miền núi đến biển đảo – mỗi điểm đến đều có lịch trình được cá nhân hóa
            </p>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-3 gap-4 md:gap-6">
            {destinations.map((dest, i) => (
              <Link href={`/plan?destination=${encodeURIComponent(dest.name)}`} key={dest.name} id={`dest-card-${i}`}>
                <div
                  className="relative rounded-2xl overflow-hidden cursor-pointer group"
                  style={{
                    background: "var(--brand-surface)",
                    border: "1px solid var(--brand-border)",
                    height: "180px",
                    transition: "all 0.3s ease",
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLDivElement).style.transform = "translateY(-6px)";
                    (e.currentTarget as HTMLDivElement).style.borderColor = dest.color + "60";
                    (e.currentTarget as HTMLDivElement).style.boxShadow = `0 12px 40px ${dest.color}25`;
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLDivElement).style.transform = "translateY(0)";
                    (e.currentTarget as HTMLDivElement).style.borderColor = "rgba(255,255,255,0.08)";
                    (e.currentTarget as HTMLDivElement).style.boxShadow = "none";
                  }}
                >
                  <div className={`absolute inset-0 ${dest.image} opacity-60`} />
                  <div className="absolute inset-0 p-5 flex flex-col justify-between">
                    <span className="text-4xl">{dest.emoji}</span>
                    <div>
                      <h3 className="text-xl font-bold text-white">{dest.name}</h3>
                      <p className="text-sm" style={{ color: dest.color }}>{dest.tagline}</p>
                    </div>
                  </div>
                  <div
                    className="absolute bottom-4 right-4 w-8 h-8 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300"
                    style={{ background: dest.color }}
                  >
                    <ArrowRight size={14} color="white" />
                  </div>
                </div>
              </Link>
            ))}
          </div>

          <div className="text-center mt-8">
            <Link href="/explore">
              <button className="btn-secondary flex items-center gap-2 mx-auto">
                Xem tất cả điểm đến <ChevronRight size={16} />
              </button>
            </Link>
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section className="py-24 px-6 relative">
        <div
          className="absolute inset-0 pointer-events-none"
          style={{ background: "radial-gradient(ellipse at center, rgba(78,205,196,0.05) 0%, transparent 70%)" }}
        />
        <div className="max-w-7xl mx-auto">
          <div className="text-center mb-16">
            <div className="badge badge-teal mb-4 mx-auto inline-flex">⚡ Quy trình</div>
            <h2 className="text-4xl md:text-5xl font-bold mb-4" style={{ fontFamily: "'Plus Jakarta Sans', sans-serif" }}>
              <span className="gradient-text-teal">4 bước đơn giản</span>
              <span style={{ color: "var(--brand-text)" }}> để có lịch trình hoàn hảo</span>
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-6 relative">
            {/* Connecting line */}
            <div
              className="hidden md:block absolute top-10 left-[12.5%] right-[12.5%] h-0.5 opacity-20"
              style={{ background: "linear-gradient(90deg, #FF6B35, #4ECDC4, #FFE66D, #FF6B9D)" }}
            />
            {steps.map((step, i) => (
              <div
                key={step.step}
                className="relative card p-6 text-center group"
                style={{ animationDelay: `${i * 0.1}s` }}
              >
                {/* Step number */}
                <div
                  className="w-12 h-12 rounded-2xl flex items-center justify-center mx-auto mb-4 group-hover:scale-110 transition-transform"
                  style={{ background: `${step.color}20`, border: `1px solid ${step.color}40` }}
                >
                  <step.icon size={22} style={{ color: step.color }} />
                </div>
                <div className="text-xs font-bold mb-2" style={{ color: step.color }}>
                  BƯỚC {step.step}
                </div>
                <h3 className="text-lg font-bold mb-2" style={{ color: "var(--brand-text)" }}>
                  {step.title}
                </h3>
                <p className="text-sm" style={{ color: "var(--brand-text-muted)" }}>
                  {step.desc}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section className="py-24 px-6">
        <div className="max-w-7xl mx-auto">
          <div className="text-center mb-16">
            <div className="badge badge-orange mb-4 mx-auto inline-flex">✨ Tính năng</div>
            <h2 className="text-4xl md:text-5xl font-bold mb-4" style={{ fontFamily: "'Plus Jakarta Sans', sans-serif" }}>
              <span style={{ color: "var(--brand-text)" }}>Không chỉ là </span>
              <span className="gradient-text">chatbot du lịch</span>
            </h2>
            <p className="text-gray-400 text-lg max-w-xl mx-auto">
              Hệ thống AI có cấu trúc, được tối ưu cho hành vi du lịch thực tế của người Việt
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {features.map((feature, i) => (
              <div
                key={feature.title}
                className="card p-8 group relative overflow-hidden"
                id={`feature-${i}`}
              >
                <div
                  className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500"
                  style={{ background: `radial-gradient(ellipse at top left, ${feature.color}08, transparent 60%)` }}
                />
                <div className="relative z-10">
                  <div className="flex items-start justify-between mb-4">
                    <div
                      className="w-12 h-12 rounded-2xl flex items-center justify-center"
                      style={{ background: `${feature.color}20` }}
                    >
                      <feature.icon size={22} style={{ color: feature.color }} />
                    </div>
                    <span
                      className="badge"
                      style={{
                        background: `${feature.color}15`,
                        color: feature.color,
                        border: `1px solid ${feature.color}30`,
                      }}
                    >
                      {feature.tag}
                    </span>
                  </div>
                  <h3 className="text-xl font-bold mb-3" style={{ color: "var(--brand-text)" }}>
                    {feature.title}
                  </h3>
                  <p style={{ color: "var(--brand-text-muted)", lineHeight: "1.7" }}>
                    {feature.desc}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Testimonials */}
      <section className="py-24 px-6">
        <div className="max-w-7xl mx-auto">
          <div className="text-center mb-16">
            <div className="badge badge-teal mb-4 mx-auto inline-flex">💬 Người dùng nói gì</div>
            <h2 className="text-4xl font-bold" style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", color: "var(--brand-text)" }}>
              Hàng nghìn chuyến đi đã được lên kế hoạch
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {testimonials.map((t, i) => (
              <div key={i} className="card p-6" id={`testimonial-${i}`}>
                <div className="flex items-center gap-3 mb-4">
                  <div
                    className="w-10 h-10 rounded-full flex items-center justify-center font-bold text-sm text-white"
                    style={{ background: "var(--gradient-brand)" }}
                  >
                    {t.avatar}
                  </div>
                  <div>
                    <div className="font-semibold text-white">{t.name}</div>
                    <div className="text-xs text-gray-400">{t.age} tuổi</div>
                  </div>
                  <div className="ml-auto">
                    <div className="flex gap-0.5">
                      {[...Array(5)].map((_, j) => (
                        <Star key={j} size={12} fill="#FFE66D" color="#FFE66D" />
                      ))}
                    </div>
                  </div>
                </div>
                <p className="text-gray-300 text-sm leading-relaxed mb-4">&ldquo;{t.text}&rdquo;</p>
                <div
                  className="inline-flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-full"
                  style={{ background: "rgba(255,107,53,0.1)", color: "#FF6B35" }}
                >
                  <MapPin size={10} />
                  {t.trip}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-24 px-6">
        <div className="max-w-4xl mx-auto">
          <div
            className="rounded-3xl p-12 text-center relative overflow-hidden"
            style={{
              background: "linear-gradient(135deg, rgba(255,107,53,0.15), rgba(78,205,196,0.10))",
              border: "1px solid rgba(255,107,53,0.2)",
            }}
          >
            <div
              className="absolute inset-0"
              style={{
                background: "radial-gradient(ellipse at center, rgba(255,107,53,0.08) 0%, transparent 70%)",
              }}
            />
            <div className="relative z-10">
              <div className="text-6xl mb-6">🗺️</div>
              <h2
                className="text-4xl md:text-5xl font-bold mb-4"
                style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", color: "var(--brand-text)" }}
              >
                Sẵn sàng lên đường?
              </h2>
              <p className="text-gray-400 text-lg mb-8 max-w-lg mx-auto">
                Tạo lịch trình đầu tiên miễn phí. Không cần thẻ tín dụng.
              </p>
              <div className="flex flex-col sm:flex-row gap-4 justify-center">
                <Link href="/plan" id="cta-bottom-plan">
                  <button className="btn-primary flex items-center gap-2 text-base px-8 py-4" style={{ borderRadius: "14px" }}>
                    <Zap size={18} />
                    Bắt đầu miễn phí
                  </button>
                </Link>
                <Link href="/login" id="cta-bottom-login">
                  <button className="btn-secondary flex items-center gap-2 text-base px-8 py-4" style={{ borderRadius: "14px" }}>
                    Đăng nhập
                  </button>
                </Link>
              </div>
              <p className="text-gray-500 text-sm mt-4">
                <CheckCircle size={12} className="inline mr-1" />
                Miễn phí mãi mãi với 3 lịch trình/tháng
              </p>
            </div>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
