"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Eye, EyeOff, LogIn, ArrowLeft } from "lucide-react";
import { AuthVisualPanel } from "@/components/auth/AuthVisualPanel";
import { GoogleAuthButton } from "@/components/auth/GoogleAuthButton";
import { type AuthResponse } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";

import { BrandLogo } from "@/components/layout/BrandLogo";

export default function LoginPage() {
  const router = useRouter();
  const auth = useAuth();
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
      await auth.login(email, password);
      router.push("/");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Email hoặc mật khẩu không đúng");
    } finally {
      setLoading(false);
    }
  };

  const handleAuthSuccess = (res: AuthResponse) => {
    auth.setSession(res);
    router.push("/");
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)", display: "flex" }}>
      <AuthVisualPanel variant="login" />

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
            <div className="scale-[0.65] origin-right">
              <BrandLogo />
            </div>
          </div>

          <div style={{ marginBottom: "26px" }}>
            <div style={{ width: 46, height: 4, borderRadius: 99, background: "linear-gradient(135deg,var(--primary),var(--secondary))", marginBottom: "14px" }} />
            <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "30px", fontWeight: 850, color: "var(--text)", lineHeight: 1.16, marginBottom: "8px" }}>
              Đăng nhập
            </h1>
          </div>

          <GoogleAuthButton onSuccess={handleAuthSuccess} onError={setError} />
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
