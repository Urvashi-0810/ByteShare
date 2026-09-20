import { store } from "@/lib/mockStore";
import type { Group } from "@/lib/mockStore";

interface Props {
  group: Group;
}

export function BillBreakdown({ group }: Props) {
  const ranked = store.rankedFor(group);
  const total = ranked.reduce((s, r) => s + r.share, 0);
  const equal = Math.round(group.bill / group.memberIds.length);

  return (
    <div className="chunky-card-lg p-5 bg-gradient-cream">
      <div className="flex items-baseline justify-between mb-1">
        <p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
          This week's bill
        </p>
        <p className="font-mono text-xs text-muted-foreground">
          equal split: ₹{equal}
        </p>
      </div>
      <p className="font-display text-5xl font-bold tracking-tight">
        ₹{group.bill.toLocaleString("en-IN")}
      </p>
      <div className="mt-4 grid grid-cols-3 gap-2 text-center">
        <Stat label="Members" value={group.memberIds.length} />
        <Stat label="Covered" value={`₹${total.toLocaleString("en-IN")}`} />
        <Stat label="Spread" value={`₹${spread(ranked)}`} />
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="rounded-xl bg-card border-2 border-foreground/10 py-2">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        {label}
      </p>
      <p className="font-display font-bold text-base leading-none mt-1">{value}</p>
    </div>
  );
}

function spread(ranked: { share: number }[]) {
  if (!ranked.length) return 0;
  const max = Math.max(...ranked.map((r) => r.share));
  const min = Math.min(...ranked.map((r) => r.share));
  return (max - min).toLocaleString("en-IN");
}
