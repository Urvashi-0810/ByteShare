import { useCallback, useEffect, useState, type ComponentType } from "react";
import { useNavigate } from "react-router-dom";
import { ClientResponseError } from "pocketbase";
import { store, type User } from "@/lib/mockStore";
import { useStore } from "@/hooks/useStore";
import { PageHeader } from "@/components/screensplit/PageHeader";
import { BottomNav } from "@/components/screensplit/BottomNav";
import { fetchFriends, friendRecordToUser } from "@/lib/friendsApi";
import {
  fetchSocialUsage,
  pbAuthRecordToUser,
} from "@/lib/screenUsageApi";
import { Zap, Target, Users, ArrowRight, Sparkles, MapPin } from "lucide-react";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import { GeoChallengeCard } from "@/components/geo/GeoChallengeCard";
import { listGeoChallenges, type GeoChallengeDTO } from "@/lib/geoApi";
import { Button } from "@/components/ui/button";

function pocketBaseErrorMessage(err: unknown): string {
  if (err instanceof ClientResponseError) {
    const data = err.response as { message?: string } | undefined;
    if (typeof data?.message === "string" && data.message) return data.message;
    return err.message || "Request failed";
  }
  return err instanceof Error ? err.message : "Something went wrong";
}

export default function Challenges() {
  const nav = useNavigate();
  const me = useStore(() => store.currentUser());
  const challenges = useStore(() => store.getGlobalChallenges());
  const [, bump] = useState(0);
  const [geoChallenges, setGeoChallenges] = useState<GeoChallengeDTO[]>([]);
  const [geoLoading, setGeoLoading] = useState(false);
  const [geoError, setGeoError] = useState<string | null>(null);
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [locationAsked, setLocationAsked] = useState(false);

  useEffect(() => {
    const unsub = store.subscribe(() => bump((n) => n + 1));
    return unsub;
  }, []);

  const loadGeoChallenges = useCallback(
    async (latLng?: { lat: number; lng: number }) => {
      setGeoLoading(true);
      setGeoError(null);
      try {
        const items = await listGeoChallenges(latLng);
        items.sort((a, b) => {
          if (a.distanceM !== undefined && b.distanceM !== undefined) {
            return a.distanceM - b.distanceM;
          }
          if (a.isPromoted !== b.isPromoted) return a.isPromoted ? -1 : 1;
          return b.created.localeCompare(a.created);
        });
        setGeoChallenges(items);
      } catch (err) {
        setGeoError(pocketBaseErrorMessage(err));
      } finally {
        setGeoLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    void loadGeoChallenges();
  }, [loadGeoChallenges]);

  const requestLocation = useCallback(() => {
    if (!("geolocation" in navigator)) {
      setGeoError("Geolocation not supported in this browser");
      return;
    }
    setLocationAsked(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const next = {
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
        };
        setCoords(next);
        void loadGeoChallenges(next);
      },
      (err) => {
        setGeoError(err.message || "Location unavailable");
      },
      { enableHighAccuracy: true, maximumAge: 60000, timeout: 15000 },
    );
  }, [loadGeoChallenges]);

  const loadUsageForTasks = useCallback(async () => {
    if (!me) return;
    try {
      const self = pbAuthRecordToUser() ?? me;
      const [edges, social] = await Promise.all([
        fetchFriends(),
        fetchSocialUsage(),
      ]);
      const friendUsers = edges.map(friendRecordToUser);
      const byId = new Map<string, User>();
      byId.set(self.id, { ...self, friendIds: me.friendIds ?? [] });
      for (const u of friendUsers) {
        byId.set(u.id, u);
      }
      store.mergeRemoteUsers([...byId.values()]);
      for (const row of social.items) {
        store.applyUsageSnapshot(row.userId, row.apps);
      }
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
    }
    store.syncGlobalChallengeProgress();
  }, [me]);

  useEffect(() => {
    void loadUsageForTasks();
  }, [loadUsageForTasks]);

  if (!me) {
    nav("/welcome", { replace: true });
    return null;
  }

  const live = challenges.filter((c) => !c.comingSoon);
  const queued = challenges.filter((c) => c.comingSoon);
  const individual = live.filter((c) => c.type === "individual");
  const group = live.filter((c) => c.type === "group");
  const promotedGeo = geoChallenges.filter((c) => c.isPromoted);
  const personalGeo = geoChallenges.filter((c) => !c.isPromoted);

  return (
    <div className="min-h-screen pb-32">
      <div className="mx-auto max-w-md px-5">
        <PageHeader
          title="Challenges"
          subtitle="Complete quests to lower your bill"
          right={
            <div className="h-9 w-9 rounded-full bg-accent border-2 border-foreground grid place-items-center">
              <Zap className="h-5 w-5 fill-current" />
            </div>
          }
        />

        {/* Promoted Geo Section */}
        {(promotedGeo.length > 0 || (!geoLoading && geoChallenges.length === 0 && geoError === null)) && (
          <section className="mt-6">
            <SectionHeader
              icon={Sparkles}
              title="Promoted near you"
              subtitle="Walk, earn, get coupons"
            />
            {!coords && !locationAsked && (
              <div className="chunky-card p-4 mb-4 bg-card flex items-start gap-3">
                <MapPin className="h-5 w-5 mt-0.5 shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="font-bold text-sm">Share your location</p>
                  <p className="text-xs text-muted-foreground">
                    We'll sort by distance so you see the closest partner spots first.
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={requestLocation}
                  className="shrink-0 font-black uppercase"
                >
                  Use location
                </Button>
              </div>
            )}
            <div className="space-y-4">
              {promotedGeo.map((c) => (
                <GeoChallengeCard
                  key={c.id}
                  challenge={c}
                  onClick={() => nav(`/walk/${c.id}`)}
                />
              ))}
              {promotedGeo.length === 0 && !geoLoading && !geoError && (
                <div className="chunky-card p-4 bg-card text-center text-xs text-muted-foreground">
                  No promoted walks yet. Check back soon!
                </div>
              )}
              {geoError && (
                <div className="chunky-card p-4 bg-red-50 border-red-200 text-xs text-red-700">
                  {geoError}
                </div>
              )}
            </div>
          </section>
        )}

        {/* Personal Walks Section */}
        {personalGeo.length > 0 && (
          <section className="mt-10">
            <SectionHeader
              icon={MapPin}
              title="Walk challenges"
              subtitle="Move your body, earn XP"
            />
            <div className="space-y-4">
              {personalGeo.map((c) => (
                <GeoChallengeCard
                  key={c.id}
                  challenge={c}
                  onClick={() => nav(`/walk/${c.id}`)}
                />
              ))}
            </div>
          </section>
        )}

        {/* Individual Section */}
        <section className="mt-10">
          <SectionHeader
            icon={Target}
            title="Screen Sabbath"
            subtitle="Individual pledges"
          />
          <div className="space-y-4">
            {individual.map((c) => (
              <ChallengeCard
                key={c.id}
                challenge={c}
                onClick={() => nav(`/challenge/${c.id}`)}
              />
            ))}
          </div>
        </section>

        {/* Group Section */}
        <section className="mt-10">
          <SectionHeader
            icon={Users}
            title="Group Pods"
            subtitle="Scale together"
          />
          <div className="space-y-4">
            {group.map((c) => (
              <ChallengeCard
                key={c.id}
                challenge={c}
                onClick={() => nav(`/challenge/${c.id}`)}
              />
            ))}
          </div>
        </section>

        {/* Coming soon */}
        {queued.length > 0 && (
          <section className="mt-10">
            <SectionHeader
              icon={Sparkles}
              title="Coming soon"
              subtitle="Needs sensors, history, or richer data"
            />
            <div className="space-y-4">
              {queued.map((c) => (
                <ChallengeCard
                  key={c.id}
                  challenge={c}
                  comingSoon
                  onClick={() => nav(`/challenge/${c.id}`)}
                />
              ))}
            </div>
          </section>
        )}
      </div>
      <BottomNav />
    </div>
  );
}

