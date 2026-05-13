"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { MapPin, Menu, X, Zap, LogIn, User } from "lucide-react";

const navLinks = [
  { label: "Lập kế hoạch", href: "/plan" },
  { label: "Khám phá", href: "/explore" },
  { label: "Cộng đồng", href: "/community" },
  { label: "Giá", href: "/pricing" },
];

export default function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener("scroll", onScroll);
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <nav
      className="fixed top-0 left-0 right-0 z-50 transition-all duration-300"
      style={{
        background: scrolled ? "rgba(13, 27, 42, 0.95)" : "transparent",
        backdropFilter: scrolled ? "blur(20px)" : "none",
        borderBottom: scrolled ? "1px solid rgba(255,255,255,0.06)" : "none",
      }}
      id="main-navbar"
    >
      <div className="max-w-7xl mx-auto px-6 flex items-center justify-between h-[72px]">
        {/* Logo */}
        <Link href="/" id="nav-logo" className="flex items-center gap-2.5 group">
          <div
            className="w-9 h-9 rounded-xl flex items-center justify-center transition-all duration-300 group-hover:scale-110"
            style={{ background: "var(--gradient-brand)", boxShadow: "0 0 20px rgba(255,107,53,0.3)" }}
          >
            <MapPin size={18} color="white" fill="white" />
          </div>
          <span
            className="text-xl font-bold"
            style={{
              background: "linear-gradient(135deg, #FF6B35, #FF8C42)",
              WebkitBackgroundClip: "text",
              WebkitTextFillColor: "transparent",
              backgroundClip: "text",
              fontFamily: "'Plus Jakarta Sans', sans-serif",
            }}
          >
            VivuPlan
          </span>
        </Link>

        {/* Desktop Nav Links */}
        <div className="hidden md:flex items-center gap-1">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              id={`nav-link-${link.label.toLowerCase().replace(/\s+/g, "-")}`}
              className="px-4 py-2 rounded-xl text-sm font-medium transition-all duration-200"
              style={{
                color: "var(--brand-text-muted)",
              }}
              onMouseEnter={(e) => {
                (e.target as HTMLAnchorElement).style.color = "var(--brand-text)";
                (e.target as HTMLAnchorElement).style.background = "rgba(255,255,255,0.06)";
              }}
              onMouseLeave={(e) => {
                (e.target as HTMLAnchorElement).style.color = "var(--brand-text-muted)";
                (e.target as HTMLAnchorElement).style.background = "transparent";
              }}
            >
              {link.label}
            </Link>
          ))}
        </div>

        {/* Auth Buttons */}
        <div className="hidden md:flex items-center gap-3">
          <Link href="/login" id="nav-login">
            <button
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-medium transition-all duration-200"
              style={{ color: "var(--brand-text-muted)" }}
              onMouseEnter={(e) => {
                (e.currentTarget as HTMLButtonElement).style.color = "var(--brand-text)";
                (e.currentTarget as HTMLButtonElement).style.background = "rgba(255,255,255,0.06)";
              }}
              onMouseLeave={(e) => {
                (e.currentTarget as HTMLButtonElement).style.color = "var(--brand-text-muted)";
                (e.currentTarget as HTMLButtonElement).style.background = "transparent";
              }}
            >
              <LogIn size={15} />
              Đăng nhập
            </button>
          </Link>
          <Link href="/plan" id="nav-cta">
            <button className="btn-primary flex items-center gap-1.5 text-sm px-5 py-2.5">
              <Zap size={14} />
              Lập kế hoạch
            </button>
          </Link>
        </div>

        {/* Mobile Menu Toggle */}
        <button
          className="md:hidden p-2 rounded-xl transition-colors"
          onClick={() => setMobileOpen(!mobileOpen)}
          id="nav-mobile-toggle"
          style={{ color: "var(--brand-text)", background: mobileOpen ? "rgba(255,255,255,0.08)" : "transparent" }}
        >
          {mobileOpen ? <X size={22} /> : <Menu size={22} />}
        </button>
      </div>

      {/* Mobile Menu */}
      {mobileOpen && (
        <div
          className="md:hidden animate-fade-in"
          style={{
            background: "rgba(13, 27, 42, 0.98)",
            backdropFilter: "blur(20px)",
            borderTop: "1px solid rgba(255,255,255,0.06)",
          }}
        >
          <div className="px-6 py-4 flex flex-col gap-2">
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                className="px-4 py-3 rounded-xl text-sm font-medium"
                style={{ color: "var(--brand-text-muted)", background: "rgba(255,255,255,0.03)" }}
                onClick={() => setMobileOpen(false)}
              >
                {link.label}
              </Link>
            ))}
            <div className="flex gap-3 mt-2">
              <Link href="/login" className="flex-1">
                <button
                  className="w-full btn-secondary flex items-center justify-center gap-2 text-sm py-2.5"
                  onClick={() => setMobileOpen(false)}
                >
                  <LogIn size={14} />
                  Đăng nhập
                </button>
              </Link>
              <Link href="/plan" className="flex-1">
                <button
                  className="w-full btn-primary flex items-center justify-center gap-2 text-sm py-2.5"
                  onClick={() => setMobileOpen(false)}
                >
                  <Zap size={14} />
                  Lập kế hoạch
                </button>
              </Link>
            </div>
          </div>
        </div>
      )}
    </nav>
  );
}
