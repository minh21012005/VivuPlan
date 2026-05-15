import { ErrorPage } from "@/components/ErrorPage";

export default function ForbiddenPage() {
  return (
    <ErrorPage
      code={403}
      title="Bạn không có quyền truy cập"
      description="Trang này không dành cho bạn. Nếu bạn cho rằng đây là nhầm lẫn, vui lòng liên hệ hỗ trợ."
      primaryAction={{ label: "Về trang chủ", href: "/", icon: "home" }}
    />
  );
}
