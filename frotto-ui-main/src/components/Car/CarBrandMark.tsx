import React, { useEffect, useState } from "react";
import { IonIcon } from "@ionic/react";
import { carOutline } from "ionicons/icons";
import {
  resolveCarBrandDisplayName,
  resolveCarBrandLogo,
} from "./carBrandAssets";

interface CarBrandMarkProps {
  brand?: string | null;
  marca?: string | null;
  className?: string;
  size?: "sm" | "md" | "lg";
}

const joinClassNames = (...classNames: Array<string | undefined>) =>
  classNames.filter(Boolean).join(" ");

const CarBrandMark: React.FC<CarBrandMarkProps> = ({
  brand,
  marca,
  className,
  size = "md",
}) => {
  const [hasImageError, setHasImageError] = useState(false);
  const logo = resolveCarBrandLogo({ brand, marca });
  const brandLabel = resolveCarBrandDisplayName({ brand, marca });

  useEffect(() => {
    setHasImageError(false);
  }, [brandLabel, logo?.key]);

  return (
    <span
      className={joinClassNames(
        "vehicle-brand-mark",
        `vehicle-brand-mark--${size}`,
        className
      )}
      title={brandLabel ? `Marca ${brandLabel}` : "Veiculo"}
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
