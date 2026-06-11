import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Bảng giá & Gói lượt tạo lịch trình du lịch | VivuPlan",
  description: "Mua thêm lượt lập lịch trình và chỉnh sửa ngày bằng AI của VivuPlan. Bảng giá rõ ràng, thanh toán qua QR SePay nhanh chóng và an toàn.",
  openGraph: {
    title: "Bảng giá & Gói lượt tạo lịch trình du lịch | VivuPlan",
    description: "Xem các gói dịch vụ VivuPlan AI. Nạp lượt lập lịch trình và chỉnh sửa dễ dàng.",
    type: "website",
  },
};

export default function PricingLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
