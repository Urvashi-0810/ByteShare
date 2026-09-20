import { useState } from "react";
import type { Group } from "@/lib/mockStore";
import { store } from "@/lib/mockStore";
import { Moon, Swords, Check, X, Plus } from "lucide-react";
import { toast } from "sonner";

interface Props {
  group: Group;
  meId: string;
}

export function RedemptionPanel({ group, meId }: Props) {
  const [creating, setCreating] = useState(false);
  const isHostage = (() => {
    const ranked = store.rankedFor(group);
    const me = ranked.find((r) => r.userId === meId);
    if (!me) return false;
    return me.rank === ranked.length && group.vouchedHostage !== meId && ranked.length > 1;
  })();

  const handleSabbath = () => {
    if (isHostage) {
      toast.error("You're in jail. Get a vouch first.");
      return;
    }
    store.toggleSabbath(group.id, meId);
    const done = !group.sabbaths[meId];
    toast(done ? "Screen Sabbath claimed 🧘" : "Sabbath cancelled", {
      description: done ? "Your weighted score is reduced by 30% this week." : undefined,
    });
  };

  const startChallenge = (title: string, emoji: string, hours: number) => {
    if (isHostage) {
      toast.error("You're in jail. Can't start a challenge.");
      return;
    }
    store.startChallenge(group.id, title, emoji, hours);
    toast(`${emoji} ${title} started`, {
      description: `Group has ${hours}h. Win = -20%, fail = +10%.`,
    });
    setCreating(false);
  };

  const sabbathOn = !!group.sabbaths[meId];

  return (
    <div className="space-y-3">
      <div className="flex items-baseline justify-between">
        <h2 className="font-display text-xl font-bold">Redemption</h2>
        <p className="text-xs text-muted-foreground">Earn your way out</p>
      </div>

      <button
        onClick={handleSabbath}
        className={[
          "w-full chunky-card p-4 text-left transition-all flex items-center gap-3",
          sabbathOn ? "bg-accent" : "bg-card hover:-translate-y-0.5",
        ].join(" ")}
      >
        <div className="grid place-items-center h-12 w-12 rounded-full border-2 border-foreground bg-card">
          <Moon className="h-5 w-5" />
        </div>
        <div className="flex-1">
          <p className="font-display font-bold">Screen Sabbath</p>
          <p className="text-xs text-muted-foreground">
            {sabbathOn ? "Active — score × 0.7" : "Claim a no-phone window"}
          </p>
        </div>
        {sabbathOn && (
          <span className="text-xs font-bold uppercase tracking-wider px-2 py-1 rounded-full bg-foreground text-background">
            On
          </span>
        )}
      </button>

      <div className="chunky-card p-4 bg-card">
        <div className="flex items-center gap-3">
          <div className="grid place-items-center h-12 w-12 rounded-full border-2 border-foreground bg-card">
            <Swords className="h-5 w-5" />
          </div>
          <div className="flex-1">
            <p className="font-display font-bold">Detox Challenges</p>
            <p className="text-xs text-muted-foreground">
              Win as a group · trying counts
            </p>
          </div>
          <button
            onClick={() => setCreating((v) => !v)}
            aria-label="New challenge"
            className="grid place-items-center h-9 w-9 rounded-full bg-primary text-primary-foreground hover:opacity-90"
          >
            <Plus className="h-4 w-4" strokeWidth={3} />
          </button>
        </div>

        {creating && (
          <div className="mt-3 grid grid-cols-2 gap-2">
            {PRESETS.map((p) => (
              <button
                key={p.title}
                onClick={() => startChallenge(p.title, p.emoji, p.hours)}
                className="rounded-xl border-2 border-foreground/10 bg-muted/40 p-2 text-left hover:bg-accent/50 transition-colors"
              >
                <p className="text-lg leading-none">{p.emoji}</p>
                <p className="text-xs font-semibold mt-1 leading-tight">{p.title}</p>
                <p className="text-[10px] text-muted-foreground">{p.hours}h</p>
              </button>
            ))}
          </div>
        )}

        {group.challenges.length > 0 && (
          <ul className="mt-3 space-y-2">
            {group.challenges.slice(-3).reverse().map((c) => {
              const remainingMs = c.endsAt - Date.now();
              const ended = remainingMs <= 0;
              const won = c.completed.includes(meId);
              const lost = c.failed.includes(meId);
              return (
                <li
                  key={c.id}
                  className="flex items-center gap-2 rounded-xl bg-muted/50 px-3 py-2"
                >
                  <span className="text-xl">{c.emoji}</span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold truncate">{c.title}</p>
                    <p className="text-[10px] text-muted-foreground font-mono">
                      {ended ? "Ended" : `${Math.ceil(remainingMs / 3600_000)}h left`}
                    </p>
                  </div>
                  {ended ? (
                    won ? <Check className="h-4 w-4 text-champion" /> :
                    lost ? <X className="h-4 w-4 text-destructive" /> : null
                  ) : (
                    <button
                      onClick={() =>
                        store.resolveChallenge(group.id, c.id, [meId])
                      }
                      className="text-[10px] font-bold uppercase tracking-wider px-2 py-1 rounded-full bg-foreground text-background"
                    >
                      I won
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

const PRESETS = [
  { title: "No Instagram 48h", emoji: "📵", hours: 48 },
  { title: "No Reels 24h", emoji: "🎬", hours: 24 },
  { title: "Sleep before 11", emoji: "😴", hours: 24 },
  { title: "Walk meeting", emoji: "🚶", hours: 12 },
];
