import Link from "next/link";
import { Camera, Globe, Heart, MessageCircle, Play } from "lucide-react";

const footerLinks = {
  "Sản phẩm": [
    { label: "Lập kế hoạch AI", href: "/plan" },
    { label: "Khám phá điểm đến", href: "/explore" },
    { label: "Cộng đồng", href: "/community" },
    { label: "Bảng giá", href: "/pricing" },
  ],
  "Điểm đến": [
    { label: "Khám phá tất cả", href: "/explore" },
    { label: "Miền Bắc", href: "/explore?region=Mi%E1%BB%81n+B%E1%BA%AFc" },
    { label: "Miền Trung", href: "/explore?region=Mi%E1%BB%81n+Trung" },
    { label: "Miền Nam", href: "/explore?region=Mi%E1%BB%81n+Nam" },
  ],
  "Hỗ trợ": [
    { label: "Điều khoản", href: "" },
    { label: "Bảo mật", href: "" },
    { label: "Liên hệ", href: "" },
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
    <footer className="site-footer">
      <div className="container site-footer-inner">
        <div className="site-footer-grid">
          <section className="site-footer-brand" aria-label="VivuPlan">
            <Link href="/" className="site-footer-logo">
              VivuPlan
            </Link>

            <p className="site-footer-copy">
              Nền tảng lập kế hoạch du lịch Việt Nam được AI hỗ trợ. Tạo lịch trình hoàn hảo chỉ trong 30 giây và khám phá
              những hành trình tuyệt vời nhất.
            </p>

            <div className="site-footer-socials">
              {socials.map(({ icon: Icon, href, label }) => (
                <a key={label} href={href} aria-label={label} className="site-footer-social-link">
                  <Icon size={18} />
                </a>
              ))}
            </div>
          </section>

          {Object.entries(footerLinks).map(([group, items]) => (
            <section key={group} className="site-footer-column">
              <h4>{group}</h4>
              <ul>
                {items.map((item) => (
                  <li key={item.label}>
                    <Link href={item.href} className="site-footer-link">
                      {item.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>

        <div className="site-footer-bottom">
          <p>© 2026 VivuPlan. All rights reserved.</p>

          <p className="site-footer-made">
            Made with <Heart size={14} fill="#F43F5E" color="#F43F5E" /> in Vietnam
          </p>
        </div>
      </div>
    </footer>
  );
}
