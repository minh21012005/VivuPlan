import { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  const baseUrl = process.env.NEXT_PUBLIC_APP_URL || "https://vivuplan.vn";
  return {
    rules: {
      userAgent: "*",
      allow: ["/", "/explore", "/pricing", "/plan", "/itinerary/public/share/"],
      disallow: ["/admin/", "/settings/", "/forbidden", "/unauthorized", "/itinerary/"],
    },
    sitemap: `${baseUrl}/sitemap.xml`,
  };
}
