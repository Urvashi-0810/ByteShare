/**
 * Keeps focused fields visible when the OS keyboard opens (mobile WebViews + browsers).
 */
export function initKeyboardViewportHelpers(): void {
  const root = document.documentElement;

  const syncInset = () => {
    const vv = window.visualViewport;
    if (!vv) return;
    const overlap = Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
    root.style.setProperty("--keyboard-inset", `${overlap}px`);
  };

  const vv = window.visualViewport;
  if (vv) {
    vv.addEventListener("resize", syncInset);
    vv.addEventListener("scroll", syncInset);
    syncInset();
  }

  document.addEventListener(
    "focusin",
    (e) => {
      const t = e.target;
      if (!(t instanceof HTMLElement)) return;
      if (!["INPUT", "TEXTAREA", "SELECT"].includes(t.tagName)) return;
      requestAnimationFrame(() => {
        t.scrollIntoView({
          block: "center",
          inline: "nearest",
          behavior: "smooth",
        });
      });
    },
    true,
  );
}
