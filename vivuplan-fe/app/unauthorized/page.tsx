import { ErrorPage } from "@/components/ErrorPage";

export default function UnauthorizedPage() {
  return (
    <ErrorPage
      code={401}
      title="Bạn chưa đăng nhập"
      description="Trang này yêu cầu đăng nhập để truy cập. Vui lòng đăng nhập để tiếp tục hành trình của bạn!"
      primaryAction={{ label: "Đăng nhập ngay", href: "/login", icon: "login" }}
    />
  );
}
