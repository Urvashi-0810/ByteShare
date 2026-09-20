import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ClientResponseError } from "pocketbase";
import { toast } from "sonner";
import {
  Compass,
  Copy,
  Flag,
  MapPin,
  PartyPopper,
  Sparkles,
  Ticket,
  XCircle,
} from "lucide-react";
import { PageHeader } from "@/components/screensplit/PageHeader";
import { BottomNav } from "@/components/screensplit/BottomNav";
import { GeoMap } from "@/components/geo/GeoMap";
import { Button } from "@/components/ui/button";
import { useGeoTracking } from "@/hooks/useGeoTracking";
import { formatDistance, haversineMeters, bearingDeg } from "@/lib/haversine";
import {
  abandonGeoAttempt,
  checkinGeoAttempt,
  completeGeoAttempt,
  getGeoChallenge,
  startGeoAttempt,
  type CheckinResponse,
  type CompleteResponse,
  type GeoAttemptDTO,
  type GeoChallengeDTO,
} from "@/lib/geoApi";
import { cn } from "@/lib/utils";

function describeError(err: unknown): string {
  if (err instanceof ClientResponseError) {
    const data = err.response as { message?: string } | undefined;
    if (typeof data?.message === "string" && data.message) return data.message;
    return err.message || "Request failed";
  }
  return err instanceof Error ? err.message : "Something went wrong";
}

const COMPASS_ARROWS = ["↑", "↗", "→", "↘", "↓", "↙", "←", "↖"];

function bearingToArrow(deg: number): string {
  const idx = Math.round(deg / 45) % 8;
  return COMPASS_ARROWS[idx];
}

