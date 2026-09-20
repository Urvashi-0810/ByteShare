import { useEffect, useState } from "react";
import { store } from "@/lib/mockStore";

/**
 * Subscribe to the mock store and re-run `selector` whenever it changes.
 * We deliberately avoid useSyncExternalStore — selectors return fresh
 * arrays/objects, which would trip its reference-equality check.
 */
export function useStore<T>(selector: () => T): T {
  const [value, setValue] = useState<T>(() => selector());
  useEffect(() => {
    // Re-read once on mount in case state changed between render and effect.
    setValue(selector());
    const unsub = store.subscribe(() => setValue(selector()));
    return () => {
      unsub();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return value;
}
