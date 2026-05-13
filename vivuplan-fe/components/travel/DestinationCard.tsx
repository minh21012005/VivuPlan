import Link from "next/link";
import { ArrowRight, Clock, Star } from "lucide-react";
import type { Destination } from "@/lib/travel-data";
import { Badge } from "@/components/ui/Badge";

export function DestinationCard({ destination }: { destination: Destination }) {
  return (
    <Link href={`/plan?destination=${encodeURIComponent(destination.name)}`} className="destination-card-link">
      <article className="destination-card">
        <div className="destination-card-media" style={{ backgroundImage: `url(${destination.image})` }}>
          <Badge tone="glass">{destination.region}</Badge>
        </div>
        <div className="destination-card-body">
          <div className="destination-card-title-row">
            <div>
              <h3>{destination.name}</h3>
              <Badge tone="teal">{destination.tag}</Badge>
            </div>
            <span className="destination-rating">
              <Star size={14} fill="#FBBF24" color="#FBBF24" />
              {destination.rating}
            </span>
          </div>
          <p>{destination.desc}</p>
          <div className="destination-card-footer">
            <span>
              <Clock size={13} />
              {destination.days}
            </span>
            <strong>
              Lên kế hoạch <ArrowRight size={13} />
            </strong>
          </div>
        </div>
      </article>
    </Link>
  );
}

