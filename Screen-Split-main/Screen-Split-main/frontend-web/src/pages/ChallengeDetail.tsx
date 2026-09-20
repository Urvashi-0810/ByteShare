import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { ClientResponseError } from "pocketbase";
import { store, type Challenge, type User } from "@/lib/mockStore";
import { useStore } from "@/hooks/useStore";
import { BottomNav } from "@/components/screensplit/BottomNav";
import {
  ChevronLeft,
  Info,
  CheckCircle2,
  AlertCircle,
  Clock,
} from "lucide-react";
import { toast } from "sonner";
import { fetchFriends, friendRecordToUser } from "@/lib/friendsApi";
import {
  fetchSocialUsage,
  pbAuthRecordToUser,
} from "@/lib/screenUsageApi";
import { allParticipantsPass } from "@/lib/globalChallengeEval";
import { rawMinutes } from "@/lib/rank";

function pocketBaseErrorMessage(err: unknown): string {
  if (err instanceof ClientResponseError) {
    const data = err.response as { message?: string } | undefined;
    if (typeof data?.message === "string" && data.message) return data.message;
    return err.message || "Request failed";
  }
  return err instanceof Error ? err.message : "Something went wrong";
}

function participantSubline(ch: Challenge, userId: string): string {
  const u = store.usageFor(userId);
  if (!u?.apps.length) {
    return "No usage snapshot yet — sync from Usage admin or the app";
  }
  const raw = rawMinutes(u);
  switch (ch.id) {
    case "deep-work": {
      const nonProd = u.apps.filter((a) => a.category !== "productive");
      const prodMins = u.apps
        .filter((a) => a.category === "productive")
        .reduce((s, a) => s + a.minutes, 0);
      if (nonProd.length)
        return `${nonProd.length} non-productive app(s) in snapshot · ${prodMins}m productive`;
      return `${prodMins}m productive-only (need ≥120m)`;
    }
    case "no-scroll-sunday": {
      const social = u.apps
        .filter((a) => a.category === "social")
        .reduce((s, a) => s + a.minutes, 0);
      return `${social}m social · ${raw}m raw total`;
    }
    case "category-cleanse": {
      const stream = u.apps
        .filter((a) => a.category === "stream")
        .reduce((s, a) => s + a.minutes, 0);
      return `${stream}m streaming · ${raw}m raw total`;
    }
    default:
      return `${raw}m raw in snapshot`;
  }
}

