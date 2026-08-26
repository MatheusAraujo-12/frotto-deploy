import { navigateToCheckout } from "./checkoutNavigation";

describe("checkout navigation", () => {
  const assign = jest.fn();
  beforeEach(() => assign.mockReset());

  it("navigates externally to a valid HTTPS checkout URL", () => {
    Object.defineProperty(window, "location", { configurable: true, value: { assign } });
    navigateToCheckout("https://www.mercadopago.com.br/subscriptions/checkout?id=1");
    expect(assign).toHaveBeenCalledWith("https://www.mercadopago.com.br/subscriptions/checkout?id=1");
  });

  it.each(["javascript:alert(1)", "http://unsafe.test/checkout"])("rejects unsafe URL %s", (url) => {
    expect(() => navigateToCheckout(url)).toThrow("Invalid checkout URL");
    expect(assign).not.toHaveBeenCalled();
  });
});
