import { useNavigate } from "react-router-dom";
import { store } from "@/lib/mockStore";
import { pb } from "@/lib/pocketbase";
import { useStore } from "@/hooks/useStore";
import { PageHeader } from "@/components/screensplit/PageHeader";
import { BottomNav } from "@/components/screensplit/BottomNav";
import { AppCategoryBar } from "@/components/screensplit/AppCategoryBar";
import { appDisplayName } from "@/lib/appDisplayName";
import { LogOut, Ticket, Store, ChevronRight } from "lucide-react";

export default function Me() {
  const nav = useNavigate();
  const me = useStore(() => store.currentUser());
  const groups = useStore(() => store.groupsForCurrentUser());
  const authRecord = pb.authStore.record as { is_partner?: boolean } | null;
  const isPartner = Boolean(authRecord?.is_partner);

  if (!me) { nav("/welcome", { replace: true }); return null; }
  const usage = store.usageFor(me.id);
  const totalOwed = groups.reduce((s, g) => {
    const r = store.rankedFor(g).find((x) => x.userId === me.id);
    return s + (r?.share ?? 0);
  }, 0);

  return (
    <div className="min-h-screen pb-28">
      <div className="mx-auto max-w-md px-5">
        <PageHeader
          title="You"
          subtitle={me.email}
          right={
            <button
              onClick={() => {
                pb.authStore.clear();
                store.signOut();
                nav("/welcome", { replace: true });
              }}
              aria-label="Sign out"
              className="grid place-items-center h-9 w-9 rounded-full border-2 border-foreground bg-card"
            >
              <LogOut className="h-4 w-4" />
            </button>
          }
        />

        <div className="chunky-card-lg p-5 bg-gradient-cream text-center">
          <div className="text-5xl">{me.avatar}</div>
          <p className="mt-2 font-display text-2xl font-bold">{me.name}</p>
          <p className="text-xs text-muted-foreground">Across {groups.length} crews</p>
          <div className="mt-4 inline-block px-4 py-2 rounded-full bg-foreground text-background">
            <p className="text-[10px] font-semibold uppercase tracking-widest opacity-70">You owe this week</p>
            <p className="font-display text-2xl font-bold">₹{totalOwed.toLocaleString("en-IN")}</p>
          </div>
        </div>

        <section className="mt-6 space-y-3">
          <button
            type="button"
            onClick={() => nav("/coupons")}
            className="w-full chunky-card p-4 bg-card flex items-center gap-3 text-left active:scale-[0.98] transition-transform"
          >
            <div className="h-10 w-10 rounded-xl bg-accent border-2 border-foreground grid place-items-center shrink-0">
              <Ticket className="h-5 w-5" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="font-display font-black text-base leading-tight">My coupons</p>
              <p className="text-xs text-muted-foreground">Rewards from walks to partners</p>
            </div>
            <ChevronRight className="h-4 w-4 text-muted-foreground" />
          </button>

          <button
            type="button"
            onClick={() => nav("/partner")}
            className="w-full chunky-card p-4 bg-card flex items-center gap-3 text-left active:scale-[0.98] transition-transform"
          >
            <div className="h-10 w-10 rounded-xl bg-primary border-2 border-foreground grid place-items-center shrink-0 text-primary-foreground">
              <Store className="h-5 w-5" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="font-display font-black text-base leading-tight">
                {isPartner ? "Partner portal" : "Run a local business?"}
              </p>
              <p className="text-xs text-muted-foreground">
                {isPartner
                  ? "Manage your walk challenges and coupons"
                  : "Create promoted walks and drive footfall"}
              </p>
            </div>
            <ChevronRight className="h-4 w-4 text-muted-foreground" />
          </button>
        </section>

        {usage && (
          <section className="mt-6 chunky-card p-4 bg-card">
            <h2 className="font-display text-xl font-bold mb-3">Your screen time</h2>
            <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-3">
              Updated when you publish from the ScreenSplit Android app (cloud button).
            </p>
            <AppCategoryBar apps={usage.apps} />
            <ul className="mt-4 space-y-1.5">
              {[...usage.apps]
                .sort((a, b) => b.minutes - a.minutes)
                .slice(0, 6)
                .map((a) => {
                  const label = appDisplayName(a.appName);
                  const initial = label.charAt(0).toUpperCase() || "?";
                  return (
                    <li
                      key={a.appName}
                      className="flex items-center justify-between gap-2 text-sm"
                    >
                      <span className="flex items-center gap-2 min-w-0">
                        <span
                          className="h-8 w-8 shrink-0 rounded-xl border-2 border-foreground/15 bg-muted grid place-items-center text-xs font-black font-display"
                          aria-hidden
                        >
                          {initial}
                        </span>
                        <span className="truncate">{label}</span>
                      </span>
                      <span className="font-mono text-xs text-muted-foreground shrink-0">
                        {a.minutes}m
                      </span>
                    </li>
                  );
                })}
            </ul>
          </section>
        )}
      </div>
      <BottomNav />
    </div>
  );
}