export default function ChallengeDetail() {
  const { id } = useParams();
  const nav = useNavigate();
  const me = useStore(() => store.currentUser());
  const [storeRev, setStoreRev] = useState(0);

  useEffect(() => {
    const unsub = store.subscribe(() => setStoreRev((n) => n + 1));
    return unsub;
  }, []);

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

  const ch = useMemo(
    () => (id ? store.getChallenge(id) : undefined),
    [id, storeRev],
  );

  if (!me || !ch) {
    return (
      <div className="p-10 text-center">
        <p>Challenge not found</p>
        <button
          type="button"
          onClick={() => nav("/challenges")}
          className="mt-4 text-primary font-bold"
        >
          Back to tasks
        </button>
      </div>
    );
  }

  const isJoined = ch.participants.includes(me.id);
  const comingSoon = !!ch.comingSoon;

  const onJoin = () => {
    if (comingSoon) return;
    store.joinChallenge(ch.id, me.id);
    toast.success("Joined Challenge! 🚀", {
      description: "You are now being tracked. Good luck!",
    });
  };

  const podComplete =
    ch.type === "group" &&
    ch.participants.length > 0 &&
    allParticipantsPass(ch, ch.completed);

  const howItWorks = comingSoon
    ? "This quest needs time-of-day usage, device inactivity, week-over-week totals, or pairing flows we have not wired on web yet. It will unlock when the mobile + backend pipeline exposes that data."
    : ch.id === "deep-work"
      ? "We read your latest daily usage snapshot (same source as Social Rank). You pass when every logged app is in the Productive category and productive time adds up to at least 120 minutes. This approximates a 2h deep-work block until we have live per-session data."
      : ch.id === "no-scroll-sunday" || ch.id === "category-cleanse"
        ? "Everyone who joined must have a synced snapshot with zero minutes in the target category (social or streaming). Friends need usage in the store too — open Social Rank or Usage admin after syncing."
        : "Complete the requirement using your latest synced usage data.";

  return (
    <div className="min-h-screen pb-32">
      <div className="mx-auto max-w-md px-5">
        <div className="pt-6">
          <button
            type="button"
            onClick={() => nav("/challenges")}
            className="h-10 w-10 rounded-full border-2 border-foreground flex items-center justify-center bg-card shadow-[2px_2px_0px_rgba(0,0,0,1)] hover:bg-muted active:translate-y-0.5 transition-all"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        </div>

        <div className="mt-8 text-center">
          <div className="h-24 w-24 rounded-[2rem] bg-card border-4 border-foreground mx-auto flex items-center justify-center text-5xl shadow-[6px_6px_0px_rgba(0,0,0,1)] animate-rise">
            {ch.emoji}
          </div>
          <h1 className="mt-6 font-display font-black text-3xl leading-tight">
            {ch.title}
          </h1>
          <div className="mt-2 inline-flex items-center gap-2 text-xs font-bold text-muted-foreground uppercase tracking-widest flex-wrap justify-center">
            <span className="px-2 py-0.5 rounded bg-foreground text-background">
              {ch.category}
            </span>
            <span>•</span>
            {comingSoon ? (
              <span>Coming soon</span>
            ) : (
              <span>
                Ends{" "}
                {new Date(ch.endsAt).toLocaleDateString(undefined, {
                  month: "short",
                  day: "numeric",
                })}
              </span>
            )}
          </div>
        </div>

        {comingSoon && (
          <div className="mt-6 chunky-card p-4 bg-muted/40 border-2 border-dashed border-foreground/25 text-center">
            <p className="font-display font-bold text-sm">
              Not available to complete yet
            </p>
            <p className="text-[11px] text-muted-foreground mt-1 leading-relaxed">
              We will turn this on once the app reports the signals this quest
              needs.
            </p>
          </div>
        )}

        {!comingSoon && ch.type === "group" && podComplete && (
          <div className="mt-6 chunky-card p-4 bg-teal-50 border-2 border-teal-200 text-center">
            <p className="font-display font-black text-sm text-teal-800">
              Pod requirement met — badges awarded
            </p>
          </div>
        )}

        {/* Info Block */}
        <section className="mt-10 space-y-4">
          <div className="chunky-card p-5 bg-card border-primary/20">
            <div className="flex items-center gap-2 mb-3">
              <Info className="h-4 w-4 text-primary" />
              <h3 className="font-display font-black text-sm uppercase tracking-wider">
                Instructions
              </h3>
            </div>
            <p className="text-sm font-medium leading-relaxed italic text-foreground/80">
              &quot;{ch.description}&quot;
            </p>
            <div className="mt-4 pt-4 border-t-2 border-dashed border-foreground/10 space-y-3">
              <div className="flex justify-between items-center text-xs gap-2">
                <span className="font-black uppercase text-muted-foreground tracking-widest shrink-0">
                  Requirement
                </span>
                <span className="font-bold flex items-center gap-1 text-right">
                  <Clock className="h-3 w-3 shrink-0" /> {ch.requirement}
                </span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="font-black uppercase text-muted-foreground tracking-widest">
                  Reward
                </span>
                <span className="font-display font-bold text-teal-600 bg-teal-50 px-2 py-0.5 rounded border border-teal-200">
                  {ch.reward}
                </span>
              </div>
            </div>
          </div>

          {comingSoon ? (
            <div className="w-full h-16 rounded-[1.2rem] border-2 border-dashed border-foreground/30 flex items-center justify-center font-display font-black text-muted-foreground">
              Join disabled
            </div>
          ) : !isJoined ? (
            <button
              type="button"
              onClick={onJoin}
              className="w-full h-16 rounded-[1.2rem] bg-primary text-primary-foreground font-display font-black text-xl shadow-[4px_4px_0px_rgba(0,0,0,1)] active:shadow-none active:translate-y-1 transition-all"
            >
              Accept Challenge
            </button>
          ) : ch.completed.includes(me.id) ? (
            <div className="w-full h-16 rounded-[1.2rem] bg-teal-500 text-white flex items-center justify-center gap-2 font-display font-black text-lg shadow-[4px_4px_0px_rgba(0,0,0,1)]">
              <CheckCircle2 className="h-6 w-6" /> You passed
            </div>
          ) : ch.failed.includes(me.id) ? (
            <div className="w-full h-16 rounded-[1.2rem] bg-red-500 text-white flex items-center justify-center gap-2 font-display font-black text-lg shadow-[4px_4px_0px_rgba(0,0,0,1)]">
              <AlertCircle className="h-6 w-6" /> Needs another try
            </div>
          ) : (
            <div className="w-full h-16 rounded-[1.2rem] bg-teal-500/90 text-white flex items-center justify-center gap-2 font-display font-black text-lg shadow-[4px_4px_0px_rgba(0,0,0,1)]">
              <Clock className="h-6 w-6" /> In progress
            </div>
          )}
        </section>

        {/* Participants */}
        <section className="mt-12">
          <div className="flex items-baseline justify-between mb-4 gap-2">
            <h2 className="font-display font-black text-xl tracking-tight">
              {ch.type === "group" ? "Joined pod" : "Participants"}
            </h2>
            <Link
              to="/screen-usage-admin"
              className="text-[10px] font-black uppercase text-primary tracking-widest shrink-0"
            >
              Usage admin
            </Link>
          </div>

          {ch.participants.length === 0 ? (
            <p className="text-sm text-muted-foreground font-medium text-center chunky-card p-6">
              No one has joined yet. Be the first.
            </p>
          ) : (
            <div className="space-y-3">
              {ch.participants.map((uid) => {
                const u = store.getUser(uid);
                const name = u?.name ?? uid.slice(0, 8);
                const avatar = u?.avatar ?? "👤";
                let status: "active" | "success" | "failed" = "active";
                if (ch.completed.includes(uid)) status = "success";
                else if (ch.failed.includes(uid)) status = "failed";
                return (
                  <ParticipantRow
                    key={uid}
                    name={name}
                    status={status}
                    sub={participantSubline(ch, uid)}
                    avatar={avatar}
                  />
                );
              })}
            </div>
          )}
        </section>

        <section className="mt-12 chunky-card p-5 bg-muted/30 border-2 border-foreground/10 text-center">
          <div className="h-10 w-10 rounded-full bg-white border-2 border-foreground grid place-items-center mx-auto mb-3">
            <CheckCircle2 className="h-5 w-5 text-teal-600" />
          </div>
          <h4 className="font-display font-black text-sm uppercase tracking-wider mb-1">
            How it works
          </h4>
          <p className="text-[11px] font-medium text-muted-foreground leading-relaxed">
            {howItWorks}
          </p>
        </section>
      </div>
      <BottomNav />
    </div>
  );
}

