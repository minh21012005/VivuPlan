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
    <footer style={{ background: "var(--surface)", borderTop: "1px solid var(--border)" }}>
      <div className="container" style={{ paddingTop: "72px", paddingBottom: "40px" }}>

        {/* ── Main grid: brand col + 3 link cols ───────────────────────── */}
        <div style={{
          display: "grid",
          gridTemplateColumns: "2fr 1fr 1fr 1fr",
          gap: "48px",
          marginBottom: "56px",
        }}>

          {/* Brand block */}
          <div>
            <Link href="/" style={{ display: "inline-flex", alignItems: "center", gap: "10px", textDecoration: "none", marginBottom: "20px" }}>
              <div style={{
                width: 36, height: 36, borderRadius: 10,
                background: "linear-gradient(135deg, var(--primary), var(--secondary))",
                display: "flex", alignItems: "center", justifyContent: "center",
                boxShadow: "0 4px 12px rgba(15,159,156,0.22)",
                flexShrink: 0,
              }}>
                <Compass size={17} color="white" />
              </div>
              <span style={{
                fontFamily: "var(--font-heading)", fontWeight: 800, fontSize: "20px",
                background: "linear-gradient(135deg, var(--primary), var(--secondary))",
                WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent",
              }}>
                VivuPlan
              </span>
            </Link>

            <p style={{ fontSize: "14px", color: "var(--text-3)", lineHeight: 1.75, marginBottom: "24px", maxWidth: "280px" }}>
              Nền tảng lập kế hoạch du lịch Việt Nam được AI hỗ trợ. Tạo lịch trình hoàn hảo chỉ trong 30 giây.
            </p>

            {/* Social icons */}
            <div style={{ display: "flex", gap: "10px" }}>
              {socials.map(({ icon: Icon, href }, i) => (
                <a
                  key={i}
                  href={href}
                  style={{
                    width: 36, height: 36, borderRadius: "var(--r-md)",
                    background: "var(--surface-2)", border: "1px solid var(--border)",
                    display: "flex", alignItems: "center", justifyContent: "center",
                    color: "var(--text-3)", textDecoration: "none",
                    transition: "all 0.2s",
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = "var(--primary-light)";
                    e.currentTarget.style.borderColor = "var(--primary-muted)";
                    e.currentTarget.style.color = "var(--primary)";
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = "var(--surface-2)";
                    e.currentTarget.style.borderColor = "var(--border)";
                    e.currentTarget.style.color = "var(--text-3)";
                  }}
                >
                  <Icon size={16} />
                </a>
              ))}
            </div>
          </div>

          {/* Link columns */}
          {Object.entries(footerLinks).map(([group, items]) => (
            <div key={group}>
              <h4 style={{
                fontSize: "12px", fontWeight: 700, color: "var(--text-2)",
                textTransform: "uppercase", letterSpacing: "0.08em",
                marginBottom: "20px",
              }}>
                {group}
              </h4>
              <ul style={{ listStyle: "none", display: "flex", flexDirection: "column", gap: "12px" }}>
                {items.map((item) => (
                  <li key={item.label}>
                    <Link
                      href={item.href}
                      style={{ fontSize: "14px", color: "var(--text-3)", textDecoration: "none", transition: "color 0.15s" }}
                      onMouseEnter={(e) => { e.currentTarget.style.color = "var(--primary)"; }}
                      onMouseLeave={(e) => { e.currentTarget.style.color = "var(--text-3)"; }}
                    >
                      {item.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        {/* ── Bottom bar ───────────────────────────────────────────────── */}
        <div style={{
          borderTop: "1px solid var(--border)",
          paddingTop: "28px",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          flexWrap: "wrap",
          gap: "12px",
        }}>
          <p style={{ fontSize: "13px", color: "var(--text-4)" }}>
            © 2026 VivuPlan. Mọi quyền được bảo lưu.
          </p>

          <p style={{ fontSize: "13px", color: "var(--text-4)", display: "flex", alignItems: "center", gap: "5px" }}>
            Tạo ra với{" "}
            <Heart size={12} fill="var(--primary)" color="var(--primary)" />
            {" "}tại Việt Nam 🇻🇳
          </p>
        </div>
      </div>
    </footer>
  );
}
