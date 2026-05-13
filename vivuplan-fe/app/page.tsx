"use client";
import { useState, useEffect } from "react";
import Link from "next/link";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import {
  MapPin, Sparkles, ArrowRight, Star, Users, Clock,
  CheckCircle, TrendingUp, Zap, Shield, Globe, ChevronRight
} from "lucide-react";

const destinations = [
  { name: "Đà Lạt", emoji: "🌸", tag: "Thành phố hoa" },
  { name: "Hạ Long", emoji: "⛵", tag: "Kỳ quan thế giới" },
  { name: "Hội An", emoji: "🏮", tag: "Phố cổ đèn lồng" },
  { name: "Phú Quốc", emoji: "🌴", tag: "Đảo ngọc" },
  { name: "Sapa", emoji: "⛰️", tag: "Mây núi hùng vĩ" },
];

const features = [
  { icon: Sparkles, title: "AI lập kế hoạch thông minh", desc: "Chỉ cần nhập điểm đến và ngân sách, AI tạo lịch trình chi tiết trong 30 giây.", color: "#F97316" },
  { icon: MapPin, title: "Địa điểm thực tế, đã xác minh", desc: "Mọi địa điểm đều có tọa độ, giờ mở cửa và chi phí ước tính chính xác.", color: "#0EA5E9" },
  { icon: TrendingUp, title: "Tối ưu tuyến đường", desc: "AI sắp xếp hoạt động theo địa lý, giảm thiểu thời gian di chuyển.", color: "#8B5CF6" },
  { icon: Shield, title: "Ngân sách minh bạch", desc: "Phân tích chi phí chi tiết: ăn uống, di chuyển, lưu trú, tham quan.", color: "#10B981" },
];

const testimonials = [
  { name: "Minh Tuấn", role: "Kỹ sư phần mềm", text: "VivuPlan giúp tôi tiết kiệm hàng giờ lập kế hoạch. Lịch trình Đà Lạt 3 ngày cực kỳ hợp lý!", avatar: "M", rating: 5 },
  { name: "Thu Hương", role: "Nhiếp ảnh gia", text: "Tôi thích cách AI gợi ý địa điểm check-in đẹp phù hợp phong cách của mình.", avatar: "T", rating: 5 },
  { name: "Quang Khải", role: "Sinh viên", text: "Đi Hạ Long 4 ngày với budget 4tr mà AI vẫn sắp xếp được lịch rất ổn!", avatar: "Q", rating: 5 },
];

const stats = [
  { value: "50K+", label: "Lịch trình đã tạo" },
  { value: "200+", label: "Điểm đến Việt Nam" },
  { value: "4.9★", label: "Đánh giá trung bình" },
  { value: "30s", label: "Thời gian tạo lịch trình" },
];

