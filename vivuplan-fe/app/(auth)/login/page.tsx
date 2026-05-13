"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MapPin, Eye, EyeOff, LogIn, Globe, ArrowLeft } from "lucide-react";
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
      setError(err instanceof Error ? err.message : "Email hoặc mật khẩu không đúng");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)", display: "flex" }}>
      {/* Left panel – decorative */}
      <div className="hidden lg:flex" style={{
        width: "44%", background: "linear-gradient(145deg, #FFF7ED 0%, #FEF3C7 50%, #EFF6FF 100%)",
        flexDirection: "column", justifyContent: "center", alignItems: "center",
        padding: "60px", borderRight: "1px solid var(--border)", position: "relative", overflow: "hidden",
      }}>
        <div style={{ position: "absolute", top: -60, right: -60, width: 300, height: 300, borderRadius: "50%", background: "rgba(249,115,22,0.07)" }} />
        <div style={{ position: "absolute", bottom: -40, left: -40, width: 200, height: 200, borderRadius: "50%", background: "rgba(14,165,233,0.07)" }} />
        <div style={{ position: "relative", zIndex: 1, textAlign: "center", maxWidth: "360px" }}>
          <div style={{ fontSize: "80px", marginBottom: "24px" }}>🗺️</div>
          <h2 style={{ fontFamily: "var(--font-heading)", fontSize: "28px", fontWeight: 800, color: "var(--text)", marginBottom: "16px", lineHeight: 1.3 }}>
            Hành trình đẹp bắt đầu từ một kế hoạch tốt
          </h2>
          <p style={{ fontSize: "15px", color: "var(--text-3)", lineHeight: 1.7 }}>
            Đăng nhập để lưu lịch trình, chia sẻ với bạn bè và quản lý mọi chuyến đi của bạn.
          </p>
          <div style={{ display: "flex", gap: "16px", marginTop: "32px", justifyContent: "center" }}>
            {["🌸 Đà Lạt", "⛵ Hạ Long", "🏖️ Quy Nhơn"].map((d) => (
              <span key={d} className="badge badge-orange" style={{ fontSize: "12px" }}>{d}</span>
            ))}
          </div>
        </div>
      </div>

      {/* Right panel – form */}
      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: "40px 24px" }}>
        <div style={{ width: "100%", maxWidth: "400px" }}>
          {/* Back + Logo */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "40px" }}>
            <Link href="/" style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "13px", color: "var(--text-3)", textDecoration: "none" }}
              onMouseEnter={(e) => e.currentTarget.style.color = "var(--text)"}
              onMouseLeave={(e) => e.currentTarget.style.color = "var(--text-3)"}>
              <ArrowLeft size={15} /> Trang chủ
            </Link>
            <Link href="/" style={{ display: "flex", alignItems: "center", gap: "8px", textDecoration: "none" }}>
              <div style={{ width: 30, height: 30, borderRadius: 8, background: "linear-gradient(135deg,#F97316,#FB923C)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <MapPin size={14} color="white" fill="white" />
              </div>
              <span style={{ fontFamily: "var(--font-heading)", fontWeight: 800, fontSize: "16px", background: "linear-gradient(135deg,#F97316,#EA580C)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>VivuPlan</span>
            </Link>
          </div>

          <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "26px", fontWeight: 800, color: "var(--text)", marginBottom: "6px" }}>Đăng nhập</h1>
          <p style={{ fontSize: "14px", color: "var(--text-3)", marginBottom: "28px" }}>Chào mừng trở lại! Hãy tiếp tục hành trình của bạn.</p>

          {/* Google button */}
          <button id="btn-google-login"
            className="btn btn-secondary"
            style={{ width: "100%", marginBottom: "20px", justifyContent: "center", padding: "12px" }}>
            <Globe size={17} /> Tiếp tục với Google
          </button>

          <div className="divider" style={{ marginBottom: "20px" }}>hoặc dùng email</div>

          {error && (
            <div style={{
              marginBottom: "16px", padding: "12px 14px", borderRadius: "var(--r-lg)",
              background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626", fontSize: "14px",
            }}>{error}</div>
          )}

          <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
            <div>
              <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "6px" }}>Email</label>
              <input id="input-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com" required className="input" />
            </div>

            <div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px" }}>
                <label style={{ fontSize: "13px", fontWeight: 600, color: "var(--text-2)" }}>Mật khẩu</label>
                <Link href="/forgot-password" style={{ fontSize: "12px", color: "var(--primary)", textDecoration: "none" }}>Quên mật khẩu?</Link>
              </div>
              <div style={{ position: "relative" }}>
                <input id="input-password" type={showPass ? "text" : "password"} value={password} onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••" required className="input" style={{ paddingRight: "44px" }} />
                <button type="button" onClick={() => setShowPass(!showPass)} style={{
                  position: "absolute", right: "12px", top: "50%", transform: "translateY(-50%)",
                  background: "none", border: "none", cursor: "pointer", color: "var(--text-4)", padding: "4px",
                }}>
                  {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button id="btn-login-submit" type="submit" disabled={loading}
              className="btn btn-primary" style={{ justifyContent: "center", padding: "13px", marginTop: "4px" }}>
              {loading ? <div className="spinner" style={{ borderTopColor: "white", borderColor: "rgba(255,255,255,0.3)" }} /> : <><LogIn size={16} /> Đăng nhập</>}
            </button>
          </form>

          <p style={{ textAlign: "center", fontSize: "14px", color: "var(--text-3)", marginTop: "24px" }}>
            Chưa có tài khoản?{" "}
            <Link href="/register" style={{ color: "var(--primary)", fontWeight: 600, textDecoration: "none" }}>Đăng ký miễn phí</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
