import {
  CalendarDays,
  Compass,
  MapPin,
  Navigation,
  PlaneTakeoff,
  Route,
  WalletCards,
} from "lucide-react";

type AuthVisualPanelVariant = "login" | "register";

interface AuthVisualPanelProps {
  variant: AuthVisualPanelVariant;
}

const panelContent = {
  login: {
    eyebrow: "VivuPlan",
    title: "Tiếp tục chuyến đi",
    trip: "Đà Nẵng mùa biển",
    route: "Hà Nội → Đà Nẵng",
    duration: "3 ngày",
    budget: "2.8tr",
    focus: "Biển Mỹ Khê",
  },
  register: {
    eyebrow: "VivuPlan",
    title: "Bắt đầu hành trình",
    trip: "Đà Lạt cuối tuần",
    route: "Sài Gòn → Đà Lạt",
    duration: "3 ngày",
    budget: "2.8tr",
    focus: "Quán cà phê mới",
  },
};

export function AuthVisualPanel({ variant }: AuthVisualPanelProps) {
  const content = panelContent[variant];

  return (
    <aside className={`auth-visual-panel auth-visual-panel-${variant}`} aria-hidden="true">
      <div className="auth-visual-grid" />
      <div className="auth-visual-shell">
        <div className="auth-visual-copy">
          <span className="auth-visual-eyebrow">
            <Compass size={15} />
            {content.eyebrow}
          </span>
          <h2>{content.title}</h2>
        </div>

        <div className="auth-map-stage">
          <div className="auth-map-sun" />
          <div className="auth-map-ridge auth-map-ridge-back" />
          <div className="auth-map-ridge auth-map-ridge-front" />

          <svg className="auth-route-line" viewBox="0 0 420 300" fill="none" aria-hidden="true">
            <path
              d="M72 224 C122 126 188 146 224 88 C260 30 334 74 356 142 C380 216 284 246 188 220"
              stroke="currentColor"
              strokeWidth="3"
              strokeLinecap="round"
              strokeDasharray="10 12"
            />
          </svg>

          <span className="auth-route-pin auth-route-pin-a">
            <MapPin size={18} fill="currentColor" />
          </span>
          <span className="auth-route-pin auth-route-pin-b">
            <Navigation size={17} fill="currentColor" />
          </span>
          <span className="auth-route-pin auth-route-pin-c">
            <MapPin size={18} fill="currentColor" />
          </span>

          <div className="auth-journey-card">
            <div className="auth-journey-head">
              <span>
                <Route size={16} />
                {content.trip}
              </span>
              <strong>{content.duration}</strong>
            </div>
            <div className="auth-journey-route">{content.route}</div>
            <div className="auth-journey-steps">
              <span>Ăn sáng</span>
              <span>Check-in</span>
              <span>{content.focus}</span>
            </div>
          </div>

          <div className="auth-floating-note auth-floating-note-left">
            <CalendarDays size={17} />
            <div>
              <span>Lịch trình</span>
              <strong>{content.duration}</strong>
            </div>
          </div>

          <div className="auth-floating-note auth-floating-note-right">
            <WalletCards size={17} />
            <div>
              <span>Dự chi</span>
              <strong>{content.budget}</strong>
            </div>
          </div>

          <div className="auth-flight-chip">
            <PlaneTakeoff size={16} />
            <span>Sẵn sàng lên đường</span>
          </div>
        </div>
      </div>
    </aside>
  );
}
