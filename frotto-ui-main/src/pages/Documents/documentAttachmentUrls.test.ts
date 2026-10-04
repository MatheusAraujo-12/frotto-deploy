import { buildChecklistPhotoPreviewCandidates } from "./DocumentsPage";
import { resolveChecklistAttachmentUrls } from "./documentPdf";

jest.mock("pdfmake/build/pdfmake", () => ({ createPdf: jest.fn() }));
jest.mock("pdfmake/build/vfs_fonts", () => ({}));

const SIGNED = "https://api-staging.frotto.test/files/documents/2026/10/a.png?exp=1&sig=s";

describe.each([
  ["editor (DocumentsPage)", buildChecklistPhotoPreviewCandidates],
  ["PDF (documentPdf)", resolveChecklistAttachmentUrls],
])("checklist photo URLs - %s", (_label, resolve) => {
  it("uses the URL resolved by the backend", () => {
    expect(resolve("documents/2026/10/a.png", { "documents/2026/10/a.png": SIGNED })).toEqual([SIGNED]);
  });

  it("an empty resolved URL means unavailable: no S3 or API guess", () => {
    expect(resolve("1774293055000_DOC_99.png", { "1774293055000_DOC_99.png": "" })).toEqual([]);
  });

  it("references the backend does not list keep the legacy candidates", () => {
    const candidates = resolve("1774293055000_DOC_20.png", {});
    expect(candidates.length).toBeGreaterThan(0);
    expect(candidates.some((url) => url.endsWith("1774293055000_DOC_20.png"))).toBe(true);
  });

  it("works without the map (older backend)", () => {
    expect(resolve("1774293055000_DOC_20.png").length).toBeGreaterThan(0);
  });
});
