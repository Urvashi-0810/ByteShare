// Pure helpers for ScreenSplit ranking + bill multipliers.
// No React, no storage — easy to unit-test and swap behind a server later.



export type Category = "social" | "stream" | "neutral" | "productive";

export const CATEGORY_META: Record<
  Category,
  { label: string; weight: number; tokenClass: string; emoji: string }
> = {
  social: { label: "Social Media", weight: 2.0, tokenClass: "bg-cat-social", emoji: "📱" },
  stream: { label: "Streaming", weight: 1.5, tokenClass: "bg-cat-stream", emoji: "📺" },
  neutral: { label: "Neutral", weight: 1.0, tokenClass: "bg-cat-neutral", emoji: "🧭" },
  productive: { label: "Productivity", weight: 0.5, tokenClass: "bg-cat-productive", emoji: "📒" },
};

export interface AppUsage {
  appName: string;
  category: Category;
  minutes: number;
}

export interface MemberUsage {
  userId: string;
  apps: AppUsage[];
  /** Multiplicative bonus (0..1). 1 = no change. 0.7 = 30% reduction. */
  redemptionMultiplier?: number;
}

export interface Usage {
  userId: string;
  apps: AppUsage[];
}


export function weightedMinutes(usage?: MemberUsage | Usage): number {
  if (!usage) return 0;
  const raw = usage.apps.reduce(
    (sum, a) => sum + a.minutes * CATEGORY_META[a.category].weight,
    0,
  );
  const mult = (usage as MemberUsage).redemptionMultiplier ?? 1;
  return raw * mult;
}


export function rawMinutes(usage?: MemberUsage | Usage): number {
  if (!usage) return 0;
  return usage.apps.reduce((s, a) => s + a.minutes, 0);
}


/**
 * Distribute multipliers between 0.5x and 1.5x such that the sum equals n.
 * For n=4 this matches the spec exactly: [0.5, 0.8, 1.2, 1.5].
 */
export function multipliersFor(n: number): number[] {
  if (n <= 0) return [];
  if (n === 1) return [1];
  if (n === 2) return [0.5, 1.5];
  if (n === 4) return [0.5, 0.8, 1.2, 1.5];
  // generic linear distribution
  const base: number[] = Array.from({ length: n }, (_, i) =>
    0.5 + (i * (1.5 - 0.5)) / (n - 1),
  );
  const sum = base.reduce((a, b) => a + b, 0);
  const scale = n / sum;
  return base.map((v) => +(v * scale).toFixed(3));
}

export interface RankedMember {
  userId: string;
  weighted: number;
  raw: number;
  rank: number;       // 1 = best (lowest weighted)
  multiplier: number; // bill multiplier
  share: number;      // amount they owe
}

export function rankGroup(
  usages: MemberUsage[],
  totalBill: number,
): RankedMember[] {
  const enriched = usages.map((u) => ({
    userId: u.userId,
    weighted: weightedMinutes(u),
    raw: rawMinutes(u),
  }));
  const sorted = [...enriched].sort((a, b) => a.weighted - b.weighted);
  const mults = multipliersFor(sorted.length);
  const perHead = totalBill / sorted.length;
  const ranked: RankedMember[] = sorted.map((m, i) => ({
    ...m,
    rank: i + 1,
    multiplier: mults[i],
    share: Math.round(perHead * mults[i]),
  }));
  // return in original order
  return usages.map((u) => ranked.find((r) => r.userId === u.userId)!);
}
