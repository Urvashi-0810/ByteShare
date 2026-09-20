# ScreenSplit — Cursor / Agent Guide

> The bill-splitting app where your screen time decides what you pay.

## Product in one paragraph

A group of friends goes out. They split the bill — but **not equally**. Each
person's share is multiplied by a coefficient determined by their **weighted
screen time rank** for the week. Lowest screen time pays the least (0.5×),
highest pays the most (1.5×). The multipliers always sum to `n` (the group
size) so the restaurant is always paid in full.

## Core mechanics (must stay true to these)

### 1. The Fame Engine — rank → multiplier
For a group of 4 the multipliers are fixed:
`[0.5, 0.8, 1.2, 1.5]` (sum = 4.0). For other group sizes scale linearly
between 0.5 and 1.5 such that the sum equals `n`.

### 2. Weighted app categories
| Category     | Weight | Examples                       |
|--------------|--------|--------------------------------|
| Social Media | 2.0×   | Instagram, Twitter, Snapchat   |
| Streaming    | 1.5×   | YouTube, Netflix, Reels        |
| Neutral      | 1.0×   | Maps, Weather, Calculator      |
| Productivity | 0.5×   | Notion, Calendar, Duolingo     |

`weighted_minutes = Σ (raw_minutes_in_app × category_weight)`

### 3. Redemption mechanics
- **Screen Sabbath** — declared no-phone window. Completed → score × 0.7.
- **Detox Challenge** — group challenge (e.g. no IG for 48h). Win → −20%
  score. Fail → +10% penalty. Attempting still counts as engagement.

### 4. Social triggers
- **Scarlet Score** — worst offender gets a red glow + 🔒 "In Jail" badge.
- **Bill Hostage** — worst offender locked out of redemption until another
  member "vouches" (which costs the voucher some score).
- **Intervention Push** — one-tap pre-written roast notification.

## Tech & conventions

- **Stack:** React 18 + Vite + TypeScript + Tailwind + shadcn/ui.
- **Mobile first.** Design at 360px wide. Cap content with `max-w-md mx-auto`.
- **Theme:** see `src/index.css` and `tailwind.config.ts`. Always use semantic
  tokens (`bg-background`, `text-primary`, `bg-jail`, etc.) — never raw hex.
- **State:** mocked via `src/lib/mockStore.ts` (localStorage-backed). When the
  user enables Lovable Cloud, swap this module for Supabase queries.
- **Auth:** mocked Google sign-in in `src/lib/mockStore.ts`. Replace with
  `supabase.auth.signInWithOAuth({ provider: 'google' })` when Cloud is on.
- **Invites:** `/join/:inviteCode` route reads code, joins the active user
  into that group. Share URL via `navigator.share` or clipboard.

## File map

```
src/
  pages/
    Index.tsx            landing → redirects based on auth
    Auth.tsx             google sign-in (mock)
    Groups.tsx           group list + create + join
    GroupDetail.tsx      leaderboard, bill, social actions
    Join.tsx             /join/:code accept-invite screen
  components/
    screensplit/
      Leaderboard.tsx
      BillBreakdown.tsx
      AppCategoryBar.tsx
      RedemptionPanel.tsx
      SocialTriggers.tsx
      MemberRow.tsx
      BottomNav.tsx
  lib/
    mockStore.ts         users, groups, screen-time, ranking math
    rank.ts              pure ranking + multiplier helpers
```

## Adding a feature — checklist

1. Read this file + the relevant component.
2. Add tokens to `index.css` if you need a new color.
3. Keep components < 150 lines; extract sub-components.
4. Mobile first — test at 360×640 before anything else.
5. Update `mockStore.ts` shape *and* this doc if you touch data.
