import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "VivuPlan – Lập kế hoạch du lịch thông minh bằng AI",
  description:
    "Nền tảng lập kế hoạch du lịch Việt Nam được hỗ trợ bởi AI. Tạo lịch trình tối ưu, ước tính ngân sách và tìm kiếm địa điểm phù hợp chỉ trong vài phút.",
  keywords: [
    "lập kế hoạch du lịch",
    "AI travel planning",
    "du lịch Việt Nam",
    "VivuPlan",
    "lịch trình du lịch",
  ],
  authors: [{ name: "VivuPlan" }],
  openGraph: {
    title: "VivuPlan – Lập kế hoạch du lịch thông minh bằng AI",
    description: "Tạo lịch trình du lịch Việt Nam tối ưu trong vài phút với AI",
    type: "website",
    locale: "vi_VN",
  },
};

import { AuthProvider } from "@/context/AuthContext";

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi" className="h-full">
      <body className="min-h-full flex flex-col antialiased">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
