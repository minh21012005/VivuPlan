"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Navbar from "@/components/layout/Navbar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { tripApi, type TripResponse } from "@/lib/api";
import { copyTextToClipboard, getTripShareUrl } from "@/lib/share";
import { getDestinationImage, heroImages, type Destination } from "@/lib/travel-data";
import { useDestinations } from "@/lib/use-destinations";
import { AlertTriangle, CheckCircle2, ChevronLeft, ChevronRight, Clock, Eye, Plus, Share2, Sparkles, Trash2, Users, Wallet, X } from "lucide-react";
import { useRequireAuth } from "@/hooks/useRequireAuth"

type TripTimingBadge = { label: string; tone: "green" | "blue" | "gray" };
type ToastState = { message: string; tone: "success" | "error" };

const TRIPS_PER_PAGE = 6;

function fmtBudget(value: number) {
  return value >= 1_000_000 ? `${(value / 1_000_000).toFixed(1)}tr ₫` : `${Math.round(value / 1000)}k ₫`;
}

function fmtDateRange(trip: TripResponse) {
  if (!trip.startDate || !trip.endDate) return null;
  return `${new Date(`${trip.startDate}T00:00:00`).toLocaleDateString("vi-VN")} - ${new Date(`${trip.endDate}T00:00:00`).toLocaleDateString("vi-VN")}`;
}

const groupLabel: Record<string, string> = {
  SOLO: "Một mình",
  COUPLE: "Cặp đôi",
  FRIENDS: "Nhóm bạn",
  FAMILY: "Gia đình",
};

function getTripTimingBadge(trip: TripResponse): TripTimingBadge {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const start = trip.startDate ? new Date(`${trip.startDate}T00:00:00`) : null;
  const end = trip.endDate ? new Date(`${trip.endDate}T00:00:00`) : start;

  if (!start && !end) return { label: "Chưa có ngày", tone: "gray" };
  if (end && end < today) return { label: "Đã qua", tone: "gray" };
  if (start && start <= today && (!end || end >= today)) return { label: "Đang đi", tone: "green" };
  return { label: "Sắp tới", tone: "blue" };
}

