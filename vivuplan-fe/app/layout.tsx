import type { Metadata } from "next";
import { Inter, Plus_Jakarta_Sans } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin", "vietnamese"],
  variable: "--font-inter",
  display: "swap",
});

const plusJakarta = Plus_Jakarta_Sans({
  subsets: ["latin"],
  variable: "--font-jakarta",
  display: "swap",
  weight: ["400", "500", "600", "700", "800"],
});

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

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi" className={`${inter.variable} ${plusJakarta.variable} h-full`}>
      <body className="min-h-full flex flex-col antialiased">{children}</body>
    </html>
  );
}
