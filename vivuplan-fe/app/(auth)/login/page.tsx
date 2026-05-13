"use client";
import { useState } from "react";
import Link from "next/link";
import { MapPin, Eye, EyeOff, Zap, Globe } from "lucide-react";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setTimeout(() => setLoading(false), 1500);
  };

  return (
    <div className="min-h-screen flex items-center justify-center px-4 relative" style={{ background: "var(--brand-dark)" }}>
      {/* Glow BG */}
      <div className="absolute inset-0 pointer-events-none" style={{ background: "radial-gradient(ellipse at 30% 40%, rgba(255,107,53,0.08) 0%, transparent 60%)" }} />

      <div className="w-full max-w-md relative z-10">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2 justify-center mb-8">
          <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: "var(--gradient-brand)" }}>
            <MapPin size={20} color="white" fill="white" />
          </div>
          <span className="text-2xl font-bold gradient-text" style={{ fontFamily: "'Plus Jakarta Sans',sans-serif" }}>VivuPlan</span>
        </Link>

        {/* Card */}
        <div className="glass-strong rounded-2xl p-8">
          <h1 className="text-2xl font-bold mb-1" style={{ color: "var(--brand-text)", fontFamily: "'Plus Jakarta Sans',sans-serif" }}>
            Chào mừng trở lại 👋
          </h1>
          <p className="text-sm mb-6" style={{ color: "var(--brand-text-muted)" }}>
            Đăng nhập để tiếp tục lập kế hoạch du lịch
          </p>

          {/* Google Login */}
          <button
            id="btn-google-login"
            className="w-full flex items-center justify-center gap-3 py-3 rounded-xl mb-5 font-medium text-sm transition-all duration-200"
            style={{ background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.1)", color: "var(--brand-text)" }}
            onMouseEnter={(e) => (e.currentTarget.style.background = "rgba(255,255,255,0.1)")}
            onMouseLeave={(e) => (e.currentTarget.style.background = "rgba(255,255,255,0.06)")}
          >
            <Globe size={18} />
            Tiếp tục với Google
          </button>

          <div className="flex items-center gap-3 mb-5">
            <div className="flex-1 h-px" style={{ background: "var(--brand-border)" }} />
            <span className="text-xs" style={{ color: "var(--brand-text-dim)" }}>hoặc</span>
            <div className="flex-1 h-px" style={{ background: "var(--brand-border)" }} />
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-sm font-medium mb-1.5 block" style={{ color: "var(--brand-text-muted)" }}>Email</label>
              <input
                id="input-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                className="input-field"
                required
              />
            </div>
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-sm font-medium" style={{ color: "var(--brand-text-muted)" }}>Mật khẩu</label>
                <Link href="/forgot-password" className="text-xs hover:text-orange-400 transition-colors" style={{ color: "var(--brand-primary)" }}>
                  Quên mật khẩu?
                </Link>
              </div>
              <div className="relative">
                <input
                  id="input-password"
                  type={showPw ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="input-field pr-11"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPw(!showPw)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 transition-colors"
                  style={{ color: "var(--brand-text-dim)" }}
                >
                  {showPw ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button
              id="btn-login-submit"
              type="submit"
              disabled={loading}
              className="btn-primary w-full py-3 flex items-center justify-center gap-2"
              style={{ opacity: loading ? 0.7 : 1 }}
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <>
                  <Zap size={16} />
                  Đăng nhập
                </>
              )}
            </button>
          </form>

          <p className="text-center text-sm mt-6" style={{ color: "var(--brand-text-muted)" }}>
            Chưa có tài khoản?{" "}
            <Link href="/register" className="font-semibold hover:underline" style={{ color: "var(--brand-primary)" }}>
              Đăng ký miễn phí
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
