import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { ClientResponseError } from "pocketbase";
import type { User } from "@/lib/mockStore";
import { cn } from "@/lib/utils";
import {
  Flame,
  Trophy,
  BarChart3,
  X,
  Mail,
  BrainCircuit,
  UserMinus,
} from "lucide-react";
import { toast } from "sonner";
import { removeFriend } from "@/lib/friendsApi";
import {
  fetchUserScreenTimeline,
  type UserScreenTimeline,
} from "@/lib/screenUsageApi";

function pocketBaseErrorMessage(err: unknown): string {
  if (err instanceof ClientResponseError) {
    const data = err.response as { message?: string } | undefined;
    if (typeof data?.message === "string" && data.message) return data.message;
    return err.message || "Request failed";
  }
  return err instanceof Error ? err.message : "Something went wrong";
}

interface Props {
  user: User;
  onClose: () => void;
  /** Refresh friends list / social data after a successful remove */
  onFriendRemoved?: () => void;
}

export function UserProfileOverlay({
  user,
  onClose,
  onFriendRemoved,
}: Props) {
  const [timeline, setTimeline] = useState<UserScreenTimeline | null>(null);
  const [timelineLoading, setTimelineLoading] = useState(true);
  const [removingFriend, setRemovingFriend] = useState(false);

  const analytics =
    timeline !== null
      ? timeline.analytics
      : timelineLoading
        ? []
        : user.analytics;
  const dayCount = Math.max(analytics.length, 1);
  const weeklyTotal = Math.round(
    analytics.reduce((s, a) => s + a.usageMinutes, 0),
  );
  const avgUsage = Math.round(weeklyTotal / dayCount);
  const topApp = timeline?.topApp;
  const hasUsage = weeklyTotal > 0;
  /** Higher when average raw minutes are lower; derived from synced snapshots only. */
  const focusScore =
    hasUsage && avgUsage > 0
      ? Math.max(
          0,
          Math.min(100, Math.round(100 - (avgUsage / 240) * 100)),
        )
      : null;

  useEffect(() => {
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prevOverflow;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    setTimelineLoading(true);
    setTimeline(null);
    void (async () => {
      try {
        const t = await fetchUserScreenTimeline(user.id);
        if (!cancelled) setTimeline(t);
      } catch {
        if (!cancelled) setTimeline(null);
      } finally {
        if (!cancelled) setTimelineLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [user.id]);

  const onRemoveFriend = async () => {
    const fid = user.friendshipId;
    if (!fid || removingFriend) return;
    if (
      !window.confirm(
        `Remove ${user.name} from your friends? You can send a new request later.`,
      )
    ) {
      return;
    }
    setRemovingFriend(true);
    try {
      await removeFriend(fid);
      toast.success("Friend removed");
      onFriendRemoved?.();
      onClose();
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
    } finally {
      setRemovingFriend(false);
    }
  };

  return createPortal(
    <div
      className="fixed inset-0 z-[200] box-border flex min-h-0 touch-manipulation justify-center px-3 pt-[max(0.75rem,env(safe-area-inset-top))] pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:px-6 sm:py-6"
      onClick={onClose}
      role="presentation"
    >
      <div className="absolute inset-0 bg-foreground/70" aria-hidden />
      <div
        className={cn(
          "relative flex min-h-0 w-full max-w-md flex-col self-stretch overflow-hidden rounded-[2.5rem] border-4 border-foreground bg-background p-6 pb-8 shadow-[0_12px_50px_rgba(0,0,0,0.25)] animate-rise sm:rounded-[3rem]",
        )}
        onClick={(e) => e.stopPropagation()}
      >
        <button 
            onClick={onClose}
            className="absolute top-6 right-6 z-10 grid h-10 w-10 place-items-center rounded-full border-2 border-foreground bg-muted shadow-[2px_2px_0px_rgba(0,0,0,1)] transition-colors hover:bg-accent"
            aria-label="Close"
        >
            <X className="h-5 w-5" />
        </button>
        
        <div className="mx-auto mb-6 h-2 w-16 shrink-0 rounded-full bg-foreground/10" />
        
        <div className="custom-scrollbar min-h-0 flex-1 overflow-y-auto overflow-x-hidden pr-1 -mr-1 [-webkit-overflow-scrolling:touch]">
            <div className="flex flex-col items-center text-center">
                <div className="h-28 w-28 rounded-full border-4 border-foreground bg-card shadow-[4px_4px_0px_rgba(0,0,0,1)] grid place-items-center text-6xl mb-4 relative animate-rise">
                   {user.avatar}
                   <div className="absolute -bottom-2 -right-2 bg-primary text-primary-foreground border-2 border-foreground rounded-full h-10 w-10 grid place-items-center shadow-sm">
                      <Trophy className="h-5 w-5" />
                   </div>
                </div>
                <h2 className="font-display text-4xl font-black">{user.name}</h2>
                <div className="flex items-center gap-2 mt-2 opacity-70">
                    <Mail className="h-3 w-3" />
                    <span className="text-xs font-bold uppercase tracking-wider">{user.email}</span>
                </div>
                
                <div className="flex items-center gap-2 mt-4">
                    <div className="flex items-center gap-1 bg-orange-500 text-white px-4 py-1.5 rounded-full border-2 border-foreground shadow-[4px_4px_0px_rgba(0,0,0,1)] active:translate-x-0.5 active:translate-y-0.5 active:shadow-none transition-all">
                       <Flame className="h-5 w-5 fill-current" />
                       <span className="font-display font-black">{user.streak} Day Streak</span>
                    </div>
                </div>
                <p className="mt-6 text-base font-medium bg-accent/30 text-foreground px-6 py-3 rounded-2xl border-2 border-foreground/10 italic leading-relaxed">
                   "{user.bio}"
                </p>
            </div>

            <div className="mt-10 space-y-8 pb-4">
                {/* Visual Stats Cards */}
                <div className="grid grid-cols-2 gap-4">
                   <div className="chunky-card p-4 bg-primary/10 border-2 border-foreground flex flex-col items-center group hover:bg-primary/20 transition-colors">
                      <p className="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground mb-1">Avg Daily</p>
                      <p className="font-display text-2xl font-black text-primary">
                        {timelineLoading ? "…" : `${avgUsage}m`}
                      </p>
                   </div>
                   <div className="chunky-card p-4 bg-orange-50 border-2 border-foreground flex flex-col items-center group hover:bg-orange-100 transition-colors">
                      <p className="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground mb-1">Focus Score</p>
                      <p className="font-display text-2xl font-black text-orange-600">
                        {timelineLoading ? "…" : focusScore === null ? "—" : focusScore}
                      </p>
                   </div>
                </div>

                {/* Deep Insights */}
                <div className="space-y-4">
                    <h3 className="text-xs font-black uppercase tracking-[0.2em] text-muted-foreground flex items-center gap-2">
                        <BrainCircuit className="h-4 w-4 text-primary" /> Digital Footprint
                    </h3>
                    <div className="grid grid-cols-1 gap-3">
                        <div className="flex items-center justify-between p-4 rounded-2xl bg-card border-2 border-foreground shadow-[2px_2px_0px_rgba(0,0,0,1)]">
                            <div className="flex items-center gap-3">
                                <div className="h-10 w-10 flex items-center justify-center rounded-xl bg-accent border-2 border-foreground">📱</div>
                                <div>
                                    <p className="text-[10px] font-black text-muted-foreground uppercase">Favorite App</p>
                                    <p className="font-display font-bold">
                                      {timelineLoading
                                        ? "…"
                                        : topApp?.appName ?? "No data yet"}
                                    </p>
                                </div>
                            </div>
                            <p className="font-mono font-black text-primary">
                              {timelineLoading
                                ? "—"
                                : topApp
                                  ? `${topApp.minutes}m`
                                  : "—"}
                            </p>
                        </div>
                        <div className="flex items-center justify-between p-4 rounded-2xl bg-card border-2 border-foreground shadow-[2px_2px_0px_rgba(0,0,0,1)]">
                            <div className="flex items-center gap-3">
                                <div className="h-10 w-10 flex items-center justify-center rounded-xl bg-primary/20 border-2 border-foreground">📅</div>
                                <div>
                                    <p className="text-[10px] font-black text-muted-foreground uppercase">Weekly Total</p>
                                    <p className="font-display font-bold">Last 7 days</p>
                                </div>
                            </div>
                            <p className="font-mono font-black text-orange-600">
                              {timelineLoading ? "…" : `${weeklyTotal}m`}
                            </p>
                        </div>
                    </div>
                </div>

                <div>
                   <h3 className="text-xs font-black uppercase tracking-[0.2em] text-muted-foreground flex items-center gap-2 mb-4">
                      <Trophy className="h-4 w-4 text-primary" /> Hall of Fame
                   </h3>
                   <div className="flex flex-wrap gap-2">
                      {user.badges.map(b => (
                         <span key={b} className="px-4 py-2 rounded-xl border-2 border-foreground bg-accent font-display font-bold text-sm shadow-[3px_3px_0px_rgba(0,0,0,1)] hover:translate-y-[-2px] transition-transform">
                            {b}
                         </span>
                      ))}
                   </div>
                </div>

                <div>
                   <h3 className="text-xs font-black uppercase tracking-[0.2em] text-muted-foreground flex items-center gap-2 mb-4">
                      <BarChart3 className="h-4 w-4 text-primary" /> Screen Time Heatmap
                   </h3>
                   <div className="chunky-card p-5 bg-card border-2 border-foreground shadow-[4px_4px_0px_rgba(0,0,0,1)]">
                      <div className="flex items-end justify-between h-32 gap-1.5">
                         {timelineLoading && analytics.length === 0 ? (
                           Array.from({ length: 7 }).map((_, i) => (
                             <div
                               key={i}
                               className="flex-1 flex flex-col items-center gap-2 justify-end"
                             >
                               <div className="w-full rounded-t-xl border-2 border-foreground/20 bg-muted animate-pulse min-h-[40%]" />
                               <span className="text-[10px] font-black text-muted-foreground uppercase">
                                 ·
                               </span>
                             </div>
                           ))
                         ) : analytics.length === 0 ? (
                           <p className="w-full text-center text-sm font-bold text-muted-foreground py-8">
                             No screen time synced yet for this profile.
                           </p>
                         ) : (
                           [...analytics].reverse().map((a, i) => {
                            const height = Math.min((a.usageMinutes / 240) * 100, 100);
                            const isOverLimit = a.usageMinutes > 120;
                            const label =
                              a.date && !Number.isNaN(new Date(`${a.date}T12:00:00Z`).getTime())
                                ? new Date(`${a.date}T12:00:00Z`).toLocaleDateString("en", {
                                    weekday: "narrow",
                                    timeZone: "UTC",
                                  })
                                : "—";
                            return (
                               <div key={`${a.date}-${i}`} className="flex-1 flex flex-col items-center gap-2 group relative">
                                  <div 
                                    className={cn(
                                        "w-full rounded-t-xl transition-all border-b-0 border-x-2 border-t-2 border-foreground shadow-sm group-hover:scale-x-110",
                                        isOverLimit ? "bg-destructive border-destructive" : "bg-primary"
                                    )} 
                                    style={{ height: `${height}%` }}
                                  />
                                  <span className="text-[10px] font-black text-muted-foreground uppercase">
                                      {label}
                                  </span>
                                  <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-3 bg-foreground text-background text-xs font-black px-2 py-1.5 rounded-lg opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none z-20 shadow-lg">
                                      {a.usageMinutes} mins
                                  </div>
                               </div>
                            );
                           })
                         )}
                      </div>
                      <div className="mt-6 pt-6 border-t-2 border-dashed border-foreground/10 flex justify-between items-center text-[10px] font-black uppercase tracking-widest">
                         <div className="flex items-center gap-2">
                            <div className="h-3 w-3 bg-primary rounded-full border-2 border-foreground" />
                            <span>Safe Mode</span>
                         </div>
                         <div className="flex items-center gap-2">
                            <div className="h-3 w-3 bg-destructive rounded-full border-2 border-foreground" />
                            <span>Doom Scroll</span>
                         </div>
                      </div>
                   </div>
                </div>
            </div>
        </div>

        <div className="mt-4 shrink-0 border-t-2 border-dashed border-foreground/10 pt-6 space-y-3">
            {user.friendshipId ? (
              <button
                type="button"
                disabled={removingFriend}
                onClick={() => void onRemoveFriend()}
                className="w-full h-12 rounded-2xl border-2 border-destructive bg-destructive/10 text-destructive font-display font-black text-sm shadow-[3px_3px_0px_rgba(0,0,0,1)] active:translate-x-0.5 active:translate-y-0.5 active:shadow-none transition-all hover:bg-destructive/20 disabled:opacity-50 flex items-center justify-center gap-2"
              >
                <UserMinus className="h-4 w-4 shrink-0" />
                {removingFriend ? "Removing…" : "Remove friend"}
              </button>
            ) : null}
            <button 
                onClick={onClose}
                className="w-full h-16 bg-foreground text-background font-display font-black text-xl rounded-2xl border-4 border-foreground shadow-[8px_8px_0px_rgba(0,0,0,0.2)] active:translate-x-1 active:translate-y-1 active:shadow-none transition-all hover:bg-primary hover:text-primary-foreground"
            >
                Keep Grinding 🚀
            </button>
            <p className="text-center text-[10px] font-bold text-muted-foreground uppercase tracking-widest mt-4">
               Member Since {user.joinDate}
            </p>
        </div>
      </div>

      <style>{`
        .custom-scrollbar::-webkit-scrollbar { width: 4px; }
        .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
        .custom-scrollbar::-webkit-scrollbar-thumb { background: hsl(var(--foreground)/0.1); border-radius: 10px; }
      `}</style>
    </div>,
    document.body,
  );
}
