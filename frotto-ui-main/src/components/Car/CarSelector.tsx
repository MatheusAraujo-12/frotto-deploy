import React, { useEffect, useMemo, useState, useCallback } from "react";
import {
  IonItem,
  IonLabel,
  IonSearchbar,
  IonSpinner,
  type SearchbarCustomEvent,
} from "@ionic/react";
import ItemNotFound from "../List/ItemNotFound";
import { CarModel } from "../../constants/CarModels";
import { TEXT } from "../../constants/texts";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { normalizeCarRecord, resolveCarIdentity } from "./carIdentity";

type Props = {
  cars?: CarModel[];
  onSelect: (car: CarModel) => void;
};

type CarListItem = CarModel & {
  car?: CarModel;
  carId?: number;
};

const normalizeCars = (list: CarListItem[]): CarModel[] =>
  list.map((item) => {
    const { car, carId, ...rest } = item;
    const baseCar = car ?? rest;
    const resolvedId = baseCar.id ?? rest.id ?? carId;
    return normalizeCarRecord({
      ...rest,
      ...baseCar,
      id: resolvedId,
    });
  });

const CarSelector: React.FC<Props> = ({ cars = [], onSelect }) => {
  const [search, setSearch] = useState<string>("");
  const [internalCars, setInternalCars] = useState<CarModel[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (cars && cars.length > 0) return;

    let mounted = true;
    const controller = new AbortController();

    const fetchCars = async () => {
      try {
        setLoading(true);
        setError(null);

        const { data } = await api.get(
          endpoints.CARS_ACTIVE?.() ?? endpoints.CARS(),
          { signal: controller.signal }
        );

        if (mounted) {
          if (Array.isArray(data)) {
            setInternalCars(normalizeCars(data));
            return;
          }
          if (data && typeof data === "object") {
            const anyData = data as {
              items?: CarListItem[];
              content?: CarListItem[];
              data?: CarListItem[];
            };
            const list = anyData.items || anyData.content || anyData.data || [];
            setInternalCars(normalizeCars(list));
            return;
          }
          setInternalCars([]);
        }
      } catch (e: any) {
        if (e?.name !== "AbortError" && mounted) {
          console.error("Erro ao buscar carros:", e);
          setError("Não foi possível carregar os veículos");
        }
      } finally {
        if (mounted) setLoading(false);
      }
    };

    fetchCars();

    return () => {
      mounted = false;
      controller.abort();
    };
  }, [cars]);

  const source = useMemo(
    () =>
      cars && cars.length > 0
        ? cars.map((car) => normalizeCarRecord(car))
        : internalCars,
    [cars, internalCars]
  );

  const filtered = useMemo(() => {
    if (!search.trim()) return source;

    const q = search.toLowerCase().trim();
    return source.filter((car) => {
      const identity = resolveCarIdentity(car);
      return [identity.displayName, identity.brand, identity.model, car.name, car.plate]
        .filter((value): value is string => Boolean(value))
        .some((value) => value.toLowerCase().includes(q));
    });
  }, [source, search]);

  const handleSearchChange = useCallback((e: SearchbarCustomEvent) => {
    setSearch(e.detail.value ?? "");
  }, []);

  const handleSelect = useCallback(
    (car: CarModel) => onSelect(car),
    [onSelect]
  );

  if (loading) {
    return (
      <div className="car-selector__feedback">
        <IonSpinner />
        <p>Carregando veículos...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="car-selector__feedback car-selector__feedback--error">
        {error}
      </div>
    );
  }

  return (
    <div className="car-selector">
      <IonSearchbar
        debounce={300}
        placeholder={TEXT.search}
        value={search}
        onIonChange={handleSearchChange}
      />

      {filtered.length === 0 && search.trim() && (
        <ItemNotFound message="Nenhum veículo encontrado" />
      )}

      {filtered.length === 0 && !search.trim() && source.length === 0 && (
        <ItemNotFound message="Nenhum veículo disponível" />
      )}

      {filtered.map((car: CarModel, index) => (
        <IonItem
          className="car-selector__item"
          button
          key={car.id ?? `car-${index}`}
          onClick={() => handleSelect(car)}
          detail={false}
        >
          <IonLabel>
            <div className="car-selector__title">
              {resolveCarIdentity(car).displayName}
            </div>
            <div className="car-selector__meta">
              {[car.plate?.trim(), car.group?.trim()]
                .filter((value): value is string => Boolean(value))
                .join(" • ") || "Sem placa"}
            </div>
          </IonLabel>
        </IonItem>
      ))}
    </div>
  );
};

export default CarSelector;