export default function ItineraryLibraryPage() {
  const { user: authUser, loading: authLoading } = useRequireAuth();
  const { destinations } = useDestinations();
  const [trips, setTrips] = useState<TripResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [deleting, setDeleting] = useState<number | null>(null);
  const [deleteCandidate, setDeleteCandidate] = useState<TripResponse | null>(null);
  const [sharingTripId, setSharingTripId] = useState<number | null>(null);
  const [copiedTripId, setCopiedTripId] = useState<number | null>(null);
  const [openingTripId, setOpeningTripId] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const [toast, setToast] = useState<ToastState | null>(null);
  const openingResetTimer = useRef<number | null>(null);

  useEffect(() => {
    if (authLoading || !authUser) return;
    tripApi
      .myTrips()
      .then((data) => {
        setTrips(data);
        setPage(1);
      })
      .catch(() => setError("Không thể tải dữ liệu"))
      .finally(() => setLoading(false));
  }, [authLoading, authUser]);

  useEffect(() => {
    return () => {
      if (openingResetTimer.current) window.clearTimeout(openingResetTimer.current);
    };
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (!deleteCandidate || deleting) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setDeleteCandidate(null);
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [deleteCandidate, deleting]);

  const totalPages = Math.max(1, Math.ceil(trips.length / TRIPS_PER_PAGE));
  const currentPage = Math.min(page, totalPages);
  const visibleTrips = useMemo(() => {
    const start = (currentPage - 1) * TRIPS_PER_PAGE;
    return trips.slice(start, start + TRIPS_PER_PAGE);
  }, [currentPage, trips]);

  const handleDelete = async () => {
    if (!deleteCandidate) return;
    const id = deleteCandidate.id;
    setDeleting(id);
    try {
      await tripApi.deleteTrip(id);
      const nextTripCount = Math.max(0, trips.length - 1);
      setTrips((prev) => prev.filter((trip) => trip.id !== id));
      setPage((current) => Math.min(current, Math.max(1, Math.ceil(nextTripCount / TRIPS_PER_PAGE))));
      setToast({ message: "Đã xóa lịch trình.", tone: "success" });
      setDeleteCandidate(null);
    } catch {
      setToast({ message: "Xóa lịch trình thất bại. Vui lòng thử lại.", tone: "error" });
    } finally {
      setDeleting(null);
    }
  };

  const handleShare = async (trip: TripResponse) => {
    if (!trip.shareCode) {
      setToast({ message: "Lịch trình chưa có link chia sẻ.", tone: "error" });
      return;
    }

    setSharingTripId(trip.id);
    try {
      const shareableTrip = trip.isPublic ? trip : await tripApi.toggleVisibility(trip.id);

      if (!trip.isPublic) {
        setTrips((prev) => prev.map((item) => (item.id === trip.id ? { ...item, isPublic: shareableTrip.isPublic } : item)));
      }

      await copyTextToClipboard(getTripShareUrl(shareableTrip.shareCode));
      setCopiedTripId(trip.id);
      window.setTimeout(() => {
        setCopiedTripId((current) => (current === trip.id ? null : current));
      }, 1600);
      setToast({
        message: trip.isPublic ? "Đã copy link chia sẻ." : "Đã bật chia sẻ và copy link.",
        tone: "success",
      });
    } catch {
      setToast({ message: "Không thể tạo link chia sẻ. Vui lòng thử lại.", tone: "error" });
    } finally {
      setSharingTripId(null);
    }
  };

  const handleOpenTrip = (id: number) => {
    if (openingTripId !== null) return;

    setOpeningTripId(id);
    if (openingResetTimer.current) window.clearTimeout(openingResetTimer.current);
    openingResetTimer.current = window.setTimeout(() => {
      setOpeningTripId((current) => (current === id ? null : current));
    }, 12000);
  };

  // Don't render anything until auth is resolved — prevents flash before redirect
  if (authLoading || !authUser) return null;

  return (
    <div className="itinerary-library-page">
      <Navbar />

      <section
        className="trip-library-hero"
        style={{
          backgroundImage: `linear-gradient(90deg, rgba(4,47,46,0.82), rgba(2,132,199,0.36)), url(${heroImages.hoiAn})`,
        }}
      >
        <div className="container">
          <Badge tone="glass">
            <Sparkles size={13} /> Trip library
          </Badge>
          <h1>Những chuyến đi của bạn</h1>
          <div className="library-hero-bottom">
            <p>Lưu, xem lại, chia sẻ hoặc tiếp tục tối ưu các lịch trình đã tạo.</p>
            <Button href="/plan">
              <Plus size={16} /> Tạo lịch trình mới
            </Button>
          </div>
        </div>
      </section>

      <main className="container trip-library-main">
        <div className="library-toolbar">
          <div>
            <h2>Thư viện lịch trình</h2>
          </div>
        </div>

        {loading && (
          <Card className="library-state">
            <div className="spinner" />
            <p>Đang tải lịch trình...</p>
          </Card>
        )}

        {error && !loading && (
          <Card className="library-state">
            <p style={{ color: "#DC2626" }}>{error}</p>
            <Button variant="secondary" size="sm" onClick={() => window.location.reload()}>
              Thử lại
            </Button>
          </Card>
        )}

        {!loading && !error && trips.length === 0 && (
          <Card className="library-empty">
            <div style={{ backgroundImage: `url(${heroImages.vietnamBay})` }} />
            <section>
              <Badge tone="teal">Chưa có lịch trình</Badge>
              <h2>Bắt đầu với chuyến đi đầu tiên</h2>
              <p>Chọn điểm đến, nhập ngân sách và để VivuPlan tạo itinerary thực tế cho bạn.</p>
              <Button href="/plan">
                <Sparkles size={15} /> Lập kế hoạch ngay
              </Button>
            </section>
          </Card>
        )}

        {!loading && !error && trips.length > 0 && (
          <>
            <div className="trip-card-grid">
              {visibleTrips.map((trip) => (
                <TripCard
                  key={trip.id}
                  trip={trip}
                  destinations={destinations}
                  deleting={deleting === trip.id}
                  sharing={sharingTripId === trip.id}
                  copied={copiedTripId === trip.id}
                  opening={openingTripId === trip.id}
                  viewDisabled={openingTripId !== null}
                  onDelete={() => setDeleteCandidate(trip)}
                  onShare={() => handleShare(trip)}
                  onView={() => handleOpenTrip(trip.id)}
                />
              ))}
            </div>

            {totalPages > 1 && (
              <nav className="library-pagination" aria-label="Phân trang lịch trình">
                <Button type="button" variant="secondary" size="icon" onClick={() => setPage((current) => Math.max(1, current - 1))} disabled={currentPage === 1} title="Trang trước">
                  <ChevronLeft size={16} />
                </Button>
                <div className="library-page-list">
                  {Array.from({ length: totalPages }, (_, index) => index + 1).map((pageNumber) => (
                    <button
                      key={pageNumber}
                      type="button"
                      className={`library-page-button${currentPage === pageNumber ? " active" : ""}`}
                      onClick={() => setPage(pageNumber)}
                      aria-current={currentPage === pageNumber ? "page" : undefined}
                    >
                      {pageNumber}
                    </button>
                  ))}
                </div>
                <Button type="button" variant="secondary" size="icon" onClick={() => setPage((current) => Math.min(totalPages, current + 1))} disabled={currentPage === totalPages} title="Trang sau">
                  <ChevronRight size={16} />
                </Button>
              </nav>
            )}
          </>
        )}
      </main>

      {deleteCandidate && (
        <DeleteTripDialog
          trip={deleteCandidate}
          deleting={deleting === deleteCandidate.id}
          onCancel={() => setDeleteCandidate(null)}
          onConfirm={handleDelete}
        />
      )}

      {toast && <div className={`itinerary-toast itinerary-toast-${toast.tone}`}>{toast.message}</div>}
    </div>
  );
}

function TripCard({
  trip,
  destinations,
  deleting,
  sharing,
  copied,
  opening,
  viewDisabled,
  onDelete,
  onShare,
  onView,
}: {
  trip: TripResponse;
  destinations: Destination[];
  deleting: boolean;
  sharing: boolean;
  copied: boolean;
  opening: boolean;
  viewDisabled: boolean;
  onDelete: () => void;
  onShare: () => void;
  onView: () => void;
}) {
  const timingBadge = getTripTimingBadge(trip);

  return (
    <article className="trip-card">
      <div className="trip-card-media" style={{ backgroundImage: `linear-gradient(180deg, rgba(0,0,0,0.04), rgba(0,0,0,0.46)), url(${getDestinationImage(trip.destination, destinations)})` }}>
        <div>
          <Badge tone={timingBadge.tone}>{timingBadge.label}</Badge>
          {trip.isPublic && <Badge tone="teal">Đang chia sẻ</Badge>}
        </div>
        <h3>{trip.destination}</h3>
      </div>
      <div className="trip-card-body">
        <p className="trip-route-text">{trip.departure || "Điểm xuất phát"} → {trip.destination}</p>
        <div className="trip-meta-grid">
          {fmtDateRange(trip) && (
            <span>
              <Clock size={13} /> {fmtDateRange(trip)}
            </span>
          )}
          <span>
            <Clock size={13} /> {trip.days}N{trip.days - 1}Đ
          </span>
          <span>
            <Wallet size={13} /> {fmtBudget(trip.budgetTotal || trip.budgetPerPerson * (trip.travelerCount || 1))}
          </span>
          <span>
            <Users size={13} /> {groupLabel[trip.groupType] ?? trip.groupType} ({trip.travelerCount || 1} người)
          </span>
        </div>
        <div className="trip-card-actions">
          <Button
            href={`/itinerary/${trip.id}`}
            variant="secondary"
            size="sm"
            className="trip-view-button"
            aria-busy={opening}
            aria-disabled={viewDisabled}
            tabIndex={viewDisabled ? -1 : undefined}
            onClick={(event) => {
              if (viewDisabled) {
                event.preventDefault();
                return;
              }

              if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
              onView();
            }}
          >
            {opening ? <span className="spinner spinner-inline" /> : <Eye size={13} />}
            {opening ? "Đang mở..." : "Xem"}
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            className={`trip-share-button${trip.isPublic ? " is-public" : ""}`}
            onClick={onShare}
            disabled={sharing}
            aria-busy={sharing}
            title="Sao chép link chia sẻ lịch trình"
          >
            {sharing ? <span className="spinner spinner-inline" /> : copied ? <CheckCircle2 size={14} /> : <Share2 size={14} />}
            {sharing ? "Đang chia sẻ..." : copied ? "Đã copy" : "Chia sẻ"}
          </Button>
          <Button type="button" variant="ghost" size="icon" onClick={onDelete} disabled={deleting} title="Xóa lịch trình">
            <Trash2 size={15} />
          </Button>
        </div>
      </div>
    </article>
  );
}

function DeleteTripDialog({
  trip,
  deleting,
  onCancel,
  onConfirm,
}: {
  trip: TripResponse;
  deleting: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div
      className="trip-delete-modal-backdrop"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="trip-delete-title"
      aria-describedby="trip-delete-description"
    >
      <section className="trip-delete-modal-panel">
        <button type="button" className="trip-delete-modal-close" onClick={onCancel} disabled={deleting} aria-label="Đóng xác nhận xóa">
          <X size={16} />
        </button>
        <div className="trip-delete-modal-icon">
          <AlertTriangle size={24} />
        </div>
        <h3 id="trip-delete-title">Xóa lịch trình này?</h3>
        <p id="trip-delete-description">
          Lịch trình <strong>{trip.destination}</strong> sẽ bị xóa vĩnh viễn khỏi thư viện của bạn.
        </p>
        <div className="trip-delete-modal-actions">
          <Button type="button" variant="secondary" onClick={onCancel} disabled={deleting}>
            Hủy
          </Button>
          <Button type="button" variant="primary" className="trip-delete-confirm-button" onClick={onConfirm} disabled={deleting}>
            {deleting ? <span className="spinner spinner-inline" /> : <Trash2 size={15} />}
            {deleting ? "Đang xóa..." : "Xóa lịch trình"}
          </Button>
        </div>
      </section>
    </div>
  );
}
