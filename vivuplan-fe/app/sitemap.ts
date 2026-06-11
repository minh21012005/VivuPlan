import { MetadataRoute } from "next";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const baseUrl = process.env.NEXT_PUBLIC_APP_URL || "https://vivuplan.vn";
  const apiBase = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

  const staticRoutes: MetadataRoute.Sitemap = [
    { url: baseUrl, lastModified: new Date(), changeFrequency: "daily", priority: 1.0 },
    { url: `${baseUrl}/explore`, lastModified: new Date(), changeFrequency: "weekly", priority: 0.8 },
    { url: `${baseUrl}/pricing`, lastModified: new Date(), changeFrequency: "monthly", priority: 0.5 },
    { url: `${baseUrl}/plan`, lastModified: new Date(), changeFrequency: "weekly", priority: 0.8 },
  ];

  try {
    const response = await fetch(`${apiBase}/api/destinations`, {
      next: { revalidate: 86400 } // Cache sitemap trong 1 ngày
    });
    if (response.ok) {
      const destinations = await response.json();
      const destinationRoutes = destinations.map((d: any) => ({
        url: `${baseUrl}/plan?destination=${encodeURIComponent(d.name)}`,
        lastModified: new Date(),
        changeFrequency: "weekly",
        priority: 0.6,
      }));
      return [...staticRoutes, ...destinationRoutes];
    }
  } catch (e) {
    // Bỏ qua nếu có lỗi fetch và trả về các route tĩnh
  }

  return staticRoutes;
}
