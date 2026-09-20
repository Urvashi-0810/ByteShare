import { store } from "@/lib/mockStore";
import type { Group } from "@/lib/mockStore";
import { CATEGORY_META } from "@/lib/rank";
import type { RankedMember } from "@/lib/rank";

import { cn } from "@/lib/utils";
import { Info } from "lucide-react";

interface Props {
  userId: string;
  rankData: RankedMember;
}

export function MemberBreakdown({ userId, rankData }: Props) {
  const user = store.getUser(userId);
  const usage = store.usageFor(userId);
  
  if (!user || !usage) return null;

  // Group minutes by category
  const categories = usage.apps.reduce((acc, app) => {
    acc[app.category] = (acc[app.category] || 0) + app.minutes;
    return acc;
  }, {} as Record<string, number>);

  const sortedCats = Object.entries(categories).sort((a, b) => b[1] - a[1]);
  const maxMins = Math.max(...Object.values(categories));

  return (
    <div className="chunky-card p-6 bg-white border-4 border-foreground rounded-[2rem] shadow-[6px_6px_0px_rgba(0,0,0,1)] animate-rise relative overflow-hidden">
      <div className="flex items-center gap-4 mb-6 relative">
        <div className="h-12 w-12 rounded-2xl bg-muted border-2 border-foreground grid place-items-center shrink-0 shadow-[3px_3px_0px_rgba(0,0,0,1)]">
          <Info className="h-6 w-6 text-foreground" />
        </div>
        <div className="flex-1">
          <h3 className="font-display text-2xl font-black tracking-tight flex items-baseline gap-2">
             Why {userId === store.currentUser()?.id ? 'you' : user.name} pay 
             <span className="text-xl font-bold opacity-60">₹{rankData.share.toLocaleString("en-IN")}</span>
          </h3>
          <p className="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground mt-1 flex items-center gap-1.5">
            <span className="h-1 w-1 rounded-full bg-accent" />
            Transparent calculation formula
          </p>
        </div>
      </div>

      <div className="space-y-6 px-1">
        {sortedCats.map(([cat, mins]) => {
          const meta = CATEGORY_META[cat as keyof typeof CATEGORY_META];
          const hours = (mins / 60).toFixed(1);
          const percentage = Math.max((mins / maxMins) * 100, 2);

          return (
            <div key={cat} className="group flex items-center gap-6">
              <div className="w-24 text-[11px] font-black uppercase tracking-[0.2em] text-muted-foreground shrink-0">
                {meta?.label || cat}
              </div>
              <div className="flex-1 h-2.5 bg-muted rounded-full overflow-hidden border border-foreground/5 relative">
                <div 
                  className={cn(
                    "h-full absolute left-0 top-0 transition-all duration-1000 ease-out rounded-full", 
                    meta?.tokenClass || "bg-primary"
                  )} 
                  style={{ width: `${percentage}%` }}
                />
              </div>
              <div className="w-20 text-right shrink-0">
                <p className="text-xs font-black font-mono tracking-tight leading-none">{hours}h</p>
                <p className="text-[9px] font-black text-muted-foreground mt-1 uppercase opacity-60">×{meta?.weight.toFixed(1)}</p>
              </div>
            </div>
          );
        })}
      </div>

      <div className="mt-8 pt-6 border-t-2 border-dashed border-foreground/10 grid grid-cols-3 gap-3">
        <div className="bg-muted/40 p-3 rounded-2xl border-2 border-foreground/5 text-center">
          <p className="text-[9px] font-black uppercase tracking-widest text-muted-foreground mb-1 leading-none">Raw Time</p>
          <p className="font-display font-black text-base">{(rankData.raw / 60).toFixed(1)}h</p>
        </div>
        <div className="bg-muted/40 p-3 rounded-2xl border-2 border-foreground/5 text-center">
          <p className="text-[9px] font-black uppercase tracking-widest text-muted-foreground mb-1 leading-none">Weighted</p>
          <p className="font-display font-black text-base">{rankData.weighted.toFixed(1)}</p>
        </div>
        <div className="bg-accent p-3 rounded-2xl border-2 border-foreground text-center shadow-[3px_3px_0px_rgba(0,0,0,1)]">
          <p className="text-[9px] font-black uppercase tracking-widest text-accent-foreground/60 mb-1 leading-none">Mult.</p>
          <p className="font-display font-black text-base tracking-tighter">×{rankData.multiplier.toFixed(2)}</p>
        </div>
      </div>
    </div>
  );
}


