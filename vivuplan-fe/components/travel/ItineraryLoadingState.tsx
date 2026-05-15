"use client";

import Navbar from "@/components/layout/Navbar";
import { heroImages } from "@/lib/travel-data";
import { Clock, MapPin, Wallet } from "lucide-react";

interface ItineraryLoadingStateProps {
  message?: string;
}

export function ItineraryLoadingState({ message = "Đang mở lịch trình..." }: ItineraryLoadingStateProps) {
  return (
    <div className="itinerary-loading-page" aria-live="polite" aria-busy="true">
      <Navbar />

      <section
        className="itinerary-loading-hero"
        style={{
          backgroundImage: `linear-gradient(90deg, rgba(4,47,46,0.86), rgba(2,132,199,0.34)), url(${heroImages.vietnamBay})`,
        }}
      >
        <div className="container itinerary-loading-hero-inner">
          <div className="itinerary-loading-kicker">
            <span className="spinner" />
            {message}
          </div>
          <div className="itinerary-loading-title skeleton-shimmer" />
          <div className="itinerary-loading-meta">
            <span>
              <MapPin size={14} /> Đang lấy điểm đến
            </span>
            <span>
              <Clock size={14} /> Đang tải số ngày
            </span>
            <span>
              <Wallet size={14} /> Đang tính ngân sách
            </span>
          </div>
        </div>
      </section>

      <main className="container itinerary-loading-main">
        <section className="itinerary-loading-schedule">
          <div className="itinerary-loading-tabs">
            {[0, 1, 2].map((item) => (
              <div key={item} className="itinerary-loading-tab skeleton-shimmer" />
            ))}
          </div>

          <div className="itinerary-loading-panel">
            <div className="itinerary-loading-heading skeleton-shimmer" />
            <div className="itinerary-loading-subheading skeleton-shimmer" />
            <div className="itinerary-loading-timeline">
              {[0, 1, 2].map((item) => (
                <div key={item} className="itinerary-loading-row">
                  <span className="itinerary-loading-dot skeleton-shimmer" />
                  <div className="itinerary-loading-activity">
                    <div className="itinerary-loading-activity-title skeleton-shimmer" />
                    <div className="itinerary-loading-activity-meta skeleton-shimmer" />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <aside className="itinerary-loading-aside">
          {[0, 1].map((item) => (
            <div key={item} className="itinerary-loading-side-panel">
              <div className="itinerary-loading-side-title skeleton-shimmer" />
              <div className="itinerary-loading-side-line skeleton-shimmer" />
              <div className="itinerary-loading-side-line short skeleton-shimmer" />
            </div>
          ))}
        </aside>
      </main>
    </div>
  );
}
