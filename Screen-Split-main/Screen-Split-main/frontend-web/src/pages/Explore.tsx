import { useCallback, useEffect, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { ClientResponseError } from "pocketbase";
import { store } from "@/lib/mockStore";
import { useStore } from "@/hooks/useStore";
import { PageHeader } from "@/components/screensplit/PageHeader";
import { BottomNav } from "@/components/screensplit/BottomNav";
import { Plus, Users, Sparkles } from "lucide-react";
import { toast } from "sonner";
import {
  createCrewOnServer,
  joinCrewByCode,
  refreshMyCrewsInStore,
} from "@/lib/crewsApi";

const EMOJIS = ["🍕", "🍣", "🌮", "🍜", "🍔", "☕", "🍺", "🥘"];

function pocketBaseErrorMessage(err: unknown): string {
  if (err instanceof ClientResponseError) {
    const data = err.response as { message?: string } | undefined;
    if (typeof data?.message === "string" && data.message) return data.message;
    return err.message || "Request failed";
  }
  return err instanceof Error ? err.message : "Something went wrong";
}

export default function Explore() {
  const nav = useNavigate();
  const me = useStore(() => store.currentUser());
  const groups = useStore(() => store.groupsForCurrentUser());
  const [showCreate, setShowCreate] = useState(false);
  const [showJoin, setShowJoin] = useState(false);
  const [name, setName] = useState("");
  const [emoji, setEmoji] = useState("🍕");
  const [bill, setBill] = useState("2000");
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const loadCrews = useCallback(async () => {
    if (!me) {
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      await refreshMyCrewsInStore(me.id);
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [me]);

  useEffect(() => {
    void loadCrews();
  }, [loadCrews]);

  if (!me) {
    nav("/welcome", { replace: true });
    return null;
  }

  const create = async () => {
    if (!name.trim()) return toast.error("Group needs a name");
    setSaving(true);
    try {
      const g = await createCrewOnServer(me.id, {
        name: name.trim(),
        emoji,
        bill: Number(bill) || 2000,
      });
      setShowCreate(false);
      setName("");
      toast.success(`Crew "${g.name}" created!`);
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const join = async () => {
    setSaving(true);
    try {
      const { crew: g } = await joinCrewByCode(me.id, code.trim());
      setShowJoin(false);
      setCode("");
      nav(`/group/${g.id}`);
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen pb-28">
      <div className="mx-auto max-w-md px-5">
        <PageHeader
          title="Crew Hub"
          subtitle="Manage your splits and teams"
          right={
            <span className="grid place-items-center h-9 w-9 rounded-full bg-accent border-2 border-foreground">
              <Sparkles className="h-4 w-4" />
            </span>
          }
        />

        {/* Quick actions moved from Groups */}
        <div className="grid grid-cols-2 gap-3 mt-4">
          <button
            onClick={() => setShowCreate(true)}
            className="chunky-card p-4 bg-primary text-primary-foreground text-left hover:-translate-y-0.5 transition-all border-2 border-foreground"
          >
            <Plus className="h-5 w-5 mb-2" strokeWidth={2.5} />
            <p className="font-display font-bold">New crew</p>
            <p className="text-[11px] opacity-80">Start a split</p>
          </button>
          <button
            onClick={() => setShowJoin(true)}
            className="chunky-card p-4 bg-card text-left hover:-translate-y-0.5 transition-all border-2 border-foreground"
          >
            <Users className="h-5 w-5 mb-2 text-primary" strokeWidth={2.5} />
            <p className="font-display font-bold">Join with code</p>
            <p className="text-[11px] text-muted-foreground">Got a link?</p>
          </button>
        </div>

        {/* Groups list moved from Groups */}
        <div className="mt-8 space-y-3">
          <h2 className="font-display text-xl font-bold">Your Crews</h2>
          {loading ? (
            <div className="chunky-card p-6 bg-card text-center border-2 border-foreground/20">
              <p className="font-display font-bold text-muted-foreground">
                Loading…
              </p>
            </div>
          ) : groups.length === 0 ? (
            <div className="chunky-card p-6 bg-card text-center border-2 border-dashed border-foreground/20">
              <Sparkles className="h-6 w-6 mx-auto mb-2 text-primary" />
              <p className="font-display font-bold">No crews yet</p>
              <p className="text-sm text-muted-foreground">
                Found a new team of non-scrollers above!
              </p>
            </div>
          ) : (
            groups.map((g) => {
              const ranked = store.rankedFor(g);
              const me2 = ranked.find((r) => r.userId === me.id);
              return (
                <Link
                  key={g.id}
                  to={`/group/${g.id}`}
                  className="block chunky-card p-4 bg-card hover:-translate-y-0.5 transition-transform border-2 border-foreground"
                >
                  <div className="flex items-center gap-3">
                    <span className="text-3xl">{g.emoji}</span>
                    <div className="flex-1 min-w-0">
                      <p className="font-display font-bold truncate">{g.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {g.memberIds.length} members · ₹
                        {g.bill.toLocaleString("en-IN")}
                      </p>
                    </div>
                    {me2 && (
                      <div className="text-right">
                        <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
                          Owe
                        </p>
                        <p className="font-display font-bold text-lg leading-none text-primary">
                          ₹{me2.share}
                        </p>
                      </div>
                    )}
                  </div>
                </Link>
              );
            })
          )}
        </div>
      </div>

      {/* Sheets */}
      {showCreate && (
        <Sheet onClose={() => setShowCreate(false)} title="New crew">
          <div className="space-y-4">
            <Field label="Crew name">
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Friday Night"
                className="ssp-input"
              />
            </Field>
            <Field label="Vibe">
              <div className="grid grid-cols-8 gap-1">
                {EMOJIS.map((e) => (
                  <button
                    key={e}
                    type="button"
                    onClick={() => setEmoji(e)}
                    className={`text-xl p-1.5 rounded-lg border-2 transition-all ${emoji === e ? "bg-accent border-foreground" : "bg-muted border-transparent"}`}
                  >
                    {e}
                  </button>
                ))}
              </div>
            </Field>
            <Field label="Bill (₹)">
              <input
                value={bill}
                onChange={(e) => setBill(e.target.value)}
                type="number"
                className="ssp-input"
              />
            </Field>
            <button
              type="button"
              disabled={saving}
              onClick={() => void create()}
              className="ssp-btn mt-2"
            >
              {saving ? "Creating…" : "Create crew"}
            </button>
          </div>
        </Sheet>
      )}

      {showJoin && (
        <Sheet onClose={() => setShowJoin(false)} title="Join crew">
          <div className="space-y-4">
            <Field label="Invite code">
              <input
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                placeholder="ABC123"
                className="ssp-input font-mono tracking-widest text-center text-lg"
              />
            </Field>
            <button
              type="button"
              disabled={saving}
              onClick={() => void join()}
              className="ssp-btn mt-2"
            >
              {saving ? "Joining…" : "Join crew"}
            </button>
          </div>
        </Sheet>
      )}

      <BottomNav />

      <style>{`
        .ssp-input { width:100%; background:hsl(var(--muted)); border-radius:0.75rem; padding:0.8rem 1rem; font-size:1rem; border:2px solid hsl(var(--foreground)); outline:none; font-family:'Space Grotesk',sans-serif; font-weight: 500;}
        .ssp-input:focus { border-color:hsl(var(--primary)); }
        .ssp-btn { width:100%; background:hsl(var(--primary)); color:hsl(var(--primary-foreground)); font-family:'Space Grotesk',sans-serif; font-weight:700; padding:1rem; border-radius:1rem; border:2px solid hsl(var(--foreground)); box-shadow:4px 4px 0px hsl(var(--foreground)); transition:all .1s; }
        .ssp-btn:hover { transform:translate(2px,2px); box-shadow:2px 2px 0px hsl(var(--foreground)); }
        .ssp-btn:active { transform:translate(4px,4px); box-shadow:none; }
        .ssp-btn:disabled { opacity:0.6; pointer-events:none; }
      `}</style>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs font-bold uppercase tracking-widest text-muted-foreground ml-1">
        {label}
      </label>
      <div className="mt-1.5">{children}</div>
    </div>
  );
}

function Sheet({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center px-4" onClick={onClose}>
      <div className="absolute inset-0 bg-foreground/60 backdrop-blur-sm" />
      <div
        className="relative w-full max-w-md bg-background border-t-4 border-l-4 border-r-4 border-foreground rounded-t-[2rem] p-6 pb-10 animate-rise"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mx-auto h-1.5 w-12 rounded-full bg-foreground/20 mb-6" />
        <h2 className="font-display text-3xl font-black mb-6">{title}</h2>
        {children}
      </div>
    </div>
  );
}