function ParticipantRow({
  name,
  status,
  sub,
  avatar,
}: {
  name: string;
  status: "active" | "success" | "failed";
  sub: string;
  avatar: string;
}) {
  return (
    <div className="flex items-center gap-3 p-3 rounded-2xl border-2 border-foreground/5 bg-card">
      <div className="h-10 w-10 rounded-xl bg-muted border-2 border-foreground flex items-center justify-center text-xl shrink-0">
        {avatar}
      </div>
      <div className="flex-1 min-w-0">
        <p className="font-display font-bold text-sm leading-none">{name}</p>
        <p className="text-[10px] font-medium text-muted-foreground mt-1 break-words">
          {sub}
        </p>
      </div>
      <div>
        {status === "active" && (
          <div className="h-6 px-2 rounded-full bg-primary/20 text-primary border border-primary/30 flex items-center gap-1 text-[9px] font-black uppercase">
            <Clock className="h-3 w-3" /> Live
          </div>
        )}
        {status === "success" && (
          <div className="h-6 px-2 rounded-full bg-teal-100 text-teal-700 border border-teal-200 flex items-center gap-1 text-[9px] font-black uppercase">
            <CheckCircle2 className="h-3 w-3" /> Pass
          </div>
        )}
        {status === "failed" && (
          <div className="h-6 px-2 rounded-full bg-red-100 text-red-600 border border-red-200 flex items-center gap-1 text-[9px] font-black uppercase">
            <AlertCircle className="h-3 w-3" /> Fail
          </div>
        )}
      </div>
    </div>
  );
}
