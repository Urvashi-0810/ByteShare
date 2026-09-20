import { store } from "@/lib/mockStore";
import type { Group } from "@/lib/mockStore";
import type { RankedMember } from "@/lib/rank";

import { cn } from "@/lib/utils";

interface Props {
  group: Group;
  selectedId: string;
  onSelect: (id: string) => void;
}

export function BillSplitList({ group, selectedId, onSelect }: Props) {
  const ranked = store.rankedFor(group);
  const sorted = [...ranked].sort((a, b) => a.rank - b.rank);

  return (
    <div className="chunky-card bg-white border-4 border-foreground rounded-[2rem] shadow-[6px_6px_0px_rgba(0,0,0,1)] overflow-hidden animate-rise">
      {/* Header */}
      <div className="grid grid-cols-[1fr_60px_80px] px-6 py-3 bg-muted/40 border-b-2 border-foreground text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground">
        <span>Member</span>
        <span className="text-center">×</span>
        <span className="text-right">Pays</span>
      </div>

      <ul className="divide-y-2 divide-foreground/5">
        {sorted.map((r) => {
          const user = store.getUser(r.userId);
          if (!user) return null;
          const isSelected = r.userId === selectedId;

          return (
            <li
              key={r.userId}
              onClick={() => onSelect(r.userId)}
              className={cn(
                "grid grid-cols-[1fr_60px_80px] items-center px-6 py-4 cursor-pointer transition-all",
                isSelected ? "bg-accent/10" : "hover:bg-muted/30"
              )}
            >
              <div className="flex items-center gap-4 min-w-0">
                <div className="h-10 w-10 rounded-full border-2 border-foreground bg-card grid place-items-center text-xl shrink-0">
                   {user.avatar}
                </div>
                <span className={cn(
                  "font-display font-bold text-base truncate",
                  isSelected ? "text-foreground" : "text-muted-foreground"
                )}>
                  {user.name}
                  {isSelected && <span className="text-[10px] ml-2 text-accent-foreground bg-accent px-1.5 py-0.5 rounded uppercase font-black">You</span>}
                </span>
              </div>
              <div className="font-mono text-sm text-center font-bold text-muted-foreground">
                {r.multiplier.toFixed(2)}
              </div>
              <div className="text-right font-display font-black text-xl tracking-tight">
                ₹{r.share.toLocaleString("en-IN")}
              </div>
            </li>
          );
        })}
      </ul>

      {/* Footer Total */}
      <div className="grid grid-cols-[1fr_100px] px-6 py-4 bg-muted/20 border-t-4 border-foreground font-display font-black uppercase tracking-widest">
        <span>Total</span>
        <span className="text-right text-2xl">₹{group.bill.toLocaleString("en-IN")}</span>
      </div>
    </div>
  );


}
