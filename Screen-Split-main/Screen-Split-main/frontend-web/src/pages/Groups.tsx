import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ClientResponseError } from "pocketbase";
import { store, User } from "@/lib/mockStore";
import { useStore } from "@/hooks/useStore";
import { PageHeader } from "@/components/screensplit/PageHeader";
import { BottomNav } from "@/components/screensplit/BottomNav";
import { UserPlus, Flame, Search, Sparkles, Check, X } from "lucide-react";
import { toast } from "sonner";
import { UserProfileOverlay } from "@/components/screensplit/UserProfileOverlay";
import {
  acceptFriendRequest,
  cancelFriendRequest,
  fetchFriends,
  fetchIncomingRequests,
  fetchOutgoingRequests,
  friendPayloadToUser,
  friendRecordToUser,
  rejectFriendRequest,
  requestFriendByEmail,
} from "@/lib/friendsApi";
import { pb } from "@/lib/pocketbase";

function pocketBaseErrorMessage(err: unknown): string {
  if (err instanceof ClientResponseError) {
    const data = err.response as { message?: string } | undefined;
    if (typeof data?.message === "string" && data.message) return data.message;
    return err.message || "Request failed";
  }
  return err instanceof Error ? err.message : "Something went wrong";
}

export default function Groups() {
  const nav = useNavigate();
  const me = useStore(() => store.currentUser());
  const [friends, setFriends] = useState<User[]>([]);
  const [incoming, setIncoming] = useState<
    Awaited<ReturnType<typeof fetchIncomingRequests>>
  >([]);
  const [outgoing, setOutgoing] = useState<
    Awaited<ReturnType<typeof fetchOutgoingRequests>>
  >([]);
  const [loading, setLoading] = useState(true);
  const [selectedFriend, setSelectedFriend] = useState<User | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [email, setEmail] = useState("");
  const [search, setSearch] = useState("");
  const [sendingRequest, setSendingRequest] = useState(false);
  const [busyRequestId, setBusyRequestId] = useState<string | null>(null);

  const loadAll = useCallback(async () => {
    if (!me) {
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const [friendEdges, incomingItems, outgoingItems] = await Promise.all([
        fetchFriends(),
        fetchIncomingRequests(),
        fetchOutgoingRequests(),
      ]);
      setFriends(friendEdges.map((e) => friendRecordToUser(e)));
      setIncoming(incomingItems);
      setOutgoing(outgoingItems);
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
      setFriends([]);
      setIncoming([]);
      setOutgoing([]);
    } finally {
      setLoading(false);
    }
  }, [me]);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  useEffect(() => {
    if (!me?.id || !pb.authStore.isValid) return;

    let cancelled = false;
    let debounceTimer: ReturnType<typeof setTimeout> | null = null;
    const scheduleReload = () => {
      if (cancelled) return;
      if (debounceTimer) clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        debounceTimer = null;
        if (!cancelled) void loadAll();
      }, 100);
    };

    const uid = me.id;
    const frFilter = `from = "${uid}" || to = "${uid}"`;
    const fsFilter = `owner = "${uid}" || peer = "${uid}"`;

    let unsubFr: (() => Promise<void>) | undefined;
    let unsubFs: (() => Promise<void>) | undefined;

    void (async () => {
      try {
        unsubFr = await pb.collection("friend_requests").subscribe(
          "*",
          () => scheduleReload(),
          { filter: frFilter },
        );
        unsubFs = await pb.collection("friendships").subscribe(
          "*",
          () => scheduleReload(),
          { filter: fsFilter },
        );
      } catch {
        /* realtime is best-effort; lists still refresh on actions + mount */
      }
    })();

    return () => {
      cancelled = true;
      if (debounceTimer) clearTimeout(debounceTimer);
      void unsubFr?.();
      void unsubFs?.();
    };
  }, [me?.id, loadAll]);

  const filteredFriends = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return friends;
    return friends.filter(
      (f) =>
        f.name.toLowerCase().includes(q) ||
        f.email.toLowerCase().includes(q),
    );
  }, [friends, search]);

  if (!me) {
    nav("/welcome", { replace: true });
    return null;
  }

  const sendRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    setSendingRequest(true);
    try {
      const res = await requestFriendByEmail(email);
      const messages: Record<string, string> = {
        created: "Friend request sent",
        already_friends: "Already friends",
        request_pending: "You already sent a request to this person",
        now_friends: "You're now friends",
      };
      toast.success(messages[res.status] ?? "Done");
      setShowAdd(false);
      setEmail("");
      await loadAll();
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
    } finally {
      setSendingRequest(false);
    }
  };

  const onAccept = async (requestId: string) => {
    setBusyRequestId(requestId);
    try {
      await acceptFriendRequest(requestId);
      toast.success("Friend request accepted");
      await loadAll();
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
    } finally {
      setBusyRequestId(null);
    }
  };

  const onReject = async (requestId: string) => {
    setBusyRequestId(requestId);
    try {
      await rejectFriendRequest(requestId);
      toast.success("Request declined");
      await loadAll();
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
    } finally {
      setBusyRequestId(null);
    }
  };

  const onCancelOutgoing = async (requestId: string) => {
    setBusyRequestId(requestId);
    try {
      await cancelFriendRequest(requestId);
      toast.success("Request cancelled");
      await loadAll();
    } catch (err) {
      toast.error(pocketBaseErrorMessage(err));
    } finally {
      setBusyRequestId(null);
    }
  };

  return (
    <div className="min-h-screen pb-28">
      <div className="mx-auto max-w-md px-5">
        <PageHeader
          title="Friends"
          subtitle="Track your crew members"
          right={
            <button
              onClick={() => setShowAdd(true)}
              className="grid place-items-center h-9 w-9 rounded-full border-2 border-foreground bg-primary text-primary-foreground hover:bg-primary/90 shadow-[2px_2px_0px_rgba(0,0,0,1)] active:translate-x-0.5 active:translate-y-0.5 active:shadow-none transition-all"
            >
              <UserPlus className="h-4 w-4" />
            </button>
          }
        />

        <div className="mt-6 space-y-4">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search friends..."
              className="w-full bg-card border-2 border-foreground rounded-xl pl-10 pr-4 py-2.5 font-display font-medium outline-none focus:ring-2 ring-primary/20 transition-all"
            />
          </div>

          {!loading && incoming.length > 0 && (
            <div className="space-y-2">
              <p className="text-xs font-black uppercase tracking-widest text-muted-foreground px-1">
                Friend requests
              </p>
              <div className="space-y-2">
                {incoming.map((req) => {
                  const u = friendPayloadToUser(req.fromUser);
                  const busy = busyRequestId === req.id;
                  return (
                    <div
                      key={req.id}
                      className="chunky-card p-4 bg-card border-2 border-foreground flex flex-col gap-3"
                    >
                      <div className="flex items-center gap-3">
                        <div className="h-12 w-12 rounded-full border-2 border-foreground bg-accent grid place-items-center text-2xl shrink-0">
                          {u.avatar}
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="font-display font-bold leading-tight truncate">
                            {u.name}
                          </p>
                          <p className="text-xs font-bold text-muted-foreground truncate">
                            {u.email}
                          </p>
                        </div>
                      </div>
                      <div className="flex gap-2">
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void onAccept(req.id)}
                          className="flex-1 h-10 rounded-xl border-2 border-foreground bg-primary text-primary-foreground font-display font-black text-sm shadow-[3px_3px_0px_rgba(0,0,0,1)] active:translate-x-0.5 active:translate-y-0.5 active:shadow-none disabled:opacity-50 flex items-center justify-center gap-1.5"
                        >
                          <Check className="h-4 w-4" />
                          Accept
                        </button>
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void onReject(req.id)}
                          className="flex-1 h-10 rounded-xl border-2 border-foreground bg-muted font-display font-black text-sm shadow-[3px_3px_0px_rgba(0,0,0,1)] active:translate-x-0.5 active:translate-y-0.5 active:shadow-none disabled:opacity-50 flex items-center justify-center gap-1.5"
                        >
                          <X className="h-4 w-4" />
                          Decline
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {!loading && outgoing.length > 0 && (
            <div className="space-y-2">
              <p className="text-xs font-black uppercase tracking-widest text-muted-foreground px-1">
                Waiting for a response
              </p>
              <div className="space-y-2">
                {outgoing.map((req) => {
                  const u = friendPayloadToUser(req.toUser);
                  const busy = busyRequestId === req.id;
                  return (
                    <div
                      key={req.id}
                      className="chunky-card p-3 bg-card border-2 border-dashed border-foreground/30 flex items-center gap-3"
                    >
                      <div className="h-10 w-10 rounded-full border-2 border-foreground bg-accent grid place-items-center text-lg shrink-0">
                        {u.avatar}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="font-display font-bold text-sm truncate">
                          {u.name}
                        </p>
                        <p className="text-[10px] font-bold text-muted-foreground truncate">
                          {u.email}
                        </p>
                      </div>
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => void onCancelOutgoing(req.id)}
                        className="shrink-0 text-xs font-black uppercase tracking-wide text-destructive underline-offset-2 hover:underline disabled:opacity-50"
                      >
                        Cancel
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {loading ? (
            <div className="chunky-card p-8 bg-card text-center border-2 border-foreground/20 mt-4">
              <p className="font-display font-bold text-muted-foreground">
                Loading…
              </p>
            </div>
          ) : friends.length === 0 ? (
            <div className="chunky-card p-8 bg-card text-center border-2 border-dashed border-foreground/20 mt-4">
              <Sparkles className="h-8 w-8 mx-auto mb-3 text-primary" />
              <p className="font-display font-bold text-lg">No friends yet</p>
              <p className="text-sm text-muted-foreground mt-1">
                Send a friend request by email. They&apos;ll need to accept
                before you show up in each other&apos;s lists.
              </p>
              <button
                onClick={() => setShowAdd(true)}
                className="mt-6 px-6 py-2 bg-foreground text-background font-display font-bold rounded-full hover:opacity-90"
              >
                Find friends
              </button>
            </div>
          ) : filteredFriends.length === 0 ? (
            <div className="chunky-card p-6 bg-card text-center border-2 border-dashed border-foreground/20 mt-2">
              <p className="font-display font-bold text-muted-foreground">
                No friends match “{search.trim()}”
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3">
              {filteredFriends.map((f) => (
                <button
                  key={f.id}
                  onClick={() => setSelectedFriend(f)}
                  className="chunky-card p-4 bg-card hover:-translate-y-1 transition-all border-2 border-foreground flex items-center gap-4 text-left active:scale-[0.98]"
                >
                  <div className="h-14 w-14 rounded-full border-2 border-foreground bg-accent grid place-items-center text-3xl shrink-0">
                    {f.avatar}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-display font-bold text-lg leading-tight truncate">
                      {f.name}
                    </p>
                    <div className="flex items-center gap-2 mt-1">
                      {f.streak > 0 && (
                        <span className="inline-flex items-center gap-1 text-[10px] font-black uppercase tracking-wider px-2 py-0.5 rounded-full bg-orange-100 text-orange-600 border border-orange-200">
                          <Flame className="h-3 w-3 fill-current" /> {f.streak}{" "}
                          Day Streak
                        </span>
                      )}
                      <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">
                        {f.badges.length} Badges
                      </span>
                    </div>
                  </div>
                  <div className="h-8 w-8 rounded-full border-2 border-foreground flex items-center justify-center opacity-20 group-hover:opacity-100 italic font-black text-xs">
                    i
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {showAdd && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center px-6"
          onClick={() => setShowAdd(false)}
        >
          <div className="absolute inset-0 bg-foreground/60 backdrop-blur-sm" />
          <div
            className="relative w-full max-w-sm bg-background border-4 border-foreground rounded-[2rem] p-6 animate-scale shadow-[8px_8px_0px_rgba(0,0,0,1)]"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className="font-display text-2xl font-black mb-1">
              Send request
            </h2>
            <p className="text-sm text-muted-foreground mb-4">
              They can accept or decline from their Friends tab when they sign
              in.
            </p>
            <form onSubmit={sendRequest} className="space-y-4">
              <div>
                <label className="text-xs font-black uppercase tracking-widest text-muted-foreground ml-1">
                  Their email
                </label>
                <input
                  autoFocus
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="example@gmail.com"
                  className="w-full mt-1.5 bg-muted border-2 border-foreground rounded-xl px-4 py-3 font-display font-bold outline-none focus:border-primary transition-all"
                />
              </div>
              <button
                type="submit"
                disabled={sendingRequest}
                className="w-full h-12 bg-primary text-primary-foreground font-display font-black rounded-xl border-2 border-foreground shadow-[4px_4px_0px_rgba(0,0,0,1)] active:translate-x-0.5 active:translate-y-0.5 active:shadow-none disabled:opacity-60"
              >
                {sendingRequest ? "Sending…" : "Send friend request"}
              </button>
              <button
                type="button"
                onClick={() => setShowAdd(false)}
                className="w-full text-sm font-bold text-muted-foreground underline"
              >
                Cancel
              </button>
            </form>
          </div>
        </div>
      )}

      {selectedFriend && (
        <UserProfileOverlay
          user={selectedFriend}
          onClose={() => setSelectedFriend(null)}
          onFriendRemoved={() => {
            void loadAll();
          }}
        />
      )}

      <BottomNav />
    </div>
  );
}
