import { isCardActivationKey, isFromInnerControl } from "./cardSelection";

describe("cardSelection", () => {
  const card = document.createElement("div");
  card.innerHTML =
    '<h3 class="title">Multa <span class="inner">AIT</span></h3>' +
    '<ion-button class="ion"><span class="label">Quitar</span></ion-button>' +
    '<button class="native">Editar</button><a class="link" href="#x">Ver</a>' +
    '<ion-checkbox class="check"></ion-checkbox><div role="button" class="aria">Menu</div>';
  const from = (selector: string | null) => ({ target: selector ? card.querySelector(selector) : card, currentTarget: card });

  it("ignores events that start in any interactive element inside the card", () => {
    [".ion", ".label", ".native", ".link", ".check", ".aria"].forEach((selector) => expect(isFromInnerControl(from(selector))).toBe(true));
  });

  it("the card itself and its plain content are its free area", () => {
    expect(isFromInnerControl(from(null))).toBe(false);
    expect(isFromInnerControl(from(".title"))).toBe(false);
    expect(isFromInnerControl(from(".inner"))).toBe(false);
  });

  it("Enter / Space only when the focus is on the card itself", () => {
    expect(isCardActivationKey({ key: "Enter", target: card, currentTarget: card })).toBe(true);
    expect(isCardActivationKey({ key: " ", target: card, currentTarget: card })).toBe(true);
    expect(isCardActivationKey({ key: "Enter", target: card.querySelector(".ion"), currentTarget: card })).toBe(false);
    expect(isCardActivationKey({ key: "a", target: card, currentTarget: card })).toBe(false);
  });
});
