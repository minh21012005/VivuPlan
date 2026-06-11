import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Lập lịch trình du lịch bằng AI | VivuPlan",
  description: "Nhập điểm xuất phát, điểm đến, ngân sách và thời gian để AI tự động lên lịch trình chi tiết và ước tính chi phí thực tế cho bạn.",
  openGraph: {
    title: "Lập lịch trình du lịch bằng AI | VivuPlan",
    description: "Tạo lịch trình du lịch Việt Nam tối ưu chi phí và thời gian bằng AI chỉ trong vài giây.",
    type: "website",
  },
};

export default function PlanLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
