"use client";
import { useState, useEffect } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { MapPin, Menu, X, Zap, LogIn, User, LogOut, LayoutDashboard } from "lucide-react";

const links = [
  { label: "Lập kế hoạch", href: "/plan" },
  { label: "Khám phá", href: "/explore" },
  { label: "Bảng giá", href: "/pricing" },
];

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);
  const [user, setUser] = useState<{ name: string; avatarUrl?: string } | null>(null);
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    try {
      const stored = localStorage.getItem("vp_user");
      if (stored) setUser(JSON.parse(stored));
    } catch { /* ignore */ }
  }, [pathname]); // refresh on route change

  const logout = () => {
    localStorage.removeItem("vp_token");
    localStorage.removeItem("vp_user");
    setUser(null);
    setUserMenuOpen(false);
    router.push("/");
  };

  const navStyle: React.CSSProperties = {
    position: "fixed", top: 0, left: 0, right: 0, zIndex: 50,
    transition: "all 0.3s ease",
    background: scrolled ? "rgba(13,27,42,0.92)" : "transparent",
    backdropFilter: scrolled ? "blur(12px)" : "none",
    borderBottom: scrolled ? "1px solid rgba(255,255,255,0.06)" : "none",
  };

  return (
    <nav style={navStyle}>
      <div className="max-w-7xl mx-auto px-4 md:px-6 h-16 flex items-center justify-between">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2 shrink-0">
          <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: "linear-gradient(135deg,#FF6B35,#FF8C42)" }}>
            <MapPin size={15} color="white" fill="white" />
          </div>
          <span className="text-lg font-bold" style={{
            fontFamily: "var(--font-heading)",
            background: "linear-gradient(135deg,#FF6B35,#4ECDC4)",
            WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent",
          }}>VivuPlan</span>
        </Link>

        {/* Desktop nav */}
        <div className="hidden md:flex items-center gap-1">
          {links.map(({ label, href }) => (
            <Link key={href} href={href}
              className="px-4 py-2 rounded-lg text-sm font-medium transition-colors"
              style={{ color: pathname === href ? "var(--brand-primary)" : "var(--brand-text-muted)" }}
              onMouseEnter={(e) => (e.currentTarget.style.color = "var(--brand-text)")}
              onMouseLeave={(e) => (e.currentTarget.style.color = pathname === href ? "var(--brand-primary)" : "var(--brand-text-muted)")}
            >{label}</Link>
          ))}
        </div>

        {/* Auth */}
        <div className="hidden md:flex items-center gap-2">
          {user ? (
            <div className="relative">
              <button
                id="btn-user-menu"
                onClick={() => setUserMenuOpen(!userMenuOpen)}
                className="flex items-center gap-2 px-3 py-2 rounded-xl text-sm font-medium transition-colors hover:bg-white/5"
                style={{ color: "var(--brand-text-muted)", border: "1px solid var(--brand-border)" }}
              >
                <div className="w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold"
                  style={{ background: "var(--gradient-brand)", color: "white" }}>
                  {user.name.charAt(0).toUpperCase()}
                </div>
                {user.name.split(" ").slice(-1)[0]}
              </button>
              {userMenuOpen && (
                <div className="absolute right-0 mt-2 w-48 rounded-xl overflow-hidden shadow-xl z-50"
                  style={{ background: "var(--brand-surface-2)", border: "1px solid var(--brand-border)" }}>
                  <Link href="/dashboard" onClick={() => setUserMenuOpen(false)}
                    className="flex items-center gap-2 px-4 py-3 text-sm transition-colors hover:bg-white/5"
                    style={{ color: "var(--brand-text-muted)" }}>
                    <LayoutDashboard size={14} /> Dashboard
                  </Link>
                  <button onClick={logout}
                    className="w-full flex items-center gap-2 px-4 py-3 text-sm transition-colors hover:bg-red-900/20 text-left"
                    style={{ color: "#ff6b6b" }}>
                    <LogOut size={14} /> Đăng xuất
                  </button>
                </div>
              )}
            </div>
          ) : (
            <>
              <Link href="/login">
                <button id="btn-login" className="btn-ghost flex items-center gap-1.5 text-sm px-4 py-2">
                  <LogIn size={14} /> Đăng nhập
                </button>
              </Link>
              <Link href="/plan">
                <button id="btn-start" className="btn-primary flex items-center gap-1.5 text-sm px-4 py-2">
                  <Zap size={13} /> Bắt đầu
                </button>
              </Link>
            </>
          )}
        </div>

        {/* Mobile menu toggle */}
        <button id="btn-mobile-menu" className="md:hidden p-2" style={{ color: "var(--brand-text-muted)" }}
          onClick={() => setOpen(!open)}>
          {open ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      {/* Mobile menu */}
      {open && (
        <div className="md:hidden px-4 pb-4" style={{ background: "rgba(13,27,42,0.96)", borderBottom: "1px solid var(--brand-border)" }}>
          {links.map(({ label, href }) => (
            <Link key={href} href={href} onClick={() => setOpen(false)}
              className="block py-3 text-sm font-medium border-b"
              style={{ color: "var(--brand-text-muted)", borderColor: "var(--brand-border)" }}>
              {label}
            </Link>
          ))}
          <div className="flex gap-2 mt-4">
            {user ? (
              <button onClick={logout} className="btn-secondary flex-1 py-2.5 text-sm flex items-center justify-center gap-2">
                <LogOut size={14} /> Đăng xuất
              </button>
            ) : (
              <>
                <Link href="/login" className="flex-1" onClick={() => setOpen(false)}>
                  <button className="btn-secondary w-full py-2.5 text-sm flex items-center justify-center gap-2">
                    <User size={14} /> Đăng nhập
                  </button>
                </Link>
                <Link href="/plan" className="flex-1" onClick={() => setOpen(false)}>
                  <button className="btn-primary w-full py-2.5 text-sm flex items-center justify-center gap-2">
                    <Zap size={13} /> Bắt đầu
                  </button>
                </Link>
              </>
            )}
          </div>
        </div>
      )}
    </nav>
  );
}
