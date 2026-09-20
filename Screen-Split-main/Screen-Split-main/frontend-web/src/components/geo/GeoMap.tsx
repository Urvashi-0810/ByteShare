import { useEffect, useMemo } from "react";
import L from "leaflet";
import {
  MapContainer,
  TileLayer,
  Marker,
  Circle,
  Polyline,
  useMap,
} from "react-leaflet";
import { cn } from "@/lib/utils";

// Leaflet's default icon URL paths break with Vite bundling. Use lightweight
// DivIcons instead — they also blend better with the ScreenSplit style.

function makeEmojiIcon(emoji: string, bgClass: string) {
  return L.divIcon({
    className: "",
    html: `<div class="${bgClass} h-9 w-9 rounded-full border-2 border-foreground flex items-center justify-center text-lg shadow-[2px_2px_0px_rgba(0,0,0,1)]">${emoji}</div>`,
    iconSize: [36, 36],
    iconAnchor: [18, 18],
  });
}

const targetIcon = makeEmojiIcon("📍", "bg-accent");
const originIcon = makeEmojiIcon("🏁", "bg-primary text-primary-foreground");
const meIcon = L.divIcon({
  className: "",
  html: `<div class="h-4 w-4 rounded-full bg-sky-500 border-2 border-white shadow-[0_0_0_2px_rgba(14,165,233,0.35)] animate-pulse"></div>`,
  iconSize: [16, 16],
  iconAnchor: [8, 8],
});

type LatLng = { lat: number; lng: number };

type GeoMapProps = {
  target: LatLng;
  targetRadiusM: number;
  me?: LatLng | null;
  origin?: LatLng | null;
  originRadiusM?: number;
  path?: LatLng[];
  className?: string;
  /** Height override. Defaults to 280px. */
  height?: number;
  /** Zoom level. Defaults to a reasonable walking zoom. */
  zoom?: number;
};

/** Reacts to target / me changes by smoothly adjusting the map viewport. */
function Viewport({ target, me, origin }: {
  target: LatLng;
  me?: LatLng | null;
  origin?: LatLng | null;
}) {
  const map = useMap();
  useEffect(() => {
    const points: LatLng[] = [target];
    if (me) points.push(me);
    if (origin) points.push(origin);
    if (points.length === 1) {
      map.setView([target.lat, target.lng], 17, { animate: true });
      return;
    }
    const bounds = L.latLngBounds(
      points.map((p) => [p.lat, p.lng] as [number, number]),
    );
    map.fitBounds(bounds, { padding: [40, 40], maxZoom: 18 });
  }, [map, target.lat, target.lng, me?.lat, me?.lng, origin?.lat, origin?.lng]);
  return null;
}

export function GeoMap({
  target,
  targetRadiusM,
  me,
  origin,
  originRadiusM,
  path,
  className,
  height = 280,
  zoom = 16,
}: GeoMapProps) {
  const center = useMemo<[number, number]>(
    () => [target.lat, target.lng],
    [target.lat, target.lng],
  );

  return (
    <div
      className={cn(
        "rounded-2xl overflow-hidden border-2 border-foreground shadow-[4px_4px_0px_rgba(0,0,0,1)]",
        className,
      )}
      style={{ height }}
    >
      <MapContainer
        center={center}
        zoom={zoom}
        scrollWheelZoom={false}
        style={{ height: "100%", width: "100%" }}
        attributionControl={false}
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; OpenStreetMap'
        />
        <Viewport target={target} me={me} origin={origin} />

        <Circle
          center={[target.lat, target.lng]}
          radius={targetRadiusM}
          pathOptions={{
            color: "#22c55e",
            weight: 2,
            fillColor: "#bbf7d0",
            fillOpacity: 0.35,
          }}
        />
        <Marker position={[target.lat, target.lng]} icon={targetIcon} />

        {origin && (
          <>
            {originRadiusM ? (
              <Circle
                center={[origin.lat, origin.lng]}
                radius={originRadiusM}
                pathOptions={{
                  color: "#2563eb",
                  weight: 2,
                  fillColor: "#bfdbfe",
                  fillOpacity: 0.25,
                  dashArray: "6 6",
                }}
              />
            ) : null}
            <Marker position={[origin.lat, origin.lng]} icon={originIcon} />
          </>
        )}

        {path && path.length > 1 && (
          <Polyline
            positions={path.map((p) => [p.lat, p.lng])}
            pathOptions={{ color: "#0ea5e9", weight: 4, opacity: 0.7 }}
          />
        )}

        {me && <Marker position={[me.lat, me.lng]} icon={meIcon} />}
      </MapContainer>
    </div>
  );
}
