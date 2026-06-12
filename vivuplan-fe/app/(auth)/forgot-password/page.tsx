"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, CheckCircle2, Eye, EyeOff, KeyRound, Mail, RefreshCw } from "lucide-react";
import { AuthVisualPanel } from "@/components/auth/AuthVisualPanel";
import { BrandLogo } from "@/components/layout/BrandLogo";
import { authApi } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";

type ForgotStep = "email" | "otp" | "done";

export default function ForgotPasswordPage() {
  const router = useRouter();
  const auth = useAuth();
  const [step, setStep] = useState<ForgotStep>("email");
  const [email, setEmail] = useState("");
  const [pendingEmail, setPendingEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!auth.loading && auth.user) {
      router.replace("/");
    }
  }, [auth.loading, auth.user, router]);

  if (auth.loading || auth.user) return null;

  const requestOtp = async (targetEmail = email) => {
    setError("");
    setLoading(true);
    try {
      const res = await authApi.requestPasswordResetOtp({ email: targetEmail });
      setPendingEmail(res.email);
      setOtp("");
      setStep("otp");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không thể gửi mã xác nhận. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  };

  const handleEmailSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    await requestOtp();
  };

  const handleResetSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!/^\d{6}$/.test(otp.trim())) {
      setError("Vui lòng nhập mã xác nhận gồm 6 chữ số");
      return;
    }
    if (password.length < 8) {
      setError("Mật khẩu mới phải có ít nhất 8 ký tự");
      return;
    }
    if (password !== confirm) {
      setError("Mật khẩu xác nhận không khớp");
      return;
    }

    setError("");
    setLoading(true);
    try {
      await authApi.resetPasswordWithOtp({
        email: pendingEmail || email,
        otp: otp.trim(),
        newPassword: password,
      });
      setStep("done");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không thể đặt lại mật khẩu. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)", display: "flex" }}>
      <AuthVisualPanel variant="login" />

      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: "40px 24px" }}>
        <div style={{ width: "100%", maxWidth: "400px" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "40px" }}>
            <Link href="/login" style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "13px", color: "var(--text-3)", textDecoration: "none" }}>
              <ArrowLeft size={15} /> Đăng nhập
            </Link>
            <div className="scale-[0.65] origin-right">
              <BrandLogo />
            </div>
          </div>

          <div style={{ marginBottom: "26px" }}>
            <div style={{ width: 46, height: 4, borderRadius: 99, background: "linear-gradient(135deg,var(--primary),var(--secondary))", marginBottom: "14px" }} />
            <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "30px", fontWeight: 850, color: "var(--text)", lineHeight: 1.16, marginBottom: "8px" }}>
              {step === "done" ? "Mật khẩu đã được đổi" : step === "otp" ? "Tạo mật khẩu mới" : "Quên mật khẩu"}
            </h1>
            {step === "otp" && (
              <p style={{ margin: 0, color: "var(--text-3)", fontSize: "14px", lineHeight: 1.65 }}>
                Nhập mã 6 chữ số được gửi tới <strong style={{ color: "var(--text)" }}>{pendingEmail}</strong>.
              </p>
            )}
          </div>

          {error && (
            <div style={{ marginBottom: "16px", padding: "12px 14px", borderRadius: "var(--r-lg)", background: "#FEF2F2", border: "1px solid #FECACA", color: "#DC2626", fontSize: "14px" }}>
              {error}
            </div>
          )}

          {step === "email" ? (
            <form onSubmit={handleEmailSubmit} style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
              <div>
                <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "6px" }}>Email</label>
                <input
                  id="input-forgot-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  required
                  className="input"
                />
              </div>

              <button type="submit" disabled={loading || auth.loading} className="btn btn-primary" style={{ justifyContent: "center", padding: "13px", marginTop: "4px" }}>
                {loading ? <div className="spinner" style={{ borderTopColor: "white", borderColor: "rgba(255,255,255,0.3)" }} /> : <><Mail size={16} /> Gửi mã xác nhận</>}
              </button>
            </form>
          ) : step === "otp" ? (
            <form onSubmit={handleResetSubmit} style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
              <div>
                <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "6px" }}>Mã xác nhận</label>
                <input
                  id="input-reset-otp"
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

              <div>
                <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "6px" }}>Mật khẩu mới</label>
                <div style={{ position: "relative" }}>
                  <input
                    id="input-new-password"
                    type={showPass ? "text" : "password"}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Tối thiểu 8 ký tự"
                    required
                    className="input"
                    style={{ paddingRight: "44px" }}
                  />
                  <button type="button" onClick={() => setShowPass(!showPass)} style={{ position: "absolute", right: "12px", top: "50%", transform: "translateY(-50%)", background: "none", border: "none", cursor: "pointer", color: "var(--text-4)", padding: "4px" }}>
                    {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <div>
                <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "var(--text-2)", marginBottom: "6px" }}>Xác nhận mật khẩu</label>
                <input
                  id="input-confirm-password"
                  type="password"
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                  placeholder="Nhập lại mật khẩu"
                  required
                  className="input"
                />
              </div>

              <button type="submit" disabled={loading || auth.loading} className="btn btn-primary" style={{ justifyContent: "center", padding: "13px", marginTop: "4px" }}>
                {loading ? <div className="spinner" style={{ borderTopColor: "white", borderColor: "rgba(255,255,255,0.3)" }} /> : <><KeyRound size={16} /> Đặt lại mật khẩu</>}
              </button>

              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
                <button type="button" onClick={() => { setStep("email"); setOtp(""); setError(""); }} style={{ border: "none", background: "transparent", color: "var(--text-3)", fontSize: 13, fontWeight: 700, cursor: "pointer", padding: 0 }}>
                  Đổi email
                </button>
                <button type="button" onClick={() => requestOtp(pendingEmail || email)} disabled={loading || auth.loading} style={{ border: "none", background: "transparent", color: "var(--primary)", fontSize: 13, fontWeight: 800, cursor: "pointer", padding: 0, display: "inline-flex", alignItems: "center", gap: 6 }}>
                  <RefreshCw size={14} /> Gửi lại mã
                </button>
              </div>
            </form>
          ) : (
            <div style={{ display: "grid", gap: 16 }}>
              <div style={{ display: "flex", gap: 12, alignItems: "flex-start", padding: "14px", borderRadius: "var(--r-lg)", background: "#ECFDF5", border: "1px solid #BBF7D0", color: "#047857", fontSize: 14, lineHeight: 1.6 }}>
                <CheckCircle2 size={20} />
                <span>Bạn có thể đăng nhập bằng mật khẩu mới ngay bây giờ.</span>
              </div>
              <Link href="/login" className="btn btn-primary" style={{ justifyContent: "center", padding: "13px" }}>
                Đăng nhập
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
