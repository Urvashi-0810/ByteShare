import type { Challenge } from "@/lib/mockStore";
import type { Usage } from "@/lib/rank";
import type { Category } from "@/lib/rank";

export type ChallengeEvalResult = {
  completed: string[];
  failed: string[];
};

const BADGE_BY_CHALLENGE: Record<string, string> = {
  "no-scroll-sunday": "Immunity Badge 🛡️",
  "category-cleanse": "Cleanse Badge 🫧",
  "deep-work": "Deep Work 🧠",
};

export function badgeForChallengeComplete(challengeId: string): string | undefined {
  return BADGE_BY_CHALLENGE[challengeId];
}

function sumCategoryMinutes(usage: Usage | undefined, cat: Category): number {
  if (!usage?.apps.length) return 0;
  return usage.apps.reduce((s, a) => (a.category === cat ? s + a.minutes : s), 0);
}

function totalProductiveMinutes(usage: Usage | undefined): number {
  if (!usage?.apps.length) return 0;
  return usage.apps.reduce(
    (s, a) => (a.category === "productive" ? s + a.minutes : s),
    0,
  );
}

function hasNonProductiveUsage(usage: Usage | undefined): boolean {
  if (!usage?.apps.length) return false;
  return usage.apps.some((a) => a.category !== "productive");
}

/**
 * Evaluates progress from the latest per-user usage snapshot (same data as Social Rank).
 * Time-of-day windows, multi-day streaks, and inactivity verification are not available here.
 */
export function evaluateGlobalChallenge(
  ch: Challenge,
  usageFor: (userId: string) => Usage | undefined,
): ChallengeEvalResult {
  if (ch.comingSoon) {
    return { completed: [...ch.completed], failed: [...ch.failed] };
  }

  const participants = ch.participants;
  if (participants.length === 0) {
    return { completed: [], failed: [] };
  }

  switch (ch.id) {
    case "deep-work": {
      const completed: string[] = [];
      const failed: string[] = [];
      for (const uid of participants) {
        const u = usageFor(uid);
        if (!u?.apps.length) continue;
        if (hasNonProductiveUsage(u)) {
          failed.push(uid);
          continue;
        }
        if (totalProductiveMinutes(u) >= 120) {
          completed.push(uid);
        }
      }
      return { completed, failed };
    }
    case "no-scroll-sunday": {
      const completed: string[] = [];
      const failed: string[] = [];
      for (const uid of participants) {
        const u = usageFor(uid);
        if (!u?.apps.length) continue;
        if (sumCategoryMinutes(u, "social") > 0) {
          failed.push(uid);
        } else {
          completed.push(uid);
        }
      }
      return { completed, failed };
    }
    case "category-cleanse": {
      const completed: string[] = [];
      const failed: string[] = [];
      for (const uid of participants) {
        const u = usageFor(uid);
        if (!u?.apps.length) continue;
        if (sumCategoryMinutes(u, "stream") > 0) {
          failed.push(uid);
        } else {
          completed.push(uid);
        }
      }
      return { completed, failed };
    }
    default:
      return { completed: [...ch.completed], failed: [...ch.failed] };
  }
}

export function allParticipantsPass(
  ch: Challenge,
  completed: string[],
): boolean {
  return (
    ch.participants.length > 0 &&
    ch.participants.every((id) => completed.includes(id))
  );
}

