"use client";
import { useState, useEffect } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Compass, Menu, X, ChevronDown, LayoutDashboard, LogOut, LogIn } from "lucide-react";

const links = [
  { label: "Lập kế hoạch", href: "/plan" },
  { label: "Khám phá", href: "/explore" },
  { label: "Bảng giá", href: "/pricing" },
];

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [user, setUser] = useState<{ name: string } | null>(null);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
    try {
      const s = localStorage.getItem("vp_user");
      if (s) setUser(JSON.parse(s));
    } catch { /* */ }
    }, 0);
    return () => window.clearTimeout(timer);
  }, [pathname]);

  const logout = () => {
    localStorage.removeItem("vp_token");
    localStorage.removeItem("vp_user");
    setUser(null);
    setUserMenuOpen(false);
    router.push("/");
  };

  return (
    <>
      <header
        style={{
          position: "fixed", top: 0, left: 0, right: 0, zIndex: 100,
          background: scrolled ? "rgba(255,255,255,0.92)" : "rgba(255,255,255,0.75)",
          backdropFilter: "blur(16px)",
          borderBottom: `1px solid ${scrolled ? "var(--border)" : "transparent"}`,
          boxShadow: scrolled ? "var(--shadow-sm)" : "none",
          transition: "all 0.25s ease",
        }}
      >
        <div className="container" style={{ display: "flex", alignItems: "center", height: "64px", gap: "32px" }}>
          {/* Logo */}
          <Link href="/" style={{ display: "flex", alignItems: "center", gap: "9px", textDecoration: "none", flexShrink: 0 }}>
            <div style={{
              width: 34, height: 34, borderRadius: 10,
              background: "linear-gradient(135deg, var(--primary), var(--secondary))",
              display: "flex", alignItems: "center", justifyContent: "center",
              boxShadow: "0 2px 8px rgba(15,159,156,0.28)",
            }}>
              <Compass size={17} color="white" />
            </div>
            <span style={{
              fontFamily: "var(--font-heading)", fontWeight: 800, fontSize: "18px",
              background: "linear-gradient(135deg, var(--primary), var(--secondary))",
              WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent",
            }}>VivuPlan</span>
          </Link>

          {/* Desktop links */}
          <nav style={{ display: "flex", alignItems: "center", gap: "4px", flex: 1 }} className="hidden md:flex">
            {links.map(({ label, href }) => (
              <Link key={href} href={href} style={{
                padding: "7px 14px", borderRadius: "var(--r-md)", fontSize: "14px", fontWeight: 500,
                color: pathname === href ? "var(--primary)" : "var(--text-3)",
                textDecoration: "none",
                background: pathname === href ? "var(--primary-light)" : "transparent",
                transition: "all 0.15s",
              }}
              onMouseEnter={(e) => { if (pathname !== href) e.currentTarget.style.color = "var(--text)"; e.currentTarget.style.background = "var(--surface-2)"; }}
              onMouseLeave={(e) => { e.currentTarget.style.color = pathname === href ? "var(--primary)" : "var(--text-3)"; e.currentTarget.style.background = pathname === href ? "var(--primary-light)" : "transparent"; }}
              >{label}</Link>
            ))}
          </nav>

          {/* Auth */}
          <div style={{ display: "flex", alignItems: "center", gap: "8px", flexShrink: 0 }} className="hidden md:flex">
            {user ? (
              <div style={{ position: "relative" }}>
                <button
                  id="btn-user-menu"
                  onClick={() => setUserMenuOpen(!userMenuOpen)}
                  style={{
                    display: "flex", alignItems: "center", gap: "8px",
                    padding: "7px 12px", borderRadius: "var(--r-lg)",
                    background: "var(--surface-2)", border: "1.5px solid var(--border)",
                    cursor: "pointer", fontSize: "14px", fontWeight: 600, color: "var(--text-2)",
                  }}
                >
                  <div style={{
                    width: 26, height: 26, borderRadius: "50%",
                    background: "linear-gradient(135deg, var(--primary), var(--secondary))",
                    display: "flex", alignItems: "center", justifyContent: "center",
                    color: "white", fontSize: "11px", fontWeight: 700,
                  }}>
                    {user.name.charAt(0).toUpperCase()}
                  </div>
                  {user.name.split(" ").slice(-1)[0]}
                  <ChevronDown size={13} style={{ color: "var(--text-4)" }} />
                </button>
                {userMenuOpen && (
                  <div style={{
                    position: "absolute", right: 0, top: "calc(100% + 6px)",
                    background: "var(--surface)", border: "1px solid var(--border)",
                    borderRadius: "var(--r-lg)", boxShadow: "var(--shadow-lg)",
                    minWidth: "180px", overflow: "hidden", zIndex: 200,
                  }}>
                    <Link href="/dashboard" onClick={() => setUserMenuOpen(false)} style={{
                      display: "flex", alignItems: "center", gap: "10px",
                      padding: "11px 16px", fontSize: "14px", fontWeight: 500,
                      color: "var(--text-2)", textDecoration: "none",
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.background = "var(--surface-2)"}
                    onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}>
                      <LayoutDashboard size={15} style={{ color: "var(--text-4)" }} /> Bảng điều khiển
                    </Link>
                    <div style={{ height: 1, background: "var(--border)" }} />
                    <button onClick={logout} style={{
                      display: "flex", alignItems: "center", gap: "10px", width: "100%",
                      padding: "11px 16px", fontSize: "14px", fontWeight: 500,
                      color: "#DC2626", background: "transparent", border: "none", cursor: "pointer",
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.background = "#FEF2F2"}
                    onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}>
                      <LogOut size={15} /> Đăng xuất
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <Link href="/login" className="btn btn-primary btn-sm" style={{ textDecoration: "none" }}>
                <LogIn size={14} /> Đăng nhập
              </Link>
            )}
          </div>

          {/* Mobile toggle */}
          <button onClick={() => setMobileOpen(!mobileOpen)} className="md:hidden btn btn-ghost btn-icon" style={{ marginLeft: "auto" }}>
            {mobileOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>

        {/* Mobile menu */}
        {mobileOpen && (
          <div className="md:hidden" style={{
            borderTop: "1px solid var(--border)", background: "var(--surface)",
            padding: "12px 24px 20px",
          }}>
            {links.map(({ label, href }) => (
              <Link key={href} href={href} onClick={() => setMobileOpen(false)} style={{
                display: "flex", alignItems: "center", gap: "8px",
                padding: "12px 0", borderBottom: "1px solid var(--divider)",
                fontSize: "15px", fontWeight: 500,
                color: pathname === href ? "var(--primary)" : "var(--text-2)", textDecoration: "none",
              }}>
                <Compass size={16} style={{ color: "var(--text-4)" }} /> {label}
              </Link>
            ))}
            <div style={{ display: "flex", gap: "10px", marginTop: "16px" }}>
              {user ? (
                <button onClick={logout} className="btn btn-secondary" style={{ flex: 1 }}>
                  <LogOut size={15} /> Đăng xuất
                </button>
              ) : (
                <Link href="/login" className="btn btn-primary" style={{ flex: 1, textDecoration: "none", justifyContent: "center" }} onClick={() => setMobileOpen(false)}>
                  <LogIn size={14} /> Đăng nhập
                </Link>
              )}
            </div>
          </div>
        )}
      </header>
    </>
  );
}
