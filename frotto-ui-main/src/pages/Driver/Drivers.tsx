import {
  IonButton,
  IonBackButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonModal,
  IonPage,
  IonProgressBar,
  IonSearchbar,
  IonTitle,
  IonToolbar,
  useIonRouter,
  useIonViewWillEnter,
} from "@ionic/react";
import { personCircleOutline } from "ionicons/icons";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { TEXT } from "../../constants/texts";
import { MouseEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import {
  CarDriverModel,
  DriverDebtSummaryModel,
} from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import DriverAdd from "./DriverAddModal/DriverAdd";
import DriverVehicleHistory from "./DriverVehicleHistory";
import { DriverCarBadges, DriverCarLifecycleActions } from "./DriverCarLifecycle";
import { driverCarStatus, groupDriverCars } from "../../services/driverAssignmentService";
import { formatDateView } from "../../services/dateFormat";
import { currencyFormat } from "../../services/currencyFormat";
import { formatCPF, formatTel } from "../../services/iMaskFormat";
import "./Drivers.css";

interface DriverDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Drivers: React.FC<DriverDetail> = ({ match }) => {
  const location = useLocation();
  const nav = useHistory();
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalDriver, setModalDriver] = useState<CarDriverModel>({});
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [driverList, setDriversList] = useState<CarDriverModel[]>([]);
  /** Driver whose vehicle history (all cars of this account) is open. */
  const [historyDriver, setHistoryDriver] = useState<{ id: number; name?: string } | null>(null);
  const [debtSummaryByDriverId, setDebtSummaryByDriverId] = useState<
    Record<number, DriverDebtSummaryModel>
  >({});

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  const loadDebtSummaryByDrivers = async (drivers: CarDriverModel[]) => {
    const items = drivers
      .map((driver) => ({
        carDriverId: driver.id,
        driverId: driver.driver?.id,
      }))
      .filter(
        (item): item is { carDriverId: number; driverId: number } =>
          typeof item.carDriverId === "number" && typeof item.driverId === "number"
      );

    if (items.length === 0) {
      setDebtSummaryByDriverId({});
      return;
    }

    const summaries = await Promise.all(
      items.map(async ({ carDriverId, driverId }) => {
        try {
          const response = await api.get(
            endpoints.DRIVER_DEBT_SUMMARY({
              pathVariables: { id: driverId },
            })
          );
          return { carDriverId, summary: response.data as DriverDebtSummaryModel };
        } catch (error) {
          return { carDriverId, summary: undefined };
        }
      })
    );

    const summaryMap: Record<number, DriverDebtSummaryModel> = {};
    summaries.forEach(({ carDriverId, summary }) => {
      if (summary) {
        summaryMap[carDriverId] = summary;
      }
    });
    setDebtSummaryByDriverId(summaryMap);
  };

  const loadDrivers = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.DRIVERS({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setDriversList(data);
        loadDebtSummaryByDrivers(data);
      } else {
        history.push("/menu", "none", "replace");
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadDriversFailed);
    }
  };

  useIonViewWillEnter(() => {
    loadDrivers();
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(driverList, searchValue);
  }, [driverList, searchValue]);

  // Open contracts (ACTIVE and SUSPENDED) on top, the concluded history below.
  const activeDoneList = useMemo(() => groupDriverCars(filteredList), [filteredList]);

  const closeModal = useCallback((response?: CarDriverModel) => {
    setIsModalOpen(false);
    nav.goBack();

    if (!response) return;

    // A movement (transfer, reserve, return, restore) can change several contracts: reload what the backend says.
    loadDrivers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const getOutstandingDebt = (carDriver?: CarDriverModel) => {
    if (!carDriver?.id) {
      return 0;
    }
    const summary = debtSummaryByDriverId[carDriver.id];
    if (summary && typeof summary.totalOutstanding === "number") {
      return summary.totalOutstanding;
    }
    return carDriver.debt || 0;
  };

  const openDriverPendencies = (
    event: MouseEvent,
    carDriver?: CarDriverModel
  ) => {
    event.stopPropagation();
    if (!carDriver?.id) {
      return;
    }
    nav.push(`/menu/carros/motorista/${carDriver.id}/pendencias`);
  };

  const openVehicleHistory = (event: MouseEvent, carDriver?: CarDriverModel) => {
    event.stopPropagation();
    if (carDriver?.driver?.id) {
      setHistoryDriver({ id: carDriver.driver.id, name: carDriver.driver.name });
    }
  };

  const openDriverEditor = useCallback(
    (carDriver: CarDriverModel) => {
      setModalDriver(carDriver);
      setIsModalOpen(true);
      nav.push(nav.location.pathname + "?modalOpened=true");
    },
    [nav]
  );

  return (
    <IonPage id="drivers-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.drivers}</IonTitle>
        </IonToolbar>
        <IonToolbar className="app-subtoolbar">
          <IonSearchbar
            debounce={500}
            placeholder={TEXT.search}
            onIonChange={(e) => setSearchValue(e.detail.value)}
          ></IonSearchbar>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="app-shell app-shell--compact">
          <IonList className="app-nested-list">
            <IonItem lines="none" className="app-nested-list__title">
              <IonLabel>
                <h1>{TEXT.active}</h1>
              </IonLabel>
            </IonItem>

            {activeDoneList.open.map((carDriver: CarDriverModel, index) => (
              <DriverRow
                key={carDriver.id ?? index}
                carDriver={carDriver}
                outstandingDebt={getOutstandingDebt(carDriver)}
                onOpen={() => openDriverEditor(carDriver)}
                onOpenPendencies={(event) => openDriverPendencies(event, carDriver)}
                onOpenHistory={(event) => openVehicleHistory(event, carDriver)}
                onChanged={() => loadDrivers()}
              />
            ))}
            {!isLoading && activeDoneList.open.length === 0 && (
              <ItemNotFound message="Nenhum motorista ativo neste veículo." />
            )}
          </IonList>
          <IonList className="app-nested-list">
            <IonItem lines="none" className="app-nested-list__title">
              <IonLabel>
                <h1>{TEXT.carDamagesDone}</h1>
              </IonLabel>
            </IonItem>

            {activeDoneList.done.map((carDriver: CarDriverModel, index) => (
              <DriverRow
                key={carDriver.id ?? index}
                carDriver={carDriver}
                outstandingDebt={getOutstandingDebt(carDriver)}
                onOpen={() => openDriverEditor(carDriver)}
                onOpenPendencies={(event) => openDriverPendencies(event, carDriver)}
                onOpenHistory={(event) => openVehicleHistory(event, carDriver)}
                onChanged={() => loadDrivers()}
              />
            ))}
            {!isLoading && activeDoneList.done.length === 0 && (
              <ItemNotFound message="Nenhum motorista anterior registrado." />
            )}
          </IonList>
        </div>
      </IonContent>
      <IonModal isOpen={Boolean(historyDriver)} onDidDismiss={() => setHistoryDriver(null)}>
        {historyDriver && (
          <DriverVehicleHistory
            driverId={historyDriver.id}
            driverName={historyDriver.name}
            onClose={() => setHistoryDriver(null)}
          />
        )}
      </IonModal>
      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
        <DriverAdd
          carId={match.params.id}
          closeModal={closeModal}
          initialValues={modalDriver}
        />
      </IonModal>
    </IonPage>
  );
};

export default Drivers;

export const DriverRow: React.FC<{
  carDriver: CarDriverModel;
  outstandingDebt: number;
  onOpen: () => void;
  onOpenPendencies: (event: MouseEvent) => void;
  /** Vehicle history of this driver (all cars of the account); absent where the row is shown without it. */
  onOpenHistory?: (event: MouseEvent) => void;
  onChanged: () => void;
}> = ({ carDriver, outstandingDebt, onOpen, onOpenPendencies, onOpenHistory, onChanged }) => {
  const hasDebt = outstandingDebt > 0;
  const contractLabel = formatDriverContract(carDriver?.contractNumber);
  const period =
    driverCarStatus(carDriver) === "CONCLUDED"
      ? `${formatDateView(carDriver?.startDate)} - ${formatDateView(carDriver?.endDate ?? undefined)}`
      : `${formatDateView(carDriver?.startDate)} até —`;

  return (
    <IonItem className="app-nested-list__item driver-list-item" button onClick={onOpen}>
      <div className="driver-row">
        <div className="driver-col-left">
          <div className="driver-list-item__avatar app-soft-icon">
            <IonIcon icon={personCircleOutline} />
          </div>
          <div className="driver-col-left__content">
            <div className="driver-list-item__header">
              <h2>{carDriver?.driver?.name}</h2>
              <DriverCarBadges driverCar={carDriver} />
            </div>
            <p>{formatCPF(carDriver?.driver?.cpf)}</p>
            <p>{formatTel(carDriver?.driver?.contact)}</p>
            <p>{`${carDriver?.driver?.email || ""}`}</p>
          </div>
        </div>
        <div className="driver-col-right">
          <p>{period}</p>
          <p>{currencyFormat(carDriver?.warranty)}</p>
          {contractLabel && <p>{contractLabel}</p>}
          <p className={hasDebt ? "app-text-financial-negative" : undefined}>
            {TEXT.totalOutstanding}: {currencyFormat(outstandingDebt)}
          </p>
          <div className="driver-col-actions">
            <DriverCarLifecycleActions driverCar={carDriver} onChanged={onChanged} />
            <IonButton
              className="driver-pendency-button app-semantic-btn app-semantic--warning"
              size="small"
              fill="outline"
              onClick={onOpenPendencies}
            >
              {TEXT.driverPendencies}
            </IonButton>
            {onOpenHistory && carDriver?.driver?.id && (
              <IonButton className="app-outline-btn" size="small" fill="outline" onClick={onOpenHistory}>
                Histórico de veículos
              </IonButton>
            )}
          </div>
        </div>
      </div>
    </IonItem>
  );
};

function formatDriverContract(contractNumber?: string): string | undefined {
  const value = `${contractNumber || ""}`.trim();
  return value ? `Contrato: ${value}` : undefined;
}