export const CANONICAL_GLOBAL_CHALLENGES: Challenge[] = [
  {
    id: "sabbath-redemption",
    title: "Screen Sabbath redemption",
    emoji: "🧘",
    description:
      "Pledge a 4-hour phone-free window. Shave 0.2 off your multiplier.",
    requirement: "4-hour phone-free window (verified via inactivity)",
    reward: "-0.2 Multiplier",
    type: "individual",
    category: "Sabbath",
    endsAt: 0,
    participants: [],
    failed: [],
    completed: [],
    isGlobal: true,
    comingSoon: true,
  },
  {
    id: "morning-fast",
    title: "The Morning Fast",
    emoji: "☀️",
    description:
      "No phone for the first hour after waking. Small reward but builds habit.",
    requirement: "No activity between 7–9 AM",
    reward: "-0.05 Multiplier",
    type: "individual",
    category: "Sabbath",
    endsAt: 0,
    participants: [],
    failed: [],
    completed: [],
    isGlobal: true,
    comingSoon: true,
  },
  {
    id: "dinner-detox",
    title: "Dinner Table Detox",
    emoji: "🍽️",
    description:
      "Phone-free during dinner. Earn a 'Present at the table' badge.",
    requirement: "No notifications/activity 7–9 PM",
    reward: "Present Badge 🏅",
    type: "individual",
    category: "Sabbath",
    endsAt: 0,
    participants: [],
    failed: [],
    completed: [],
    isGlobal: true,
    comingSoon: true,
  },
  {
    id: "deep-work",
    title: "The Deep Work Block",
    emoji: "🧠",
    description:
      "2-hour focus window where only productivity apps are allowed.",
    requirement:
      "Only 'Productive' category apps for 2 hours (checked against your latest daily snapshot)",
    reward: "-0.15 Multiplier",
    type: "individual",
    category: "Sabbath",
    endsAt: 0,
    participants: [],
    failed: [],
    completed: [],
    isGlobal: true,
    comingSoon: false,
  },
  {
    id: "collective-detox",
    title: "The Collective Detox",
    emoji: "📉",
    description: "Pod's combined weekly screen time must drop by 20% vs last week.",
    requirement: "20% reduction across all pod members vs prior week",
    reward: "Shared Fund Bonus 💰",
    type: "group",
    category: "Group",
    endsAt: 0,
    participants: [],
    failed: [],
    completed: [],
    isGlobal: true,
    comingSoon: true,
  },
  {
    id: "no-scroll-sunday",
    title: "No Scroll Sunday",
    emoji: "🚫",
    description: "Zero social media across the entire pod for one day. High stakes.",
    requirement:
      "0 mins in 'Social' in your synced daily usage (24h tracking needs the mobile pipeline)",
    reward: "Immunity Badge 🛡️",
    type: "group",
    category: "Group",
    endsAt: 0,
    participants: [],
    failed: [],
    completed: [],
    isGlobal: true,
    comingSoon: false,
  },
  {
    id: "streak-synchrony",
    title: "Streak Synchrony",
    emoji: "🔗",
    description:
      "All pod members maintain a 3-day low-usage streak simultaneously.",
    requirement: "All members < 2h raw for 3 consecutive days",
    reward: "+50 Social XP ⭐",
    type: "group",
    category: "Group",
    endsAt: 0,
    participants: [],
    failed: [],
    completed: [],
    isGlobal: true,
    comingSoon: true,
  },
  {
    id: "category-cleanse",
    title: "Category Cleanse",
    emoji: "🧼",
    description:
      "Pick one category (Streaming) and the whole pod avoids it for 48h.",
    requirement:
      "0 mins in 'Stream' in your synced daily usage (48h window needs history on the server)",
    reward: "Cleanse Badge 🫧",
    type: "group",
    category: "Group",
    endsAt: 0,
    participants: [],
    failed: [],
    completed: [],
    isGlobal: true,
    comingSoon: false,
  },
  {
    id: "accountability-pair",
    title: "The Accountability Pair",
    emoji: "👯",
    description: "Pod splits into buddy pairs. Each pair shares a combined score.",
    requirement: "Pairing + combined score vs pod average (needs crew pairing UX)",
    reward: "Pair Bond Ribbon 🎀",
    type: "group",
    category: "Group",
    endsAt: 0,
    participants: [],
    failed: [],
    completed: [],
    isGlobal: true,
    comingSoon: true,
  },
];

const PERIOD_MS: Record<string, number> = {
  "sabbath-redemption": 86400000,
  "morning-fast": 86400000,
  "dinner-detox": 86400000,
  "deep-work": 86400000,
  "collective-detox": 86400000 * 7,
  "no-scroll-sunday": 86400000,
  "streak-synchrony": 86400000 * 3,
  "category-cleanse": 86400000 * 2,
  "accountability-pair": 86400000 * 4,
};

function sortedIds(ids: string[]): string[] {
  return [...ids].sort();
}

function sameSet(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false;
  const sa = sortedIds(a);
  const sb = sortedIds(b);
  return sa.every((v, i) => v === sb[i]);
}

export function migrateGlobalChallenges(
  stored: Challenge[],
  now: number,
): { list: Challenge[]; changed: boolean } {
  const byId = new Map(stored.map((c) => [c.id, { ...c }]));
  let changed = false;
  const result: Challenge[] = [];

  for (const def of CANONICAL_GLOBAL_CHALLENGES) {
    const prev = byId.get(def.id);
    const period = PERIOD_MS[def.id] ?? 86400000;
    let endsAt = prev?.endsAt ?? now + period;
    let participants = prev?.participants ?? [];
    let completed = prev?.completed ?? [];
    let failed = prev?.failed ?? [];

    if (!prev) {
      endsAt = now + period;
      changed = true;
    }

    if (endsAt < now) {
      endsAt = now + period;
      participants = [...participants];
      completed = [];
      failed = [];
      changed = true;
    }

    const ch: Challenge = {
      ...def,
      endsAt,
      participants,
      completed,
      failed,
      isGlobal: true,
      comingSoon: def.comingSoon,
    };

    if (
      prev &&
      (prev.comingSoon !== def.comingSoon ||
        prev.title !== def.title ||
        prev.requirement !== def.requirement)
    ) {
      changed = true;
    }

    result.push(ch);
  }

  const extra = stored.filter((c) => !CANONICAL_GLOBAL_CHALLENGES.some((d) => d.id === c.id));
  if (extra.length) {
    result.push(...extra);
    changed = true;
  }

  if (stored.length !== result.length) changed = true;

  return { list: result, changed };
}

export function mergeEvalIntoChallenge(
  ch: Challenge,
  next: ChallengeEvalResult,
): Challenge {
  const completed = sortedIds(next.completed);
  const failed = sortedIds(next.failed.filter((id) => !next.completed.includes(id)));
  return { ...ch, completed, failed };
}

export function evalChanged(prev: Challenge, next: Challenge): boolean {
  return (
    !sameSet(prev.completed, next.completed) || !sameSet(prev.failed, next.failed)
  );
}
