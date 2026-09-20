import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ClientResponseError } from "pocketbase";
import { toast } from "sonner";
import {
  Crosshair,
  MapPin,
  Plus,
  Ticket,
  Trash2,
  Store as StoreIcon,
  ScanLine,
  CheckCircle2,
} from "lucide-react";
import { PageHeader } from "@/components/screensplit/PageHeader";
import { BottomNav } from "@/components/screensplit/BottomNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import {
  createPartnerChallenge,
  createPartnerCoupon,
  deletePartnerChallenge,
  deletePartnerCoupon,
  getPartnerMe,
  listPartnerRedemptions,
  markPartnerRedemption,
  updatePartnerChallenge,
  upsertPartnerMe,
  type GeoChallengeDTO,
  type PartnerDTO,
  type PartnerRedemption,
  type PartnerStats,
} from "@/lib/geoApi";
import { pb } from "@/lib/pocketbase";
import { cn } from "@/lib/utils";

function describeError(err: unknown): string {
  if (err instanceof ClientResponseError) {
    const data = err.response as { message?: string } | undefined;
    if (typeof data?.message === "string" && data.message) return data.message;
    return err.message || "Request failed";
  }
  return err instanceof Error ? err.message : "Something went wrong";
}

type ChallengeDraft = {
  title: string;
  description: string;
  targetLat: string;
  targetLng: string;
  targetLabel: string;
  targetRadiusM: string;
  originRadiusM: string;
  minElapsedSeconds: string;
  xpReward: string;
  active: boolean;
};

function emptyChallenge(): ChallengeDraft {
  return {
    title: "",
    description: "",
    targetLat: "",
    targetLng: "",
    targetLabel: "",
    targetRadiusM: "30",
    originRadiusM: "50",
    minElapsedSeconds: "60",
    xpReward: "25",
    active: true,
  };
}

