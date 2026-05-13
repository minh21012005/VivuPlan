import Link from "next/link";
import { MapPin, Home, Compass } from "lucide-react";

export default function NotFound() {
  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "40px 24px", textAlign: "center" }}>
      <Link href="/" style={{ display: "flex", alignItems: "center", gap: "8px", textDecoration: "none", marginBottom: "48px" }}>
        <div style={{ width: 36, height: 36, borderRadius: 10, background: "linear-gradient(135deg,#F97316,#FB923C)", display: "flex", alignItems: "center", justifyContent: "center", boxShadow: "0 2px 8px rgba(249,115,22,0.35)" }}>
          <MapPin size={17} color="white" fill="white" />
        </div>
        <span style={{ fontFamily: "var(--font-heading)", fontWeight: 800, fontSize: "18px", background: "linear-gradient(135deg,#F97316,#EA580C)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>VivuPlan</span>
      </Link>

      <div style={{ fontSize: "96px", fontFamily: "var(--font-heading)", fontWeight: 900, color: "var(--surface-3)", lineHeight: 1, marginBottom: "8px" }}>404</div>
      <div style={{ fontSize: "72px", marginBottom: "24px" }}>🗺️</div>

      <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "28px", fontWeight: 800, color: "var(--text)", marginBottom: "12px" }}>
        Trang không tìm thấy
      </h1>
      <p style={{ fontSize: "16px", color: "var(--text-3)", maxWidth: "400px", lineHeight: 1.7, marginBottom: "36px" }}>
        Có vẻ bạn đã đi lạc đường. Đừng lo — hãy để AI dẫn đường cho bạn!
      </p>

      <div style={{ display: "flex", gap: "12px", flexWrap: "wrap", justifyContent: "center" }}>
        <Link href="/" className="btn btn-primary" style={{ textDecoration: "none" }}>
          <Home size={16} /> Về trang chủ
        </Link>
        <Link href="/explore" className="btn btn-secondary" style={{ textDecoration: "none" }}>
          <Compass size={16} /> Khám phá điểm đến
        </Link>
      </div>
    </div>
  );
}