export default function GeoChallengeDetail() {
  const { id } = useParams<{ id: string }>();
  const nav = useNavigate();

  const [challenge, setChallenge] = useState<GeoChallengeDTO | null>(null);
  const [attempt, setAttempt] = useState<GeoAttemptDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [completion, setCompletion] = useState<CompleteResponse | null>(null);
  const [lastCheckin, setLastCheckin] = useState<CheckinResponse | null>(null);
  const [copied, setCopied] = useState(false);

  const attemptIdRef = useRef<string | null>(null);
  const postingRef = useRef(false);

  const onPosition = useCallback(
    async (pos: { lat: number; lng: number }) => {
      const attemptId = attemptIdRef.current;
      if (!attemptId) return;
      if (postingRef.current) return;
      postingRef.current = true;
      try {
        const res = await checkinGeoAttempt(attemptId, {
          lat: pos.lat,
          lng: pos.lng,
        });
        setLastCheckin(res);
        setAttempt(res.attempt);
      } catch (err) {
        toast.error(describeError(err));
      } finally {
        postingRef.current = false;
      }
    },
    [],
  );

  const tracking = useGeoTracking({
    throttleMs: 4000,
    minDistanceM: 6,
    onPosition,
  });

  // Load challenge details once on mount.
  useEffect(() => {
    if (!id) return;
    let alive = true;
    void (async () => {
      try {
        const data = await getGeoChallenge(id);
        if (!alive) return;
        setChallenge(data);
      } catch (err) {
        toast.error(describeError(err));
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => {
      alive = false;
    };
  }, [id]);

  // Attempt transitions to completed? Auto-finalize to issue the coupon.
  useEffect(() => {
    if (!attempt) return;
    if (attempt.status !== "completed") return;
    if (completion) return;
    if (completing) return;
    (async () => {
      setCompleting(true);
      try {
        const res = await completeGeoAttempt(attempt.id);
        setCompletion(res);
        setAttempt(res.attempt);
        tracking.stop();
      } catch (err) {
        toast.error(describeError(err));
      } finally {
        setCompleting(false);
      }
    })();
  }, [attempt, completion, completing, tracking]);

  const meLatLng = tracking.position
    ? { lat: tracking.position.lat, lng: tracking.position.lng }
    : null;

  const origin = attempt
    ? { lat: attempt.originLat, lng: attempt.originLng }
    : null;

  const target = challenge
    ? { lat: challenge.targetLat, lng: challenge.targetLng }
    : null;

  const liveDistToTarget = useMemo(() => {
    if (!meLatLng || !target) return null;
    return haversineMeters(meLatLng, target);
  }, [meLatLng, target]);

  const liveDistToOrigin = useMemo(() => {
    if (!meLatLng || !origin) return null;
    return haversineMeters(meLatLng, origin);
  }, [meLatLng, origin]);

  const bearing = useMemo(() => {
    if (!meLatLng) return null;
    const aim =
      attempt?.status === "reached_target" && origin
        ? origin
        : target ?? null;
    if (!aim) return null;
    return bearingDeg(meLatLng, aim);
  }, [meLatLng, target, origin, attempt?.status]);

  async function handleStart() {
    if (!challenge) return;
    setStarting(true);
    try {
      let lat = tracking.position?.lat;
      let lng = tracking.position?.lng;
      if (lat === undefined || lng === undefined) {
        const fix = await tracking.requestOnce();
        lat = fix.lat;
        lng = fix.lng;
      }
      const res = await startGeoAttempt(challenge.id, { lat, lng });
      setAttempt(res.attempt);
      attemptIdRef.current = res.attempt.id;
      tracking.start();
      if (res.reused) {
        toast.success("Resumed your in-progress attempt");
      } else {
        toast.success("Walk challenge started. Head to the destination!");
      }
    } catch (err) {
      toast.error(describeError(err));
    } finally {
      setStarting(false);
    }
  }

  async function handleAbandon() {
    if (!attempt) return;
    try {
      await abandonGeoAttempt(attempt.id);
      tracking.stop();
      toast.message("Attempt ended. You can start again whenever.");
      setAttempt(null);
      attemptIdRef.current = null;
      setLastCheckin(null);
    } catch (err) {
      toast.error(describeError(err));
    }
  }

  if (loading || !challenge) {
    return (
      <div className="min-h-screen pb-28">
        <div className="mx-auto max-w-md px-5">
          <PageHeader title="Walk Challenge" back />
          <div className="chunky-card p-6 text-center text-muted-foreground">
            Loading…
          </div>
        </div>
        <BottomNav />
      </div>
    );
  }

  const status = attempt?.status ?? "not_started";
  const isActive = status === "in_progress" || status === "reached_target";
  const isCompleted = status === "completed";

  return (
    <div className="min-h-screen pb-28">
      <div className="mx-auto max-w-md px-5">
        <PageHeader
          title={challenge.title}
          subtitle={
            challenge.isPromoted && challenge.partner
              ? `${challenge.partner.logoEmoji} ${challenge.partner.name}`
              : "Walk there and back"
          }
          back
          right={
            challenge.isPromoted ? (
              <span className="bg-accent text-accent-foreground text-[10px] font-black uppercase px-2 py-1 rounded-full border-2 border-foreground inline-flex items-center gap-1">
                <Sparkles className="h-3 w-3" />
                Promoted
              </span>
            ) : null
          }
        />

        <div className="mt-2">
          {target && (
            <GeoMap
              target={target}
              targetRadiusM={challenge.targetRadiusM}
              originRadiusM={challenge.originRadiusM}
              me={meLatLng}
              origin={origin}
              path={tracking.path.map((p) => ({ lat: p.lat, lng: p.lng }))}
              height={300}
            />
          )}
        </div>

        {tracking.permissionDenied && (
          <div className="mt-4 chunky-card p-4 bg-red-50 border-red-200">
            <p className="font-bold text-red-700">
              Location permission denied
            </p>
            <p className="text-xs text-red-700/80 mt-1">
              Enable location in your browser settings to start this challenge.
            </p>
          </div>
        )}

        {/* Pre-start */}
        {!attempt && !isCompleted && (
          <div className="mt-5 space-y-4">
            {challenge.description && (
              <div className="chunky-card p-4 bg-card">
                <p className="text-sm leading-relaxed">
                  {challenge.description}
                </p>
              </div>
            )}

            {challenge.isPromoted && challenge.partner && (
              <div className="chunky-card p-4 bg-gradient-to-br from-accent/20 to-primary/10">
                <div className="flex items-center gap-3">
                  <div className="h-10 w-10 rounded-xl bg-card border-2 border-foreground grid place-items-center text-xl">
                    {challenge.partner.logoEmoji}
                  </div>
                  <div className="min-w-0">
                    <p className="font-display font-black truncate">
                      {challenge.partner.name}
                    </p>
                    {challenge.partner.address && (
                      <p className="text-xs text-muted-foreground truncate">
                        {challenge.partner.address}
                      </p>
                    )}
                  </div>
                </div>
                {challenge.coupons && challenge.coupons.length > 0 && (
                  <div className="mt-3 space-y-2">
                    {challenge.coupons.map((c) => (
                      <div
                        key={c.id}
                        className="flex items-center gap-2 rounded-xl border-2 border-dashed border-foreground/40 p-2.5 bg-card/60"
                      >
                        <Ticket className="h-4 w-4 shrink-0" />
                        <div className="min-w-0 flex-1">
                          <p className="font-bold text-sm truncate">
                            {c.title}
                          </p>
                          <p className="text-[11px] text-muted-foreground truncate">
                            {c.discountText}
                            {c.totalAvailable > 0 && c.remaining <= 20
                              ? ` · ${c.remaining} left`
                              : ""}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            <div className="chunky-card p-4 bg-card space-y-2 text-xs">
              <Row
                label="Destination"
                value={challenge.targetLabel || "Pinned on the map"}
              />
              <Row
                label="Finish when back within"
                value={`${challenge.originRadiusM} m of start`}
              />
              {challenge.minElapsedSeconds > 0 && (
                <Row
                  label="Minimum time"
                  value={`${Math.round(challenge.minElapsedSeconds / 60)} min`}
                />
              )}
              {liveDistToTarget !== null && (
                <Row
                  label="Your distance"
                  value={formatDistance(liveDistToTarget)}
                />
              )}
            </div>

            <Button
              className="w-full h-14 text-base font-black uppercase tracking-widest"
              onClick={handleStart}
              disabled={starting}
            >
              <MapPin className="h-5 w-5 mr-2" />
              {starting ? "Starting…" : "Start walk challenge"}
            </Button>
          </div>
        )}

        {/* In progress */}
        {attempt && isActive && (
          <div className="mt-5 space-y-4">
            <ActiveTracker
              status={status}
              challenge={challenge}
              distToTarget={liveDistToTarget ?? lastCheckin?.distanceToTargetM ?? null}
              distToOrigin={liveDistToOrigin ?? lastCheckin?.distanceToOriginM ?? null}
              bearing={bearing}
            />

            <div className="grid grid-cols-2 gap-3">
              <Button
                variant="outline"
                className="h-12 font-black uppercase tracking-widest"
                onClick={handleAbandon}
              >
                <XCircle className="h-4 w-4 mr-2" />
                Give up
              </Button>
              <Button
                variant="outline"
                className="h-12 font-black uppercase tracking-widest"
                onClick={() => tracking.start()}
                disabled={tracking.tracking}
              >
                <Compass className="h-4 w-4 mr-2" />
                {tracking.tracking ? "Tracking" : "Resume GPS"}
              </Button>
            </div>
          </div>
        )}

        {/* Completed */}
        {isCompleted && (
          <div className="mt-5 space-y-4">
            <div className="chunky-card p-6 bg-gradient-to-br from-accent/30 to-primary/20 text-center">
              <div className="mx-auto h-16 w-16 rounded-full bg-card border-2 border-foreground grid place-items-center shadow-[3px_3px_0px_rgba(0,0,0,1)]">
                <PartyPopper className="h-8 w-8" />
              </div>
              <p className="mt-4 font-display text-2xl font-black">
                You nailed it!
              </p>
              <p className="text-sm text-muted-foreground">
                Walked there and made it back.
                {completion && completion.xpAward > 0
                  ? ` +${completion.xpAward} XP added to your streak.`
                  : ""}
              </p>
            </div>

            {completion?.coupon ? (
              <CouponReveal
                coupon={completion.coupon}
                copied={copied}
                onCopy={() => {
                  navigator.clipboard
                    .writeText(completion.coupon!.code)
                    .then(() => {
                      setCopied(true);
                      toast.success("Code copied");
                      setTimeout(() => setCopied(false), 1500);
                    })
                    .catch(() => toast.error("Could not copy code"));
                }}
              />
            ) : completing ? (
              <div className="chunky-card p-4 bg-card text-center text-sm text-muted-foreground">
                Finalizing your reward…
              </div>
            ) : null}

            <Button
              variant="outline"
              className="w-full h-12 font-black uppercase tracking-widest"
              onClick={() => nav("/coupons")}
            >
              <Ticket className="h-4 w-4 mr-2" />
              View my coupons
            </Button>
            <Button
              className="w-full h-12 font-black uppercase tracking-widest"
              onClick={() => nav("/challenges")}
            >
              <Flag className="h-4 w-4 mr-2" />
              Find another challenge
            </Button>
          </div>
        )}
      </div>
      <BottomNav />
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-2">
      <span className="text-[11px] font-black uppercase tracking-widest text-muted-foreground">
        {label}
      </span>
      <span className="text-xs font-semibold text-right truncate">{value}</span>
    </div>
  );
}

function ActiveTracker({
  status,
  challenge,
  distToTarget,
  distToOrigin,
  bearing,
}: {
  status: string;
  challenge: GeoChallengeDTO;
  distToTarget: number | null;
  distToOrigin: number | null;
  bearing: number | null;
}) {
  const reached = status === "reached_target";
  const focusDist = reached ? distToOrigin : distToTarget;
  const threshold = reached ? challenge.originRadiusM : challenge.targetRadiusM;
  const title = reached ? "Head back to start" : "Head to the destination";
  const subtitle = reached
    ? `Within ${challenge.originRadiusM} m of start to finish`
    : `Within ${challenge.targetRadiusM} m of the pin`;
  const progress = focusDist !== null ? Math.max(0, Math.min(100, 100 - (focusDist / Math.max(focusDist + 100, 200)) * 100)) : 0;

  return (
    <div
      className={cn(
        "chunky-card p-5",
        reached ? "bg-primary/10" : "bg-accent/10",
      )}
    >
      <div className="flex items-center justify-between">
        <div className="min-w-0">
          <p className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
            {reached ? "Halfway! Now return" : "Walk to destination"}
          </p>
          <p className="font-display font-black text-lg truncate">{title}</p>
          <p className="text-xs text-muted-foreground">{subtitle}</p>
        </div>
        <div className="h-14 w-14 rounded-2xl bg-card border-2 border-foreground grid place-items-center text-2xl font-display font-black shadow-[2px_2px_0px_rgba(0,0,0,1)]">
          {bearing !== null ? bearingToArrow(bearing) : "🚶"}
        </div>
      </div>

      <div className="mt-4">
        <div className="flex items-end justify-between">
          <span className="font-display font-black text-3xl">
            {focusDist === null ? "—" : formatDistance(focusDist)}
          </span>
          <span className="text-xs font-bold text-muted-foreground">
            {focusDist !== null && focusDist <= threshold
              ? "You're in the zone"
              : "Keep going"}
          </span>
        </div>
        <div className="mt-2 h-2 rounded-full bg-muted overflow-hidden border border-foreground/20">
          <div
            className={cn(
              "h-full transition-all",
              reached ? "bg-primary" : "bg-accent",
            )}
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>
    </div>
  );
}

function CouponReveal({
  coupon,
  copied,
  onCopy,
}: {
  coupon: NonNullable<CompleteResponse["coupon"]>;
  copied: boolean;
  onCopy: () => void;
}) {
  return (
    <div className="chunky-card-lg p-5 bg-card">
      <div className="flex items-center gap-2 text-[10px] font-black uppercase tracking-widest text-muted-foreground">
        <Ticket className="h-3.5 w-3.5" />
        Your reward
      </div>
      {coupon.partnerName && (
        <p className="mt-1 text-sm font-bold">
          <span className="mr-1">{coupon.partnerLogo}</span>
          {coupon.partnerName}
        </p>
      )}
      <p className="mt-1 font-display text-xl font-black">{coupon.title}</p>
      <p className="text-sm text-muted-foreground">{coupon.discountText}</p>

      <button
        type="button"
        onClick={onCopy}
        className="mt-4 w-full rounded-2xl border-2 border-dashed border-foreground py-4 text-center bg-gradient-to-br from-accent/20 to-primary/10 active:scale-[0.99] transition-transform"
      >
        <p className="text-[10px] font-black uppercase tracking-widest text-muted-foreground mb-1">
          Show this code
        </p>
        <p className="font-mono font-black text-2xl tracking-[0.3em]">
          {coupon.code}
        </p>
        <p className="mt-2 inline-flex items-center gap-1 text-[10px] font-black uppercase tracking-widest text-muted-foreground">
          <Copy className="h-3 w-3" />
          {copied ? "Copied!" : "Tap to copy"}
        </p>
      </button>

      {coupon.terms && (
        <p className="mt-3 text-[10px] text-muted-foreground leading-relaxed">
          {coupon.terms}
        </p>
      )}
    </div>
  );
}
