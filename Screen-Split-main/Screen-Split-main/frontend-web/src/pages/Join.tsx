import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ClientResponseError } from "pocketbase";
import { store } from "@/lib/mockStore";
import { joinCrewByCode } from "@/lib/crewsApi";

function pocketBaseErrorMessage(err: unknown): string {
  if (err instanceof ClientResponseError) {
    const data = err.response as { message?: string } | undefined;
    if (typeof data?.message === "string" && data.message) return data.message;
    return err.message || "Request failed";
  }
  return err instanceof Error ? err.message : "Something went wrong";
}

export default function Join() {
  const { code } = useParams();
  const nav = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!code) return;
    const me = store.currentUser();
    if (!me) {
      sessionStorage.setItem("ss_pending_join", code);
      nav("/welcome", { replace: true });
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const { crew } = await joinCrewByCode(me.id, code);
        if (!cancelled) nav(`/group/${crew.id}`, { replace: true });
      } catch (err) {
        if (!cancelled) {
          setError(pocketBaseErrorMessage(err));
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [code, nav]);

  return (
    <div className="min-h-screen grid place-items-center px-6 text-center">
      {error ? (
        <div>
          <p className="font-display text-2xl font-bold">{error}</p>
          <button onClick={() => nav("/groups")} className="mt-3 underline">Go to your crews</button>
        </div>
      ) : (
        <p className="font-display text-2xl animate-pulse">Joining…</p>
      )}
    </div>
  );
}
