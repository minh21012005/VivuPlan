"use client";
import { useState } from "react";
import Link from "next/link";
import { MapPin, Eye, EyeOff, Zap, Globe, User } from "lucide-react";

export default function RegisterPage() {
  const [form, setForm] = useState({ name: "", email: "", password: "", confirm: "" });
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleChange = (k: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [k]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (form.password !== form.confirm) return alert("Mật khẩu không khớp");
    setLoading(true);
    setTimeout(() => setLoading(false), 1500);
  };

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-12 relative" style={{ background: "var(--brand-dark)" }}>
      <div className="absolute inset-0 pointer-events-none" style={{ background: "radial-gradient(ellipse at 70% 40%, rgba(78,205,196,0.07) 0%, transparent 60%)" }} />

      <div className="w-full max-w-md relative z-10">
        <Link href="/" className="flex items-center gap-2 justify-center mb-8">
          <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: "var(--gradient-brand)" }}>
            <MapPin size={20} color="white" fill="white" />
          </div>
          <span className="text-2xl font-bold gradient-text" style={{ fontFamily: "'Plus Jakarta Sans',sans-serif" }}>VivuPlan</span>
        </Link>

        <div className="glass-strong rounded-2xl p-8">
          <h1 className="text-2xl font-bold mb-1" style={{ color: "var(--brand-text)", fontFamily: "'Plus Jakarta Sans',sans-serif" }}>
            Tạo tài khoản miễn phí ✨
          </h1>
          <p className="text-sm mb-6" style={{ color: "var(--brand-text-muted)" }}>
            Bắt đầu lập kế hoạch du lịch thông minh ngay hôm nay
          </p>

          <button
            id="btn-google-register"
            className="w-full flex items-center justify-center gap-3 py-3 rounded-xl mb-5 font-medium text-sm transition-all duration-200"
            style={{ background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.1)", color: "var(--brand-text)" }}
            onMouseEnter={(e) => (e.currentTarget.style.background = "rgba(255,255,255,0.1)")}
            onMouseLeave={(e) => (e.currentTarget.style.background = "rgba(255,255,255,0.06)")}
          >
            <Globe size={18} />
            Đăng ký với Google
          </button>

          <div className="flex items-center gap-3 mb-5">
            <div className="flex-1 h-px" style={{ background: "var(--brand-border)" }} />
            <span className="text-xs" style={{ color: "var(--brand-text-dim)" }}>hoặc</span>
            <div className="flex-1 h-px" style={{ background: "var(--brand-border)" }} />
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-sm font-medium mb-1.5 block" style={{ color: "var(--brand-text-muted)" }}>Họ và tên</label>
              <div className="relative">
                <input id="input-name" type="text" value={form.name} onChange={handleChange("name")} placeholder="Nguyễn Văn A" className="input-field pl-10" required />
                <User size={15} className="absolute left-3 top-1/2 -translate-y-1/2" style={{ color: "var(--brand-text-dim)" }} />
              </div>
            </div>
            <div>
              <label className="text-sm font-medium mb-1.5 block" style={{ color: "var(--brand-text-muted)" }}>Email</label>
              <input id="input-email" type="email" value={form.email} onChange={handleChange("email")} placeholder="you@example.com" className="input-field" required />
            </div>
            <div>
              <label className="text-sm font-medium mb-1.5 block" style={{ color: "var(--brand-text-muted)" }}>Mật khẩu</label>
              <div className="relative">
                <input id="input-password" type={showPw ? "text" : "password"} value={form.password} onChange={handleChange("password")} placeholder="Ít nhất 8 ký tự" className="input-field pr-11" required minLength={8} />
                <button type="button" onClick={() => setShowPw(!showPw)} className="absolute right-3 top-1/2 -translate-y-1/2" style={{ color: "var(--brand-text-dim)" }}>
                  {showPw ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>
            <div>
              <label className="text-sm font-medium mb-1.5 block" style={{ color: "var(--brand-text-muted)" }}>Xác nhận mật khẩu</label>
              <input id="input-confirm" type="password" value={form.confirm} onChange={handleChange("confirm")} placeholder="••••••••" className="input-field" required />
            </div>

            <button id="btn-register-submit" type="submit" disabled={loading} className="btn-primary w-full py-3 flex items-center justify-center gap-2" style={{ opacity: loading ? 0.7 : 1 }}>
              {loading ? <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <><Zap size={16} />Tạo tài khoản</>}
            </button>
          </form>

          <p className="text-xs text-center mt-4" style={{ color: "var(--brand-text-dim)" }}>
            Bằng cách đăng ký, bạn đồng ý với{" "}
            <Link href="/terms" className="hover:underline" style={{ color: "var(--brand-primary)" }}>Điều khoản</Link>
            {" "}và{" "}
            <Link href="/privacy" className="hover:underline" style={{ color: "var(--brand-primary)" }}>Chính sách bảo mật</Link>
          </p>

          <p className="text-center text-sm mt-4" style={{ color: "var(--brand-text-muted)" }}>
            Đã có tài khoản?{" "}
            <Link href="/login" className="font-semibold hover:underline" style={{ color: "var(--brand-primary)" }}>Đăng nhập</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
