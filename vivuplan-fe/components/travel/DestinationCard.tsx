import Link from "next/link";
import { ArrowRight, Clock, Star } from "lucide-react";
import type { Destination } from "@/lib/travel-data";
import { Badge } from "@/components/ui/Badge";

export function DestinationCard({
  destination,
  loading = false,
  disabled = false,
  onNavigate,
}: {
  destination: Destination;
  loading?: boolean;
  disabled?: boolean;
  onNavigate?: () => void;
}) {
  const href = `/plan?destination=${encodeURIComponent(destination.name)}`;

  return (
    <Link
      href={href}
      className="destination-card-link"
      aria-busy={loading}
      aria-disabled={disabled}
      tabIndex={disabled ? -1 : undefined}
      onClick={(event) => {
        if (disabled) {
          event.preventDefault();
          return;
        }
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        onNavigate?.();
      }}
    >
      <article className="destination-card">
        <div className="destination-card-media" style={{ backgroundImage: `url(${destination.image})` }}>
          <div style={{ position: "absolute", top: "12px", left: "12px", display: "flex", gap: "8px" }}>
            <Badge tone="glass">{destination.region}</Badge>
          </div>
          <div className="destination-rating" style={{ 
            position: "absolute", top: "12px", right: "12px", 
            background: "rgba(255, 255, 255, 0.9)", padding: "4px 8px", 
            borderRadius: "var(--r-sm)", fontSize: "13px", zIndex: 10,
            boxShadow: "var(--shadow-sm)"
          }}>
            <Star size={14} fill="#FBBF24" color="#FBBF24" />
            {destination.rating}
          </div>
        </div>
        <div className="destination-card-body">
          <div style={{ marginBottom: "16px" }}>
            <h3 style={{ fontSize: "20px", marginBottom: "10px", color: "var(--text)" }}>{destination.name}</h3>
            <Badge tone="teal" style={{ 
              width: "100%", 
              justifyContent: "flex-start", 
              padding: "8px 14px", 
              textTransform: "none", 
              fontSize: "13px", 
              letterSpacing: "normal",
              borderRadius: "var(--r-lg)",
              border: "none",
              background: "var(--primary-light)"
            }}>
              {destination.tag}
            </Badge>
          </div>
          <p style={{ marginBottom: "20px" }}>{destination.desc}</p>
          <div className="destination-card-footer">
            <span>
              <Clock size={13} />
              {destination.days}
            </span>
            <strong>
              {loading ? <span className="spinner spinner-inline" /> : null}
              {loading ? "Đang mở..." : "Lên kế hoạch"} {loading ? null : <ArrowRight size={13} />}
            </strong>
          </div>
        </div>
      </article>
    </Link>
  );
}
