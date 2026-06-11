import { Metadata } from "next";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>;
}): Promise<Metadata> {
  const resolvedParams = await params;
  const id = resolvedParams.id;

  // Nếu không phải là ID số nguyên (tức là mã shareCode công khai)
  if (!/^\d+$/.test(id)) {
    try {
      const apiBase = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
      const response = await fetch(`${apiBase}/api/trips/public/share/${id}`, {
        next: { revalidate: 3600 } // Cache 1 tiếng
      });
      if (response.ok) {
        const trip = await response.json();
        const title = `Lịch trình du lịch ${trip.destination} ${trip.days} ngày – VivuPlan`;
        const description = `Khám phá kế hoạch đi du lịch ${trip.destination} ${trip.days} ngày ${trip.days - 1} đêm xuất phát từ ${trip.departure || "Hà Nội"}. Lịch trình tối ưu chi phí, thời tiết lập bởi AI của VivuPlan.`;
        return {
          title,
          description,
          openGraph: {
            title,
            description,
            type: "website",
            locale: "vi_VN",
          },
        };
      }
    } catch (e) {
      // Fallback below
    }
  }

  return {
    title: "Chi tiết lịch trình du lịch | VivuPlan",
    description: "Xem chi tiết lịch trình du lịch Việt Nam tự động được thiết kế bằng AI của VivuPlan.",
  };
}

export default async function ItineraryDetailLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ id: string }>;
}) {
  return <>{children}</>;
}
