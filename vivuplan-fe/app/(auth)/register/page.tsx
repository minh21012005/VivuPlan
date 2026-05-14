"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MapPin, Eye, EyeOff, UserPlus, ArrowLeft, CheckCircle } from "lucide-react";
import { authApi } from "@/lib/api";

const perks = [
  "Tạo không giới hạn lịch trình AI",
  "Lưu và chia sẻ chuyến đi dễ dàng",
  "Theo dõi ngân sách chi tiết",
  "Hoàn toàn miễn phí để bắt đầu",
];

export default function RegisterPage() {
  const router = useRouter();
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
      const res = await authApi.register({ name: form.name, email: form.email, password: form.password });
      localStorage.setItem("vp_token", res.token);
      localStorage.setItem("vp_user", JSON.stringify(res.user));
      router.push("/dashboard");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Đăng ký thất bại");
    } finally { setLoading(false); }
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)", display: "flex" }}>
      {/* Left panel */}
      <div className="hidden lg:flex" style={{
        width: "44%", background: "linear-gradient(145deg, #F0FDF4 0%, #E0F2FE 50%, #E6FFFB 100%)",
        flexDirection: "column", justifyContent: "center", padding: "60px",
        borderRight: "1px solid var(--border)", position: "relative", overflow: "hidden",
      }}>
        <div style={{ position: "absolute", top: -80, left: -80, width: 320, height: 320, borderRadius: "50%", background: "rgba(16,185,129,0.06)" }} />
        <div style={{ position: "relative", zIndex: 1 }}>
          <div style={{ fontSize: "56px", marginBottom: "24px" }}>✈️</div>
          <h2 style={{ fontFamily: "var(--font-heading)", fontSize: "28px", fontWeight: 800, color: "var(--text)", marginBottom: "12px", lineHeight: 1.3 }}>
            Bắt đầu hành trình khám phá Việt Nam
          </h2>
          <p style={{ fontSize: "15px", color: "var(--text-3)", marginBottom: "32px", lineHeight: 1.7 }}>
            Đăng ký miễn phí và sử dụng AI để tạo lịch trình du lịch hoàn hảo.
          </p>
          <div style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
            {perks.map((p) => (
              <div key={p} style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                <div style={{ width: 22, height: 22, borderRadius: "50%", background: "#D1FAE5", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                  <CheckCircle size={13} color="#10B981" />
                </div>
                <span style={{ fontSize: "14px", color: "var(--text-2)" }}>{p}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right panel */}
      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: "40px 24px", overflowY: "auto" }}>
        <div style={{ width: "100%", maxWidth: "400px" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "36px" }}>
            <Link href="/" style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "13px", color: "var(--text-3)", textDecoration: "none" }}>
              <ArrowLeft size={15} /> Trang chủ
            </Link>
            <Link href="/" style={{ display: "flex", alignItems: "center", gap: "8px", textDecoration: "none" }}>
              <div style={{ width: 30, height: 30, borderRadius: 8, background: "linear-gradient(135deg,var(--primary),var(--secondary))", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <MapPin size={14} color="white" fill="white" />
              </div>
              <span style={{ fontFamily: "var(--font-heading)", fontWeight: 800, fontSize: "16px", background: "linear-gradient(135deg,var(--primary),var(--secondary))", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>VivuPlan</span>
            </Link>
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
                    {[1,2,3].map((i) => (
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
