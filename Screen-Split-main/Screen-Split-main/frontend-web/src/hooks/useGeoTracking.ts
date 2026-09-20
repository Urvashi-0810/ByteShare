import { useCallback, useEffect, useRef, useState } from "react";

export type GeoPosition = {
  lat: number;
  lng: number;
  accuracy: number;
  timestamp: number;
};

export type UseGeoTrackingOptions = {
  /** Call `navigator.geolocation.watchPosition` immediately on mount. */
  enabled?: boolean;
  /** Minimum time between checkin callbacks (ms). Default 5000. */
  throttleMs?: number;
  /** Minimum movement before re-emitting a position (m). Default 5. */
  minDistanceM?: number;
  /** Callback for each (throttled) position. */
  onPosition?: (pos: GeoPosition) => void;
};

export type UseGeoTrackingResult = {
  position: GeoPosition | null;
  error: string | null;
  permissionDenied: boolean;
  /** True while `watchPosition` is active. */
  tracking: boolean;
  /** Request a one-shot position (useful before starting). */
  requestOnce: () => Promise<GeoPosition>;
  /** Begin continuous tracking. Re-runs when called again. */
  start: () => void;
  /** Stop continuous tracking. */
  stop: () => void;
  /** Path of recent positions (last ~50). */
  path: GeoPosition[];
};

const DEFAULT_THROTTLE = 5000;
const DEFAULT_MIN_DISTANCE = 5;

function distanceMeters(a: GeoPosition, b: { lat: number; lng: number }) {
  const R = 6371000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const s1 = Math.sin(dLat / 2);
  const s2 = Math.sin(dLng / 2);
  const h =
    s1 * s1 + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * s2 * s2;
  return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

export function useGeoTracking(
  options: UseGeoTrackingOptions = {},
): UseGeoTrackingResult {
  const {
    enabled = false,
    throttleMs = DEFAULT_THROTTLE,
    minDistanceM = DEFAULT_MIN_DISTANCE,
    onPosition,
  } = options;

  const [position, setPosition] = useState<GeoPosition | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [permissionDenied, setPermissionDenied] = useState(false);
  const [tracking, setTracking] = useState(false);
  const [path, setPath] = useState<GeoPosition[]>([]);

  const watchIdRef = useRef<number | null>(null);
  const lastEmitRef = useRef<GeoPosition | null>(null);
  const onPositionRef = useRef(onPosition);
  onPositionRef.current = onPosition;

  const handleFix = useCallback(
    (raw: GeolocationPosition) => {
      const fix: GeoPosition = {
        lat: raw.coords.latitude,
        lng: raw.coords.longitude,
        accuracy: raw.coords.accuracy,
        timestamp: raw.timestamp,
      };
      setPosition(fix);
      setError(null);
      setPermissionDenied(false);

      const last = lastEmitRef.current;
      const movedEnough =
        !last || distanceMeters(last, fix) >= minDistanceM;
      const dueByTime =
        !last || fix.timestamp - last.timestamp >= throttleMs;
      if (movedEnough && dueByTime) {
        lastEmitRef.current = fix;
        setPath((p) => {
          const next = [...p, fix];
          return next.length > 100 ? next.slice(-100) : next;
        });
        onPositionRef.current?.(fix);
      }
    },
    [throttleMs, minDistanceM],
  );

  const handleError = useCallback((err: GeolocationPositionError) => {
    setError(err.message || "Unable to get location");
    if (err.code === err.PERMISSION_DENIED) {
      setPermissionDenied(true);
    }
  }, []);

  const stop = useCallback(() => {
    if (watchIdRef.current !== null && "geolocation" in navigator) {
      navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
    setTracking(false);
  }, []);

  const start = useCallback(() => {
    if (!("geolocation" in navigator)) {
      setError("Geolocation is not supported in this browser");
      return;
    }
    if (watchIdRef.current !== null) {
      navigator.geolocation.clearWatch(watchIdRef.current);
    }
    const id = navigator.geolocation.watchPosition(handleFix, handleError, {
      enableHighAccuracy: true,
      maximumAge: 5000,
      timeout: 20000,
    });
    watchIdRef.current = id;
    setTracking(true);
  }, [handleFix, handleError]);

  const requestOnce = useCallback((): Promise<GeoPosition> => {
    return new Promise((resolve, reject) => {
      if (!("geolocation" in navigator)) {
        reject(new Error("Geolocation is not supported in this browser"));
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (raw) => {
          const fix: GeoPosition = {
            lat: raw.coords.latitude,
            lng: raw.coords.longitude,
            accuracy: raw.coords.accuracy,
            timestamp: raw.timestamp,
          };
          setPosition(fix);
          setError(null);
          setPermissionDenied(false);
          resolve(fix);
        },
        (err) => {
          handleError(err);
          reject(new Error(err.message || "Unable to get location"));
        },
        { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 },
      );
    });
  }, [handleError]);

  useEffect(() => {
    if (enabled) start();
    return () => stop();
    // We intentionally depend on `enabled` only; start/stop are stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);

  return {
    position,
    error,
    permissionDenied,
    tracking,
    requestOnce,
    start,
    stop,
    path,
  };
}
