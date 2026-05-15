export interface Destination {
  name: string;
  region: "Miền Bắc" | "Miền Trung" | "Miền Nam";
  tag: string;
  days: string;
  rating: number;
  trips: number;
  image: string;
  desc: string;
}

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

export const destinations: Destination[] = [
  {
    name: "Đà Lạt",
    region: "Miền Trung",
    tag: "Rừng thông, cà phê và khí hậu mát",
    days: "3-5 ngày",
    rating: 4.9,
    trips: 8420,
    image: heroImages.mountains,
    desc: "Khí hậu mát, rừng thông, cà phê view đồi và những cung đường nhẹ nhàng cho nhóm bạn.",
  },
  {
    name: "Hạ Long",
    region: "Miền Bắc",
    tag: "Vịnh biển, du thuyền và hang động",
    days: "2-4 ngày",
    rating: 4.8,
    trips: 12300,
    image: heroImages.vietnamBay,
    desc: "Vịnh biển, du thuyền, hang động và lịch trình phù hợp cho gia đình hoặc cặp đôi.",
  },
  {
    name: "Hội An",
    region: "Miền Trung",
    tag: "Phố cổ, ẩm thực và đèn lồng",
    days: "2-3 ngày",
    rating: 4.9,
    trips: 9870,
    image: heroImages.hoiAn,
    desc: "Phố cổ, ẩm thực địa phương, biển An Bàng và nhịp đi bộ thư thái.",
  },
  {
    name: "Phú Quốc",
    region: "Miền Nam",
    tag: "Biển xanh, hoàng hôn và nghỉ dưỡng",
    days: "3-5 ngày",
    rating: 4.7,
    trips: 11200,
    image: heroImages.vietnamCoast,
    desc: "Biển xanh, hoàng hôn, hải sản và các resort phù hợp nghỉ dưỡng.",
  },
  {
    name: "Sapa",
    region: "Miền Bắc",
    tag: "Mây núi Tây Bắc",
    days: "3-4 ngày",
    rating: 4.8,
    trips: 7650,
    image: heroImages.mountains,
    desc: "Ruộng bậc thang, bản làng, trekking nhẹ và trải nghiệm khí hậu vùng cao.",
  },
  {
    name: "Nha Trang",
    region: "Miền Trung",
    tag: "Thiên đường biển",
    days: "3-5 ngày",
    rating: 4.6,
    trips: 10500,
    image: heroImages.vietnamCoast,
    desc: "Bãi biển dài, đảo gần bờ, hải sản và các hoạt động biển dễ sắp lịch.",
  },
  {
    name: "Đà Nẵng",
    region: "Miền Trung",
    tag: "Thành phố biển",
    days: "3-4 ngày",
    rating: 4.8,
    trips: 13400,
    image: heroImages.hoiAn,
    desc: "Biển Mỹ Khê, Sơn Trà, Bà Nà và lịch trình dễ kết hợp Hội An.",
  },
  {
    name: "Quy Nhơn",
    region: "Miền Trung",
    tag: "Biển yên bình",
    days: "3-4 ngày",
    rating: 4.9,
    trips: 5300,
    image: heroImages.vietnamCoast,
    desc: "Kỳ Co, Eo Gió, tháp Chăm và nhịp đi biển thoải mái hơn các điểm quá đông.",
  },
];

export function getDestinationImage(destination?: string) {
  return destinations.find((item) => item.name === destination)?.image ?? heroImages.vietnamBay;
}