export default function HomePage() {
  const [destIdx, setDestIdx] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setDestIdx((i) => (i + 1) % destinations.length), 2800);
    return () => clearInterval(t);
  }, []);

  const cur = destinations[destIdx];

  return (
    <div style={{ background: "var(--bg)", minHeight: "100vh" }}>
      <Navbar />

      {/* ── Hero ─────────────────────────────────────────────────────────── */}
      <section style={{ paddingTop: "120px", paddingBottom: "80px" }}>
        <div className="container" style={{ textAlign: "center" }}>
          <div className="badge badge-orange animate-fade-up" style={{ marginBottom: "20px", display: "inline-flex" }}>
            <Sparkles size={13} /> Powered by Gemini AI
          </div>

          <h1 className="animate-fade-up" style={{
            fontFamily: "var(--font-heading)", fontWeight: 900,
            fontSize: "clamp(38px, 6vw, 68px)", lineHeight: 1.15,
            color: "var(--text)", marginBottom: "20px",
            animationDelay: "0.05s",
          }}>
            Lập kế hoạch du lịch{" "}
            <span className="gradient-text">thông minh</span>
            <br />cho mọi chuyến đi Việt Nam
          </h1>

          <p className="animate-fade-up" style={{
            fontSize: "18px", color: "var(--text-3)", maxWidth: "560px",
            margin: "0 auto 36px", lineHeight: 1.7, animationDelay: "0.1s",
          }}>
            Chỉ cần nói điểm đến và ngân sách — VivuPlan AI tạo lịch trình chi tiết,
            tối ưu tuyến đường và ước tính chi phí trong <strong style={{ color: "var(--text-2)" }}>30 giây</strong>.
          </p>

          {/* CTA */}
          <div className="animate-fade-up" style={{ display: "flex", gap: "12px", justifyContent: "center", flexWrap: "wrap", marginBottom: "56px", animationDelay: "0.15s" }}>
            <Link href="/plan" className="btn btn-primary btn-lg" style={{ textDecoration: "none", fontSize: "16px" }}>
              <Sparkles size={18} /> Tạo lịch trình miễn phí
            </Link>
            <Link href="/explore" className="btn btn-secondary btn-lg" style={{ textDecoration: "none", fontSize: "16px" }}>
              <Globe size={16} /> Khám phá điểm đến <ArrowRight size={15} />
            </Link>
          </div>

          {/* Cycling destination card */}
          <div className="animate-fade-up" style={{ animationDelay: "0.2s", display: "flex", justifyContent: "center" }}>
            <div style={{
              display: "inline-flex", alignItems: "center", gap: "14px",
              background: "var(--surface)", border: "1px solid var(--border)",
              borderRadius: "var(--r-2xl)", padding: "14px 24px",
              boxShadow: "var(--shadow-md)", maxWidth: "480px",
            }}>
              <div style={{
                width: 52, height: 52, borderRadius: "var(--r-lg)",
                background: "var(--primary-light)", fontSize: "28px",
                display: "flex", alignItems: "center", justifyContent: "center",
                flexShrink: 0,
              }}>
                {cur.emoji}
              </div>
              <div style={{ textAlign: "left" }}>
                <p style={{ fontSize: "12px", color: "var(--text-4)", fontWeight: 500, marginBottom: "2px" }}>{cur.tag}</p>
                <p style={{ fontSize: "18px", fontWeight: 700, color: "var(--text)", fontFamily: "var(--font-heading)" }}>{cur.name}</p>
                <div style={{ display: "flex", alignItems: "center", gap: "5px", marginTop: "4px" }}>
                  <div className="spinner" style={{ width: 10, height: 10, borderWidth: 1.5 }} />
                  <span style={{ fontSize: "12px", color: "var(--primary)", fontWeight: 600 }}>AI đang tạo lịch trình...</span>
                </div>
              </div>
              <Link href={`/plan?destination=${encodeURIComponent(cur.name)}`} className="btn btn-primary btn-sm" style={{ textDecoration: "none", flexShrink: 0 }}>
                Lên kế hoạch <ChevronRight size={14} />
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* ── Stats ────────────────────────────────────────────────────────── */}
      <section style={{ background: "var(--surface)", borderTop: "1px solid var(--border)", borderBottom: "1px solid var(--border)", padding: "36px 0" }}>
        <div className="container">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "16px" }} className="grid-cols-2 md:grid-cols-4">
            {stats.map(({ value, label }) => (
              <div key={label} style={{ textAlign: "center", padding: "8px 0" }}>
                <div style={{ fontFamily: "var(--font-heading)", fontWeight: 800, fontSize: "28px", color: "var(--primary)", marginBottom: "4px" }}>{value}</div>
                <div style={{ fontSize: "13px", color: "var(--text-3)" }}>{label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── How it works ─────────────────────────────────────────────────── */}
      <section className="section">
        <div className="container">
          <div style={{ textAlign: "center", marginBottom: "56px" }}>
            <div className="badge badge-blue" style={{ display: "inline-flex", marginBottom: "16px" }}>Cách hoạt động</div>
            <h2 style={{ fontSize: "clamp(28px, 4vw, 40px)" }}>Lập kế hoạch chỉ trong <span className="gradient-text">3 bước</span></h2>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(1,1fr)", gap: "24px" }} className="md:grid-cols-3">
            {[
              { step: "01", title: "Nhập thông tin chuyến đi", desc: "Chọn điểm đến, số ngày, ngân sách và phong cách du lịch của bạn.", icon: MapPin, color: "#F97316" },
              { step: "02", title: "AI tạo lịch trình tối ưu", desc: "Gemini AI phân tích và tạo lịch trình chi tiết, phù hợp sở thích trong 30 giây.", icon: Sparkles, color: "#8B5CF6" },
              { step: "03", title: "Khám phá và tùy chỉnh", desc: "Xem lịch trình, chỉnh sửa theo ý muốn, chia sẻ với bạn bè và bắt đầu hành trình.", icon: Globe, color: "#0EA5E9" },
            ].map(({ step, title, desc, icon: Icon, color }) => (
              <div key={step} className="card" style={{ padding: "32px", position: "relative" }}>
                <div style={{
                  position: "absolute", top: "24px", right: "24px",
                  fontFamily: "var(--font-heading)", fontSize: "40px", fontWeight: 900,
                  color: color, opacity: 0.08, lineHeight: 1,
                }}>{step}</div>
                <div style={{
                  width: 48, height: 48, borderRadius: "var(--r-lg)",
                  background: `${color}15`, display: "flex", alignItems: "center", justifyContent: "center",
                  marginBottom: "20px",
                }}>
                  <Icon size={22} style={{ color }} />
                </div>
                <h3 style={{ fontSize: "17px", marginBottom: "10px" }}>{title}</h3>
                <p style={{ fontSize: "14px", color: "var(--text-3)", lineHeight: "1.7" }}>{desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Features ─────────────────────────────────────────────────────── */}
      <section className="section" style={{ background: "var(--surface-2)" }}>
        <div className="container">
          <div style={{ textAlign: "center", marginBottom: "56px" }}>
            <div className="badge badge-purple" style={{ display: "inline-flex", marginBottom: "16px" }}>Tính năng</div>
            <h2 style={{ fontSize: "clamp(28px, 4vw, 40px)" }}>Mọi thứ bạn cần cho <span className="gradient-text">chuyến đi hoàn hảo</span></h2>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(1,1fr)", gap: "20px" }} className="md:grid-cols-2">
            {features.map(({ icon: Icon, title, desc, color }) => (
              <div key={title} className="card card-hover" style={{ padding: "28px", display: "flex", gap: "20px", alignItems: "flex-start" }}>
                <div style={{
                  width: 48, height: 48, borderRadius: "var(--r-lg)", flexShrink: 0,
                  background: `${color}15`, display: "flex", alignItems: "center", justifyContent: "center",
                }}>
                  <Icon size={22} style={{ color }} />
                </div>
                <div>
                  <h3 style={{ fontSize: "16px", marginBottom: "8px" }}>{title}</h3>
                  <p style={{ fontSize: "14px", color: "var(--text-3)", lineHeight: "1.7" }}>{desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Popular destinations ──────────────────────────────────────────── */}
      <section className="section">
        <div className="container">
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "36px", flexWrap: "wrap", gap: "12px" }}>
            <div>
              <div className="badge badge-teal" style={{ display: "inline-flex", marginBottom: "12px" }}>Điểm đến</div>
              <h2 style={{ fontSize: "clamp(24px, 3vw, 36px)" }}>Điểm đến <span className="gradient-text">phổ biến</span></h2>
            </div>
            <Link href="/explore" className="btn btn-secondary btn-sm" style={{ textDecoration: "none" }}>
              Xem tất cả <ArrowRight size={14} />
            </Link>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(1,1fr)", gap: "16px" }} className="sm:grid-cols-2 md:grid-cols-5">
            {destinations.map(({ name, emoji, tag }) => (
              <Link key={name} href={`/plan?destination=${encodeURIComponent(name)}`} style={{ textDecoration: "none" }}>
                <div className="card card-hover" style={{ padding: "24px 16px", textAlign: "center" }}>
                  <div style={{ fontSize: "40px", marginBottom: "12px" }}>{emoji}</div>
                  <h3 style={{ fontSize: "15px", marginBottom: "4px" }}>{name}</h3>
                  <p style={{ fontSize: "12px", color: "var(--text-4)" }}>{tag}</p>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* ── Testimonials ─────────────────────────────────────────────────── */}
      <section className="section" style={{ background: "var(--surface-2)" }}>
        <div className="container">
          <div style={{ textAlign: "center", marginBottom: "48px" }}>
            <div className="badge badge-green" style={{ display: "inline-flex", marginBottom: "16px" }}>Đánh giá</div>
            <h2 style={{ fontSize: "clamp(24px, 3vw, 36px)" }}>Người dùng nói gì về <span className="gradient-text">VivuPlan</span></h2>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(1,1fr)", gap: "20px" }} className="md:grid-cols-3">
            {testimonials.map(({ name, role, text, avatar, rating }) => (
              <div key={name} className="card" style={{ padding: "28px" }}>
                <div style={{ display: "flex", gap: "4px", marginBottom: "16px" }}>
                  {Array.from({ length: rating }).map((_, i) => (
                    <Star key={i} size={14} fill="#F97316" color="#F97316" />
                  ))}
                </div>
                <p style={{ fontSize: "14px", color: "var(--text-2)", lineHeight: "1.75", marginBottom: "20px" }}>"{text}"</p>
                <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                  <div style={{
                    width: 38, height: 38, borderRadius: "50%",
                    background: "linear-gradient(135deg, #F97316, #FB923C)",
                    display: "flex", alignItems: "center", justifyContent: "center",
                    color: "white", fontSize: "14px", fontWeight: 700,
                  }}>{avatar}</div>
                  <div>
                    <p style={{ fontWeight: 600, fontSize: "14px", color: "var(--text)" }}>{name}</p>
                    <p style={{ fontSize: "12px", color: "var(--text-4)" }}>{role}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── CTA banner ───────────────────────────────────────────────────── */}
      <section className="section">
        <div className="container">
          <div style={{
            background: "linear-gradient(135deg, #FFF7ED 0%, #F0F9FF 100%)",
            border: "1px solid #FED7AA", borderRadius: "var(--r-2xl)",
            padding: "64px 40px", textAlign: "center",
            boxShadow: "0 8px 32px rgba(249,115,22,0.08)",
          }}>
            <div className="badge badge-orange" style={{ display: "inline-flex", marginBottom: "20px" }}>
              <Zap size={13} /> Bắt đầu ngay hôm nay
            </div>
            <h2 style={{ fontSize: "clamp(28px, 4vw, 44px)", marginBottom: "16px" }}>
              Chuyến đi mơ ước chỉ cách bạn <span className="gradient-text">30 giây</span>
            </h2>
            <p style={{ fontSize: "16px", color: "var(--text-3)", maxWidth: "480px", margin: "0 auto 32px" }}>
              Miễn phí hoàn toàn. Không cần tạo tài khoản để bắt đầu.
            </p>
            <div style={{ display: "flex", gap: "12px", justifyContent: "center", flexWrap: "wrap" }}>
              <Link href="/plan" className="btn btn-primary btn-lg" style={{ textDecoration: "none" }}>
                <Sparkles size={18} /> Tạo lịch trình ngay
              </Link>
              <Link href="/explore" className="btn btn-secondary btn-lg" style={{ textDecoration: "none" }}>
                Khám phá điểm đến
              </Link>
            </div>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
