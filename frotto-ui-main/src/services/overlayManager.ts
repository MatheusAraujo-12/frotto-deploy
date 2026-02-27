type DismissibleOverlayElement = HTMLElement & {
  dismiss?: () => Promise<boolean>;
};

const OVERLAY_SELECTOR = [
  "ion-action-sheet",
  "ion-alert",
  "ion-loading",
  "ion-modal",
  "ion-picker",
  "ion-popover",
  "ion-toast",
].join(", ");

export const dismissAllOverlays = async (reason = "overlay-manager") => {
  if (typeof document === "undefined") {
    return;
  }

  const overlays = Array.from(
    document.querySelectorAll(OVERLAY_SELECTOR)
  ) as DismissibleOverlayElement[];

  if (!overlays.length) {
    return;
  }

  if (process.env.NODE_ENV === "development") {
    // eslint-disable-next-line no-console
    console.log(
      `[overlayManager] ${reason} overlays=${overlays.length} backdrops=${document.querySelectorAll("ion-backdrop").length}`
    );
  }

  await Promise.allSettled(
    overlays.map((overlay) => {
      if (typeof overlay.dismiss !== "function") {
        return Promise.resolve(false);
      }

      return overlay.dismiss().catch(() => false);
    })
  );
};
