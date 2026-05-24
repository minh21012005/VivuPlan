"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Eye, EyeOff, UserPlus, ArrowLeft } from "lucide-react";
import { AuthVisualPanel } from "@/components/auth/AuthVisualPanel";
import { useAuth } from "@/hooks/useAuth";

import { BrandLogo } from "@/components/layout/BrandLogo";

export default function RegisterPage() {
  const router = useRouter();
  const auth = useAuth();
  const [form, setForm] = useState({ name: "", email: "", password: "", confirm: "" });
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm((p) => ({ ...p, [k]: e.target.value }));

  const strength = form.password.length === 0 ? 0 : form.password.length < 6 ? 1 : form.password.length < 10 ? 2 : 3;
  const strengthColors = ["", "#EF4444", "#F59E0B", "#10B981"];
  const strengthLabels = ["", "Yếu", "Trung bình", "Mạnh"];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (form.password !== form.confirm) { setError("Mật khẩu xác nhận không khớp"); return; }
    if (form.password.length < 8) { setError("Mật khẩu phải có ít nhất 8 ký tự"); return; }
    setError(""); setLoading(true);
    try {
      await auth.register(form.name, form.email, form.password);
      router.push("/");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Đăng ký thất bại");
    } finally { setLoading(false); }
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)", display: "flex" }}>
      <AuthVisualPanel variant="register" />

      {/* Right panel */}
      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: "40px 24px", overflowY: "auto" }}>
        <div style={{ width: "100%", maxWidth: "400px" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "36px" }}>
            <Link href="/" style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "13px", color: "var(--text-3)", textDecoration: "none" }}>
              <ArrowLeft size={15} /> Trang chủ
            </Link>
            <div className="scale-[0.65] origin-right">
              <BrandLogo />
            </div>
          </div>

          <div style={{ marginBottom: "26px" }}>
            <div style={{ width: 46, height: 4, borderRadius: 99, background: "linear-gradient(135deg,var(--primary),var(--secondary))", marginBottom: "14px" }} />
            <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "30px", fontWeight: 850, color: "var(--text)", lineHeight: 1.16, marginBottom: "8px" }}>
              Tạo tài khoản
            </h1>
          </div>

          {error && (
            <div style={{ marginBottom: "16px", padding: "12px 14px", borderRadius: "var(--r-lg)", background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626", fontSize: "14px" }}>
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
            <div>
              <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "6px" }}>Họ và tên</label>
              <input id="input-name" type="text" value={form.name} onChange={set("name")} placeholder="Nguyễn Văn A" required className="input" />
            </div>

            <div>
              <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "6px" }}>Email</label>
              <input id="input-email" type="email" value={form.email} onChange={set("email")} placeholder="you@example.com" required className="input" />
            </div>

            <div>
              <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "6px" }}>Mật khẩu</label>
              <div style={{ position: "relative" }}>
                <input id="input-password" type={showPass ? "text" : "password"} value={form.password} onChange={set("password")} placeholder="Tối thiểu 8 ký tự" required className="input" style={{ paddingRight: "44px" }} />
                <button type="button" onClick={() => setShowPass(!showPass)} style={{ position: "absolute", right: "12px", top: "50%", transform: "translateY(-50%)", background: "none", border: "none", cursor: "pointer", color: "var(--text-4)" }}>
                  {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {form.password.length > 0 && (
                <div style={{ display: "flex", alignItems: "center", gap: "8px", marginTop: "8px" }}>
                  <div style={{ flex: 1, display: "flex", gap: "4px" }}>
                    {[1, 2, 3].map((i) => (
                      <div key={i} style={{ flex: 1, height: "4px", borderRadius: "99px", background: i <= strength ? strengthColors[strength] : "var(--surface-3)", transition: "background 0.2s" }} />
                    ))}
                  </div>
                  <span style={{ fontSize: "12px", fontWeight: 600, color: strengthColors[strength], width: "68px", textAlign: "right" }}>{strengthLabels[strength]}</span>
                </div>
              )}
            </div>

            <div>
              <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "6px" }}>Xác nhận mật khẩu</label>
              <input id="input-confirm" type="password" value={form.confirm} onChange={set("confirm")} placeholder="Nhập lại mật khẩu" required className="input" />
            </div>

            <button id="btn-register-submit" type="submit" disabled={loading} className="btn btn-primary" style={{ justifyContent: "center", padding: "13px", marginTop: "4px" }}>
              {loading ? <div className="spinner" style={{ borderTopColor: "white", borderColor: "rgba(255,255,255,0.3)" }} /> : <><UserPlus size={16} /> Tạo tài khoản miễn phí</>}
            </button>
          </form>

          <p style={{ textAlign: "center", fontSize: "14px", color: "var(--text-3)", marginTop: "20px" }}>
            Đã có tài khoản?{" "}
            <Link href="/login" style={{ color: "var(--primary)", fontWeight: 600, textDecoration: "none" }}>Đăng nhập</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
