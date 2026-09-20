import { store } from "@/lib/mockStore";
import { Bell, Zap } from "lucide-react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";

interface Props {
  userId: string;
}

export function ScarletScoreCard({ userId }: Props) {
  const user = store.getUser(userId);
  const usage = store.usageFor(userId);
  
  if (!user || !usage) return null;

  const onNudge = () => {
    toast.success(`Nudge sent to ${user.name}`, {
      description: "Asking them to put the phone down for a bit! 📴",
      icon: <Bell className="h-4 w-4" />
    });
  };

  const onReaction = (emoji: string) => {
    toast(`Reacted ${emoji} to ${user.name}'s usage`, {
      icon: <span>{emoji}</span>
    });
  };

  return (
    <div className="rounded-[2.5rem] bg-[#FFF2F2] border-2 border-[#FFDADA] p-6 animate-rise">
      {/* Header Row */}
      <div className="flex items-center gap-3 mb-6">
        <div className="bg-[#FFE5E5] text-[#FF5F5F] px-4 py-1.5 rounded-full text-[11px] font-black uppercase tracking-wider border border-[#FFBFBF]">
          Scarlet score
        </div>
        <div className="text-[#845F5F] text-[11px] font-bold uppercase tracking-widest opacity-70">
          Today's highest payer
        </div>
      </div>

      {/* Profile Row */}
      <div className="flex items-center gap-4 mb-8">
        <div className="h-16 w-16 rounded-3xl bg-white border-2 border-[#FFDADA] grid place-items-center text-3xl shadow-sm">
          {user.avatar}
        </div>
        <div className="flex-1 min-w-0">
          <h4 className="font-display font-black text-xl text-[#4A2D2D] leading-tight truncate">
            {user.name}
          </h4>
          <p className="text-xs font-bold text-[#845F5F] opacity-70">
            {userId === store.currentUser()?.id ? 'Doing too much' : 'Doomscroll Pro'}
          </p>
        </div>
        <button
          onClick={onNudge}
          className="bg-[#111122] text-white px-5 py-3 rounded-2xl flex items-center gap-2 hover:scale-105 transition-transform active:scale-95"
        >
          <Bell className="h-4 w-4" />
          <span className="text-xs font-black uppercase tracking-wider">Nudge</span>
        </button>
      </div>

      {/* Reaction Row */}
      <div className="flex items-center gap-3">
        {["😂", "💀", "🔥", "👀", "🫠"].map((emoji) => (
          <button
            key={emoji}
            onClick={() => onReaction(emoji)}
            className="h-12 w-12 rounded-2xl bg-white border border-[#FFDADA] grid place-items-center text-xl hover:bg-[#FFE5E5] transition-colors hover:scale-110 active:scale-95 shadow-sm"
          >
            {emoji}
          </button>
        ))}
      </div>
    </div>
  );
}
