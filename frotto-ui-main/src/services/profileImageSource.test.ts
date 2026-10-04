import { imageCacheIdentity, resolveProfileImageSource } from "./profileImageSource";

const legacy = (value: string) => `https://bucket.example/${value}`;

describe("resolveProfileImageSource", () => {
  it("uses the backend-resolved URL when present", () => {
    expect(
      resolveProfileImageSource("https://arquivos.example/files/avatars/a.png?exp=1&sig=x", "1771248962905_USER_4.png", legacy)
    ).toBe("https://arquivos.example/files/avatars/a.png?exp=1&sig=x");
  });

  it("treats an empty or null resolved URL as no image, never rebuilding an S3 URL from the key", () => {
    expect(resolveProfileImageSource("", "1771248962905_USER_4.png", legacy)).toBe("");
    expect(resolveProfileImageSource(null, "1771248962905_USER_4.png", legacy)).toBe("");
  });

  it("falls back to the legacy key only when an older backend omits the field", () => {
    expect(resolveProfileImageSource(undefined, "1771248962905_USER_4.png", legacy)).toBe(
      "https://bucket.example/1771248962905_USER_4.png"
    );
    expect(resolveProfileImageSource(undefined, null, legacy)).toBe("");
  });
});

describe("imageCacheIdentity", () => {
  it("drops the expiring signature so a re-signed URL hits the same cache entry", () => {
    const first = "https://arquivos.example/files/logos/2026/10/a.png?exp=100&sig=aaa";
    const second = "https://arquivos.example/files/logos/2026/10/a.png?exp=200&sig=bbb";
    expect(imageCacheIdentity(first)).toBe("https://arquivos.example/files/logos/2026/10/a.png");
    expect(imageCacheIdentity(first)).toBe(imageCacheIdentity(second));
  });

  it("keeps other URLs unchanged", () => {
    expect(imageCacheIdentity("https://bucket.example/1771248962905_LOGO_4.png")).toBe(
      "https://bucket.example/1771248962905_LOGO_4.png"
    );
    expect(imageCacheIdentity("/api/x.png?size=2")).toBe("/api/x.png?size=2");
  });
});
