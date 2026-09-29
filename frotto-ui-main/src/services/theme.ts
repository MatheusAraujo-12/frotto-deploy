export type Theme = "dark" | "light";

const THEME_KEY = "app-theme";
const THEME_EVENT = "frotto:theme-change";
let initialized = false;
let sessionChoice: Theme | null = null;

const isTheme = (value: unknown): value is Theme => value === "dark" || value === "light";

export const getTheme = (): Theme | null => {
  try {
    const saved = localStorage.getItem(THEME_KEY);
    return isTheme(saved) ? saved : null;
  } catch {
    return null;
  }
};

const getSystemTheme = (): Theme =>
  window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";

export const getActiveTheme = (): Theme => {
  const active = document.documentElement.dataset.theme;
  return isTheme(active) ? active : getTheme() || getSystemTheme();
};

export const applyTheme = (theme: Theme) => {
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
  document.body.classList.toggle("dark", theme === "dark");
  document.body.classList.toggle("light", theme === "light");
  // CSS is loaded by this point; release the pre-paint critical background.
  document.documentElement.style.removeProperty("background-color");
  window.dispatchEvent(new CustomEvent<Theme>(THEME_EVENT, { detail: theme }));
};

export const setTheme = (theme: Theme) => {
  sessionChoice = theme;
  try {
    localStorage.setItem(THEME_KEY, theme);
  } catch {
    // Restricted storage must not prevent a choice for the current session.
  }
  applyTheme(theme);
};

export const subscribeTheme = (listener: (theme: Theme) => void): (() => void) => {
  const handler = (event: Event) => listener((event as CustomEvent<Theme>).detail);
  window.addEventListener(THEME_EVENT, handler);
  return () => window.removeEventListener(THEME_EVENT, handler);
};

export const initTheme = (): Theme => {
  if (initialized) return getActiveTheme();
  initialized = true;
  // Reuse the pre-paint choice; do not reinitialize from the menu.
  const initial = getActiveTheme();
  applyTheme(initial);
  const media = window.matchMedia?.("(prefers-color-scheme: dark)");
  const syncSystem = () => {
    if (!sessionChoice && !getTheme()) applyTheme(getSystemTheme());
  };
  if (media?.addEventListener) media.addEventListener("change", syncSystem);
  else media?.addListener?.(syncSystem);
  window.addEventListener("storage", (event) => {
    if (event.key === THEME_KEY || event.key === null) {
      sessionChoice = null;
      applyTheme(getTheme() || getSystemTheme());
    }
  });
  return initial;
};

const themeApi = { initTheme, setTheme, getTheme, getActiveTheme, applyTheme, subscribeTheme };
export default themeApi;
