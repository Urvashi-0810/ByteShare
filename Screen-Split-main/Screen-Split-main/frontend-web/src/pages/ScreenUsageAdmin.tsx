import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ClientResponseError } from "pocketbase";
import { ArrowLeft, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { pb } from "@/lib/pocketbase";
import { store } from "@/lib/mockStore";
import { useStore } from "@/hooks/useStore";
import {
  adminUpsertScreenUsage,
  pbAuthRecordToUser,
  USAGE_CATEGORIES,
} from "@/lib/screenUsageApi";
import type { AppUsage, Category } from "@/lib/rank";
import {
  fetchFriends,
  friendRecordToUser,
} from "@/lib/friendsApi";

const SECRET_KEY = "screen_usage_admin_secret";

function pocketBaseErrorMessage(err: unknown): string {
  if (err instanceof ClientResponseError) {
    const data = err.response as { message?: string } | undefined;
    if (typeof data?.message === "string" && data.message) return data.message;
    return err.message || "Request failed";
  }
  return err instanceof Error ? err.message : "Something went wrong";
}

type Row = { appName: string; category: Category; minutes: string };

function emptyRow(): Row {
  return { appName: "", category: "social", minutes: "30" };
}

export default function ScreenUsageAdmin() {
  const nav = useNavigate();
  const me = useStore(() => store.currentUser());
  const [secret, setSecret] = useState(() =>
    typeof sessionStorage !== "undefined"
      ? sessionStorage.getItem(SECRET_KEY) ?? ""
      : "",
  );
  const [reportDate, setReportDate] = useState(() =>
    new Date().toISOString().slice(0, 10),
  );
  const [targetUserId, setTargetUserId] = useState("");
  const [rows, setRows] = useState<Row[]>([emptyRow()]);
  const [saving, setSaving] = useState(false);
  const [friendOptions, setFriendOptions] = useState<
    { id: string; label: string }[]
  >([]);

  useEffect(() => {
    if (!me) {
      nav("/welcome", { replace: true });
    }
  }, [me, nav]);

  useEffect(() => {
    const self = pbAuthRecordToUser();
    if (self) {
      setTargetUserId((prev) => (prev.trim() === "" ? self.id : prev));
    }
  }, [me?.id]);

  const loadFriends = useCallback(async () => {
    if (!me) return;
    try {
      const edges = await fetchFriends();
      const opts = edges.map((e) => ({
        id: e.user.id,
        label: `${e.user.name} (${e.user.email})`,
      }));
      const self = pbAuthRecordToUser();
      if (self) {
        opts.unshift({
          id: self.id,
          label: `${self.name} (you)`,
        });
      }
      setFriendOptions(opts);
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
    }
  }, [me]);

  useEffect(() => {
    void loadFriends();
  }, [loadFriends]);

  const rowsToApps = useMemo((): AppUsage[] => {
    return rows
      .filter((r) => r.appName.trim() !== "")
      .map((r) => ({
        appName: r.appName.trim(),
        category: r.category,
        minutes: Math.max(0, Number(r.minutes) || 0),
      }));
  }, [rows]);

  const persistSecret = (s: string) => {
    setSecret(s);
    try {
      sessionStorage.setItem(SECRET_KEY, s);
    } catch {
      /* ignore */
    }
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedSecret = secret.trim();
    if (!trimmedSecret) {
      toast.error("Admin secret is required (set SCREEN_USAGE_ADMIN_SECRET on the server).");
      return;
    }
    const uid = targetUserId.trim();
    if (!uid) {
      toast.error("Pick a user or paste a user id.");
      return;
    }
    setSaving(true);
    try {
      await adminUpsertScreenUsage({
        adminSecret: trimmedSecret,
        userId: uid,
        reportDate: reportDate.trim(),
        apps: rowsToApps,
      });
      toast.success("Screen usage saved");
      const self = pbAuthRecordToUser();
      const edges = await fetchFriends();
      const users = [
        ...(self ? [self] : []),
        ...edges.map(friendRecordToUser),
      ];
      const byId = new Map(users.map((u) => [u.id, u]));
      if (me) byId.set(me.id, me);
      store.mergeRemoteUsers([...byId.values()]);
      store.applyUsageSnapshot(uid, rowsToApps);
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  if (!me) return null;

  return (
    <div className="min-h-screen pb-16">
      <div className="mx-auto max-w-md px-5 pt-6">
        <div className="flex items-center gap-3 mb-6">
          <Link
            to="/social"
            className="grid place-items-center h-10 w-10 rounded-full border-2 border-foreground bg-card"
          >
            <ArrowLeft className="h-4 w-4" />
          </Link>
          <div>
            <h1 className="font-display text-2xl font-black">Usage admin</h1>
            <p className="text-xs text-muted-foreground font-bold">
              Temporary: seed per-user screen time until the Android client
              posts snapshots.
            </p>
          </div>
        </div>

        <form onSubmit={submit} className="space-y-5">
          <div className="chunky-card p-4 bg-card border-2 border-foreground space-y-3">
            <label className="block">
              <span className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
                Admin secret
              </span>
              <input
                type="password"
                autoComplete="off"
                value={secret}
                onChange={(e) => persistSecret(e.target.value)}
                placeholder="Matches SCREEN_USAGE_ADMIN_SECRET"
                className="mt-1.5 w-full bg-muted border-2 border-foreground rounded-xl px-3 py-2.5 font-mono text-sm outline-none focus:border-primary"
              />
            </label>
            <label className="block">
              <span className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
                Report date (UTC day)
              </span>
              <input
                type="date"
                value={reportDate}
                onChange={(e) => setReportDate(e.target.value)}
                className="mt-1.5 w-full bg-muted border-2 border-foreground rounded-xl px-3 py-2.5 font-display font-bold outline-none focus:border-primary"
              />
            </label>
            <label className="block">
              <span className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
                User
              </span>
              <select
                value={
                  friendOptions.some((o) => o.id === targetUserId)
                    ? targetUserId
                    : ""
                }
                onChange={(e) => setTargetUserId(e.target.value)}
                className="mt-1.5 w-full bg-muted border-2 border-foreground rounded-xl px-3 py-2.5 font-display font-bold outline-none focus:border-primary"
              >
                <option value="">— Select —</option>
                {friendOptions.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
                Or paste PocketBase user id
              </span>
              <input
                value={targetUserId}
                onChange={(e) => setTargetUserId(e.target.value)}
                placeholder={
                  (pb.authStore.record as { id?: string } | undefined)?.id ??
                  "user id"
                }
                className="mt-1.5 w-full bg-muted border-2 border-foreground rounded-xl px-3 py-2.5 font-mono text-xs outline-none focus:border-primary"
              />
            </label>
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between px-1">
              <span className="text-[10px] font-black uppercase tracking-widest text-muted-foreground">
                Apps
              </span>
              <button
                type="button"
                onClick={() => setRows((r) => [...r, emptyRow()])}
                className="inline-flex items-center gap-1 text-xs font-black uppercase text-primary"
              >
                <Plus className="h-3.5 w-3.5" /> Row
              </button>
            </div>
            {rows.map((row, i) => (
              <div
                key={i}
                className="chunky-card p-3 bg-card border-2 border-foreground/80 grid grid-cols-12 gap-2 items-end"
              >
                <div className="col-span-12 sm:col-span-5">
                  <label className="text-[9px] font-bold text-muted-foreground uppercase">
                    App
                  </label>
                  <input
                    value={row.appName}
                    onChange={(e) =>
                      setRows((prev) =>
                        prev.map((x, j) =>
                          j === i ? { ...x, appName: e.target.value } : x,
                        ),
                      )
                    }
                    className="mt-0.5 w-full bg-muted border-2 border-foreground rounded-lg px-2 py-1.5 text-sm font-display font-semibold outline-none"
                  />
                </div>
                <div className="col-span-6 sm:col-span-4">
                  <label className="text-[9px] font-bold text-muted-foreground uppercase">
                    Category
                  </label>
                  <select
                    value={row.category}
                    onChange={(e) =>
                      setRows((prev) =>
                        prev.map((x, j) =>
                          j === i
                            ? {
                                ...x,
                                category: e.target.value as Category,
                              }
                            : x,
                        ),
                      )
                    }
                    className="mt-0.5 w-full bg-muted border-2 border-foreground rounded-lg px-2 py-1.5 text-sm font-display font-semibold outline-none"
                  >
                    {USAGE_CATEGORIES.map((c) => (
                      <option key={c.value} value={c.value}>
                        {c.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="col-span-4 sm:col-span-2">
                  <label className="text-[9px] font-bold text-muted-foreground uppercase">
                    Min
                  </label>
                  <input
                    type="number"
                    min={0}
                    value={row.minutes}
                    onChange={(e) =>
                      setRows((prev) =>
                        prev.map((x, j) =>
                          j === i ? { ...x, minutes: e.target.value } : x,
                        ),
                      )
                    }
                    className="mt-0.5 w-full bg-muted border-2 border-foreground rounded-lg px-2 py-1.5 text-sm font-mono outline-none"
                  />
                </div>
                <div className="col-span-2 sm:col-span-1 flex justify-end">
                  <button
                    type="button"
                    disabled={rows.length <= 1}
                    onClick={() =>
                      setRows((prev) => prev.filter((_, j) => j !== i))
                    }
                    className="h-9 w-9 rounded-lg border-2 border-foreground bg-muted grid place-items-center disabled:opacity-30"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>

          <button
            type="submit"
            disabled={saving}
            className="w-full h-12 bg-primary text-primary-foreground font-display font-black rounded-xl border-2 border-foreground shadow-[4px_4px_0px_rgba(0,0,0,1)] active:translate-x-0.5 active:translate-y-0.5 active:shadow-none disabled:opacity-60"
          >
            {saving ? "Saving…" : "Save snapshot"}
          </button>
        </form>
      </div>
    </div>
  );
}
