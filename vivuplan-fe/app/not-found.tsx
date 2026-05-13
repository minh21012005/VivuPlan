import Link from "next/link";
import { MapPin, Home, Compass, ArrowLeft } from "lucide-react";

export default function NotFound() {
  return (
    <div
      className="min-h-screen flex flex-col items-center justify-center px-4 text-center"
      style={{ background: "var(--brand-dark)" }}
    >
      {/* Logo */}
      <div className="flex items-center gap-2 mb-10">
        <div
          className="w-10 h-10 rounded-xl flex items-center justify-center"
          style={{ background: "linear-gradient(135deg, #FF6B35, #FF8C42)" }}
        >
          <MapPin size={20} color="white" fill="white" />
        </div>
        <span
          className="text-xl font-bold"
          style={{
            fontFamily: "var(--font-heading)",
            background: "linear-gradient(135deg, #FF6B35, #4ECDC4)",
            WebkitBackgroundClip: "text",
            WebkitTextFillColor: "transparent",
          }}
        >
          VivuPlan
        </span>
      </div>

      {/* 404 */}
      <div className="text-8xl font-black mb-4" style={{ fontFamily: "var(--font-heading)", color: "var(--brand-surface-3)" }}>
        404
      </div>
      <div className="text-6xl mb-6">🗺️</div>

      <h1
        className="text-3xl font-bold mb-3"
        style={{ fontFamily: "var(--font-heading)", color: "var(--brand-text)" }}
      >
        Trang không tìm thấy
      </h1>
      <p className="text-lg mb-10 max-w-md" style={{ color: "var(--brand-text-muted)" }}>
        Có vẻ như bạn đã đi lạc đường. Đừng lo — AI của chúng tôi sẽ giúp bạn tìm đường về!
      </p>

      <div className="flex flex-col sm:flex-row gap-3">
        <Link href="/">
          <button
            className="flex items-center gap-2 px-6 py-3 rounded-xl font-semibold transition-all duration-200"
            style={{
              background: "linear-gradient(135deg, #FF6B35, #FF8C42)",
              color: "white",
            }}
          >
            <Home size={16} /> Về trang chủ
          </button>
        </Link>
        <Link href="/explore">
          <button
            className="flex items-center gap-2 px-6 py-3 rounded-xl font-semibold transition-all duration-200"
            style={{
              background: "rgba(255,255,255,0.06)",
              border: "1px solid rgba(255,255,255,0.1)",
              color: "var(--brand-text-muted)",
            }}
          >
            <Compass size={16} /> Khám phá điểm đến
          </button>
        </Link>
      </div>
    </div>
  );
}
