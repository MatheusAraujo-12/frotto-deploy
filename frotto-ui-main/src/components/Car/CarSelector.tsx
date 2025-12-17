import { IonItem, IonLabel, IonList, IonSearchbar } from "@ionic/react";
import { useEffect, useMemo, useState } from "react";
import { CarModel } from "../../constants/CarModels";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { filterListObj } from "../../services/filterList";
import { TEXT } from "../../constants/texts";
import ItemNotFound from "../List/ItemNotFound";

interface CarSelectorProps {
  onSelect: (car: CarModel) => void;
  initialSelectedId?: number | string;
}

const CarSelector: React.FC<CarSelectorProps> = ({ onSelect }) => {
  const [cars, setCars] = useState<CarModel[]>([]);
  const [search, setSearch] = useState<string | undefined>(undefined);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      try {
        const { data } = await api.get(endpoints.CARS_ACTIVE());
        if (mounted) setCars(data || []);
      } catch (e) {
        // ignore; caller should already show errors if needed
      }
    };
    load();
    return () => {
      mounted = false;
    };
  }, []);

  const filtered = useMemo(() => filterListObj(cars, search), [cars, search]);

  return (
    <div>
      <IonSearchbar
        debounce={300}
        value={search}
        onIonChange={(e) => setSearch(e.detail.value!)}
        placeholder={TEXT.search}
      />
      <IonList>
        {filtered.length === 0 && <ItemNotFound />}
        {filtered.map((c) => (
          <IonItem button key={c.id} onClick={() => onSelect(c)}>
            <IonLabel>
              <div style={{ fontWeight: 600 }}>{c.name}</div>
              <div style={{ fontSize: 12, color: "var(--ion-color-medium)" }}>{c.plate}</div>
            </IonLabel>
          </IonItem>
        ))}
      </IonList>
    </div>
  );
};

export default CarSelector;
