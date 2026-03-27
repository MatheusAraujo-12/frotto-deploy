import chevroletLogo from "../../assets/vehicle-brands/chevrolet.svg";
import fiatLogo from "../../assets/vehicle-brands/fiat.svg";
import fordLogo from "../../assets/vehicle-brands/ford.svg";
import hondaLogo from "../../assets/vehicle-brands/honda.svg";
import hyundaiLogo from "../../assets/vehicle-brands/hyundai.svg";
import jeepLogo from "../../assets/vehicle-brands/jeep.svg";
import nissanLogo from "../../assets/vehicle-brands/nissan.svg";
import renaultLogo from "../../assets/vehicle-brands/renault.svg";
import toyotaLogo from "../../assets/vehicle-brands/toyota.svg";
import volkswagenLogo from "../../assets/vehicle-brands/volkswagen.svg";

type CarBrandSource =
  | string
  | {
      brand?: string | null;
      marca?: string | null;
    }
  | null
  | undefined;

export type SupportedCarBrandKey =
  | "chevrolet"
  | "fiat"
  | "ford"
  | "honda"
  | "hyundai"
  | "jeep"
  | "nissan"
  | "renault"
  | "toyota"
  | "volkswagen";

export type CarBrandLogoEntry = {
  key: SupportedCarBrandKey;
  label: string;
  src: string;
  aliases: string[];
};

const BRAND_LOGO_ENTRIES: CarBrandLogoEntry[] = [
  {
    key: "chevrolet",
    label: "Chevrolet",
    src: chevroletLogo,
    aliases: ["chevrolet", "chevy", "gm"],
  },
  {
    key: "fiat",
    label: "Fiat",
    src: fiatLogo,
    aliases: ["fiat"],
  },
  {
    key: "ford",
    label: "Ford",
    src: fordLogo,
    aliases: ["ford"],
  },
  {
    key: "honda",
    label: "Honda",
    src: hondaLogo,
    aliases: ["honda"],
  },
  {
    key: "hyundai",
    label: "Hyundai",
    src: hyundaiLogo,
    aliases: ["hyundai"],
  },
  {
    key: "jeep",
    label: "Jeep",
    src: jeepLogo,
    aliases: ["jeep"],
  },
  {
    key: "nissan",
    label: "Nissan",
    src: nissanLogo,
    aliases: ["nissan"],
  },
  {
    key: "renault",
    label: "Renault",
    src: renaultLogo,
    aliases: ["renault"],
  },
  {
    key: "toyota",
    label: "Toyota",
    src: toyotaLogo,
    aliases: ["toyota"],
  },
  {
    key: "volkswagen",
    label: "Volkswagen",
    src: volkswagenLogo,
    aliases: ["volkswagen", "vw", "volks"],
  },
];

const BRAND_KEY_BY_ALIAS = new Map<string, SupportedCarBrandKey>();
const BRAND_ENTRY_BY_KEY = BRAND_LOGO_ENTRIES.reduce<
  Record<SupportedCarBrandKey, CarBrandLogoEntry>
>((accumulator, entry) => {
  accumulator[entry.key] = entry;

  entry.aliases.forEach((alias) => {
    BRAND_KEY_BY_ALIAS.set(normalizeCarBrand(alias), entry.key);
  });

  return accumulator;
}, {} as Record<SupportedCarBrandKey, CarBrandLogoEntry>);

export const SUPPORTED_CAR_BRANDS = BRAND_LOGO_ENTRIES.map(({ key, label }) => ({
  key,
  label,
}));

export function normalizeCarBrand(value?: string | null): string {
  return `${value || ""}`
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

export function getCarBrandValue(source?: CarBrandSource): string | undefined {
  if (!source) {
    return undefined;
  }

  if (typeof source === "string") {
    const text = source.trim();
    return text || undefined;
  }

  const text = `${source.brand || source.marca || ""}`.trim();
  return text || undefined;
}

export function resolveCarBrandLogo(
  source?: CarBrandSource
): CarBrandLogoEntry | undefined {
  const brandValue = getCarBrandValue(source);
  if (!brandValue) {
    return undefined;
  }

  const key = BRAND_KEY_BY_ALIAS.get(normalizeCarBrand(brandValue));
  return key ? BRAND_ENTRY_BY_KEY[key] : undefined;
}

export function resolveCarBrandDisplayName(
  source?: CarBrandSource
): string | undefined {
  const brandValue = getCarBrandValue(source);
  if (!brandValue) {
    return undefined;
  }

  const mappedBrand = resolveCarBrandLogo(brandValue);
  return mappedBrand?.label || brandValue;
}
