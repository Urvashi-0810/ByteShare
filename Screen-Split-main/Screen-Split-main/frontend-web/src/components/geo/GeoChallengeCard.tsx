import { ArrowRight, MapPin, Sparkles, Ticket } from "lucide-react";
import type { GeoChallengeDTO } from "@/lib/geoApi";
import { formatDistance } from "@/lib/haversine";
import { cn } from "@/lib/utils";

type Props = {
  challenge: GeoChallengeDTO;
  onClick?: () => void;
  className?: string;
};

export function GeoChallengeCard({ challenge, onClick, className }: Props) {
  const promoted = challenge.isPromoted;
  const partner = challenge.partner;
  const coupon = challenge.coupons?.find((c) => c.remaining > 0 || c.totalAvailable === 0);
  const remaining = coupon ? coupon.remaining : null;

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onClick?.();
        }
      }}
      className={cn(
        "chunky-card p-5 cursor-pointer group transition-all active:scale-[0.98]",
        promoted ? "bg-gradient-to-br from-accent/20 to-primary/10" : "bg-card",
        className,
      )}
    >
      <div className="flex items-start gap-4">
        <div className="h-12 w-12 rounded-2xl bg-muted border-2 border-foreground flex items-center justify-center text-2xl shrink-0 shadow-[2px_2px_0px_rgba(0,0,0,1)] group-hover:scale-110 transition-transform">
          {promoted && partner ? partner.logoEmoji : "🚶"}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="font-display font-black text-lg leading-tight truncate">
              {challenge.title}
            </h3>
            {promoted && (
              <span className="bg-accent text-accent-foreground text-[8px] font-black uppercase px-1.5 py-0.5 rounded leading-none inline-flex items-center gap-1">
                <Sparkles className="h-2.5 w-2.5" />
                Promoted
              </span>
            )}
          </div>
          {promoted && partner && (
            <p className="text-[11px] font-bold text-muted-foreground truncate">
              {partner.name}
              {partner.address ? ` · ${partner.address}` : ""}
            </p>
          )}
          <p className="text-xs text-muted-foreground font-medium mt-1 line-clamp-2">
            {challenge.description || "Walk there and back."}
          </p>

          <div className="mt-3 flex items-center gap-2 flex-wrap">
            {challenge.distanceM !== undefined && (
              <div className="inline-flex items-center gap-1 px-2 py-1 rounded-lg bg-card border-2 border-foreground text-[10px] font-black uppercase">
                <MapPin className="h-3 w-3" />
                {formatDistance(challenge.distanceM)} away
              </div>
            )}
            {challenge.xpReward > 0 && (
              <div className="inline-flex items-center gap-1 px-2 py-1 rounded-lg bg-teal-50 border border-teal-200 text-teal-700 text-[10px] font-black uppercase">
                +{challenge.xpReward} XP
              </div>
            )}
            {coupon && (
              <div className="inline-flex items-center gap-1 px-2 py-1 rounded-lg bg-accent/20 border border-accent text-foreground text-[10px] font-black uppercase">
                <Ticket className="h-3 w-3" />
                {coupon.discountText}
                {remaining !== null && coupon.totalAvailable > 0 && remaining <= 20 && (
                  <span className="text-[9px] font-bold text-muted-foreground">
                    · {remaining} left
                  </span>
                )}
              </div>
            )}
          </div>
        </div>
        <div className="h-10 w-10 rounded-full border-2 border-foreground grid place-items-center self-center group-hover:bg-primary group-hover:text-primary-foreground transition-colors">
          <ArrowRight className="h-4 w-4" />
        </div>
      </div>
    </div>
  );
}
