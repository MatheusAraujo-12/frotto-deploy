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
  )
    .filter((overlay) => {
      const element = overlay as DismissibleOverlayElement;

      if (!element.isConnected) {
        return false;
      }

      if (element.classList.contains("overlay-hidden")) {
        return false;
      }

      if (element.getAttribute("aria-hidden") === "true") {
        return false;
      }

      return typeof element.dismiss === "function";
    }) as DismissibleOverlayElement[];

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
    overlays.map((overlay) => overlay.dismiss!().catch(() => false))
  );
};
