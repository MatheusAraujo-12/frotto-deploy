import {
  normalizeCarBrand,
  resolveCarBrandLogo,
} from "./carBrandAssets";

export type CarIdentitySource = {
  name?: string | null;
  brand?: string | null;
  marca?: string | null;
  model?: string | null;
};

export type CarIdentity = {
  brand?: string;
  model?: string;
  legacyName?: string;
  displayName: string;
};

const cleanText = (value?: string | null): string | undefined => {
  const text = `${value || ""}`.trim();
  return text || undefined;
};

const isSameValue = (left?: string, right?: string): boolean =>
  Boolean(left && right && normalizeCarBrand(left) === normalizeCarBrand(right));

const resolveKnownBrandLabel = (value?: string | null): string | undefined => {
  const text = cleanText(value);
  if (!text) {
    return undefined;
  }

  const logo = resolveCarBrandLogo(text);
  return logo?.label;
};

const parseLegacyCarName = (value?: string | null): Partial<CarIdentity> => {
  const name = cleanText(value);
  if (!name) {
    return {};
  }

  const separatedParts = name
    .split(/\s*[-/|]\s*/)
    .map((part) => cleanText(part))
    .filter((part): part is string => Boolean(part));

  if (separatedParts.length > 1) {
    for (let index = 0; index < separatedParts.length; index += 1) {
      const part = separatedParts[index];
      const brand = resolveKnownBrandLabel(part);
      if (!brand) {
        continue;
      }

      const model = cleanText(
        separatedParts.filter((_, currentIndex) => currentIndex !== index).join(" - ")
      );
      return { brand, model };
    }
  }

  const words = name.split(/\s+/).filter(Boolean);
  if (words.length > 1) {
    const firstBrand = resolveKnownBrandLabel(words[0]);
    if (firstBrand) {
      return {
        brand: firstBrand,
        model: cleanText(words.slice(1).join(" ")),
      };
    }

    const lastBrand = resolveKnownBrandLabel(words[words.length - 1]);
    if (lastBrand) {
      return {
        brand: lastBrand,
        model: cleanText(words.slice(0, -1).join(" ")),
      };
    }
  }

  const directBrand = resolveKnownBrandLabel(name);
  return directBrand ? { brand: directBrand } : {};
};

export function resolveCarIdentity(source?: CarIdentitySource | null): CarIdentity {
  const legacyName = cleanText(source?.name);
  const parsedLegacy = parseLegacyCarName(legacyName);

  const explicitBrand = cleanText(source?.brand) || cleanText(source?.marca);
  const brand =
    resolveKnownBrandLabel(explicitBrand) ||
    explicitBrand ||
    parsedLegacy.brand;

  let model = cleanText(source?.model);
  if (model && isSameValue(model, brand)) {
    model = undefined;
  }

  if (!model) {
    if (parsedLegacy.model && !isSameValue(parsedLegacy.model, brand)) {
      model = parsedLegacy.model;
    } else if (legacyName && !isSameValue(legacyName, brand)) {
      model = legacyName;
    }
  }

  const displayName =
    [model, brand].filter((value): value is string => Boolean(value)).join(" - ") ||
    legacyName ||
    model ||
    brand ||
    "Veículo";

  return {
    brand,
    model,
    legacyName,
    displayName,
  };
}

export function buildCarLegacyName(source?: CarIdentitySource | null): string {
  const identity = resolveCarIdentity(source);
  return (
    [identity.model, identity.brand]
      .filter((value): value is string => Boolean(value))
      .join(" - ") ||
    identity.legacyName ||
    ""
  );
}

export function normalizeCarRecord<T extends CarIdentitySource & Record<string, any>>(
  source: T
): T {
  const identity = resolveCarIdentity(source);
  const legacyName = buildCarLegacyName({
    ...source,
    brand: identity.brand,
    model: identity.model,
  });

  return {
    ...source,
    brand: identity.brand ?? source.brand ?? source.marca,
    marca: source.marca ?? identity.brand,
    model: identity.model ?? source.model,
    name: legacyName || source.name,
  };
}
