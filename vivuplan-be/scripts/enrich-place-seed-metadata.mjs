import fs from "node:fs";

const seedPath = "src/main/resources/data/places.seed.json";
const islandDestinations = new Set(["Phú Quốc", "Cát Bà", "Lý Sơn", "Nam Du", "Côn Đảo", "Đảo Phú Quý"]);

const places = JSON.parse(fs.readFileSync(seedPath, "utf8"));
const enriched = places.map((place) => {
  const base = stripUnsupportedFields(place);
  return {
    ...base,
    priceLevel: inferPriceLevel(place),
    indoorOutdoor: place.indoorOutdoor ?? inferIndoorOutdoor(place),
    weatherSensitivity: place.weatherSensitivity ?? inferWeatherSensitivity(place),
    tags: place.tags && place.tags.length ? place.tags : inferTags(place),
    aliases: place.aliases ?? inferAliases(place),
    costBasis: place.costBasis ?? inferCostBasis(place),
  };
});

fs.writeFileSync(seedPath, `${JSON.stringify(enriched, null, 2)}\n`, "utf8");

function normalize(value) {
  return (value ?? "")
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function text(place) {
  return normalize(
    [
      place.name,
      place.description,
      place.openingHours,
    ].join(" "),
  );
}

function hasAny(value, ...needles) {
  const haystack = ` ${normalize(value)} `;
  return needles.some((needle) => {
    const normalizedNeedle = normalize(needle);
    return normalizedNeedle.length > 0 && haystack.includes(` ${normalizedNeedle} `);
  });
}

function stripUnsupportedFields(place) {
  const {
    bestTimeOfDay,
    recommendedDurationMinutes,
    sourceUrl,
    verifiedAt,
    ...supportedFields
  } = place;
  return supportedFields;
}

function inferIndoorOutdoor(place) {
  const value = text(place);
  if (["FOOD", "CAFE", "ACCOMMODATION", "NIGHTLIFE"].includes(place.type)) {
    return hasAny(value, "cho", "pho di bo", "bai bien", "bai tam", "vinh", "vuon") ? "MIXED" : "INDOOR";
  }
  if (
    hasAny(
      value,
      "bao tang",
      "nha co",
      "dinh",
      "nha tho",
      "chua",
      "den",
      "thap",
      "cho",
      "trung tam",
    )
  ) {
    return "MIXED";
  }
  if (
    hasAny(
      value,
      "bai bien",
      "bai tam",
      "vinh",
      "hon",
      "thuyen",
      "kayak",
      "sup",
      "deo",
      "thac",
      "nui",
      "ho",
      "rung",
      "trekking",
      "hang",
      "doi cat",
      "cao nguyen",
    )
  ) {
    return "OUTDOOR";
  }
  return "MIXED";
}

function inferWeatherSensitivity(place) {
  const value = text(place);
  if (isMostlySpiritualOrHistorical(place, value)) {
    return inferIndoorOutdoor(place) === "INDOOR" ? "LOW" : "MEDIUM";
  }
  if (
    hasAny(
      value,
      "sup",
      "kayak",
      "cano",
      "ca no",
      "thuyen",
      "du thuyen",
      "tour tau",
      "tau cao toc",
      "lan bien",
      "tam bien",
      "trekking",
      "thac",
      "leo nui",
      "zipline",
      "du luon",
      "nhay du",
    )
  ) {
    return "HIGH";
  }
  return inferIndoorOutdoor(place) === "INDOOR" ? "LOW" : "MEDIUM";
}

function inferCostBasis(place) {
  const maxCost = Math.max(0, place.estimatedCostMax ?? 0);
  if (maxCost === 0) {
    return "FREE";
  }
  if (place.type === "ACCOMMODATION") {
    return "PER_NIGHT";
  }
  if (place.type === "TRANSPORT") {
    return "PER_RIDE";
  }
  return "PER_PERSON";
}

function inferPriceLevel(place) {
  const maxCost = Math.max(0, place.estimatedCostMax ?? 0);
  if (maxCost === 0) {
    return "FREE";
  }
  if (maxCost <= 200_000) {
    return "LOW";
  }
  if (maxCost <= 700_000) {
    return "MID";
  }
  return "HIGH";
}

function inferTags(place) {
  const tags = new Set();
  const value = text(place);
  if (place.type) {
    tags.add(place.type.toLowerCase());
  }
  tags.add(inferIndoorOutdoor(place).toLowerCase());
  addTagIf(tags, value, "beach", "bai bien", "bai tam", "bai sao", "bai xep", "bai sau", "bai cat", "bai cay", "bai dam", "bai rach", "vinh");
  if (isIslandPlace(place, value)) {
    tags.add("island");
  }
  addTagIf(tags, value, "boat", "thuyen", "du thuyen", "tour tau", "tau cao toc", "cano", "ca no", "kayak", "sup");
  addTagIf(tags, value, "mountain", "nui", "deo", "cao nguyen", "trekking");
  addTagIf(tags, value, "waterfall", "thac");
  addTagIf(tags, value, "museum", "bao tang", "trung bay");
  addTagIf(tags, value, "heritage", "unesco", "pho co", "di san", "di tich");
  addTagIf(tags, value, "spiritual", "chua", "den", "mieu", "nha tho", "thien vien");
  addTagIf(tags, value, "food", "cho", "am thuc", "hai san", "dac san", "mon");
  addTagIf(tags, value, "family", "gia dinh", "bao tang", "cong vien", "vuon");
  addTagIf(tags, value, "couple", "hoang hon", "ngam canh", "view", "di dao");
  addTagIf(tags, value, "adventure", "zipline", "trekking", "kayak", "sup", "leo", "xe dia hinh");
  return [...tags];
}

function addTagIf(tags, value, tag, ...needles) {
  if (hasAny(value, ...needles)) {
    tags.add(tag);
  }
}

function isMostlySpiritualOrHistorical(place, value) {
  if (place.type === "ACTIVITY") {
    return false;
  }
  return hasAny(value, "chua", "den", "mieu", "nha tho", "dinh", "di tich", "nha tu");
}

function isIslandPlace(place, value) {
  return islandDestinations.has(place.destination)
    || normalize(place.name).startsWith("dao ")
    || normalize(place.name).startsWith("hon ")
    || /(^|\s)Hòn(\s|$)/i.test(place.name ?? "");
}

function inferAliases(place) {
  const aliases = new Set();
  const name = normalize(place.name);
  if (name.includes("dinh doc lap")) {
    aliases.add("Hội trường Thống Nhất");
  }
  if (name.includes("bao tang chung tich chien tranh")) {
    aliases.add("War Remnants Museum");
  }
  if (name.includes("cho ben thanh")) {
    aliases.add("Ben Thanh Market");
  }
  return [...aliases];
}
