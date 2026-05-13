"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MapPin, Eye, EyeOff, Zap, Globe, User } from "lucide-react";
import { authApi } from "@/lib/api";

export default function RegisterPage() {
  const router = useRouter();
  const [form, setForm] = useState({ name: "", email: "", password: "", confirm: "" });
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((p) => ({ ...p, [k]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (form.password !== form.confirm) { setError("Mật khẩu xác nhận không khớp"); return; }
    if (form.password.length < 8) { setError("Mật khẩu phải có ít nhất 8 ký tự"); return; }
    setLoading(true);
    try {
      const res = await authApi.register({ name: form.name, email: form.email, password: form.password });
      localStorage.setItem("vp_token", res.token);
      localStorage.setItem("vp_user", JSON.stringify(res.user));
      router.push("/dashboard");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Đăng ký thất bại");
    } finally {
      setLoading(false);
    }
  };

  const strength = form.password.length === 0 ? 0 : form.password.length < 6 ? 1 : form.password.length < 10 ? 2 : 3;
  const strengthLabel = ["", "Yếu", "Trung bình", "Mạnh"];
  const strengthColor = ["", "#ff6b6b", "#FFE66D", "#4ECDC4"];

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-12" style={{ background: "var(--brand-dark)" }}>
      <div className="w-full max-w-md">
        <Link href="/" className="flex items-center gap-2 mb-8 justify-center">
          <div className="w-9 h-9 rounded-xl flex items-center justify-center" style={{ background: "var(--gradient-brand)" }}>
            <MapPin size={18} color="white" fill="white" />
          </div>
          <span className="text-xl font-bold gradient-text" style={{ fontFamily: "var(--font-heading)" }}>VivuPlan</span>
        </Link>

        <div className="glass-strong rounded-2xl p-8">
          <h1 className="text-2xl font-bold mb-1 text-center" style={{ fontFamily: "var(--font-heading)", color: "var(--brand-text)" }}>
            Tạo tài khoản
          </h1>
          <p className="text-sm text-center mb-7" style={{ color: "var(--brand-text-muted)" }}>
            Miễn phí mãi mãi. Không cần thẻ tín dụng.
          </p>

          <button
            id="btn-google-register"
            className="w-full flex items-center justify-center gap-3 py-3 rounded-xl mb-5 text-sm font-medium transition-all duration-200"
            style={{ background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.1)", color: "var(--brand-text-muted)" }}
            onMouseEnter={(e) => (e.currentTarget.style.background = "rgba(255,255,255,0.1)")}
            onMouseLeave={(e) => (e.currentTarget.style.background = "rgba(255,255,255,0.06)")}
          >
            <Globe size={18} /> Đăng ký với Google
          </button>

          <div className="flex items-center gap-3 mb-5">
            <div className="flex-1 h-px" style={{ background: "var(--brand-border)" }} />
            <span className="text-xs" style={{ color: "var(--brand-text-dim)" }}>hoặc email</span>
            <div className="flex-1 h-px" style={{ background: "var(--brand-border)" }} />
          </div>

          {error && (
            <div className="mb-4 p-3 rounded-xl text-sm" style={{ background: "rgba(255,107,107,0.1)", border: "1px solid rgba(255,107,107,0.2)", color: "#ff6b6b" }}>
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-sm font-medium mb-1.5 block" style={{ color: "var(--brand-text-muted)" }}>Họ và tên</label>
              <div className="relative">
                <input id="input-name" type="text" value={form.name} onChange={set("name")}
                  placeholder="Nguyễn Văn A" required className="input-field pl-10" />
                <User size={15} className="absolute left-3 top-1/2 -translate-y-1/2" style={{ color: "var(--brand-text-dim)" }} />
              </div>
            </div>

            <div>
              <label className="text-sm font-medium mb-1.5 block" style={{ color: "var(--brand-text-muted)" }}>Email</label>
              <input id="input-email" type="email" value={form.email} onChange={set("email")}
                placeholder="you@example.com" required className="input-field" />
            </div>

            <div>
              <label className="text-sm font-medium mb-1.5 block" style={{ color: "var(--brand-text-muted)" }}>Mật khẩu</label>
              <div className="relative">
                <input id="input-password" type={showPass ? "text" : "password"} value={form.password} onChange={set("password")}
                  placeholder="Tối thiểu 8 ký tự" required className="input-field pr-11" />
                <button type="button" onClick={() => setShowPass(!showPass)}
                  className="absolute right-3 top-1/2 -translate-y-1/2" style={{ color: "var(--brand-text-dim)" }}>
                  {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {form.password.length > 0 && (
                <div className="mt-1.5 flex items-center gap-2">
                  <div className="flex gap-1 flex-1">
                    {[1,2,3].map((i) => (
                      <div key={i} className="h-1 flex-1 rounded-full transition-all duration-300"
                        style={{ background: i <= strength ? strengthColor[strength] : "var(--brand-surface-3)" }} />
                    ))}
                  </div>
                  <span className="text-xs font-medium" style={{ color: strengthColor[strength] }}>
                    {strengthLabel[strength]}
                  </span>
                </div>
              )}
            </div>

            <div>
              <label className="text-sm font-medium mb-1.5 block" style={{ color: "var(--brand-text-muted)" }}>Xác nhận mật khẩu</label>
              <input id="input-confirm" type="password" value={form.confirm} onChange={set("confirm")}
                placeholder="Nhập lại mật khẩu" required className="input-field" />
            </div>

            <button id="btn-register-submit" type="submit" disabled={loading}
              className="btn-primary w-full py-3 flex items-center justify-center gap-2">
              {loading ? (
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <><Zap size={15} /> Tạo tài khoản miễn phí</>
              )}
            </button>
          </form>

          <p className="text-center text-xs mt-5" style={{ color: "var(--brand-text-dim)" }}>
            Bằng cách đăng ký, bạn đồng ý với{" "}
            <Link href="/terms" className="hover:underline" style={{ color: "var(--brand-primary)" }}>Điều khoản dịch vụ</Link>
            {" "}và{" "}
            <Link href="/privacy" className="hover:underline" style={{ color: "var(--brand-primary)" }}>Chính sách bảo mật</Link>
          </p>

          <p className="text-center text-sm mt-4" style={{ color: "var(--brand-text-muted)" }}>
            Đã có tài khoản?{" "}
            <Link href="/login" className="font-semibold hover:underline" style={{ color: "var(--brand-primary)" }}>
              Đăng nhập
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
