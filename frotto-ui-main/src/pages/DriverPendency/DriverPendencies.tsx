import {
  IonBackButton,
  IonBadge,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonModal,
  IonPage,
  IonProgressBar,
  IonSearchbar,
  IonTitle,
  IonToolbar,
  useIonRouter,
  useIonViewWillEnter,
} from "@ionic/react";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { TEXT } from "../../constants/texts";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import {
  CarDriverModel,
  DriverDebtSummaryModel,
  DriverPendencyModel,
  DriverPendencyStatus,
} from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import DriverPendencyAdd from "./DriverPendencyAddModal/DriverPendencyAdd";
import { currencyFormat } from "../../services/currencyFormat";
import {
  add,
  alertCircleOutline,
  chevronForwardOutline,
  checkmarkDoneCircleOutline,
  checkmarkDoneOutline,
  closeOutline,
  createOutline,
  documentTextOutline,
  personCircleOutline,
  timeOutline,
} from "ionicons/icons";
import { formatDateView } from "../../services/dateFormat";
import { formatCPF } from "../../services/iMaskFormat";
import DriverPendencyPaymentModal, {
  DriverPendencyPaymentRequest,
} from "./DriverPendencyPaymentModal";
import "./DriverPendencies.css";
import "./DriverPendenciesSummary.css";

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
  const [isSummaryModalOpen, setIsSummaryModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [driverPendencyList, setDriverPendencyList] = useState<
    DriverPendencyModel[]
  >([]);
  const [debtSummary, setDebtSummary] = useState<DriverDebtSummaryModel>({});
  const [driverCar, setDriverCar] = useState<CarDriverModel | undefined>();
  const [paymentTarget, setPaymentTarget] = useState<
    DriverPendencyModel | undefined
  >(undefined);
  const [isPaymentModalOpen, setIsPaymentModalOpen] = useState(false);

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
      const driverCarResponse = await api.get(
        endpoints.DRIVERS_EDIT({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      const loadedDriverCar = driverCarResponse.data as CarDriverModel;
      const driverId = loadedDriverCar?.driver?.id;

      if (!driverId) {
        history.push("/menu", "none", "replace");
        setisLoading(false);
        return;
      }

      setDriverCar(loadedDriverCar);

      const { data } = await api.get(
        endpoints.DRIVER_DEBTS({
          pathVariables: {
            id: driverId,
          },
        })
      );
      if (data) {
        setDriverPendencyList(data);
        try {
          const summaryResponse = await api.get(
            endpoints.DRIVER_DEBT_SUMMARY({
              pathVariables: {
                id: driverId,
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

  useIonViewWillEnter(() => {
    loadDriverPendencys();
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

  const pendencyStatusOrder = (status?: DriverPendencyStatus) => {
    if (status === "OPEN") return 0;
    if (status === "PARTIALLY_PAID") return 1;
    if (status === "PAID") return 2;
    return 3;
  };

  const summaryPendencyList = useMemo(() => {
    return [...driverPendencyList].sort((first, second) => {
      const statusDiff =
        pendencyStatusOrder(first.status) - pendencyStatusOrder(second.status);

      if (statusDiff !== 0) {
        return statusDiff;
      }

      return String(second.date || "").localeCompare(String(first.date || ""));
    });
  }, [driverPendencyList]);

  const getStatusLabel = (status?: DriverPendencyStatus) => {
    if (status === "PAID") {
      return TEXT.debtPaid;
    }
    if (status === "PARTIALLY_PAID") {
      return TEXT.debtPartiallyPaid;
    }
    return TEXT.debtOpen;
  };

  const statusColor = (status?: DriverPendencyStatus) => {
    if (status === "PAID") return "success";
    if (status === "PARTIALLY_PAID") return "warning";
    return "danger";
  };

  const statusToneClass = (status?: DriverPendencyStatus) => {
    if (status === "PAID") return "app-soft-icon--success";
    if (status === "PARTIALLY_PAID") return "app-soft-icon--warning";
    return "app-soft-icon--danger";
  };

  const statusIcon = (status?: DriverPendencyStatus) => {
    if (status === "PAID") return checkmarkDoneOutline;
    if (status === "PARTIALLY_PAID") return timeOutline;
    return alertCircleOutline;
  };

  const statusClassName = (status?: DriverPendencyStatus) => {
    if (status === "PAID") return "driver-pendency-list-item--paid";
    if (status === "PARTIALLY_PAID") return "driver-pendency-list-item--partial";
    return "driver-pendency-list-item--open";
  };

  const contractPeriod = () => {
    const start = driverCar?.startDate ? formatDateView(driverCar.startDate) : "-";
    if (driverCar?.concluded && driverCar?.endDate) {
      return `${start} - ${formatDateView(driverCar.endDate)}`;
    }
    return start;
  };

  const refreshSummaryFromList = useCallback(
    (updatedList: DriverPendencyModel[]) => {
      const currentSummary = buildSummaryFromList(updatedList);
      setDebtSummary(currentSummary);
    },
    [buildSummaryFromList]
  );

  const applyPendencyResponse = useCallback(
    (response?: DriverPendencyModel) => {
      if (!response) {
        return;
      }

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
    },
    [refreshSummaryFromList]
  );

  const openPendencyEditor = useCallback(
    (driverPendency: DriverPendencyModel) => {
      setModalDriverPendencyValue(driverPendency);
      setIsModalOpen(true);
      nav.push(nav.location.pathname + "?modalOpened=true");
    },
    [nav]
  );

  const openSummaryModal = useCallback(() => {
    setIsSummaryModalOpen(true);
  }, []);

  const closeSummaryModal = useCallback(() => {
    setIsSummaryModalOpen(false);
  }, []);

  const openPaymentModal = useCallback(
    async (driverPendency: DriverPendencyModel) => {
      if (!driverPendency.id || isPaid(driverPendency.status)) {
        return;
      }
      setPaymentTarget(driverPendency);
      setIsPaymentModalOpen(true);

      try {
        const response = await api.get(
          endpoints.DRIVER_PENDENCIES_EDIT({
            pathVariables: { id: driverPendency.id },
          })
        );
        const freshPendency = response.data as DriverPendencyModel;
        if (freshPendency) {
          if (isPaid(freshPendency.status)) {
            applyPendencyResponse(freshPendency);
            setIsPaymentModalOpen(false);
            setPaymentTarget(undefined);
            return;
          }
          applyPendencyResponse(freshPendency);
          setPaymentTarget(freshPendency);
        }
      } catch (error) {
        // Mantém o valor já exibido caso a atualização falhe.
      }
    },
    [applyPendencyResponse]
  );

  const closePaymentModal = useCallback(() => {
    if (isLoading) {
      return;
    }
    setIsPaymentModalOpen(false);
    setPaymentTarget(undefined);
  }, [isLoading]);

  const openPendencyFromSummary = useCallback(
    (driverPendency: DriverPendencyModel) => {
      setIsSummaryModalOpen(false);
      openPendencyEditor(driverPendency);
    },
    [openPendencyEditor]
  );

  const handleSettlePendency = async (payment: DriverPendencyPaymentRequest) => {
    if (!paymentTarget?.id || isPaid(paymentTarget.status)) {
      return;
    }
    setisLoading(true);
    try {
      const endpoint =
        payment.mode === "partial"
          ? endpoints.DRIVER_PENDENCIES_PAYMENTS({
              pathVariables: {
                id: paymentTarget.id,
              },
            })
          : endpoints.DRIVER_PENDENCIES_PAY({
              pathVariables: {
                id: paymentTarget.id,
              },
            });

      const response = await api.post(
        endpoint,
        payment.mode === "partial" ? { amount: payment.amount } : {}
      );
      const updatedPendency = response.data as DriverPendencyModel;
      applyPendencyResponse(updatedPendency);
      setIsPaymentModalOpen(false);
      setPaymentTarget(undefined);
      setisLoading(false);
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.saveFailed);
    }
  };

  const closeModal = useCallback((response?: DriverPendencyModel) => {
    setIsModalOpen(false);
    nav.goBack();

    applyPendencyResponse(response);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applyPendencyResponse, nav]);

  const totalPendenciesCount =
    debtSummary.totalPendenciesCount ?? driverPendencyList.length;
  const openPendenciesCount = debtSummary.openPendenciesCount || 0;
  const paidPendenciesCount = debtSummary.paidPendenciesCount || 0;
  const hasOutstandingBalance = totalOutstanding > 0;

  return (
    <IonPage id="driver-pendencies-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.driverPendencies}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              className="app-primary-btn driver-pendencies-add-btn"
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
        <IonToolbar className="app-subtoolbar">
          <IonSearchbar
            debounce={500}
            placeholder={TEXT.search}
            value={searchValue}
            onIonChange={(e) => setSearchValue(e.detail.value ?? undefined)}
          ></IonSearchbar>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>

      <IonContent>
        <div className="app-shell app-shell--compact">
          <section className="app-section">
            <div className="driver-pendencies-section-head">
              <h2 className="app-section-title">{TEXT.driverPendencies}</h2>
              <p className="app-section-subtitle">
                Controle o saldo em aberto e acompanhe a liquidação das
                pendências do motorista.
              </p>
            </div>

            {driverCar && (
              <IonCard
                className="app-panel-card app-panel-card--soft driver-pendencies-driver-card"
                role="button"
                tabIndex={0}
                onClick={openSummaryModal}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    openSummaryModal();
                  }
                }}
              >
                <IonCardHeader className="app-panel-header">
                  <div
                    className={`app-soft-icon ${
                      hasOutstandingBalance
                        ? "app-soft-icon--warning"
                        : "app-soft-icon--success"
                    }`.trim()}
                  >
                    <IonIcon icon={personCircleOutline} />
                  </div>
                  <div className="app-panel-header__content">
                    <IonCardTitle className="app-panel-title">
                      {driverCar.driver?.name || TEXT.driver}
                    </IonCardTitle>
                    <IonCardSubtitle className="app-panel-subtitle">
                      {formatCPF(driverCar.driver?.cpf) || "-"}
                    </IonCardSubtitle>
                    <IonCardSubtitle className="driver-pendencies-driver-meta">
                      Contrato: {contractPeriod()}
                    </IonCardSubtitle>
                  </div>
                  <div className="driver-pendencies-driver-card__action">
                    <IonIcon icon={chevronForwardOutline} />
                  </div>
                </IonCardHeader>
                <IonCardContent>
                  <div className="driver-pendencies-summary">
                    <div
                      className={`app-soft-box ${
                        hasOutstandingBalance
                          ? "app-soft-box--warning"
                          : "app-soft-box--success"
                      }`.trim()}
                    >
                      <span className="driver-pendencies-summary__label">
                        Em aberto
                      </span>
                      <strong className="driver-pendencies-summary__value">
                        {currencyFormat(totalOutstanding)}
                      </strong>
                    </div>
                    <div className="app-soft-box app-soft-box--neutral">
                      <span className="driver-pendencies-summary__label">
                        {TEXT.driverPendencies}
                      </span>
                      <strong className="driver-pendencies-summary__value">
                        {totalPendenciesCount}
                      </strong>
                    </div>
                    <div className="app-soft-box app-soft-box--warning">
                      <span className="driver-pendencies-summary__label">
                        {TEXT.debtOpen}
                      </span>
                      <strong className="driver-pendencies-summary__value">
                        {openPendenciesCount}
                      </strong>
                    </div>
                    <div className="app-soft-box app-soft-box--success">
                      <span className="driver-pendencies-summary__label">
                        {TEXT.debtPaid}
                      </span>
                      <strong className="driver-pendencies-summary__value">
                        {paidPendenciesCount}
                      </strong>
                    </div>
                  </div>
                </IonCardContent>
              </IonCard>
            )}

            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div
                  className={`app-soft-icon ${
                    hasOutstandingBalance
                      ? "app-soft-icon--warning"
                      : "app-soft-icon--success"
                  }`.trim()}
                >
                  <IonIcon icon={documentTextOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.driverPendencies}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Pendências abertas, parciais e quitadas deste contrato.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <div className="driver-pendencies-list">
                  {filteredList.map((driverPendency: DriverPendencyModel, index) => {
                    const remainingAmount = getRemainingAmount(driverPendency);
                    const paid = isPaid(driverPendency.status);

                    return (
                      <IonItem
                        className={`driver-pendency-list-item ${statusClassName(
                          driverPendency.status
                        )}`.trim()}
                        key={driverPendency.id ?? `driver-pendency-${index}`}
                        button
                        detail={false}
                        onClick={() => {
                          openPendencyEditor(driverPendency);
                        }}
                      >
                        <div className="driver-pendency-list-item__wrap">
                          <div
                            className={`app-soft-icon ${statusToneClass(
                              driverPendency.status
                            )}`.trim()}
                          >
                            <IonIcon icon={statusIcon(driverPendency.status)} />
                          </div>

                          <div className="driver-pendency-list-item__content">
                            <div className="driver-pendency-list-item__top">
                              <div className="driver-pendency-list-item__main ion-text-wrap">
                                <h3 className="driver-pendency-list-item__title">
                                  {driverPendency.name || "-"}
                                </h3>
                                <div className="driver-pendency-list-item__meta-row">
                                  <p className="driver-pendency-list-item__meta">
                                    {formatDateView(driverPendency.date)}
                                  </p>
                                  {driverPendency.paidAt && (
                                    <p className="driver-pendency-list-item__meta">
                                      {TEXT.settledAt}:{" "}
                                      {formatDateView(driverPendency.paidAt)}
                                    </p>
                                  )}
                                </div>
                              </div>
                              <IonBadge color={statusColor(driverPendency.status)}>
                                {getStatusLabel(driverPendency.status)}
                              </IonBadge>
                            </div>

                            {driverPendency.note && (
                              <p className="driver-pendency-list-item__note">
                                {driverPendency.note}
                              </p>
                            )}

                            <div className="driver-pendency-list-item__stats">
                              <p className="driver-pendency-list-item__stat">
                                <span>{TEXT.total}</span>
                                <strong>{currencyFormat(driverPendency.cost)}</strong>
                              </p>
                              <p className="driver-pendency-list-item__stat">
                                <span>{TEXT.debtPaid}</span>
                                <strong>
                                  {currencyFormat(driverPendency.paidAmount || 0)}
                                </strong>
                              </p>
                              <p className="driver-pendency-list-item__stat">
                                <span>{TEXT.pendingAmount}</span>
                                <strong>{currencyFormat(remainingAmount)}</strong>
                              </p>
                            </div>

                            <div className="driver-pendency-list-item__actions">
                              <IonButton
                                size="small"
                                fill="outline"
                                className="app-outline-btn"
                                onClick={(event) => {
                                  event.stopPropagation();
                                  openPendencyEditor(driverPendency);
                                }}
                              >
                                <IonIcon icon={createOutline} slot="start" />
                                {TEXT.edit}
                              </IonButton>

                              {!paid && (
                                <IonButton
                                  size="small"
                                  color="success"
                                  fill="outline"
                                  className="driver-pendency-list-item__settle-btn"
                                  onClick={(event) => {
                                    event.stopPropagation();
                                    openPaymentModal(driverPendency);
                                  }}
                                >
                                  <IonIcon
                                    icon={checkmarkDoneCircleOutline}
                                    slot="start"
                                  />
                                  {TEXT.settleDebt}
                                </IonButton>
                              )}
                            </div>
                          </div>
                        </div>
                      </IonItem>
                    );
                  })}
                </div>

                {!isLoading && filteredList.length === 0 && (
                  <div className="app-empty-state">
                    <strong>{TEXT.driverPendencies}</strong>
                    <span>Nenhuma pendência encontrada para os filtros atuais.</span>
                  </div>
                )}
              </IonCardContent>
            </IonCard>
          </section>
        </div>
      </IonContent>

      <IonModal
        className="driver-pendency-main-modal"
        isOpen={isModalOpen}
        backdropDismiss={false}
      >
        <DriverPendencyAdd
          driverCarId={match.params.id}
          closeModal={closeModal}
          initialValues={modalDriverPendencyValue}
        />
      </IonModal>

      <IonModal isOpen={isPaymentModalOpen} backdropDismiss={false}>
        <DriverPendencyPaymentModal
          pendency={paymentTarget}
          isLoading={isLoading}
          closeModal={closePaymentModal}
          onSubmit={handleSettlePendency}
        />
      </IonModal>

      <IonModal
        className="driver-pendency-summary-modal"
        isOpen={isSummaryModalOpen}
        onDidDismiss={() => setIsSummaryModalOpen(false)}
        initialBreakpoint={0.9}
        breakpoints={[0, 0.9, 1]}
      >
        <IonHeader className="ion-no-border">
          <IonToolbar className="app-toolbar-clean">
            <IonTitle>Resumo das pendências</IonTitle>
            <IonButtons slot="end">
              <IonButton fill="clear" color="medium" onClick={closeSummaryModal}>
                <IonIcon slot="icon-only" icon={closeOutline} />
              </IonButton>
            </IonButtons>
          </IonToolbar>
        </IonHeader>

        <IonContent>
          <div className="driver-pendency-summary-modal__content">
            {driverCar && (
              <section className="driver-pendency-summary-driver">
                <div
                  className={`app-soft-icon ${
                    hasOutstandingBalance
                      ? "app-soft-icon--warning"
                      : "app-soft-icon--success"
                  }`.trim()}
                >
                  <IonIcon icon={personCircleOutline} />
                </div>
                <div className="driver-pendency-summary-driver__info">
                  <strong>{driverCar.driver?.name || TEXT.driver}</strong>
                  <span>{formatCPF(driverCar.driver?.cpf) || "-"}</span>
                </div>
              </section>
            )}

            <section className="driver-pendency-summary-modal__totals">
              <div
                className={`app-soft-box ${
                  hasOutstandingBalance
                    ? "app-soft-box--warning"
                    : "app-soft-box--success"
                }`.trim()}
              >
                <span className="driver-pendencies-summary__label">
                  Em aberto
                </span>
                <strong className="driver-pendencies-summary__value">
                  {currencyFormat(totalOutstanding)}
                </strong>
              </div>
              <div className="app-soft-box app-soft-box--warning">
                <span className="driver-pendencies-summary__label">
                  {TEXT.debtOpen}
                </span>
                <strong className="driver-pendencies-summary__value">
                  {openPendenciesCount}
                </strong>
              </div>
              <div className="app-soft-box app-soft-box--success">
                <span className="driver-pendencies-summary__label">
                  {TEXT.debtPaid}
                </span>
                <strong className="driver-pendencies-summary__value">
                  {paidPendenciesCount}
                </strong>
              </div>
            </section>

            <section className="driver-pendency-summary-list">
              {summaryPendencyList.map((driverPendency, index) => {
                const remainingAmount = getRemainingAmount(driverPendency);

                return (
                  <button
                    type="button"
                    className={`driver-pendency-summary-row ${statusClassName(
                      driverPendency.status
                    )}`.trim()}
                    key={driverPendency.id ?? `summary-pendency-${index}`}
                    onClick={() => openPendencyFromSummary(driverPendency)}
                  >
                    <span
                      className={`driver-pendency-summary-row__marker ${statusToneClass(
                        driverPendency.status
                      )}`.trim()}
                    >
                      <IonIcon icon={statusIcon(driverPendency.status)} />
                    </span>

                    <span className="driver-pendency-summary-row__main">
                      <strong>{driverPendency.name || "-"}</strong>
                      <span>{formatDateView(driverPendency.date)}</span>
                    </span>

                    <span className="driver-pendency-summary-row__amounts">
                      <strong>{currencyFormat(remainingAmount)}</strong>
                      <IonBadge color={statusColor(driverPendency.status)}>
                        {getStatusLabel(driverPendency.status)}
                      </IonBadge>
                    </span>

                    <IonIcon
                      className="driver-pendency-summary-row__arrow"
                      icon={chevronForwardOutline}
                    />
                  </button>
                );
              })}

              {!isLoading && summaryPendencyList.length === 0 && (
                <div className="app-empty-state">
                  <strong>{TEXT.driverPendencies}</strong>
                  <span>Nenhuma pendência cadastrada para este motorista.</span>
                </div>
              )}
            </section>
          </div>
        </IonContent>
      </IonModal>
    </IonPage>
  );
};

export default DriverPendencies;
