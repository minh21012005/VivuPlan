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
          <p>© 2026 VivuPlan. Mọi quyền được bảo lưu.</p>

          <p className="site-footer-made">
            Tạo ra với <Heart size={14} fill="#F43F5E" color="#F43F5E" /> tại Việt Nam
          </p>
        </div>
      </div>
    </footer>
  );
}
