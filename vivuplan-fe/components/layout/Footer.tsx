import Link from "next/link";
import { Compass, Globe, Camera, MessageCircle, Play, Heart } from "lucide-react";

const footerLinks = {
  "Sản phẩm": [
    { label: "Lập kế hoạch AI", href: "/plan" },
    { label: "Khám phá điểm đến", href: "/explore" },
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
  { icon: Globe, href: "#", label: "Facebook" },
  { icon: Camera, href: "#", label: "Instagram" },
  { icon: MessageCircle, href: "#", label: "Twitter" },
  { icon: Play, href: "#", label: "Youtube" },
];

export default function Footer() {
  return (
    <footer style={{ background: "var(--text)", color: "white", position: "relative", overflow: "hidden" }}>
      {/* Decorative gradient blur */}
      <div style={{
        position: "absolute", top: -100, right: -100, width: 300, height: 300,
        background: "radial-gradient(circle, rgba(15,159,156,0.15) 0%, transparent 70%)",
        pointerEvents: "none"
      }} />

      <div className="container" style={{ paddingTop: "80px", paddingBottom: "40px", position: "relative", zIndex: 1 }}>

        {/* ── Main grid ─────────────────────────────────────────────── */}
        <div style={{
          display: "grid",
          gridTemplateColumns: "2fr 1fr 1fr 1fr",
          gap: "48px",
          marginBottom: "64px",
        }}>

          {/* Brand block */}
          <div>
            <Link href="/" style={{ display: "inline-flex", alignItems: "center", gap: "10px", textDecoration: "none", marginBottom: "24px" }}>
              <div style={{
                width: 40, height: 40, borderRadius: 12,
                background: "linear-gradient(135deg, var(--primary), var(--secondary))",
                display: "flex", alignItems: "center", justifyContent: "center",
                boxShadow: "0 4px 20px rgba(15,159,156,0.3)",
                flexShrink: 0,
              }}>
                <Compass size={20} color="white" />
              </div>
              <span style={{
                fontFamily: "var(--font-heading)", fontWeight: 800, fontSize: "22px",
                background: "linear-gradient(135deg, var(--primary), var(--secondary))",
                WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent",
              }}>
                VivuPlan
              </span>
            </Link>

            <p style={{ fontSize: "15px", color: "rgba(255,255,255,0.6)", lineHeight: 1.8, marginBottom: "28px", maxWidth: "320px" }}>
              Nền tảng lập kế hoạch du lịch Việt Nam được AI hỗ trợ. Tạo lịch trình hoàn hảo chỉ trong 30 giây và khám phá những hành trình tuyệt vời nhất.
            </p>

            {/* Social icons */}
            <div style={{ display: "flex", gap: "12px" }}>
              {socials.map(({ icon: Icon, href, label }, i) => (
                <a
                  key={i}
                  href={href}
                  aria-label={label}
                  style={{
                    width: 38, height: 38, borderRadius: "var(--r-md)",
                    background: "rgba(255,255,255,0.05)", border: "1px solid rgba(255,255,255,0.1)",
                    display: "flex", alignItems: "center", justifyContent: "center",
                    color: "rgba(255,255,255,0.6)", textDecoration: "none",
                    transition: "all 0.25s",
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = "var(--primary)";
                    e.currentTarget.style.borderColor = "var(--primary)";
                    e.currentTarget.style.color = "white";
                    e.currentTarget.style.transform = "translateY(-3px)";
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = "rgba(255,255,255,0.05)";
                    e.currentTarget.style.borderColor = "rgba(255,255,255,0.1)";
                    e.currentTarget.style.color = "rgba(255,255,255,0.6)";
                    e.currentTarget.style.transform = "translateY(0)";
                  }}
                >
                  <Icon size={18} />
                </a>
              ))}
            </div>
          </div>

          {/* Link columns */}
          {Object.entries(footerLinks).map(([group, items]) => (
            <div key={group}>
              <h4 style={{
                fontSize: "13px", fontWeight: 700, color: "white",
                textTransform: "uppercase", letterSpacing: "0.1em",
                marginBottom: "24px",
              }}>
                {group}
              </h4>
              <ul style={{ listStyle: "none", display: "flex", flexDirection: "column", gap: "14px" }}>
                {items.map((item) => (
                  <li key={item.label}>
                    <Link
                      href={item.href}
                      style={{ fontSize: "14px", color: "rgba(255,255,255,0.55)", textDecoration: "none", transition: "all 0.2s" }}
                      onMouseEnter={(e) => { e.currentTarget.style.color = "var(--primary-muted)"; e.currentTarget.style.paddingLeft = "4px"; }}
                      onMouseLeave={(e) => { e.currentTarget.style.color = "rgba(255,255,255,0.55)"; e.currentTarget.style.paddingLeft = "0"; }}
                    >
                      {item.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        {/* ── Bottom bar ─────────────────────────────────────────────── */}
        <div style={{
          borderTop: "1px solid rgba(255,255,255,0.08)",
          paddingTop: "32px",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          flexWrap: "wrap",
          gap: "16px",
        }}>
          <p style={{ fontSize: "14px", color: "rgba(255,255,255,0.4)" }}>
            © 2026 VivuPlan. Mọi quyền được bảo lưu.
          </p>

          <p style={{ fontSize: "14px", color: "rgba(255,255,255,0.4)", display: "flex", alignItems: "center", gap: "6px" }}>
            Tạo ra với{" "}
            <Heart size={14} fill="#F43F5E" color="#F43F5E" />
            {" "}tại Việt Nam 🇻🇳
          </p>
        </div>
      </div>
    </footer>
  );
}
