"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, CheckCircle2, Eye, EyeOff, Mail, RefreshCw, ShieldCheck, UserPlus } from "lucide-react";
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
  const [expiresIn, setExpiresIn] = useState(0);
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((p) => ({ ...p, [k]: e.target.value }));
  };

  useEffect(() => {
    if (!auth.loading && auth.user) {
      router.replace("/");
    }
  }, [auth.loading, auth.user, router]);

  useEffect(() => {
    if (step !== "otp" || expiresIn <= 0) return;
    const timer = window.setInterval(() => {
      setExpiresIn((value) => Math.max(0, value - 1));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [step, expiresIn]);

  const strength = form.password.length === 0 ? 0 : form.password.length < 6 ? 1 : form.password.length < 10 ? 2 : 3;
  const strengthColors = ["", "#EF4444", "#F59E0B", "#10B981"];
  const strengthLabels = ["", "Yếu", "Trung bình", "Mạnh"];
  const countdown = useMemo(() => {
    const minutes = Math.floor(expiresIn / 60);
    const seconds = expiresIn % 60;
    return `${minutes}:${String(seconds).padStart(2, "0")}`;
  }, [expiresIn]);

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
    setNotice("");
    setLoading(true);
    try {
      const res = await auth.requestRegisterOtp(form.name, form.email, form.password);
      setPendingEmail(res.email);
      setExpiresIn(res.expiresInSeconds);
      setOtp("");
      setStep("otp");
      setNotice("Mã xác nhận đã được gửi tới email của bạn.");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Đăng ký thất bại");
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
    setNotice("");
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
    setNotice("");
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

          {notice && (
            <div style={{ marginBottom: "16px", padding: "12px 14px", borderRadius: "var(--r-lg)", background: "#ECFDF5", border: "1px solid #BBF7D0", color: "#047857", fontSize: "14px", display: "flex", gap: 8, alignItems: "center" }}>
              <CheckCircle2 size={16} /> {notice}
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
              <div style={{ padding: "14px 16px", borderRadius: "var(--r-lg)", border: "1px solid var(--border)", background: "var(--surface)", display: "flex", alignItems: "center", gap: "12px" }}>
                <div style={{ width: 38, height: 38, borderRadius: "12px", display: "grid", placeItems: "center", background: "var(--primary-soft)", color: "var(--primary)" }}>
                  <ShieldCheck size={19} />
                </div>
                <div>
                  <div style={{ fontSize: "13px", color: "var(--text-3)", marginBottom: 2 }}>Mã xác nhận hết hạn sau</div>
                  <div style={{ fontSize: "16px", fontWeight: 800, color: expiresIn > 0 ? "var(--text)" : "#DC2626" }}>
                    {expiresIn > 0 ? countdown : "Đã hết hạn"}
                  </div>
                </div>
              </div>

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

              <button id="btn-register-verify" type="submit" disabled={loading || auth.loading || expiresIn <= 0} className="btn btn-primary" style={{ justifyContent: "center", padding: "13px", marginTop: "4px" }}>
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
