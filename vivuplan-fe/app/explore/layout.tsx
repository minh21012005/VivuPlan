import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Khám phá các điểm đến du lịch Việt Nam | VivuPlan",
  description: "Tìm nguồn cảm hứng du lịch từ Bắc chí Nam. Khám phá các địa danh hấp dẫn và để AI thiết kế lịch trình tối ưu cho chuyến đi kế tiếp của bạn.",
  openGraph: {
    title: "Khám phá các điểm đến du lịch Việt Nam | VivuPlan",
    description: "Tìm nguồn cảm hứng du lịch Việt Nam và tạo lịch trình AI tự động trong tích tắc.",
    type: "website",
  },
};

export default function ExploreLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
