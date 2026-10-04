/**
 * Avatar/logo source for the UI and PDFs.
 *
 * The backend resolves the stored key into the URL the browser must load (`avatarUrl`, `logoAccessUrl`): a signed
 * URL of the files domain, the public legacy bucket, or null when there is no image. When that field is present it
 * is authoritative - null means placeholder, and the raw key is never turned into an S3 URL by the frontend. Only an
 * older backend that does not send the field falls back to the legacy key resolution.
 */
export function resolveProfileImageSource(
  resolvedUrl: string | null | undefined,
  legacyValue: string | null | undefined,
  resolveLegacy: (value: string) => string
): string {
  if (resolvedUrl !== undefined) {
    return `${resolvedUrl || ""}`.trim();
  }
  const legacy = `${legacyValue || ""}`.trim();
  return legacy ? resolveLegacy(legacy) : "";
}

/**
 * Stable identity of an image URL for caches: signed URLs change with every expiration window while the stored
 * content behind the same path never changes, so `exp`/`sig` are dropped. Other URLs are kept as they are.
 */
export function imageCacheIdentity(url: string): string {
  const value = `${url || ""}`.trim();
  try {
    const parsed = new URL(value);
    if (parsed.searchParams.has("exp") && parsed.searchParams.has("sig")) {
      return `${parsed.origin}${parsed.pathname}`;
    }
  } catch (_error) {
    // relative or non-URL values are their own identity
  }
  return value;
}
