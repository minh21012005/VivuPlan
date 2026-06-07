"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { ActivityResponse } from "@/lib/api";

type LeafletModule = typeof import("leaflet");

interface DayRouteMapProps {
  activities: ActivityResponse[];
  destination?: string | null;
  departure?: string | null;
  directionsUrl: string;
}

interface MappableActivity {
  order: number;
  activity: ActivityResponse;
  lat: number;
  lon: number;
}

const DEFAULT_TILE_URL = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
const DEFAULT_TILE_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';
const MARKER_GROUP_DISTANCE_PIXELS = 32;

function isValidVietnamCoordinate(lat?: number, lon?: number) {
  if (typeof lat !== "number" || typeof lon !== "number") return false;
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return false;
  return lat >= 7 && lat <= 24.8 && lon >= 102 && lon <= 110.8;
}

function normalizePlaceText(value?: string | null) {
  return String(value ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

const ADMINISTRATIVE_PREFIXES = [
  "thanh pho",
  "tp",
  "tinh",
  "huyen",
  "quan",
  "thi xa",
  "thi tran",
  "xa",
  "phuong",
];

function stripAdministrativePrefix(value?: string | null) {
  let normalized = normalizePlaceText(value);
  let changed = true;
  while (changed) {
    changed = false;
    for (const prefix of ADMINISTRATIVE_PREFIXES) {
      if (normalized === prefix) return "";
      if (normalized.startsWith(`${prefix} `)) {
        normalized = normalized.slice(prefix.length).trim();
        changed = true;
        break;
      }
    }
  }
  return normalized;
}

function isSameTravelPlace(a?: string | null, b?: string | null) {
  const normalizedA = normalizePlaceText(a);
  const normalizedB = normalizePlaceText(b);
  if (!normalizedA || !normalizedB) return false;
  if (normalizedA === normalizedB) return true;

  const strippedA = stripAdministrativePrefix(a);
  const strippedB = stripAdministrativePrefix(b);
  return Boolean(strippedA && strippedB && strippedA === strippedB);
}

function shouldShowOnDayMap(
  activity: ActivityResponse,
  departure?: string | null,
  destination?: string | null,
) {
  if (activity.type === "TRANSPORT") return false;

  const normalizedDeparture = normalizePlaceText(departure);
  if (!normalizedDeparture) return true;
  if (isSameTravelPlace(departure, destination)) return true;

  const normalizedActivityPlace = normalizePlaceText([activity.name, activity.location].filter(Boolean).join(" "));
  if (!normalizedActivityPlace) return true;

  return !normalizedActivityPlace.includes(normalizedDeparture);
}

function escapeHtml(value?: string | number | null) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function buildMapsSearchUrl(lat: number, lon: number) {
  return `https://www.google.com/maps/search/?api=1&query=${lat},${lon}`;
}

function groupNearbyActivities(
  items: MappableActivity[],
  map: import("leaflet").Map,
  L: LeafletModule,
) {
  const groups: Array<{ items: MappableActivity[]; lat: number; lon: number }> = [];
  for (const item of items) {
    const itemPoint = map.latLngToLayerPoint(L.latLng(item.lat, item.lon));
    const group = groups.find((candidate) => candidate.items.some((existing) => {
      const existingPoint = map.latLngToLayerPoint(L.latLng(existing.lat, existing.lon));
      return itemPoint.distanceTo(existingPoint) <= MARKER_GROUP_DISTANCE_PIXELS;
    }));
    if (group) {
      group.items.push(item);
      group.lat = group.items.reduce((sum, current) => sum + current.lat, 0) / group.items.length;
      group.lon = group.items.reduce((sum, current) => sum + current.lon, 0) / group.items.length;
    } else {
      groups.push({ items: [item], lat: item.lat, lon: item.lon });
    }
  }
  return groups;
}

function buildPopupHtml(items: MappableActivity[], destination?: string | null) {
  const rows = items.map(({ order, activity, lat, lon }) => {
    const title = escapeHtml(activity.name || `Điểm ${order}`);
    const time = activity.time ? `<span>${escapeHtml(activity.time)}</span>` : "";
    const location = activity.location || destination;
    const locationRow = location ? `<p>${escapeHtml(location)}</p>` : "";
    const url = buildMapsSearchUrl(lat, lon);

    return `
      <div class="day-route-popup-item">
        <div class="day-route-popup-head">
          <span class="day-route-popup-order">${order}</span>
          <strong>${title}</strong>
        </div>
        ${time}
        ${locationRow}
        <a href="${url}" target="_blank" rel="noreferrer">Mở bản đồ</a>
      </div>
    `;
  }).join("");

  return `<div class="day-route-popup"><div class="day-route-popup-list">${rows}</div></div>`;
}

function createMarkerIcon(L: LeafletModule, label: string) {
  const width = label.length > 1 ? 42 : 30;
  return L.divIcon({
    className: "day-route-marker",
    html: `<span>${escapeHtml(label)}</span>`,
    iconSize: [width, 30],
    iconAnchor: [width / 2, 15],
    popupAnchor: [0, -14],
  });
}

function getGroupedMarkerLabel(items: MappableActivity[]) {
  const orders = items.map((item) => item.order).sort((a, b) => a - b);
  if (orders.length === 0) return "";
  if (orders.length === 1) return String(orders[0]);
  if (orders.length === 2) return `${orders[0]}-${orders[1]}`;
  return `${orders[0]}+`;
}

export function DayRouteMap({ activities, destination, departure, directionsUrl }: DayRouteMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<import("leaflet").Map | null>(null);
  const layerRef = useRef<import("leaflet").LayerGroup | null>(null);
  const leafletRef = useRef<LeafletModule | null>(null);
  const [mapError, setMapError] = useState(false);
  const [mapReady, setMapReady] = useState(false);

  const mapCandidateActivities = useMemo(
    () => activities.filter((activity) => shouldShowOnDayMap(activity, departure, destination)),
    [activities, departure, destination],
  );

  const mappableActivities = useMemo<MappableActivity[]>(() => {
    return mapCandidateActivities
      .map((activity) => ({
        activity,
        lat: Number(activity.latitude),
        lon: Number(activity.longitude),
      }))
      .filter(({ lat, lon }) => isValidVietnamCoordinate(lat, lon))
      .map((item, index) => ({
        ...item,
        order: index + 1,
      }));
  }, [mapCandidateActivities]);

  const missingCoordinateCount = Math.max(0, mapCandidateActivities.length - mappableActivities.length);

  useEffect(() => {
    let cancelled = false;

    async function initMap() {
      if (!containerRef.current || mapRef.current) return;

      try {
        const L = await import("leaflet");
        if (cancelled || !containerRef.current) return;

        leafletRef.current = L;
        const map = L.map(containerRef.current, {
          zoomControl: true,
          attributionControl: false,
          scrollWheelZoom: false,
        });

        L.control.attribution({
          position: "bottomright",
          prefix: false,
        }).addTo(map);

        L.tileLayer(process.env.NEXT_PUBLIC_MAP_TILE_URL || DEFAULT_TILE_URL, {
          attribution: process.env.NEXT_PUBLIC_MAP_TILE_ATTRIBUTION || DEFAULT_TILE_ATTRIBUTION,
          maxZoom: 19,
        }).addTo(map);

        const layer = L.layerGroup().addTo(map);
        mapRef.current = map;
        layerRef.current = layer;
        setMapReady(true);
        window.setTimeout(() => map.invalidateSize(), 120);
      } catch {
        setMapError(true);
      }
    }

    void initMap();

    return () => {
      cancelled = true;
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
        layerRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    const L = leafletRef.current;
    const map = mapRef.current;
    const layer = layerRef.current;
    if (!mapReady || !L || !map || !layer) return;

    layer.clearLayers();

    if (mappableActivities.length === 0) {
      map.setView([16.0471, 108.2068], 5);
      return;
    }

    if (mappableActivities.length === 1) {
      map.setView([mappableActivities[0].lat, mappableActivities[0].lon], 13);
    } else {
      const bounds = L.latLngBounds(mappableActivities.map((item) => [item.lat, item.lon]));
      map.fitBounds(bounds, { padding: [28, 28], maxZoom: 14 });
    }

    const groupedActivities = groupNearbyActivities(mappableActivities, map, L);
    for (const group of groupedActivities) {
      const markerLabel = getGroupedMarkerLabel(group.items);
      L.marker([group.lat, group.lon], {
        icon: createMarkerIcon(L, markerLabel),
        keyboard: true,
        title: group.items.map((item) => item.activity.name).filter(Boolean).join(", "),
      })
        .bindPopup(buildPopupHtml(group.items, destination), {
          closeButton: true,
          maxWidth: 260,
          className: "day-route-leaflet-popup",
        })
        .addTo(layer);
    }

    window.setTimeout(() => map.invalidateSize(), 80);
  }, [destination, mapReady, mappableActivities]);

  if (mapError) {
    return (
      <div className="day-route-map-fallback">
        <strong>Chưa tải được bản đồ</strong>
        <p>Bạn vẫn có thể mở tuyến đường thực tế trên Google Maps.</p>
        <a href={directionsUrl} target="_blank" rel="noreferrer">Mở Google Maps</a>
      </div>
    );
  }

  return (
    <div className="day-route-map-shell">
      <div ref={containerRef} className="day-route-map" aria-label="Bản đồ các điểm trong ngày" />
      {missingCoordinateCount > 0 && (
        <div className="day-route-map-note">
          {missingCoordinateCount} điểm chưa có tọa độ nên chưa hiển thị trên bản đồ.
        </div>
      )}
    </div>
  );
}
