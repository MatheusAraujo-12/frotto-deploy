import React, { useEffect, useState } from "react";
import { IonIcon } from "@ionic/react";
import { carOutline } from "ionicons/icons";
import { resolveCarIdentity } from "./carIdentity";
import {
  resolveCarBrandLogo,
} from "./carBrandAssets";

interface CarBrandMarkProps {
  name?: string | null;
  brand?: string | null;
  marca?: string | null;
  model?: string | null;
  className?: string;
  size?: "sm" | "md" | "lg";
}

const joinClassNames = (...classNames: Array<string | undefined>) =>
  classNames.filter(Boolean).join(" ");

const CarBrandMark: React.FC<CarBrandMarkProps> = ({
  name,
  brand,
  marca,
  model,
  className,
  size = "md",
}) => {
  const [hasImageError, setHasImageError] = useState(false);
  const carIdentity = resolveCarIdentity({ name, brand, marca, model });
  const logo = resolveCarBrandLogo(carIdentity.brand);
  const brandLabel = carIdentity.brand;

  useEffect(() => {
    setHasImageError(false);
  }, [brandLabel, logo?.key, model, name]);

  return (
    <span
      className={joinClassNames(
        "vehicle-brand-mark",
        `vehicle-brand-mark--${size}`,
        className
      )}
      title={brandLabel ? `Marca ${brandLabel}` : "Veículo"}
    >
      {logo && !hasImageError ? (
        <img
          src={logo.src}
          alt=""
          aria-hidden="true"
          className="vehicle-brand-mark__img"
          loading="lazy"
          onError={() => setHasImageError(true)}
        />
      ) : (
        <IonIcon
          icon={carOutline}
          className="vehicle-brand-mark__icon"
          aria-hidden="true"
        />
      )}
    </span>
  );
};

export default CarBrandMark;
