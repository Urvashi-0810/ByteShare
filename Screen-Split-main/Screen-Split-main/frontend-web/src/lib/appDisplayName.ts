const PKG_LIKE = /^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/i;

function titleCaseWords(s: string): string {
  return s.replace(/\w+/g, (w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase());
}

/** Readable app title when data is still a package id (e.g. com.instagram.android). */
export function appDisplayName(raw: string): string {
  const t = raw.trim();
  if (!t) return t;
  if (!PKG_LIKE.test(t)) return t;
  const segs = t.split(".").filter(Boolean);
  if (!segs.length) return t;
  const pick =
    segs.length >= 2 && segs[segs.length - 1] === "android"
      ? segs[segs.length - 2]!
      : segs[segs.length - 1]!;
  return titleCaseWords(pick.replace(/_/g, " "));
}
