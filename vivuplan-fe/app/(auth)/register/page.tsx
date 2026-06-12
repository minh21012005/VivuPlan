"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Eye, EyeOff, Mail, RefreshCw, UserPlus } from "lucide-react";
import { AuthVisualPanel } from "@/components/auth/AuthVisualPanel";
import { useAuth } from "@/hooks/useAuth";
import { BrandLogo } from "@/components/layout/BrandLogo";

type RegisterStep = "details" | "otp";

export default function RegisterPage() {
  const router = useRouter();
  const auth = useAuth();
  const [step, setStep] = useState<RegisterStep>("details");
  const [form, setForm] = useState({ name: "", email: "", password: "", confirm: "" });
  const [otp, setOtp] = useState("");
  const [pendingEmail, setPendingEmail] = useState("");
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((p) => ({ ...p, [k]: e.target.value }));
  };

  useEffect(() => {
    if (!auth.loading && auth.user) {
      router.replace("/");
    }
  }, [auth.loading, auth.user, router]);

  if (auth.loading || auth.user) return null;

  const strength = form.password.length === 0 ? 0 : form.password.length < 6 ? 1 : form.password.length < 10 ? 2 : 3;
  const strengthColors = ["", "#EF4444", "#F59E0B", "#10B981"];
  const strengthLabels = ["", "Yếu", "Trung bình", "Mạnh"];

  const validateForm = () => {
    if (form.password !== form.confirm) {
      setError("Mật khẩu xác nhận không khớp");
      return false;
    }
    if (form.password.length < 8) {
      setError("Mật khẩu phải có ít nhất 8 ký tự");
      return false;
    }
    return true;
  };

  const requestOtp = async () => {
    if (!validateForm()) return;
    setError("");
    setLoading(true);
    try {
      const res = await auth.requestRegisterOtp(form.name, form.email, form.password);
      setPendingEmail(res.email);
      setOtp("");
      setStep("otp");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không thể gửi mã xác nhận. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await requestOtp();
  };

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^\d{6}$/.test(otp.trim())) {
      setError("Vui lòng nhập mã xác nhận gồm 6 chữ số");
      return;
    }
    setError("");
    setLoading(true);
    try {
      await auth.verifyRegisterOtp(pendingEmail || form.email, otp.trim());
      router.push("/");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Mã xác nhận không đúng");
    } finally {
      setLoading(false);
    }
  };

  const returnToDetails = () => {
    setStep("details");
    setOtp("");
    setError("");
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)", display: "flex" }}>
      <AuthVisualPanel variant="register" />

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
              {step === "otp" ? "Xác nhận email" : "Tạo tài khoản"}
            </h1>
            {step === "otp" && (
              <p style={{ margin: 0, color: "var(--text-3)", fontSize: "14px", lineHeight: 1.65 }}>
                Nhập mã 6 chữ số vừa được gửi tới <strong style={{ color: "var(--text)" }}>{pendingEmail}</strong>.
              </p>
            )}
          </div>

          {error && (
            <div style={{ marginBottom: "16px", padding: "12px 14px", borderRadius: "var(--r-lg)", background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626", fontSize: "14px" }}>
              {error}
            </div>
          )}

          {step === "details" ? (
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

              <button id="btn-register-submit" type="submit" disabled={loading || auth.loading} className="btn btn-primary" style={{ justifyContent: "center", padding: "13px", marginTop: "4px" }}>
                {loading ? <div className="spinner" style={{ borderTopColor: "white", borderColor: "rgba(255,255,255,0.3)" }} /> : <><Mail size={16} /> Gửi mã xác nhận</>}
              </button>
            </form>
          ) : (
            <form onSubmit={handleVerify} style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
              <div>
                <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "6px" }}>Mã xác nhận</label>
                <input
                  id="input-register-otp"
                  type="text"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))}
                  placeholder="000000"
                  required
                  className="input"
                  style={{ textAlign: "center", letterSpacing: "0.32em", fontSize: "22px", fontWeight: 800 }}
                />
              </div>

              <button id="btn-register-verify" type="submit" disabled={loading || auth.loading} className="btn btn-primary" style={{ justifyContent: "center", padding: "13px", marginTop: "4px" }}>
                {loading ? <div className="spinner" style={{ borderTopColor: "white", borderColor: "rgba(255,255,255,0.3)" }} /> : <><UserPlus size={16} /> Hoàn tất đăng ký</>}
              </button>

              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
                <button type="button" onClick={returnToDetails} style={{ border: "none", background: "transparent", color: "var(--text-3)", fontSize: 13, fontWeight: 700, cursor: "pointer", padding: 0 }}>
                  Đổi email
                </button>
                <button type="button" onClick={requestOtp} disabled={loading || auth.loading} style={{ border: "none", background: "transparent", color: "var(--primary)", fontSize: 13, fontWeight: 800, cursor: "pointer", padding: 0, display: "inline-flex", alignItems: "center", gap: 6 }}>
                  <RefreshCw size={14} /> Gửi lại mã
                </button>
              </div>
            </form>
          )}

          <p style={{ textAlign: "center", fontSize: "14px", color: "var(--text-3)", marginTop: "20px" }}>
            Đã có tài khoản?{" "}
            <Link href="/login" style={{ color: "var(--primary)", fontWeight: 600, textDecoration: "none" }}>Đăng nhập</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