function SectionHeader({
  icon: Icon,
  title,
  subtitle,
}: {
  icon: ComponentType<{ className?: string }>;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="flex items-center gap-3 mb-4 px-1">
      <div className="h-10 w-10 rounded-xl bg-card border-2 border-foreground flex items-center justify-center shadow-[2px_2px_0px_rgba(0,0,0,1)]">
        <Icon className="h-5 w-5" />
      </div>
      <div>
        <h2 className="font-display font-black text-lg leading-none">{title}</h2>
        <p className="text-[10px] font-black uppercase tracking-widest text-muted-foreground mt-1">
          {subtitle}
        </p>
      </div>
    </div>
  );
}

function ChallengeCard({
  challenge,
  onClick,
  comingSoon = false,
}: {
  challenge: {
    id: string;
    title: string;
    emoji: string;
    description: string;
    reward: string;
    type: string;
    participants: string[];
  };
  onClick: () => void;
  comingSoon?: boolean;
}) {
  const me = store.currentUser();
  const isJoined = me ? challenge.participants.includes(me.id) : false;

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onClick();
        }
      }}
      className={cn(
        "chunky-card p-5 cursor-pointer group transition-all active:scale-[0.98]",
        isJoined ? "bg-accent/5" : "bg-card",
        comingSoon && "opacity-85 border-dashed",
      )}
    >
      <div className="flex items-start gap-4">
        <div className="h-12 w-12 rounded-2xl bg-muted border-2 border-foreground flex items-center justify-center text-2xl shrink-0 shadow-[2px_2px_0px_rgba(0,0,0,1)] group-hover:scale-110 transition-transform">
          {challenge.emoji}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="font-display font-black text-lg leading-tight truncate">
              {challenge.title}
            </h3>
            {comingSoon && (
              <span className="bg-muted text-muted-foreground text-[8px] font-black uppercase px-1.5 py-0.5 rounded leading-none">
                Soon
              </span>
            )}
            {isJoined && !comingSoon && (
              <span className="bg-primary text-primary-foreground text-[8px] font-black uppercase px-1.5 py-0.5 rounded leading-none">
                Joined
              </span>
            )}
          </div>
          <p className="text-xs text-muted-foreground font-medium mt-1 line-clamp-1">
            {challenge.description}
          </p>

          <div className="mt-3 flex items-center gap-3">
            <div className="inline-flex items-center gap-1.5 px-2 py-1 rounded-lg bg-teal-50 border border-teal-200 text-teal-700 text-[10px] font-black uppercase">
              {challenge.reward}
            </div>
            {challenge.type === "group" && (
              <div className="inline-flex items-center gap-1 text-[10px] font-bold text-muted-foreground">
                <Users className="h-3 w-3" /> {challenge.participants.length}{" "}
                joined
              </div>
            )}
          </div>
        </div>
        <div className="h-10 w-10 rounded-full border-2 border-foreground grid place-items-center self-center group-hover:bg-primary group-hover:text-primary-foreground transition-colors">
          <ArrowRight className="h-4 w-4" />
        </div>
      </div>
    </div>
  );
}
