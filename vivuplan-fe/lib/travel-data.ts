import type { DestinationResponse } from "@/lib/api";

export type Destination = DestinationResponse;

export const heroImages = {
  vietnamCoast:
    "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1800&q=85",
  vietnamBay:
    "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1800&q=85",
  hoiAn:
    "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=1800&q=85",
  mountains:
    "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=1800&q=85",
};

export function normalizeVietnameseSearch(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
}

export function getDestinationImage(destinationName?: string, destinations: Destination[] = []) {
  return findDestinationByName(destinationName, destinations)?.imageUrl ?? heroImages.vietnamBay;
}

export function findDestinationByName(destinationName?: string, destinations: Destination[] = []) {
  if (!destinationName) return undefined;
  const keyword = normalizeVietnameseSearch(destinationName);
  return (
    destinations.find((item) => normalizeVietnameseSearch(item.name) === keyword) ??
    destinations.find((item) => {
      const name = normalizeVietnameseSearch(item.name);
      return name.includes(keyword) || keyword.includes(name);
    })
  );
}