export default function PartnerPortal() {
  const nav = useNavigate();
  const [loading, setLoading] = useState(true);
  const [partner, setPartner] = useState<PartnerDTO | null>(null);
  const [challenges, setChallenges] = useState<GeoChallengeDTO[]>([]);
  const [stats, setStats] = useState<PartnerStats>({
    activeChallenges: 0,
    totalCoupons: 0,
    issuedCoupons: 0,
    redeemedCoupons: 0,
  });
  const [redemptions, setRedemptions] = useState<PartnerRedemption[]>([]);

  const [profileForm, setProfileForm] = useState({
    name: "",
    logoEmoji: "🏪",
    address: "",
    lat: "",
    lng: "",
  });
  const [savingProfile, setSavingProfile] = useState(false);

  const [draft, setDraft] = useState<ChallengeDraft>(emptyChallenge());
  const [creatingChallenge, setCreatingChallenge] = useState(false);
  const [showDraft, setShowDraft] = useState(false);

  const [scanCode, setScanCode] = useState("");
  const [scanning, setScanning] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [profile, reds] = await Promise.all([
        getPartnerMe(),
        listPartnerRedemptions().catch(() => []),
      ]);
      setPartner(profile.partner);
      setChallenges(profile.challenges);
      setStats(profile.stats);
      setRedemptions(reds);
      if (profile.partner) {
        setProfileForm({
          name: profile.partner.name,
          logoEmoji: profile.partner.logoEmoji || "🏪",
          address: profile.partner.address,
          lat: profile.partner.lat ? String(profile.partner.lat) : "",
          lng: profile.partner.lng ? String(profile.partner.lng) : "",
        });
      }
    } catch (err) {
      toast.error(describeError(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const isSignedIn = pb.authStore.isValid;

  useEffect(() => {
    if (!isSignedIn) nav("/welcome", { replace: true });
  }, [isSignedIn, nav]);

  async function useCurrentLocation(kind: "profile" | "challenge") {
    if (!("geolocation" in navigator)) {
      toast.error("Geolocation not supported");
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = pos.coords.latitude.toFixed(6);
        const lng = pos.coords.longitude.toFixed(6);
        if (kind === "profile") {
          setProfileForm((f) => ({ ...f, lat, lng }));
        } else {
          setDraft((d) => ({ ...d, targetLat: lat, targetLng: lng }));
        }
        toast.success("Location captured");
      },
      (err) => toast.error(err.message || "Location unavailable"),
      { enableHighAccuracy: true, maximumAge: 30000, timeout: 15000 },
    );
  }

  async function handleSaveProfile() {
    const name = profileForm.name.trim();
    if (!name) {
      toast.error("Business name is required");
      return;
    }
    setSavingProfile(true);
    try {
      const latNum = parseFloat(profileForm.lat);
      const lngNum = parseFloat(profileForm.lng);
      const saved = await upsertPartnerMe({
        name,
        logoEmoji: profileForm.logoEmoji.trim() || "🏪",
        address: profileForm.address.trim(),
        lat: Number.isFinite(latNum) ? latNum : 0,
        lng: Number.isFinite(lngNum) ? lngNum : 0,
      });
      setPartner(saved);
      toast.success("Partner profile saved");
      await load();
    } catch (err) {
      toast.error(describeError(err));
    } finally {
      setSavingProfile(false);
    }
  }

  async function handleCreateChallenge() {
    const title = draft.title.trim();
    const lat = parseFloat(draft.targetLat);
    const lng = parseFloat(draft.targetLng);
    if (!title) {
      toast.error("Title is required");
      return;
    }
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      toast.error("Set a target location");
      return;
    }
    setCreatingChallenge(true);
    try {
      await createPartnerChallenge({
        title,
        description: draft.description,
        targetLat: lat,
        targetLng: lng,
        targetLabel: draft.targetLabel,
        targetRadiusM: parseInt(draft.targetRadiusM, 10) || 30,
        originRadiusM: parseInt(draft.originRadiusM, 10) || 50,
        minElapsedSeconds: parseInt(draft.minElapsedSeconds, 10) || 0,
        xpReward: parseInt(draft.xpReward, 10) || 0,
        active: draft.active,
      });
      setDraft(emptyChallenge());
      setShowDraft(false);
      toast.success("Challenge created");
      await load();
    } catch (err) {
      toast.error(describeError(err));
    } finally {
      setCreatingChallenge(false);
    }
  }

  async function handleToggleActive(challenge: GeoChallengeDTO) {
    try {
      await updatePartnerChallenge(challenge.id, { active: !challenge.active });
      await load();
    } catch (err) {
      toast.error(describeError(err));
    }
  }

  async function handleDeleteChallenge(id: string) {
    if (!confirm("Delete this challenge? This can't be undone.")) return;
    try {
      await deletePartnerChallenge(id);
      await load();
    } catch (err) {
      toast.error(describeError(err));
    }
  }

  async function handleMarkRedeemed() {
    const code = scanCode.trim().toUpperCase();
    if (!code) return;
    setScanning(true);
    try {
      const res = await markPartnerRedemption(code);
      if (res.alreadyRedeemed) {
        toast.message("Code was already redeemed");
      } else {
        toast.success("Marked redeemed");
      }
      setScanCode("");
      await load();
    } catch (err) {
      toast.error(describeError(err));
    } finally {
      setScanning(false);
    }
  }

  return (
    <div className="min-h-screen pb-28">
      <div className="mx-auto max-w-md px-5">
        <PageHeader
          title="Partner portal"
          subtitle={partner ? partner.name : "Drive footfall with walk challenges"}
          back
          right={
            <div className="h-9 w-9 rounded-full bg-primary text-primary-foreground border-2 border-foreground grid place-items-center">
              <StoreIcon className="h-5 w-5" />
            </div>
          }
        />

        {loading ? (
          <div className="chunky-card p-6 text-center text-muted-foreground">
            Loading…
          </div>
        ) : (
          <div className="space-y-8 mt-2">
            {/* Stats */}
            {partner && (
              <section className="grid grid-cols-2 gap-3">
                <StatCard label="Active walks" value={stats.activeChallenges} />
                <StatCard label="Coupons" value={stats.totalCoupons} />
                <StatCard label="Issued" value={stats.issuedCoupons} />
                <StatCard label="Redeemed" value={stats.redeemedCoupons} />
              </section>
            )}

            {/* Business profile */}
            <section className="chunky-card p-5 bg-card">
              <h2 className="font-display font-black text-xl mb-1">
                {partner ? "Business profile" : "Set up your business"}
              </h2>
              <p className="text-xs text-muted-foreground mb-4">
                Appears on every walk challenge you publish.
              </p>
              <div className="space-y-3">
                <Field label="Business name">
                  <Input
                    value={profileForm.name}
                    onChange={(e) =>
                      setProfileForm((f) => ({ ...f, name: e.target.value }))
                    }
                    placeholder="Cafe Aroma"
                  />
                </Field>
                <div className="grid grid-cols-2 gap-3">
                  <Field label="Logo emoji">
                    <Input
                      value={profileForm.logoEmoji}
                      onChange={(e) =>
                        setProfileForm((f) => ({
                          ...f,
                          logoEmoji: e.target.value.slice(0, 4),
                        }))
                      }
                      placeholder="☕"
                    />
                  </Field>
                  <Field label="Address">
                    <Input
                      value={profileForm.address}
                      onChange={(e) =>
                        setProfileForm((f) => ({
                          ...f,
                          address: e.target.value,
                        }))
                      }
                      placeholder="12 High St"
                    />
                  </Field>
                </div>
                <div className="grid grid-cols-[1fr_1fr_auto] gap-2 items-end">
                  <Field label="Latitude">
                    <Input
                      type="number"
                      inputMode="decimal"
                      value={profileForm.lat}
                      onChange={(e) =>
                        setProfileForm((f) => ({ ...f, lat: e.target.value }))
                      }
                    />
                  </Field>
                  <Field label="Longitude">
                    <Input
                      type="number"
                      inputMode="decimal"
                      value={profileForm.lng}
                      onChange={(e) =>
                        setProfileForm((f) => ({ ...f, lng: e.target.value }))
                      }
                    />
                  </Field>
                  <Button
                    variant="outline"
                    onClick={() => useCurrentLocation("profile")}
                    title="Use my location"
                    className="h-10 px-3"
                  >
                    <Crosshair className="h-4 w-4" />
                  </Button>
                </div>
                <Button
                  onClick={handleSaveProfile}
                  disabled={savingProfile}
                  className="w-full h-11 font-black uppercase tracking-widest"
                >
                  {savingProfile ? "Saving…" : partner ? "Update profile" : "Create profile"}
                </Button>
              </div>
            </section>

            {/* Challenges */}
            {partner && (
              <section>
                <div className="flex items-center justify-between mb-3 px-1">
                  <div>
                    <h2 className="font-display font-black text-xl leading-none">
                      Promoted walks
                    </h2>
                    <p className="text-[10px] font-black uppercase tracking-widest text-muted-foreground mt-1">
                      Users walk to your spot, earn coupons
                    </p>
                  </div>
                  <Button
                    size="sm"
                    onClick={() => setShowDraft((s) => !s)}
                    className="h-9 font-black uppercase tracking-widest"
                  >
                    <Plus className="h-4 w-4 mr-1" />
                    {showDraft ? "Close" : "New"}
                  </Button>
                </div>

                {showDraft && (
                  <div className="chunky-card p-4 bg-muted/30 mb-4 space-y-3">
                    <Field label="Title">
                      <Input
                        value={draft.title}
                        onChange={(e) =>
                          setDraft((d) => ({ ...d, title: e.target.value }))
                        }
                        placeholder="Walk to Cafe Aroma"
                      />
                    </Field>
                    <Field label="Description">
                      <Textarea
                        value={draft.description}
                        onChange={(e) =>
                          setDraft((d) => ({ ...d, description: e.target.value }))
                        }
                        placeholder="Walk over and grab your coupon!"
                        rows={3}
                      />
                    </Field>
                    <div className="grid grid-cols-[1fr_1fr_auto] gap-2 items-end">
                      <Field label="Target lat">
                        <Input
                          type="number"
                          inputMode="decimal"
                          value={draft.targetLat}
                          onChange={(e) =>
                            setDraft((d) => ({ ...d, targetLat: e.target.value }))
                          }
                        />
                      </Field>
                      <Field label="Target lng">
                        <Input
                          type="number"
                          inputMode="decimal"
                          value={draft.targetLng}
                          onChange={(e) =>
                            setDraft((d) => ({ ...d, targetLng: e.target.value }))
                          }
                        />
                      </Field>
                      <Button
                        variant="outline"
                        onClick={() => useCurrentLocation("challenge")}
                        title="Use my location"
                        className="h-10 px-3"
                      >
                        <Crosshair className="h-4 w-4" />
                      </Button>
                    </div>
                    <Field label="Target label (optional)">
                      <Input
                        value={draft.targetLabel}
                        onChange={(e) =>
                          setDraft((d) => ({ ...d, targetLabel: e.target.value }))
                        }
                        placeholder="Front door"
                      />
                    </Field>
                    <div className="grid grid-cols-2 gap-2">
                      <Field label="Target radius (m)">
                        <Input
                          type="number"
                          value={draft.targetRadiusM}
                          onChange={(e) =>
                            setDraft((d) => ({ ...d, targetRadiusM: e.target.value }))
                          }
                        />
                      </Field>
                      <Field label="Origin radius (m)">
                        <Input
                          type="number"
                          value={draft.originRadiusM}
                          onChange={(e) =>
                            setDraft((d) => ({ ...d, originRadiusM: e.target.value }))
                          }
                        />
                      </Field>
                      <Field label="Min time (sec)">
                        <Input
                          type="number"
                          value={draft.minElapsedSeconds}
                          onChange={(e) =>
                            setDraft((d) => ({ ...d, minElapsedSeconds: e.target.value }))
                          }
                        />
                      </Field>
                      <Field label="XP reward">
                        <Input
                          type="number"
                          value={draft.xpReward}
                          onChange={(e) =>
                            setDraft((d) => ({ ...d, xpReward: e.target.value }))
                          }
                        />
                      </Field>
                    </div>
                    <div className="flex items-center justify-between">
                      <Label className="text-xs font-black uppercase tracking-widest">
                        Active
                      </Label>
                      <Switch
                        checked={draft.active}
                        onCheckedChange={(v) =>
                          setDraft((d) => ({ ...d, active: v }))
                        }
                      />
                    </div>
                    <Button
                      onClick={handleCreateChallenge}
                      disabled={creatingChallenge}
                      className="w-full h-11 font-black uppercase tracking-widest"
                    >
                      {creatingChallenge ? "Creating…" : "Create challenge"}
                    </Button>
                  </div>
                )}

                {challenges.length === 0 ? (
                  <div className="chunky-card p-4 bg-card text-center text-xs text-muted-foreground">
                    No promoted walks yet. Tap New to publish one.
                  </div>
                ) : (
                  <div className="space-y-3">
                    {challenges.map((c) => (
                      <ChallengeRow
                        key={c.id}
                        challenge={c}
                        onToggle={() => handleToggleActive(c)}
                        onDelete={() => handleDeleteChallenge(c.id)}
                        onCreateCoupon={async (body) => {
                          try {
                            await createPartnerCoupon({
                              challengeId: c.id,
                              ...body,
                            });
                            toast.success("Coupon template added");
                            await load();
                          } catch (err) {
                            toast.error(describeError(err));
                          }
                        }}
                        onDeleteCoupon={async (id) => {
                          try {
                            await deletePartnerCoupon(id);
                            await load();
                          } catch (err) {
                            toast.error(describeError(err));
                          }
                        }}
                      />
                    ))}
                  </div>
                )}
              </section>
            )}

            {/* Redemptions */}
            {partner && (
              <section>
                <div className="flex items-center gap-2 mb-3 px-1">
                  <h2 className="font-display font-black text-xl leading-none">
                    Redemptions
                  </h2>
                </div>

                <div className="chunky-card p-4 bg-card mb-3">
                  <Label className="text-[10px] font-black uppercase tracking-widest">
                    Mark a code as used
                  </Label>
                  <div className="mt-2 flex gap-2">
                    <Input
                      value={scanCode}
                      onChange={(e) => setScanCode(e.target.value.toUpperCase())}
                      placeholder="ABCD1234"
                      className="font-mono tracking-widest"
                    />
                    <Button
                      onClick={handleMarkRedeemed}
                      disabled={scanning || !scanCode.trim()}
                      className="font-black uppercase tracking-widest"
                    >
                      <ScanLine className="h-4 w-4 mr-1" />
                      {scanning ? "…" : "Mark"}
                    </Button>
                  </div>
                </div>

                {redemptions.length === 0 ? (
                  <div className="chunky-card p-4 bg-card text-center text-xs text-muted-foreground">
                    No redemptions yet.
                  </div>
                ) : (
                  <div className="space-y-2">
                    {redemptions.map((r) => (
                      <div
                        key={r.id}
                        className={cn(
                          "chunky-card p-3 bg-card flex items-center gap-3",
                          r.redeemed && "opacity-60",
                        )}
                      >
                        <div className="h-9 w-9 rounded-xl border-2 border-foreground bg-muted grid place-items-center">
                          {r.redeemed ? (
                            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                          ) : (
                            <Ticket className="h-4 w-4" />
                          )}
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="font-mono font-black text-sm tracking-widest">
                            {r.code}
                          </p>
                          <p className="text-[11px] text-muted-foreground truncate">
                            {r.userName || r.userEmail || r.userId} · {r.couponTitle}
                          </p>
                        </div>
                        <span className="text-[10px] font-black uppercase tracking-widest">
                          {r.redeemed ? "Used" : "Active"}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            )}
          </div>
        )}
      </div>
      <BottomNav />
    </div>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <Label className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
        {label}
      </Label>
      <div className="mt-1">{children}</div>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="chunky-card p-4 bg-card">
      <p className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
        {label}
      </p>
      <p className="font-display font-black text-2xl mt-1">{value}</p>
    </div>
  );
}

function ChallengeRow({
  challenge,
  onToggle,
  onDelete,
  onCreateCoupon,
  onDeleteCoupon,
}: {
  challenge: GeoChallengeDTO;
  onToggle: () => void;
  onDelete: () => void;
  onCreateCoupon: (body: {
    title: string;
    discountText: string;
    terms?: string;
    totalAvailable: number;
    expiresAt?: string;
  }) => void | Promise<void>;
  onDeleteCoupon: (id: string) => void | Promise<void>;
}) {
  const [showCouponForm, setShowCouponForm] = useState(false);
  const [couponTitle, setCouponTitle] = useState("");
  const [discount, setDiscount] = useState("");
  const [terms, setTerms] = useState("");
  const [total, setTotal] = useState("100");

  return (
    <div className="chunky-card p-4 bg-card">
      <div className="flex items-start gap-3">
        <div className="h-10 w-10 rounded-xl bg-muted border-2 border-foreground grid place-items-center shrink-0 text-lg">
          📍
        </div>
        <div className="min-w-0 flex-1">
          <p className="font-display font-black text-base truncate">
            {challenge.title}
          </p>
          <p className="text-[11px] text-muted-foreground truncate">
            {challenge.targetLat.toFixed(5)}, {challenge.targetLng.toFixed(5)}
            {challenge.targetLabel ? ` · ${challenge.targetLabel}` : ""}
          </p>
          <div className="mt-2 flex items-center gap-3">
            <Label className="text-[10px] font-black uppercase tracking-widest flex items-center gap-1.5">
              <Switch checked={challenge.active} onCheckedChange={onToggle} />
              {challenge.active ? "Active" : "Paused"}
            </Label>
            <button
              type="button"
              onClick={onDelete}
              className="text-xs text-red-600 font-bold inline-flex items-center gap-1"
            >
              <Trash2 className="h-3.5 w-3.5" />
              Delete
            </button>
          </div>
        </div>
      </div>

      <div className="mt-3 border-t-2 border-dashed border-foreground/20 pt-3">
        <div className="flex items-center justify-between mb-2">
          <p className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
            Coupons ({challenge.coupons?.length ?? 0})
          </p>
          <button
            type="button"
            onClick={() => setShowCouponForm((s) => !s)}
            className="text-[10px] font-black uppercase tracking-widest inline-flex items-center gap-1"
          >
            <Plus className="h-3 w-3" />
            {showCouponForm ? "Close" : "Add"}
          </button>
        </div>

        {challenge.coupons && challenge.coupons.length > 0 && (
          <div className="space-y-2 mb-2">
            {challenge.coupons.map((c) => (
              <div
                key={c.id}
                className="flex items-center gap-2 rounded-xl border-2 border-dashed border-foreground/30 p-2"
              >
                <Ticket className="h-3.5 w-3.5 shrink-0" />
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-bold truncate">{c.title}</p>
                  <p className="text-[11px] text-muted-foreground truncate">
                    {c.discountText}
                    {c.totalAvailable > 0
                      ? ` · ${c.issuedCount}/${c.totalAvailable} issued`
                      : ` · ${c.issuedCount} issued`}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => onDeleteCoupon(c.id)}
                  className="text-red-600 p-1"
                  aria-label="Delete coupon"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </div>
            ))}
          </div>
        )}

        {showCouponForm && (
          <div className="space-y-2 bg-muted/40 p-3 rounded-xl border-2 border-dashed border-foreground/20">
            <Input
              placeholder="Coupon title (e.g. 20% off coffee)"
              value={couponTitle}
              onChange={(e) => setCouponTitle(e.target.value)}
            />
            <Input
              placeholder="Discount text (e.g. 20% off)"
              value={discount}
              onChange={(e) => setDiscount(e.target.value)}
            />
            <Textarea
              placeholder="Terms (optional)"
              value={terms}
              onChange={(e) => setTerms(e.target.value)}
              rows={2}
            />
            <Input
              type="number"
              placeholder="Total available (0 = unlimited)"
              value={total}
              onChange={(e) => setTotal(e.target.value)}
            />
            <Button
              size="sm"
              onClick={async () => {
                if (!couponTitle.trim() || !discount.trim()) {
                  toast.error("Title and discount are required");
                  return;
                }
                await onCreateCoupon({
                  title: couponTitle.trim(),
                  discountText: discount.trim(),
                  terms: terms.trim() || undefined,
                  totalAvailable: parseInt(total, 10) || 0,
                });
                setCouponTitle("");
                setDiscount("");
                setTerms("");
                setTotal("100");
                setShowCouponForm(false);
              }}
              className="w-full font-black uppercase tracking-widest"
            >
              <MapPin className="h-4 w-4 mr-1" />
              Add coupon
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
