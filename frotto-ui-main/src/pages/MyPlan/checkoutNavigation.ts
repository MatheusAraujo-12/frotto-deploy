export function navigateToCheckout(checkoutUrl: string): void {
  const target = new URL(checkoutUrl);
  if (target.protocol !== "https:") throw new Error("Invalid checkout URL");
  window.location.assign(target.toString());
}
