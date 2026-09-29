import type { Theme } from "./theme";

describe("theme lifecycle", () => {
  let theme: typeof import("./theme");
  let dark: boolean;
  let systemChange: () => void;
  let events: Array<[string, EventListenerOrEventListenerObject]>;
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    jest.resetModules();
    localStorage.clear();
    delete document.documentElement.dataset.theme;
    document.body.className = "";
    dark = false;
    systemChange = () => {};
    events = [];
    window.matchMedia = jest.fn().mockImplementation(() => ({
      get matches() { return dark; },
      addEventListener: (_event: string, listener: () => void) => { systemChange = listener; },
    }));
    const add = window.addEventListener.bind(window);
    jest.spyOn(window, "addEventListener").mockImplementation((event, listener, options) => {
      events.push([event, listener]);
      add(event, listener, options);
    });
    theme = require("./theme");
  });

  afterEach(() => {
    events.forEach(([event, listener]) => window.removeEventListener(event, listener));
    jest.restoreAllMocks();
    window.matchMedia = originalMatchMedia;
  });

  it("restores an explicit light preference over a dark system", () => {
    dark = true;
    localStorage.setItem("app-theme", "light");
    expect(theme.initTheme()).toBe("light");
    expect(document.body.classList.contains("light")).toBe(true);
    expect(document.body.classList.contains("dark")).toBe(false);
    expect(document.documentElement.dataset.theme).toBe("light");
  });

  it("follows system changes only until the user makes a choice", () => {
    theme.initTheme();
    dark = true;
    systemChange();
    expect(theme.getActiveTheme()).toBe("dark");
    theme.setTheme("light");
    systemChange();
    expect(theme.getActiveTheme()).toBe("light");
    expect(localStorage.getItem("app-theme")).toBe("light");
  });

  it("reuses the pre-paint theme and initializes listeners only once", () => {
    document.documentElement.dataset.theme = "dark";
    document.documentElement.style.backgroundColor = "rgb(13, 27, 42)";
    expect(theme.initTheme()).toBe("dark");
    expect(theme.initTheme()).toBe("dark");
    expect(events.filter(([name]) => name === "storage")).toHaveLength(1);
    expect(document.documentElement.style.backgroundColor).toBe("");
  });

  it("notifies subscribers and synchronizes choices from another tab", () => {
    const changes: Theme[] = [];
    const unsubscribe = theme.subscribeTheme(value => changes.push(value));
    theme.initTheme();
    localStorage.setItem("app-theme", "dark");
    window.dispatchEvent(new StorageEvent("storage", { key: "app-theme", newValue: "dark" }));
    expect(document.body.classList.contains("dark")).toBe(true);
    expect(changes).toEqual(["light", "dark"]);
    unsubscribe();
    theme.setTheme("light");
    expect(changes).toHaveLength(2);
  });

  it("keeps a session choice when storage is unavailable", () => {
    jest.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("blocked"); });
    jest.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("blocked"); });
    theme.initTheme();
    expect(() => theme.setTheme("dark")).not.toThrow();
    systemChange();
    expect(theme.getActiveTheme()).toBe("dark");
  });

  it("ignores invalid persisted values", () => {
    dark = true;
    localStorage.setItem("app-theme", "invalid");
    expect(theme.getTheme()).toBeNull();
    expect(theme.initTheme()).toBe("dark");
  });
});
