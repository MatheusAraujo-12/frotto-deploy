import { checklistLaunchUrl, parseChecklistLaunch, withLaunchDocument } from "./checklistLaunch";

describe("checklistLaunch", () => {
  it("builds and reads the launch request", () => {
    const url = checklistLaunchUrl("ENTREGA", 7, "/menu/carros/7");
    expect(url).toBe("/documents?checklist=ENTREGA&carId=7&from=%2Fmenu%2Fcarros%2F7");
    expect(parseChecklistLaunch(url.slice("/documents".length))).toEqual({ checklistType: "ENTREGA", carId: 7, documentId: null, returnTo: "/menu/carros/7" });
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

describe("checklistLaunch - rascunhos", () => {
  it("a draft being continued travels as documentId (its own type is used; an older draft has none)", () => {
    const url = checklistLaunchUrl(null, 7, "/menu/carros/7/inspecoes", 12);
    expect(url).toBe("/documents?carId=7&documentId=12&from=%2Fmenu%2Fcarros%2F7%2Finspecoes");
    expect(parseChecklistLaunch(url.slice("/documents".length))).toEqual({
      checklistType: null,
      carId: 7,
      documentId: 12,
      returnTo: "/menu/carros/7/inspecoes",
    });
  });

  it("after the first save the same launch names the draft (a reload reopens it)", () => {
    const launch = parseChecklistLaunch("?checklist=DEVOLUCAO&carId=7&from=%2Fmenu%2Fcarros%2F7")!;
    expect(withLaunchDocument(launch, 99)).toBe("/documents?checklist=DEVOLUCAO&carId=7&documentId=99&from=%2Fmenu%2Fcarros%2F7");
  });

  it("refuses an invalid draft id or a request without operation nor draft", () => {
    expect(parseChecklistLaunch("?carId=7&documentId=abc")).toBeNull();
    expect(parseChecklistLaunch("?carId=7&documentId=0")).toBeNull();
    expect(parseChecklistLaunch("?carId=7")).toBeNull();
  });
});
