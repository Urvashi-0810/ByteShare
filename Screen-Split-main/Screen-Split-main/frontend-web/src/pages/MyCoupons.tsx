import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ClientResponseError } from "pocketbase";
import { toast } from "sonner";
import { Copy, CheckCircle2, Ticket } from "lucide-react";
import { PageHeader } from "@/components/screensplit/PageHeader";
import { BottomNav } from "@/components/screensplit/BottomNav";
import { listMyCoupons, type MyCouponRow } from "@/lib/geoApi";
import { cn } from "@/lib/utils";

function describeError(err: unknown): string {
  if (err instanceof ClientResponseError) {
    const data = err.response as { message?: string } | undefined;
    if (typeof data?.message === "string" && data.message) return data.message;
    return err.message || "Request failed";
  }
  return err instanceof Error ? err.message : "Something went wrong";
}

type CouponState = "active" | "used" | "expired";

function stateOf(row: MyCouponRow): CouponState {
  if (row.redeemed) return "used";
  if (row.expired) return "expired";
  return "active";
}

export default function MyCoupons() {
  const nav = useNavigate();
  const [items, setItems] = useState<MyCouponRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [copied, setCopied] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listMyCoupons();
      setItems(res);
    } catch (err) {
      toast.error(describeError(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const grouped = useMemo(() => {
    const out: Record<CouponState, MyCouponRow[]> = {
      active: [],
      used: [],
      expired: [],
    };
    for (const r of items) {
      out[stateOf(r)].push(r);
    }
    return out;
  }, [items]);

  return (
    <div className="min-h-screen pb-28">
      <div className="mx-auto max-w-md px-5">
        <PageHeader
          title="My coupons"
          subtitle="Earned by walking to partner spots"
          back
          right={
            <div className="h-9 w-9 rounded-full bg-accent border-2 border-foreground grid place-items-center">
              <Ticket className="h-5 w-5" />
            </div>
          }
        />

        {loading ? (
          <div className="chunky-card p-6 text-center text-muted-foreground">
            Loading coupons…
          </div>
        ) : items.length === 0 ? (
          <div className="chunky-card p-6 text-center">
            <p className="font-display font-black text-xl mb-1">
              No coupons yet
            </p>
            <p className="text-xs text-muted-foreground mb-4">
              Walk to a promoted partner spot to earn one.
            </p>
            <button
              type="button"
              onClick={() => nav("/challenges")}
              className="inline-block px-4 py-2 rounded-xl bg-primary text-primary-foreground font-black uppercase text-xs tracking-widest border-2 border-foreground"
            >
              Browse walk challenges
            </button>
          </div>
        ) : (
          <div className="space-y-8 mt-2">
            <Group title="Active" tone="accent" rows={grouped.active} copied={copied} onCopy={setCopied} />
            <Group title="Used" tone="muted" rows={grouped.used} copied={copied} onCopy={setCopied} />
            <Group title="Expired" tone="muted" rows={grouped.expired} copied={copied} onCopy={setCopied} />
          </div>
        )}
      </div>
      <BottomNav />
    </div>
  );
}

function Group({
  title,
  tone,
  rows,
  copied,
  onCopy,
}: {
  title: string;
  tone: "accent" | "muted";
  rows: MyCouponRow[];
  copied: string | null;
  onCopy: (code: string | null) => void;
}) {
  if (rows.length === 0) return null;
  return (
    <section>
      <div className="flex items-center gap-2 mb-3 px-1">
        <h2 className="font-display font-black text-lg leading-none">
          {title}
        </h2>
        <span className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
          {rows.length}
        </span>
      </div>
      <div className="space-y-3">
        {rows.map((r) => (
          <CouponRow
            key={r.id}
            row={r}
            tone={tone}
            copied={copied === r.code}
            onCopy={() => {
              navigator.clipboard
                .writeText(r.code)
                .then(() => {
                  onCopy(r.code);
                  toast.success("Code copied");
                  setTimeout(() => onCopy(null), 1500);
                })
                .catch(() => toast.error("Could not copy"));
            }}
          />
        ))}
      </div>
    </section>
  );
}

function CouponRow({
  row,
  tone,
  copied,
  onCopy,
}: {
  row: MyCouponRow;
  tone: "accent" | "muted";
  copied: boolean;
  onCopy: () => void;
}) {
  const dimmed = tone === "muted";
  return (
    <div
      className={cn(
        "chunky-card p-4",
        dimmed
          ? "bg-muted/40 opacity-70"
          : "bg-gradient-to-br from-accent/15 to-primary/5",
      )}
    >
      <div className="flex items-start gap-3">
        <div className="h-10 w-10 rounded-xl bg-card border-2 border-foreground grid place-items-center text-xl shrink-0">
          {row.partnerLogo || "🏪"}
        </div>
        <div className="min-w-0 flex-1">
          <p className="font-display font-black text-base truncate">
            {row.title}
          </p>
          {row.partnerName && (
            <p className="text-xs text-muted-foreground truncate">
              {row.partnerName}
            </p>
          )}
          <p className="text-sm mt-1">{row.discountText}</p>
          {row.terms && (
            <p className="text-[10px] text-muted-foreground mt-1 line-clamp-2">
              {row.terms}
            </p>
          )}
        </div>
      </div>
      <button
        type="button"
        onClick={onCopy}
        disabled={dimmed}
        className={cn(
          "mt-3 w-full rounded-xl border-2 border-dashed border-foreground py-3 text-center transition-transform",
          dimmed ? "cursor-not-allowed" : "bg-card active:scale-[0.99]",
        )}
      >
        <div className="flex items-center justify-center gap-2">
          {row.redeemed ? (
            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
          ) : (
            <Copy className="h-4 w-4" />
          )}
          <span className="font-mono font-black text-lg tracking-[0.3em]">
            {row.code}
          </span>
        </div>
        <p className="mt-1 text-[10px] font-black uppercase tracking-widest text-muted-foreground">
          {row.redeemed
            ? "Used"
            : row.expired
              ? "Expired"
              : copied
                ? "Copied!"
                : "Tap to copy"}
        </p>
      </button>
    </div>
  );
}
