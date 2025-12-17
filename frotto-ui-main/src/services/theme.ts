export type Theme = "dark" | "light";

const THEME_KEY = "app-theme";

export const applyTheme = (theme: Theme) => {
  const enableDark = theme === "dark";
  document.body.classList.toggle("dark", enableDark);
  document.documentElement.style.colorScheme = enableDark ? "dark" : "light";
};

export const setTheme = (theme: Theme) => {
  localStorage.setItem(THEME_KEY, theme);
  applyTheme(theme);
};

export const getTheme = (): Theme | null => {
  const t = localStorage.getItem(THEME_KEY);
  if (t === "dark" || t === "light") return t;
  return null;
};

export const initTheme = () => {
  const saved = getTheme();
  if (saved) {
    applyTheme(saved);
    return saved;
  }

  // Fallback to system preference
  const prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
  const initial: Theme = prefersDark ? "dark" : "light";
  applyTheme(initial);
  return initial;
};

const themeApi = {
  initTheme,
  setTheme,
  getTheme,
  applyTheme,
};

export default themeApi;
