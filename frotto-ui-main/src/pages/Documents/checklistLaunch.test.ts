import { checklistLaunchUrl, parseChecklistLaunch } from "./checklistLaunch";

describe("checklistLaunch", () => {
  it("builds and reads the launch request", () => {
    const url = checklistLaunchUrl("ENTREGA", 7, "/menu/carros/7");
    expect(url).toBe("/documents?checklist=ENTREGA&carId=7&from=%2Fmenu%2Fcarros%2F7");
    expect(parseChecklistLaunch(url.slice("/documents".length))).toEqual({ checklistType: "ENTREGA", carId: 7, returnTo: "/menu/carros/7" });
  });

  it("refuses an unknown operation or car", () => {
    expect(parseChecklistLaunch("?checklist=OUTRO&carId=7")).toBeNull();
    expect(parseChecklistLaunch("?checklist=DEVOLUCAO&carId=abc")).toBeNull();
    expect(parseChecklistLaunch("?checklist=DEVOLUCAO&carId=-1")).toBeNull();
    expect(parseChecklistLaunch("")).toBeNull();
  });

  it("only returns to a screen of the app menu (never an external or arbitrary target)", () => {
    expect(parseChecklistLaunch("?checklist=DEVOLUCAO&carId=7&from=https%3A%2F%2Fevil.example")?.returnTo).toBeNull();
    expect(parseChecklistLaunch("?checklist=DEVOLUCAO&carId=7&from=%2F%2Fevil.example")?.returnTo).toBeNull();
    expect(parseChecklistLaunch("?checklist=DEVOLUCAO&carId=7&from=%2Fdocuments")?.returnTo).toBeNull();
    expect(parseChecklistLaunch("?checklist=DEVOLUCAO&carId=7")?.returnTo).toBeNull();
  });
});
