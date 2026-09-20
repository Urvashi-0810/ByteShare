import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ClientResponseError } from "pocketbase";
import { pb } from "@/lib/pocketbase";
import { isPersistedCrewId } from "@/lib/crewsApi";
import { store } from "@/lib/mockStore";
import { useStore } from "@/hooks/useStore";
import { PageHeader } from "@/components/screensplit/PageHeader";
import { BottomNav } from "@/components/screensplit/BottomNav";
import { BillSplitList } from "@/components/screensplit/BillSplitList";
import { MemberBreakdown } from "@/components/screensplit/MemberBreakdown";
import { BillBreakdown } from "@/components/screensplit/BillBreakdown";
import { RedemptionPanel } from "@/components/screensplit/RedemptionPanel";
import { Share2, Pencil, Check } from "lucide-react";
import { toast } from "sonner";

export default function GroupDetail() {
  const { id } = useParams();
  const nav = useNavigate();
  const me = useStore(() => store.currentUser());
  const group = useStore(() => (id ? store.getGroup(id) : undefined));
  const [editingBill, setEditingBill] = useState(false);
  const [billDraft, setBillDraft] = useState("");

  if (!me) { nav("/welcome", { replace: true }); return null; }
  if (!group) {
    return (
      <div className="min-h-screen grid place-items-center px-6 text-center">
        <div>
          <p className="font-display text-2xl font-bold">Crew not found</p>
          <button onClick={() => nav("/groups")} className="mt-3 underline">Back to crews</button>
        </div>
      </div>
    );
  }

  const [selectedMemberId, setSelectedMemberId] = useState(me.id);
  const ranked = store.rankedFor(group);
  const currentRankData = ranked.find(r => r.userId === selectedMemberId);
  const inviteUrl = `${window.location.origin}/join/${group.inviteCode}`;

  const share = async () => {
    const text = `Join my ScreenSplit crew "${group.name}" ${group.emoji}\nCode: ${group.inviteCode}\n${inviteUrl}`;
    try {
      if (navigator.share) {
        await navigator.share({ title: `Join ${group.name}`, text, url: inviteUrl });
      } else {
        await navigator.clipboard.writeText(text);
        toast("Invite copied", { description: "Paste it into Gmail or anywhere." });
      }
    } catch { /* user cancelled */ }
  };

  const gmailShare = () => {
    const subject = encodeURIComponent(`Join my ScreenSplit crew: ${group.name}`);
    const body = encodeURIComponent(
      `Hey,\n\nJoin my ScreenSplit crew "${group.name}" ${group.emoji}.\n\nUse this code: ${group.inviteCode}\nor open: ${inviteUrl}\n\nLet's see who scrolls the most this week 👀`,
    );
    window.open(`https://mail.google.com/mail/?view=cm&fs=1&su=${subject}&body=${body}`, "_blank");
  };

  const saveBill = async () => {
    const n = Number(billDraft);
    if (!n || n < 1) return toast.error("Enter a positive number");
    if (isPersistedCrewId(group.id)) {
      try {
        await pb.collection("crews").update(group.id, { bill: n });
      } catch (err) {
        const msg =
          err instanceof ClientResponseError
            ? err.message || "Could not save bill"
            : "Could not save bill";
        toast.error(msg);
        return;
      }
    }
    store.updateGroup(group.id, { bill: n });
    setEditingBill(false);
    toast("Bill updated");
  };

  return (
    <div className="min-h-screen pb-28">
      <div className="mx-auto max-w-md px-5">
        <PageHeader
          back
          title={`${group.emoji} ${group.name}`}
          subtitle={`${group.memberIds.length} members · code ${group.inviteCode}`}
          right={
            <button
              onClick={share}
              aria-label="Share invite"
              className="grid place-items-center h-9 w-9 rounded-full border-2 border-foreground bg-accent hover:opacity-90"
            >
              <Share2 className="h-4 w-4" />
            </button>
          }
        />

        {/* Bill Summary */}
        <div className="relative mt-2">
          <BillBreakdown group={group} />
          <button
            onClick={() => { setBillDraft(String(group.bill)); setEditingBill(true); }}
            className="absolute top-4 right-4 grid place-items-center h-8 w-8 rounded-full bg-card border-2 border-foreground/20 hover:bg-accent"
            aria-label="Edit bill"
          >
            <Pencil className="h-3.5 w-3.5" />
          </button>
        </div>

        {editingBill && (
          <div className="mt-3 chunky-card p-3 bg-card flex items-center gap-2">
            <span className="font-display font-bold text-lg">₹</span>
            <input
              autoFocus
              type="number"
              value={billDraft}
              onChange={(e) => setBillDraft(e.target.value)}
              className="flex-1 bg-muted rounded-lg px-3 py-2 outline-none border-2 border-transparent focus:border-foreground"
            />
            <button
              type="button"
              onClick={() => void saveBill()}
              className="grid place-items-center h-9 w-9 rounded-full bg-primary text-primary-foreground"
            >
              <Check className="h-4 w-4" strokeWidth={3} />
            </button>
          </div>
        )}

        {/* Invite Hub */}
        <div className="mt-4 chunky-card p-4 bg-card">
          <div className="flex items-center gap-3">
            <div className="text-2xl">✉️</div>
            <div className="flex-1 min-w-0">
              <p className="font-display font-bold leading-tight">Invite via Gmail</p>
              <p className="text-[11px] text-muted-foreground truncate">{inviteUrl}</p>
            </div>
            <button
              onClick={gmailShare}
              className="px-3 py-2 rounded-full bg-foreground text-background text-xs font-bold uppercase tracking-wider"
            >
              Send
            </button>
          </div>
        </div>

        {/* Bill Split List */}
        <section className="mt-8">
          <div className="flex items-baseline justify-between mb-3 px-1">
            <h2 className="font-display text-xl font-black uppercase tracking-tight">Bill Split</h2>
            <p className="text-[10px] font-black uppercase tracking-widest text-muted-foreground mr-1">Recalculates live</p>
          </div>
          <BillSplitList 
            group={group} 
            selectedId={selectedMemberId} 
            onSelect={setSelectedMemberId} 
          />
        </section>

        {/* Member Breakdown */}
        {currentRankData && (
          <section className="mt-8">
            <MemberBreakdown 
              userId={selectedMemberId} 
              rankData={currentRankData} 
            />
          </section>
        )}

        {/* Redemption */}
        <section className="mt-8">
          <RedemptionPanel group={group} meId={me.id} />
        </section>
      </div>
      <BottomNav />
    </div>
  );
}
