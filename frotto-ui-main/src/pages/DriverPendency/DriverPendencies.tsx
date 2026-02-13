import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonListHeader,
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
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import {
  DriverDebtSummaryModel,
  DriverPendencyModel,
  DriverPendencyStatus,
} from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import {
  IonLabelLeft,
  IonLabekRight,
} from "../../components/List/IonLabekRight";
import DriverPendencyAdd from "./DriverPendencyAddModal/DriverPendencyAdd";
import { currencyFormat } from "../../services/currencyFormat";
import { add, checkmarkDoneCircleOutline } from "ionicons/icons";
import { formatDateView } from "../../services/dateFormat";

interface DriverPendencyDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const DriverPendencies: React.FC<DriverPendencyDetail> = ({ match }) => {
  const location = useLocation();
  const nav = useHistory();
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalDriverPendencyValue, setModalDriverPendencyValue] =
    useState<DriverPendencyModel>({});
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [driverPendencyList, setDriverPendencyList] = useState<
    DriverPendencyModel[]
  >([]);
  const [debtSummary, setDebtSummary] = useState<DriverDebtSummaryModel>({});

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  const isPaid = (status?: DriverPendencyStatus) => status === "PAID";

  const getRemainingAmount = useCallback((driverPendency: DriverPendencyModel) => {
    if (typeof driverPendency.remainingAmount === "number") {
      return Math.max(driverPendency.remainingAmount, 0);
    }
    if (
      typeof driverPendency.cost === "number" &&
      typeof driverPendency.paidAmount === "number"
    ) {
      return Math.max(driverPendency.cost - driverPendency.paidAmount, 0);
    }
    if (typeof driverPendency.cost === "number") {
      return driverPendency.status === "PAID" ? 0 : driverPendency.cost;
    }
    return 0;
  }, []);

  const buildSummaryFromList = useCallback(
    (list: DriverPendencyModel[]): DriverDebtSummaryModel => {
      let totalOutstanding = 0;
      let openCount = 0;
      list.forEach((driverPendency) => {
        if (driverPendency.status !== "PAID") {
          openCount += 1;
          totalOutstanding += getRemainingAmount(driverPendency);
        }
      });
      const totalPendenciesCount = list.length;
      return {
        driverCarId: Number(match.params.id),
        totalOutstanding,
        openPendenciesCount: openCount,
        totalPendenciesCount,
        paidPendenciesCount: Math.max(totalPendenciesCount - openCount, 0),
      };
    },
    [getRemainingAmount, match.params.id]
  );

  const loadDriverPendencys = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.DRIVER_DEBTS({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      if (data) {
        setDriverPendencyList(data);
        try {
          const summaryResponse = await api.get(
            endpoints.DRIVER_DEBT_SUMMARY({
              pathVariables: {
                id: match.params.id,
              },
            })
          );
          setDebtSummary(summaryResponse.data || buildSummaryFromList(data));
        } catch (summaryError) {
          setDebtSummary(buildSummaryFromList(data));
        }
      } else {
        history.push("/menu", "none", "replace");
      }
      setisLoading(false);
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadPendenciesFailed);
    }
  };

  useEffect(() => {
    loadDriverPendencys();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(driverPendencyList, searchValue);
  }, [driverPendencyList, searchValue]);

  const totalOutstanding = useMemo(() => {
    if (typeof debtSummary.totalOutstanding === "number") {
      return debtSummary.totalOutstanding;
    }
    return buildSummaryFromList(driverPendencyList).totalOutstanding || 0;
  }, [buildSummaryFromList, debtSummary.totalOutstanding, driverPendencyList]);

  const getStatusLabel = (status?: DriverPendencyStatus) => {
    if (status === "PAID") {
      return TEXT.debtPaid;
    }
    if (status === "PARTIALLY_PAID") {
      return TEXT.debtPartiallyPaid;
    }
    return TEXT.debtOpen;
  };

  const refreshSummaryFromList = useCallback(
    (updatedList: DriverPendencyModel[]) => {
      const currentSummary = buildSummaryFromList(updatedList);
      setDebtSummary(currentSummary);
    },
    [buildSummaryFromList]
  );

  const handleSettlePendency = async (driverPendency: DriverPendencyModel) => {
    if (!driverPendency.id || isPaid(driverPendency.status)) {
      return;
    }
    setisLoading(true);
    try {
      const response = await api.post(
        endpoints.DRIVER_PENDENCIES_PAY({
          pathVariables: {
            id: driverPendency.id,
          },
        }),
        {}
      );
      const updatedPendency = response.data as DriverPendencyModel;
      setDriverPendencyList((prev) => {
        const updatedList = prev.map((item) =>
          item.id === updatedPendency.id ? updatedPendency : item
        );
        refreshSummaryFromList(updatedList);
        return updatedList;
      });
      setisLoading(false);
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.saveFailed);
    }
  };

  const closeModal = useCallback((response?: DriverPendencyModel) => {
    setIsModalOpen(false);
    nav.goBack();

    if (!response) return;

    setDriverPendencyList((prev) => {
      if (response.delete && response.id !== undefined) {
        const updatedList = prev.filter((item) => item.id !== response.id);
        refreshSummaryFromList(updatedList);
        return updatedList;
      }
      const exists = prev.some((item) => item.id === response.id);
      if (exists) {
        const updatedList = prev.map((item) =>
          item.id === response.id ? response : item
        );
        refreshSummaryFromList(updatedList);
        return updatedList;
      }
      const updatedList = [response, ...prev];
      refreshSummaryFromList(updatedList);
      return updatedList;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshSummaryFromList]);

  return (
    <IonPage id="driver-pendencies-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.driverPendencies}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              strong={true}
              onClick={() => {
                setModalDriverPendencyValue({});
                setIsModalOpen(true);
                nav.push(nav.location.pathname + "?modalOpened=true");
              }}
            >
              <IonIcon slot="icon-only" icon={add}></IonIcon>
            </IonButton>
          </IonButtons>
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
            <IonListHeader>
              <IonLabel>
                <p>
                  <strong>
                    {TEXT.totalOutstanding} {currencyFormat(totalOutstanding)}
                  </strong>
                </p>
                <p>
                  {TEXT.openPendencies}: {debtSummary.openPendenciesCount || 0}
                </p>
                <p>
                  {TEXT.closedPendencies}:{" "}
                  {debtSummary.paidPendenciesCount || 0}
                </p>
              </IonLabel>
            </IonListHeader>
            {filteredList.map((driverPendency: DriverPendencyModel, index) => {
              const paid = isPaid(driverPendency.status);
              return (
                <IonItem
                  key={index}
                  button={!paid}
                  onClick={() => {
                    if (paid) {
                      return;
                    }
                    setModalDriverPendencyValue(driverPendency);
                    setIsModalOpen(true);
                    nav.push(nav.location.pathname + "?modalOpened=true");
                  }}
                >
                  <IonLabelLeft class="ion-text-wrap">
                    <h2>{formatDateView(driverPendency.date)}</h2>
                    <p>{driverPendency.name}</p>
                    {driverPendency.note && <p>{driverPendency.note}</p>}
                  </IonLabelLeft>
                  <IonLabekRight>
                    <p>{getStatusLabel(driverPendency.status)}</p>
                    <p>{currencyFormat(driverPendency.cost)}</p>
                    {!paid && (
                      <p>
                        {TEXT.pendingAmount}:{" "}
                        {currencyFormat(getRemainingAmount(driverPendency))}
                      </p>
                    )}
                    {paid && (
                      <p>
                        {TEXT.settledAt}: {formatDateView(driverPendency.paidAt)}
                      </p>
                    )}
                    {!paid && (
                      <IonButton
                        size="small"
                        fill="outline"
                        color="success"
                        onClick={(event) => {
                          event.stopPropagation();
                          handleSettlePendency(driverPendency);
                        }}
                      >
                        <IonIcon
                          icon={checkmarkDoneCircleOutline}
                          slot="start"
                        />
                        {TEXT.settleDebt}
                      </IonButton>
                    )}
                  </IonLabekRight>
                </IonItem>
              );
            })}
            {!isLoading && filteredList.length === 0 && <ItemNotFound />}
          </IonList>
        </div>
      </IonContent>
      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
        <DriverPendencyAdd
          driverCarId={match.params.id}
          closeModal={closeModal}
          initialValues={modalDriverPendencyValue}
        />
      </IonModal>
    </IonPage>
  );
};

export default DriverPendencies;
