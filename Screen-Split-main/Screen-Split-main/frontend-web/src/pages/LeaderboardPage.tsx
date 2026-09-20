import { useNavigate } from "react-router-dom";
import { store } from "@/lib/mockStore";
import { useStore } from "@/hooks/useStore";
import { PageHeader } from "@/components/screensplit/PageHeader";
import { BottomNav } from "@/components/screensplit/BottomNav";
import { Leaderboard } from "@/components/screensplit/Leaderboard";
import { Trophy } from "lucide-react";
import { toast } from "sonner";

export default function LeaderboardPage() {
  const nav = useNavigate();
  const me = useStore(() => store.currentUser());
  const groups = useStore(() => store.groupsForCurrentUser());

  if (!me) { nav("/welcome", { replace: true }); return null; }

  return (
    <div className="min-h-screen pb-28">
      <div className="mx-auto max-w-md px-5">
        <PageHeader
          title="Ranks"
          subtitle="All your crews this week"
          right={
            <span className="grid place-items-center h-9 w-9 rounded-full bg-accent border-2 border-foreground">
              <Trophy className="h-4 w-4" />
            </span>
          }
        />

        {groups.length === 0 && (
          <div className="chunky-card p-6 bg-card text-center">
            <p className="font-display font-bold">No crews to rank yet</p>
            <button onClick={() => nav("/groups")} className="mt-2 underline text-sm">
              Create one
            </button>
          </div>
        )}

        <div className="space-y-6">
          {groups.map((g) => (
            <section key={g.id}>
              <button
                onClick={() => nav(`/group/${g.id}`)}
                className="flex items-baseline justify-between w-full mb-2"
              >
                <h2 className="font-display text-lg font-bold flex items-center gap-2">
                  <span>{g.emoji}</span>{g.name}
                </h2>
                <span className="text-xs text-muted-foreground">₹{g.bill.toLocaleString("en-IN")}</span>
              </button>
              <Leaderboard
                group={g}
                meId={me.id}
                onPokeJailed={(uid) => {
                  const u = store.getUser(uid);
                  toast(`💀 Roast sent to ${u?.name}`);
                }}
                onVouch={(uid) => store.vouchHostage(g.id, uid)}
              />
            </section>
          ))}
        </div>
      </div>
      <BottomNav />
    </div>
  );
}
