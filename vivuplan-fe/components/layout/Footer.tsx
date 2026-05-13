"use client";
import Link from "next/link";
import { MapPin, Facebook, Instagram, Twitter, Github, Heart } from "lucide-react";

const footerLinks = {
  "Sản phẩm": [
    { label: "Lập kế hoạch", href: "/plan" },
    { label: "Khám phá", href: "/explore" },
    { label: "Cộng đồng", href: "/community" },
    { label: "Bảng giá", href: "/pricing" },
  ],
  "Điểm đến": [
    { label: "Đà Lạt", href: "/plan?destination=Da+Lat" },
    { label: "Hạ Long", href: "/plan?destination=Ha+Long" },
    { label: "Quy Nhơn", href: "/plan?destination=Quy+Nhon" },
    { label: "Đà Nẵng", href: "/plan?destination=Da+Nang" },
    { label: "Phú Quốc", href: "/plan?destination=Phu+Quoc" },
  ],
  "Hỗ trợ": [
    { label: "Điều khoản", href: "/terms" },
    { label: "Bảo mật", href: "/privacy" },
    { label: "Liên hệ", href: "/contact" },
  ],
};

const socials = [
  { icon: Facebook, href: "#" },
  { icon: Instagram, href: "#" },
  { icon: Twitter, href: "#" },
  { icon: Github, href: "#" },
];

export default function Footer() {
  return (
    <footer style={{ background: "var(--brand-surface)", borderTop: "1px solid var(--brand-border)" }}>
      <div className="max-w-7xl mx-auto px-6 pt-14 pb-8">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-8 mb-10">
          <div className="col-span-2 md:col-span-1">
            <Link href="/" className="flex items-center gap-2 mb-3">
              <div className="w-8 h-8 rounded-xl flex items-center justify-center" style={{ background: "var(--gradient-brand)" }}>
                <MapPin size={16} color="white" fill="white" />
              </div>
              <span className="text-lg font-bold gradient-text" style={{ fontFamily: "'Plus Jakarta Sans',sans-serif" }}>VivuPlan</span>
            </Link>
            <p className="text-sm mb-4 leading-relaxed" style={{ color: "var(--brand-text-muted)" }}>
              Lập kế hoạch du lịch Việt Nam thông minh với AI.
            </p>
            <div className="flex gap-2">
              {socials.map(({ icon: Icon, href }, i) => (
                <a key={i} href={href} className="w-8 h-8 rounded-lg flex items-center justify-center glass transition-all hover:border-orange-500/40" style={{ color: "var(--brand-text-muted)" }}>
                  <Icon size={14} />
                </a>
              ))}
            </div>
          </div>
          {Object.entries(footerLinks).map(([group, links]) => (
            <div key={group}>
              <h4 className="text-sm font-semibold mb-3" style={{ color: "var(--brand-text)" }}>{group}</h4>
              <ul className="space-y-2">
                {links.map((l) => (
                  <li key={l.label}>
                    <Link href={l.href} className="text-sm transition-colors hover:text-orange-400" style={{ color: "var(--brand-text-muted)" }}>{l.label}</Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <div className="flex flex-col md:flex-row items-center justify-between pt-6 gap-3" style={{ borderTop: "1px solid var(--brand-border)" }}>
          <p className="text-xs" style={{ color: "var(--brand-text-dim)" }}>© 2026 VivuPlan. Mọi quyền được bảo lưu.</p>
          <p className="text-xs flex items-center gap-1" style={{ color: "var(--brand-text-dim)" }}>
            Tạo ra với <Heart size={10} fill="#FF6B35" color="#FF6B35" /> tại Việt Nam
          </p>
        </div>
      </div>
    </footer>
  );
}
