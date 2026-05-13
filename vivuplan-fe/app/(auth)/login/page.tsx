"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MapPin, Eye, EyeOff, Zap, Globe } from "lucide-react";
import { authApi } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await authApi.login({ email, password });
      localStorage.setItem("vp_token", res.token);
      localStorage.setItem("vp_user", JSON.stringify(res.user));
      router.push("/dashboard");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Đăng nhập thất bại");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center px-4" style={{ background: "var(--brand-dark)" }}>
      <div className="w-full max-w-md">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2 mb-8 justify-center">
          <div className="w-9 h-9 rounded-xl flex items-center justify-center" style={{ background: "var(--gradient-brand)" }}>
            <MapPin size={18} color="white" fill="white" />
          </div>
          <span className="text-xl font-bold gradient-text" style={{ fontFamily: "var(--font-heading)" }}>VivuPlan</span>
        </Link>

        <div className="glass-strong rounded-2xl p-8">
          <h1 className="text-2xl font-bold mb-1 text-center" style={{ fontFamily: "var(--font-heading)", color: "var(--brand-text)" }}>
            Đăng nhập
          </h1>
          <p className="text-sm text-center mb-7" style={{ color: "var(--brand-text-muted)" }}>
            Chào mừng trở lại! Lên kế hoạch chuyến đi tiếp theo ngay.
          </p>

          {/* Google button */}
          <button
            id="btn-google-login"
            className="w-full flex items-center justify-center gap-3 py-3 rounded-xl mb-5 text-sm font-medium transition-all duration-200"
            style={{ background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.1)", color: "var(--brand-text-muted)" }}
            onMouseEnter={(e) => (e.currentTarget.style.background = "rgba(255,255,255,0.1)")}
            onMouseLeave={(e) => (e.currentTarget.style.background = "rgba(255,255,255,0.06)")}
          >
            <Globe size={18} /> Tiếp tục với Google
          </button>

          <div className="flex items-center gap-3 mb-5">
            <div className="flex-1 h-px" style={{ background: "var(--brand-border)" }} />
            <span className="text-xs" style={{ color: "var(--brand-text-dim)" }}>hoặc</span>
            <div className="flex-1 h-px" style={{ background: "var(--brand-border)" }} />
          </div>

          {error && (
            <div className="mb-4 p-3 rounded-xl text-sm" style={{ background: "rgba(255,107,107,0.1)", border: "1px solid rgba(255,107,107,0.2)", color: "#ff6b6b" }}>
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-sm font-medium mb-1.5 block" style={{ color: "var(--brand-text-muted)" }}>Email</label>
              <input
                id="input-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                required
                className="input-field"
              />
            </div>

            <div>
              <label className="text-sm font-medium mb-1.5 block" style={{ color: "var(--brand-text-muted)" }}>Mật khẩu</label>
              <div className="relative">
                <input
                  id="input-password"
                  type={showPass ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  required
                  className="input-field pr-11"
                />
                <button type="button" onClick={() => setShowPass(!showPass)}
                  className="absolute right-3 top-1/2 -translate-y-1/2" style={{ color: "var(--brand-text-dim)" }}>
                  {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <div className="flex justify-end">
              <Link href="/forgot-password" className="text-xs hover:text-orange-400 transition-colors" style={{ color: "var(--brand-text-dim)" }}>
                Quên mật khẩu?
              </Link>
            </div>

            <button
              id="btn-login-submit"
              type="submit"
              disabled={loading}
              className="btn-primary w-full py-3 flex items-center justify-center gap-2"
            >
              {loading ? (
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <><Zap size={15} /> Đăng nhập</>
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
