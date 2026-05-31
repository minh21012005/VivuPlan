import type { DestinationResponse } from "@/lib/api";

export type Destination = DestinationResponse;

export const vietnamProvinces = [
  "An Giang", "Bắc Ninh", "Cà Mau", "Cao Bằng", "Cần Thơ",
  "Đà Nẵng", "Đắk Lắk", "Điện Biên", "Đồng Nai", "Đồng Tháp",
  "Gia Lai", "Hà Nội", "Hà Tĩnh", "Hải Phòng", "Huế",
  "Hưng Yên", "Khánh Hòa", "Lai Châu", "Lạng Sơn", "Lào Cai",
  "Lâm Đồng", "Nghệ An", "Ninh Bình", "Phú Thọ", "Quảng Ngãi",
  "Quảng Ninh", "Quảng Trị", "Sơn La", "Tây Ninh", "Thái Nguyên",
  "Thanh Hóa", "TP.HCM", "Tuyên Quang", "Vĩnh Long"
];
export const heroImages = {
  vietnamCoast:
    "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1800&q=85",
  vietnamBay:
    "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1800&q=85",
  hoiAn:
    "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=1800&q=85",
  mountains:
    "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=1800&q=85",
  city:
    "https://images.unsplash.com/photo-1523731407965-2430cd12f5e4?auto=format&fit=crop&w=1800&q=85",
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
  const destination = findDestinationByName(destinationName, destinations);
  return destination?.imageUrl || getContextualDestinationImage(destinationName, destination);
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

function getContextualDestinationImage(destinationName?: string, destination?: Destination) {
  const searchText = normalizeImageSearchText([
    destinationName,
    destination?.name,
    destination?.region,
    destination?.tourismRegion,
    destination?.province,
    destination?.category,
    destination?.tag,
    destination?.summary,
    destination?.description,
    destination?.tags?.join(" "),
  ].filter(Boolean).join(" "));

  if (!searchText) return heroImages.mountains;

  if (containsAny(searchText, BAY_DESTINATION_KEYWORDS)) {
    return heroImages.vietnamBay;
  }

  if (containsAny(searchText, COAST_DESTINATION_KEYWORDS)) {
    return heroImages.vietnamCoast;
  }

  if (containsAny(searchText, MOUNTAIN_NATURE_DESTINATION_KEYWORDS)) {
    return heroImages.mountains;
  }

  if (containsAny(searchText, HERITAGE_DESTINATION_KEYWORDS)) {
    return heroImages.hoiAn;
  }

  if (containsAny(searchText, CITY_FOOD_DESTINATION_KEYWORDS)) {
    return heroImages.city;
  }

  return heroImages.mountains;
}

const BAY_DESTINATION_KEYWORDS = [
  "ha long", "vinh ha long", "bai tu long", "vinh bai tu long", "lan ha", "vinh lan ha",
  "cat ba", "ninh binh", "trang an", "tam coc", "van long", "thung nham",
  "mien tay", "song nuoc", "cho noi", "ben tre", "tra vinh", "soc trang",
  "chau doc", "an giang", "dong thap", "ca mau", "bac lieu",
];

const COAST_DESTINATION_KEYWORDS = [
  "bien", "bai bien", "bai tam", "dao", "coast", "beach", "island", "sea",
  "phu quoc", "con dao", "ly son", "nha trang", "quy nhon", "mui ne", "phan thiet",
  "da nang", "son tra", "hoi an bien", "cu lao cham", "cua lo", "sam son", "hai tien",
  "ho tram", "ho coc", "long hai", "phan rang", "ninh chu", "cam ranh", "van don",
  "co to", "quan lan", "minh chau", "cat ba", "tuy hoa", "phu yen", "ghenh da dia",
];

const MOUNTAIN_NATURE_DESTINATION_KEYWORDS = [
  "nui", "doi", "rung", "vuon quoc gia", "quoc gia", "thac", "hang", "dong",
  "suoi", "dam", "pha", "trek", "trekking", "forest", "mountain", "national park",
  "camping", "cam trai", "pu mat", "pumat", "bach ma", "phong nha", "cuc phuong",
  "ba vi", "tam dao", "sa pa", "sapa", "moc chau", "ha giang", "cao bang", "ban gioc",
  "da lat", "lam dong", "dak lak", "buon ma thuot", "pleiku", "kon tum", "tay nguyen",
  "ta xua", "mai chau", "mu cang chai", "yen bai", "lai chau", "dien bien", "son la",
  "ba be", "ho ba be", "ho tuyen lam", "ho thac ba", "thac ba", "doi che", "doi cat",
  "hang mua", "puluong", "pu luong",
];

const HERITAGE_DESTINATION_KEYWORDS = [
  "pho co", "di san", "co do", "van hoa", "heritage", "culture", "hoi an", "hue",
  "my son", "lang nghe", "den", "chua", "nha tho", "thanh co", "bao tang", "lang",
  "den tho", "chien khu", "dia dao", "dia dao cu chi", "nha tu", "thap cham", "thanh dia",
  "tay ninh", "ninh binh", "hoa lu", "trang an", "tam coc", "cong chieng",
];

const CITY_FOOD_DESTINATION_KEYWORDS = [
  "ha noi", "sai gon", "ho chi minh", "tp hcm", "can tho", "hai phong",
  "cho", "am thuc", "food", "cafe", "city", "urban", "pho di bo", "night market",
  "cho dem", "street food", "quan an", "nha hang", "coffee", "shopping", "mall",
];

function normalizeImageSearchText(value: string) {
  return normalizeVietnameseSearch(value)
    .replace(/[^a-z0-9]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function containsAny(value: string, keywords: string[]) {
  const paddedValue = ` ${value} `;
  return keywords.some((keyword) => {
    const normalizedKeyword = normalizeImageSearchText(keyword);
    return normalizedKeyword && paddedValue.includes(` ${normalizedKeyword} `);
  });
}
