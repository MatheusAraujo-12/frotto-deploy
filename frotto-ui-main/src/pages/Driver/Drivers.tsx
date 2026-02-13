import {
  IonButton,
  IonBackButton,
  IonButtons,
  IonContent,
  IonHeader,
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
} from "@ionic/react";
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
import { formatDateView } from "../../services/dateFormat";
import {
  IonLabelLeft,
  IonLabekRight,
} from "../../components/List/IonLabekRight";
import { currencyFormat } from "../../services/currencyFormat";
import { formatCPF, formatTel } from "../../services/iMaskFormat";

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
  const [debtSummaryByDriverId, setDebtSummaryByDriverId] = useState<
    Record<number, DriverDebtSummaryModel>
  >({});

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  const loadDebtSummaryByDrivers = async (drivers: CarDriverModel[]) => {
    const ids = drivers
      .map((driver) => driver.id)
      .filter((id): id is number => typeof id === "number");

    if (ids.length === 0) {
      setDebtSummaryByDriverId({});
      return;
    }

    const summaries = await Promise.all(
      ids.map(async (id) => {
        try {
          const response = await api.get(
            endpoints.DRIVER_DEBT_SUMMARY({
              pathVariables: { id },
            })
          );
          return { id, summary: response.data as DriverDebtSummaryModel };
        } catch (error) {
          return { id, summary: undefined };
        }
      })
    );

    const summaryMap: Record<number, DriverDebtSummaryModel> = {};
    summaries.forEach(({ id, summary }) => {
      if (summary) {
        summaryMap[id] = summary;
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

  useEffect(() => {
    loadDrivers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(driverList, searchValue);
  }, [driverList, searchValue]);

  const activeDoneList = useMemo(() => {
    let activeItem: CarDriverModel | undefined;
    const doneList: CarDriverModel[] = [];
    filteredList.forEach((item: CarDriverModel) => {
      if (item.concluded) {
        doneList.push(item);
      } else {
        activeItem = item;
      }
    });
    return { active: activeItem, done: doneList };
  }, [filteredList]);

  const closeModal = useCallback((response?: CarDriverModel) => {
    setIsModalOpen(false);
    nav.goBack();

    if (!response) return;

    setDriversList((prev) => {
      const exists = prev.some((item) => item.id === response.id);
      if (exists) {
        const updatedList = prev.map((item) =>
          item.id === response.id ? response : item
        );
        loadDebtSummaryByDrivers(updatedList);
        return updatedList;
      }
      const updatedList = [response, ...prev];
      loadDebtSummaryByDrivers(updatedList);
      return updatedList;
    });
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

  return (
    <IonPage id="car-damages-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.drivers}</IonTitle>
        </IonToolbar>
        <IonToolbar>
          <IonSearchbar
            debounce={500}
            placeholder={TEXT.search}
            onIonChange={(e) => setSearchValue(e.detail.value)}
          ></IonSearchbar>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="section-shell">
          <IonList>
            <IonItem>
              <IonLabel>
                <h1>{TEXT.active}</h1>
              </IonLabel>
            </IonItem>

            {activeDoneList.active && (
              <IonItem
                button
                onClick={() => {
                  setModalDriver(activeDoneList.active!);
                  setIsModalOpen(true);
                  nav.push(nav.location.pathname + "?modalOpened=true");
                }}
              >
                <IonLabelLeft class="ion-text-wrap">
                  <h2>{activeDoneList.active?.driver?.name}</h2>
                  <p>{formatCPF(activeDoneList.active?.driver?.cpf)}</p>
                  <p>{formatTel(activeDoneList.active?.driver?.contact)}</p>
                </IonLabelLeft>
                <IonLabekRight>
                  <p>{`${formatDateView(activeDoneList.active?.startDate)}`}</p>
                  <p>{currencyFormat(activeDoneList.active?.warranty)}</p>
                  <p>
                    {TEXT.totalOutstanding}:{" "}
                    {currencyFormat(getOutstandingDebt(activeDoneList.active))}
                  </p>
                  <p>{`${activeDoneList.active?.driver?.email}`}</p>
                  <IonButton
                    size="small"
                    fill="outline"
                    color="secondary"
                    onClick={(event) =>
                      openDriverPendencies(event, activeDoneList.active)
                    }
                  >
                    {TEXT.driverPendencies}
                  </IonButton>
                </IonLabekRight>
              </IonItem>
            )}
            {!isLoading && !activeDoneList.active && <ItemNotFound />}
          </IonList>
          <IonList>
            <IonItem>
              <IonLabel>
                <h1>{TEXT.carDamagesDone}</h1>
              </IonLabel>
            </IonItem>

            {activeDoneList.done.map((carDriver: CarDriverModel, index) => {
              return (
                <IonItem
                  key={index}
                  button
                  onClick={() => {
                    setModalDriver(carDriver);
                    setIsModalOpen(true);
                    nav.push(nav.location.pathname + "?modalOpened=true");
                  }}
                >
                  <IonLabelLeft class="ion-text-wrap">
                    <h2>{carDriver?.driver?.name}</h2>
                    <p>{formatCPF(carDriver?.driver?.cpf)}</p>
                    <p>{formatTel(carDriver?.driver?.contact)}</p>
                  </IonLabelLeft>
                  <IonLabekRight>
                    <p>{`${formatDateView(
                      carDriver?.startDate
                    )} - ${formatDateView(carDriver?.endDate)}`}</p>
                    <p>
                      {TEXT.totalOutstanding}:{" "}
                      {currencyFormat(getOutstandingDebt(carDriver))}
                    </p>
                    <p>{`${carDriver?.driver?.email}`}</p>
                    <IonButton
                      size="small"
                      fill="outline"
                      color="secondary"
                      onClick={(event) => openDriverPendencies(event, carDriver)}
                    >
                      {TEXT.driverPendencies}
                    </IonButton>
                  </IonLabekRight>
                </IonItem>
              );
            })}
            {!isLoading && activeDoneList.done.length === 0 && <ItemNotFound />}
          </IonList>
        </div>
      </IonContent>
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
