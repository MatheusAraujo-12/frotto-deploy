/**
 * A selectable card that also holds its own controls (buttons, links, the selection checkbox...): the card reacts only
 * to its free area. Any event that starts in an interactive element inside the card belongs to that element.
 */
const INTERACTIVE_SELECTOR = [
  "a[href]",
  "button",
  "input",
  "select",
  "textarea",
  "[contenteditable='true']",
  "[role='button']",
  "[role='link']",
  "[role='menuitem']",
  "[role='checkbox']",
  "[role='switch']",
  "ion-button",
  "ion-checkbox",
  "ion-toggle",
  "ion-radio",
  "ion-select",
  "ion-input",
  "ion-textarea",
  "ion-searchbar",
].join(", ");

/** True when the event comes from an interactive element inside the card (not from the card itself). */
export function isFromInnerControl(event: { target: EventTarget | null; currentTarget: EventTarget | null }): boolean {
  const card = event.currentTarget as Element | null;
  const target = event.target as Element | null;
  if (!card || !target || target === card || typeof target.closest !== "function") {
    return false;
  }
  const control = target.closest(INTERACTIVE_SELECTOR);
  return Boolean(control && control !== card && card.contains(control));
}

/** Enter / Space pressed on the card itself (focus on the card, not on one of its controls). */
export function isCardActivationKey(event: { key: string; target: EventTarget | null; currentTarget: EventTarget | null }): boolean {
  return event.target === event.currentTarget && (event.key === "Enter" || event.key === " " || event.key === "Spacebar");
}
