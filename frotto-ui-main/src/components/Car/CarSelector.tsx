import React, { useMemo, useState } from "react";
import { IonItem, IonLabel, IonSearchbar } from "@ionic/react";
import ItemNotFound from "../List/ItemNotFound";
import { CarModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";

type Props = {
  cars?: CarModel[]; // opcional: se você recebe lista por props
  onSelect: (car: CarModel) => void;
};

const CarSelector: React.FC<Props> = ({ cars = [], onSelect }) => {
  const [search, setSearch] = useState<string | undefined>(undefined);

  const filtered = useMemo(() => {
    return filterListObj(cars, search) as CarModel[];
  }, [cars, search]);

  return (
    <div>
      <IonSearchbar
        debounce={300}
        placeholder="Buscar"
        value={search}
        onIonChange={(e) => setSearch(e.detail.value)}
      />

      {filtered.length === 0 && <ItemNotFound />}

      {filtered.map((c: CarModel) => (
        <IonItem
          button
          key={c.id ?? `${c.name}-${c.plate}`}
          onClick={() => onSelect(c)}
        >
          <IonLabel>
            <div style={{ fontWeight: 600 }}>{c.name}</div>
            <div style={{ fontSize: 12, color: "var(--ion-color-medium)" }}>
              {c.plate}
            </div>
          </IonLabel>
        </IonItem>
      ))}
    </div>
  );
};

export default CarSelector;
