import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Chuyến đi của tôi | VivuPlan",
  description: "Xem và quản lý các lịch trình du lịch của bạn trên VivuPlan.",
};

export default function ItineraryLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
