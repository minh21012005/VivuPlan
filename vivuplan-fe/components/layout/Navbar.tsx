"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { ChevronDown, Compass, LayoutDashboard, LogIn, LogOut, Menu, Settings, X } from "lucide-react";
import { BrandLogo } from "@/components/layout/BrandLogo";
import { useAuth } from "@/hooks/useAuth";

const links = [
  { label: "Lập kế hoạch", href: "/plan" },
  { label: "Khám phá", href: "/explore" },
];

const userTripsLabel = "Chuyến đi của tôi";

export default function Navbar() {
  const pathname = usePathname();
  const { user, loading, logout } = useAuth();
  const [mounted, setMounted] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  useEffect(() => {
    setMounted(true);
    const onScroll = () => setScrolled(window.scrollY > 12);
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const handleLogout = () => {
    setUserMenuOpen(false);
    setMobileOpen(false);
    logout();
  };

  const lastName = user?.name.split(" ").filter(Boolean).slice(-1)[0] ?? user?.name ?? "";
  const initial = user?.name.charAt(0).toUpperCase() ?? "";

  return (
    <header className={`site-header${scrolled ? " site-header-scrolled" : ""}`}>
      <div className="container site-nav-container">
        <BrandLogo className="site-nav-logo" />

        <nav className="site-nav-links" aria-label="Điều hướng chính">
          {links.map(({ label, href }) => (
            <Link key={href} href={href} className={`site-nav-link${pathname === href ? " active" : ""}`}>
              {label}
            </Link>
          ))}
          {user && (
            <Link 
              href="/itinerary" 
              className={`site-nav-link${pathname.startsWith("/itinerary") ? " active" : ""}`}
            >
              {userTripsLabel}
            </Link>
          )}
          <Link href="/pricing" className={`site-nav-link${pathname === "/pricing" ? " active" : ""}`}>
            Bảng giá
          </Link>
        </nav>

        <div className="site-nav-auth">
          {mounted && !loading && (
            user ? (
              <div className="site-user-menu-wrap">
                <button
                  id="btn-user-menu"
                  className="site-user-button"
                  onClick={() => setUserMenuOpen((open) => !open)}
                  aria-expanded={userMenuOpen}
                  aria-haspopup="menu"
                >
                  {user.avatarUrl ? (
                    <span className="site-user-avatar site-user-avatar-img">
                      <Image
                        src={user.avatarUrl}
                        alt={user.name}
                        width={28}
                        height={28}
                        className="site-user-avatar-image"
                        referrerPolicy="no-referrer"
                      />
                    </span>
                  ) : (
                    <span className="site-user-avatar">{initial}</span>
                  )}
                  <span className="site-user-name">{lastName}</span>
                  <ChevronDown size={13} className="site-user-chevron" />
                </button>

                {userMenuOpen && (
                  <div className="site-user-dropdown" role="menu" aria-labelledby="btn-user-menu">
                    {user.provider === "LOCAL" && (
                      <Link
                        href="/settings"
                        className="site-dropdown-item"
                        role="menuitem"
                        onClick={() => setUserMenuOpen(false)}
                      >
                        <Settings size={15} /> Cài đặt tài khoản
                      </Link>
                    )}
                    <button type="button" onClick={handleLogout} className="site-dropdown-item site-dropdown-danger" role="menuitem">
                      <LogOut size={15} /> Đăng xuất
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <Link href="/login" className="btn btn-primary btn-sm site-login-link">
                <LogIn size={14} /> Đăng nhập
              </Link>
            )
          )}
        </div>

        <button
          type="button"
          onClick={() => setMobileOpen((open) => !open)}
          className="site-nav-toggle btn btn-ghost btn-icon"
          aria-label={mobileOpen ? "Đóng menu" : "Mở menu"}
          aria-expanded={mobileOpen}
        >
          {mobileOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      {mobileOpen && (
        <div className="site-mobile-menu">
          <nav className="container site-mobile-menu-inner" aria-label="Điều hướng di động">
            {links.map(({ label, href }) => (
              <Link key={href} href={href} className={`site-mobile-link${pathname === href ? " active" : ""}`} onClick={() => setMobileOpen(false)}>
                <Compass size={16} /> {label}
              </Link>
            ))}

            {user && (
              <Link 
                href="/itinerary" 
                className={`site-mobile-link${pathname.startsWith("/itinerary") ? " active" : ""}`} 
                onClick={() => setMobileOpen(false)}
              >
                <LayoutDashboard size={16} /> {userTripsLabel}
              </Link>
            )}

            <Link href="/pricing" className={`site-mobile-link${pathname === "/pricing" ? " active" : ""}`} onClick={() => setMobileOpen(false)}>
              <Compass size={16} /> Bảng giá
            </Link>

            <div className="site-mobile-auth">
              {user ? (
                <>
                  {user.provider === "LOCAL" && (
                    <Link href="/settings" className="btn btn-secondary site-mobile-auth-button" onClick={() => setMobileOpen(false)}>
                      <Settings size={15} /> Cài đặt tài khoản
                    </Link>
                  )}
                  <button type="button" onClick={handleLogout} className="btn btn-secondary site-mobile-auth-button">
                    <LogOut size={15} /> Đăng xuất
                  </button>
                </>
              ) : (
                <Link href="/login" className="btn btn-primary site-mobile-auth-button" onClick={() => setMobileOpen(false)}>
                  <LogIn size={14} /> Đăng nhập
                </Link>
              )}
            </div>
          </nav>
        </div>
      )}
    </header>
  );
}
