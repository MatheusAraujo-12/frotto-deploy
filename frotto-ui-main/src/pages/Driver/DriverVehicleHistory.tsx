import { IonButton, IonButtons, IonContent, IonHeader, IonSearchbar, IonTitle, IonToolbar } from "@ionic/react";
import { useEffect, useMemo, useState } from "react";
import endpoints from "../../constants/endpoints";
import { DriverAssignmentType, DriverCarStatus } from "../../constants/CarModels";
import FrottoBadge from "../../components/UI/FrottoBadge";
import api from "../../services/axios/axios";
import { formatDateView } from "../../services/dateFormat";
import { DRIVER_CAR_STATUS_LABEL, DRIVER_CAR_STATUS_VARIANT } from "../../services/driverAssignmentService";
import "./DriverVehicleHistory.css";

/** One contract of GET /driver-cars/driver/{driverId} (only cars of this account, most recent first). */
export interface DriverVehicleHistoryItem {
  driverCarId: number;
  carId?: number | null;
  carPlate?: string | null;
  carModel?: string | null;
  assignmentType: DriverAssignmentType;
  startDate?: string | null;
  endDate?: string | null;
  status?: DriverCarStatus | null;
}

const TABS: Array<{ value: DriverAssignmentType; label: string; empty: string }> = [
  { value: "PERMANENT", label: "Carro Principal", empty: "Nenhum vínculo como carro principal." },
  { value: "RESERVE", label: "Carro Reserva", empty: "Nenhum vínculo como carro reserva." },
];

const normalize = (value?: string | null) =>
  `${value || ""}`
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-zA-Z0-9]/g, "")
    .toLowerCase();

/** Plate (with or without the dash) or model, ignoring case and accents. */
export function matchesVehicle(item: DriverVehicleHistoryItem, query: string): boolean {
  const wanted = normalize(query);
  return !wanted || normalize(item.carPlate).includes(wanted) || normalize(item.carModel).includes(wanted);
}

interface DriverVehicleHistoryProps {
  driverId: number;
  driverName?: string;
  onClose: () => void;
}

/** Vehicle history of a driver: the contracts of this account split by kind, with search, dates and state (read only). */
const DriverVehicleHistory: React.FC<DriverVehicleHistoryProps> = ({ driverId, driverName, onClose }) => {
  const [items, setItems] = useState<DriverVehicleHistoryItem[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [tab, setTab] = useState<DriverAssignmentType>("PERMANENT");
  const [query, setQuery] = useState("");

  useEffect(() => {
    let active = true;
    setItems(null);
    setFailed(false);
    const load = async () => {
      try {
        const { data } = await api.get<DriverVehicleHistoryItem[]>(endpoints.DRIVER_CAR_HISTORY({ pathVariables: { id: driverId } }));
        if (active) {
          setItems(Array.isArray(data) ? data : []);
        }
      } catch (_error) {
        if (active) {
          setFailed(true);
        }
      }
    };
    void load();
    return () => {
      active = false;
    };
  }, [driverId]);

  const counts = useMemo(
    () => ({
      PERMANENT: (items || []).filter((item) => item.assignmentType !== "RESERVE").length,
      RESERVE: (items || []).filter((item) => item.assignmentType === "RESERVE").length,
    }),
    [items]
  );
  const visible = useMemo(
    () =>
      (items || []).filter(
        (item) => (tab === "RESERVE" ? item.assignmentType === "RESERVE" : item.assignmentType !== "RESERVE") && matchesVehicle(item, query)
      ),
    [items, tab, query]
  );
  const current = TABS.find((option) => option.value === tab)!;

  return (
    <>
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonTitle>Histórico de veículos</IonTitle>
          <IonButtons slot="end">
            <IonButton className="app-cancel-btn" fill="clear" onClick={onClose}>
              Fechar
            </IonButton>
          </IonButtons>
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="app-shell app-shell--compact driver-vehicle-history">
          {driverName && <p className="driver-vehicle-history__driver">{driverName}</p>}
          <div className="driver-vehicle-history__tabs" role="tablist" aria-label="Tipo de vínculo">
            {TABS.map((option) => (
              <button
                key={option.value}
                type="button"
                role="tab"
                id={`driver-vehicle-history-tab-${option.value}`}
                aria-selected={tab === option.value}
                aria-controls="driver-vehicle-history-panel"
                className={`driver-vehicle-history__tab${tab === option.value ? " driver-vehicle-history__tab--active" : ""}`}
                onClick={() => setTab(option.value)}
              >
                {option.label} ({counts[option.value]})
              </button>
            ))}
          </div>
          <IonSearchbar
            className="driver-vehicle-history__search"
            placeholder="Pesquisar por placa ou modelo"
            aria-label="Pesquisar por placa ou modelo"
            value={query}
            debounce={0}
            onIonChange={(event) => setQuery(`${event.detail.value ?? ""}`)}
          />
          <div
            id="driver-vehicle-history-panel"
            role="tabpanel"
            aria-labelledby={`driver-vehicle-history-tab-${tab}`}
            className="driver-vehicle-history__list"
          >
            {failed && <p className="documents-warning" role="alert">Não foi possível carregar o histórico de veículos.</p>}
            {!failed && items === null && <p className="driver-vehicle-history__hint">Carregando histórico…</p>}
            {!failed && items !== null && visible.length === 0 && (
              <p className="driver-vehicle-history__hint" data-testid="driver-vehicle-history-empty">
                {query.trim() ? `Nenhum veículo encontrado para "${query.trim()}".` : current.empty}
              </p>
            )}
            {visible.map((item) => {
              const status = item.status || "ACTIVE";
              return (
                <article key={item.driverCarId} className="driver-vehicle-history__item" data-testid="driver-vehicle-history-item">
                  <div className="driver-vehicle-history__vehicle">
                    <strong>{item.carPlate || "-"}</strong>
                    <span>{item.carModel || ""}</span>
                  </div>
                  <p className="driver-vehicle-history__period">
                    {item.endDate
                      ? `${formatDateView(item.startDate ?? undefined)} – ${formatDateView(item.endDate)}`
                      : `Desde ${formatDateView(item.startDate ?? undefined)}`}
                  </p>
                  <FrottoBadge variant={DRIVER_CAR_STATUS_VARIANT[status] || "neutral"}>{DRIVER_CAR_STATUS_LABEL[status] || status}</FrottoBadge>
                </article>
              );
            })}
          </div>
        </div>
      </IonContent>
    </>
  );
};

export default DriverVehicleHistory;
