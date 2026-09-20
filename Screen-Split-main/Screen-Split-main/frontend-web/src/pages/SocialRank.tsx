import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ClientResponseError } from "pocketbase";
import { store, type Group, type User } from "@/lib/mockStore";
import { useStore } from "@/hooks/useStore";
import { PageHeader } from "@/components/screensplit/PageHeader";
import { BottomNav } from "@/components/screensplit/BottomNav";
import { Leaderboard } from "@/components/screensplit/Leaderboard";
import { AppCategoryBar } from "@/components/screensplit/AppCategoryBar";
import { ScarletScoreCard } from "@/components/screensplit/ScarletScoreCard";
import { weightedMinutes } from "@/lib/rank";
import { fetchFriends, friendRecordToUser } from "@/lib/friendsApi";
import {
  fetchSocialUsage,
  pbAuthRecordToUser,
} from "@/lib/screenUsageApi";
import { TrendingUp, Zap } from "lucide-react";
import { toast } from "sonner";

function pocketBaseErrorMessage(err: unknown): string {
  if (err instanceof ClientResponseError) {
    const data = err.response as { message?: string } | undefined;
    if (typeof data?.message === "string" && data.message) return data.message;
    return err.message || "Request failed";
  }
  return err instanceof Error ? err.message : "Something went wrong";
}

function buildSocialGroup(self: User, friendUsers: User[]): Group {
  const ids = new Set<string>([self.id]);
  friendUsers.forEach((u) => ids.add(u.id));
  return {
    id: "social-group",
    name: "Social Rank",
    emoji: "🏆",
    inviteCode: "FRIENDS",
    bill: 0,
    memberIds: Array.from(ids),
    challenges: [],
    sabbaths: {},
  };
}

export default function SocialRank() {
  const nav = useNavigate();
  const me = useStore(() => store.currentUser());
  const [socialGroup, setSocialGroup] = useState<Group | null>(null);
  const [, bump] = useState(0);

  useEffect(() => {
    const unsub = store.subscribe(() => bump((n) => n + 1));
    return unsub;
  }, []);

  const loadSocial = useCallback(async () => {
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
      const mergedSelf = store.getUser(self.id) ?? self;
      setSocialGroup(buildSocialGroup(mergedSelf, friendUsers));
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
      const self = pbAuthRecordToUser() ?? me;
      const local = store.getSocialGroup();
      if (local) {
        setSocialGroup(local);
      } else {
        setSocialGroup(buildSocialGroup(self, []));
      }
    }
  }, [me]);

  useEffect(() => {
    void loadSocial();
  }, [loadSocial]);

  const highestPayerId =
    !socialGroup?.memberIds.length
      ? me?.id ?? ""
      : [...socialGroup.memberIds].sort((a, b) => {
          return (
            weightedMinutes(store.usageFor(b)) - weightedMinutes(store.usageFor(a))
          );
        })[0];

  if (!me) {
    nav("/welcome", { replace: true });
    return null;
  }

  if (!socialGroup) {
    return (
      <div className="min-h-screen grid place-items-center pb-28">
        <p className="font-display font-bold text-muted-foreground">Loading…</p>
        <BottomNav />
      </div>
    );
  }

  const myUsage = store.usageFor(me.id);

  const onPokeJailed = (uid: string) => {
    const u = store.getUser(uid);
    toast(`💀 Roast sent to ${u?.name}`, {
      description: '"put the phone down 💀"',
      icon: <Zap className="h-4 w-4" />,
    });
  };

  const onVouch = (uid: string) => {
    toast(`Vouched for ${store.getUser(uid)?.name}`, {
      description: "Moral support is always good! 🤝",
    });
  };

  return (
    <div className="min-h-screen pb-28">
      <div className="mx-auto max-w-md px-5">
        <PageHeader
          title="Social Rank"
          subtitle="Battle your friends for the lowest time"
          right={
            <span className="grid place-items-center h-9 w-9 rounded-full bg-primary/20 border-2 border-foreground text-primary">
              <TrendingUp className="h-4 w-4" />
            </span>
          }
        />

        {myUsage && myUsage.apps.length > 0 ? (
          <section className="mt-4 chunky-card p-5 bg-card">
            <h2 className="font-display text-xl font-bold mb-4">Your usage</h2>
            <AppCategoryBar apps={myUsage.apps} />
            <div className="mt-4 pt-4 border-t-2 border-dashed border-foreground/10 flex justify-between">
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
                  Total Raw
                </p>
                <p className="font-display text-lg font-bold">
                  {myUsage.apps
                    .reduce((s, a) => s + a.minutes, 0)
                    .toFixed(2)}
                  m
                </p>
              </div>
              <div className="text-right">
                <p className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
                  Weighted Score
                </p>
                <p className="font-display text-lg font-bold text-primary">
                  {Math.round(
                    myUsage.apps.reduce(
                      (s, a) =>
                        s +
                        a.minutes *
                          (a.category === "social"
                            ? 2
                            : a.category === "stream"
                              ? 1.5
                              : a.category === "productive"
                                ? 0.5
                                : 1),
                      0,
                    ),
                  )}
                  m
                </p>
              </div>
            </div>
          </section>
        ) : (
          <section className="mt-4 chunky-card p-5 bg-card border-2 border-dashed border-foreground/20">
            <p className="font-display font-bold text-sm text-muted-foreground text-center">
              No usage snapshot for today yet. Open the ScreenSplit Android app, sign in
              here in the web view, then use the cloud upload button to publish today&apos;s
              screen time to PocketBase.
            </p>
          </section>
        )}

        <section className="mt-8">
          <div className="flex items-baseline justify-between mb-4">
            <h2 className="font-display text-2xl font-black">Elite Leaderboard</h2>
            <p className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
              Lowest mins wins
            </p>
          </div>
          <Leaderboard
            group={socialGroup}
            meId={me.id}
            onPokeJailed={onPokeJailed}
            onVouch={onVouch}
            onFriendRemoved={() => {
              void loadSocial();
            }}
          />
        </section>

        {socialGroup.memberIds.length > 0 && (
          <section className="mt-8 mb-6">
            <ScarletScoreCard userId={highestPayerId} />
          </section>
        )}

        <section className="mt-8 chunky-card p-4 bg-accent/20 border-2 border-dashed border-foreground/30 text-center opacity-60">
          <p className="font-display font-medium text-sm italic">
            &quot;Digital wellness is better with friends. Keep the streaks alive! 🧘‍♂️&quot;
          </p>
        </section>
      </div>
      <BottomNav />
    </div>
  );
}
