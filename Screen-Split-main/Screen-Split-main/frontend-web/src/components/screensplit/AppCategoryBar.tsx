import { CATEGORY_META, type Category } from "@/lib/rank";
import { cn } from "@/lib/utils";

interface Props {
  apps: { appName: string; category: Category; minutes: number }[];
  className?: string;
}

export function AppCategoryBar({ apps, className }: Props) {
  const totals = (Object.keys(CATEGORY_META) as Category[]).map((cat) => {
    const raw = apps.filter((a) => a.category === cat).reduce((s, a) => s + a.minutes, 0);
    return { cat, raw, weighted: raw * CATEGORY_META[cat].weight };
  });
  const totalWeighted = totals.reduce((s, t) => s + t.weighted, 0) || 1;

  return (
    <div className={cn("space-y-3", className)}>
      <div className="flex h-3 w-full overflow-hidden rounded-full border-2 border-foreground bg-muted">
        {totals.map((t) => (
          <div
            key={t.cat}
            className={CATEGORY_META[t.cat].tokenClass}
            style={{ width: `${(t.weighted / totalWeighted) * 100}%` }}
            aria-label={`${CATEGORY_META[t.cat].label}: ${Math.round(t.raw)} min`}
          />
        ))}
      </div>
      <ul className="grid grid-cols-2 gap-2">
        {totals.map((t) => (
          <li
            key={t.cat}
            className="flex items-center gap-2 rounded-xl border-2 border-foreground/10 bg-muted/50 px-3 py-2"
          >
            <span className={cn("h-3 w-3 rounded-full", CATEGORY_META[t.cat].tokenClass)} />
            <div className="min-w-0 flex-1">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground truncate">
                {CATEGORY_META[t.cat].label}
              </p>
              <p className="font-mono text-sm font-bold leading-none">
                {formatMin(t.raw)}
                <span className="text-muted-foreground font-normal">
                  {" "}× {CATEGORY_META[t.cat].weight}
                </span>
              </p>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}

function formatMin(m: number) {
  if (m < 60) return `${Math.round(m)}m`;
  const h = Math.floor(m / 60);
  const mm = Math.round(m % 60);
  return mm ? `${h}h ${mm}m` : `${h}h`;
}
