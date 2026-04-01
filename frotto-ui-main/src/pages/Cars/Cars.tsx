import {
  IonButton,
  IonButtons,
  IonCard,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonMenuButton,
  IonModal,
  IonPopover,
  IonPage,
  IonProgressBar,
  IonSearchbar,
  IonTitle,
  IonToolbar,
  useIonViewDidLeave,
  useIonViewWillEnter,
  useIonViewWillLeave,
} from "@ionic/react";
import {
  add,
  carOutline,
  cashOutline,
  constructOutline,
  notificationsOutline,
  walletOutline,
} from "ionicons/icons";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { TEXT } from "../../constants/texts";
import { useAlert } from "../../services/hooks/useAlert";
import CarAdd from "./CarAddModal/CarAdd";
import MaintenanceAdd from "../Maintenance/MaintenanceAddModal/MaintenanceAdd";
import ReminderAdd from "../Reminders/ReminderAddModal/reminderAdd";
import IncomeAdd from "../Income/IncomeAddModal/IncomeAdd";
import CarExpenseAdd from "../CarExpense/CarExpenseAddModal/CarExpenseAdd";
import CarSelector from "../../components/Car/CarSelector";
import CarListItem from "./CarListItem";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import {
  CarExpenseModel,
  CarModel,
  IncomeModel,
  MaintenanceModel,
  ReminderModel,
} from "../../constants/CarModels";
import { normalizeCarRecord } from "../../components/Car/carIdentity";
import "./Car.css";

type ActionType = "maintenance" | "reminder" | "expense" | "income" | null;
type SemanticTone = "success" | "danger" | "warning" | "neutral";
type QuickAction = {
  key: ActionType | "car";
  title: string;
  description: string;
};
type QuickActionResponse =
  | MaintenanceModel
  | ReminderModel
  | IncomeModel
  | CarExpenseModel;
type CarListItemData = CarModel & {
  car?: CarModel;
  carId?: number;
};
type DashboardSummary = {
  totalCars?: number;
  activeCars: number;
  inactiveCars?: number;
  rentedCars: number;
  availableCars: number;
  totalExpenses?: number;
  hasTotalCarsData: boolean;
  hasExpenseData: boolean;
  hasRevenueData: boolean;
  hasOpenPendenciesData: boolean;
};

const EMPTY_DASHBOARD_SUMMARY: DashboardSummary = {
  activeCars: 0,
  inactiveCars: 0,
  rentedCars: 0,
  availableCars: 0,
  hasTotalCarsData: false,
  hasExpenseData: false,
  hasRevenueData: false,
  hasOpenPendenciesData: false,
};

const extractListData = <T extends object>(data: unknown): T[] => {
  if (Array.isArray(data)) {
    return data as T[];
  }

  if (data && typeof data === "object") {
    const anyData = data as {
      items?: T[];
      content?: T[];
      data?: T[];
    };
    return anyData.items || anyData.content || anyData.data || [];
  }

  return [];
};

const normalizeCars = (list: CarListItemData[]): CarModel[] =>
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

const isOperationalCar = (car: CarModel): boolean =>
  car.active !== false && (car.adminStatus || "ATIVO") === "ATIVO";

const buildDashboardSummary = (
  activeCars: CarModel[],
  allCarsData?: unknown,
  expensesData?: unknown
): DashboardSummary => {
  const allCars = extractListData<CarListItemData>(allCarsData);
  const normalizedAllCars = normalizeCars(allCars);
  const carsForStatus = allCarsData !== undefined ? normalizedAllCars : activeCars;
  const nonDeletedCars = carsForStatus.filter((car) => car.active !== false);
  const operationalCars = nonDeletedCars.filter(isOperationalCar);
  const rentedCars = operationalCars.filter((car) => Boolean(car.driverName)).length;
  const availableCars = Math.max(operationalCars.length - rentedCars, 0);
  const expenseItems = extractListData<CarExpenseModel>(expensesData);
  const totalExpenses =
    expenseItems.length > 0
      ? expenseItems.reduce(
          (sum, item) => sum + (typeof item.cost === "number" ? item.cost : 0),
          0
        )
      : expenseItems.length === 0 && expensesData !== undefined
      ? 0
      : undefined;

  return {
    totalCars: allCarsData !== undefined ? nonDeletedCars.length : undefined,
    activeCars: operationalCars.length,
    inactiveCars:
      allCarsData !== undefined
        ? Math.max(nonDeletedCars.length - operationalCars.length, 0)
        : undefined,
    rentedCars,
    availableCars,
    totalExpenses,
    hasTotalCarsData: allCarsData !== undefined,
    hasExpenseData: expensesData !== undefined,
    hasRevenueData: false,
    hasOpenPendenciesData: false,
  };
};

const resolveQuickActionTone = (key: QuickAction["key"]): SemanticTone => {
  if (key === "income") return "success";
  if (key === "expense") return "danger";
  if (key === "reminder") return "warning";
  return "neutral";
};

const resolveQuickActionIcon = (key: QuickAction["key"]): string => {
  if (key === "income") return cashOutline;
  if (key === "expense") return walletOutline;
  if (key === "reminder") return notificationsOutline;
  if (key === "maintenance") return constructOutline;
  return carOutline;
};

const Cars: React.FC = () => {
  const location = useLocation();
  const { showErrorAlert } = useAlert();

  const [isLoading, setIsLoading] = useState(false);
  const [isAddCarModalOpen, setIsAddCarModalOpen] = useState(false);
  const [isActionPickerOpen, setIsActionPickerOpen] = useState(false);
  const [actionPickerEvent, setActionPickerEvent] = useState<Event | undefined>(undefined);
  const [actionPickerKey, setActionPickerKey] = useState(0);
  const [addCarModalKey, setAddCarModalKey] = useState(0);
  const [isActionSelectorModalOpen, setIsActionSelectorModalOpen] = useState(false);
  const [isActionModalOpen, setIsActionModalOpen] = useState(false);
  const [actionSelectorModalKey, setActionSelectorModalKey] = useState(0);
  const [actionModalKey, setActionModalKey] = useState(0);
  const [selectedAction, setSelectedAction] = useState<ActionType>(null);
  const [selectedCar, setSelectedCar] = useState<CarModel | null>(null);
  const [searchValue, setSearchValue] = useState("");
  const [carList, setCarList] = useState<CarModel[]>([]);
  const [dashboardSummary, setDashboardSummary] = useState<DashboardSummary>(
    EMPTY_DASHBOARD_SUMMARY
  );
  const [isDashboardLoading, setIsDashboardLoading] = useState(true);

  const abortControllerRef = useRef<AbortController | null>(null);
  const actionOpenTimerRef = useRef<number | null>(null);
  const pendingQuickActionRef = useRef<QuickAction["key"] | null>(null);
  const isCarsActiveRef = useRef(location.pathname === "/menu/carros");
  const previousPathnameRef = useRef(location.pathname);

  const logBackdropCount = useCallback((label: string) => {
    if (process.env.NODE_ENV !== "development" || typeof document === "undefined") {
      return;
    }

    // eslint-disable-next-line no-console
    console.log(
      `[Cars overlays] ${label} backdrops=${document.querySelectorAll("ion-backdrop").length}`
    );
  }, []);

  const clearScheduledActionOpen = useCallback(() => {
    if (actionOpenTimerRef.current !== null) {
      window.clearTimeout(actionOpenTimerRef.current);
      actionOpenTimerRef.current = null;
    }
  }, []);

  const resetCarsOverlayState = useCallback(() => {
    setIsAddCarModalOpen(false);
    setIsActionPickerOpen(false);
    setActionPickerEvent(undefined);
    setIsActionSelectorModalOpen(false);
    setIsActionModalOpen(false);
    pendingQuickActionRef.current = null;
    setSelectedAction(null);
    setSelectedCar(null);
  }, []);

  const cleanupCarsOverlays = useCallback(
    (reason: string) => {
      clearScheduledActionOpen();
      logBackdropCount(`${reason}:cleanup`);
      resetCarsOverlayState();
    },
    [clearScheduledActionOpen, logBackdropCount, resetCarsOverlayState]
  );

  const loadDashboardSummary = useCallback(async (activeCars: CarModel[], signal?: AbortSignal) => {
    setIsDashboardLoading(true);

    try {
      const [allCarsResult, expensesResult] = await Promise.allSettled([
        api.get(endpoints.CARS(), { signal }),
        api.get(endpoints.CAR_EXPENSES_ALL(), { signal }),
      ]);

      if (signal?.aborted) {
        return;
      }

      const allCarsData =
        allCarsResult.status === "fulfilled" ? allCarsResult.value?.data : undefined;
      const expensesData =
        expensesResult.status === "fulfilled" ? expensesResult.value?.data : undefined;

      setDashboardSummary(buildDashboardSummary(activeCars, allCarsData, expensesData));
    } finally {
      if (!signal?.aborted) {
        setIsDashboardLoading(false);
      }
    }
  }, []);

  const loadCars = useCallback(
    async (signal?: AbortSignal) => {
      setIsLoading(true);

      try {
        if (abortControllerRef.current) abortControllerRef.current.abort();
        abortControllerRef.current = new AbortController();
        const currentSignal = signal || abortControllerRef.current.signal;

        const response = await api.get(endpoints.CARS_ACTIVE(), {
          signal: currentSignal,
        });

        const normalizedCars = normalizeCars(
          extractListData<CarListItemData>(response?.data ?? [])
        );

        setCarList(normalizedCars);
        void loadDashboardSummary(normalizedCars, currentSignal);
      } catch (error: any) {
        if (error?.name === "AbortError" || error?.code === "ERR_CANCELED") return;

        // eslint-disable-next-line no-console
        console.error("Erro ao carregar carros:", error);
        showErrorAlert(
          `${TEXT.loadCarsFailed}${error?.message ? `: ${error.message}` : ""}`
        );
        setCarList([]);
        setDashboardSummary(EMPTY_DASHBOARD_SUMMARY);
        setIsDashboardLoading(false);
      } finally {
        setIsLoading(false);
      }
    },
    [loadDashboardSummary, showErrorAlert]
  );

  useIonViewWillEnter(() => {
    isCarsActiveRef.current = true;
    loadCars();

    return () => {
      if (abortControllerRef.current) abortControllerRef.current.abort();
    };
  }, [loadCars]);

  // ✅ AQUI é o ajuste que elimina: "Property 'id' does not exist on type 'Object'"
  const filteredList = useMemo<CarModel[]>(() => {
    if (!searchValue.trim()) return carList;

    // filterListObj está “perdendo” o tipo, então forçamos o retorno como CarModel[]
    return filterListObj(carList, searchValue) as CarModel[];
  }, [carList, searchValue]);

  const fleetSummary = useMemo(
    () => [
      {
        key: "active",
        label: "Veículos ativos",
        value: `${dashboardSummary.activeCars}`,
        tone: "active",
      },
      {
        key: "inactive",
        label: "Veículos inativos",
        value:
          dashboardSummary.hasTotalCarsData &&
          typeof dashboardSummary.inactiveCars === "number"
            ? `${dashboardSummary.inactiveCars}`
            : "--",
        tone: "inactive",
      },
    ],
    [
      dashboardSummary.activeCars,
      dashboardSummary.hasTotalCarsData,
      dashboardSummary.inactiveCars,
    ]
  );

  const carsListCaption = useMemo(() => {
    if (searchValue.trim()) {
      return `${filteredList.length} resultado(s) encontrado(s)`;
    }

    return "Toque em um veículo para ver os detalhes.";
  }, [filteredList.length, searchValue]);

  const handleDeleteCar = useCallback((deletedCarId: number) => {
    setCarList((prev) => prev.filter((car) => car.id !== deletedCarId));
    void loadCars();
  }, [loadCars]);

  const handleOpenActionPicker = useCallback((event?: Event) => {
    if (!isCarsActiveRef.current) {
      return;
    }

    clearScheduledActionOpen();
    pendingQuickActionRef.current = null;
    setActionPickerEvent(event);
    setActionPickerKey((prev) => prev + 1);
    setIsActionPickerOpen(true);
    logBackdropCount("popover-open");
  }, [clearScheduledActionOpen, logBackdropCount]);

  const handleOpenAddCarModal = useCallback(() => {
    clearScheduledActionOpen();
    pendingQuickActionRef.current = null;
    setAddCarModalKey((prev) => prev + 1);
    setIsAddCarModalOpen(true);
    logBackdropCount("add-car-open");
  }, [clearScheduledActionOpen, logBackdropCount]);

  const handleCloseAddCarModal = useCallback(
    (response?: CarModel) => {
      logBackdropCount("add-car-close");
      setIsAddCarModalOpen(false);
      pendingQuickActionRef.current = null;

      if (!response) return;
      const normalizedResponse = normalizeCarRecord(response);

      setCarList((prev) => {
        const exists = prev.some((item) => item.id === normalizedResponse.id);
        if (exists) {
          return prev.map((item) =>
            item.id === normalizedResponse.id ? normalizedResponse : item
          );
        }
        return [normalizedResponse, ...prev];
      });
      void loadCars();
    },
    [loadCars, logBackdropCount]
  );

  const handleAddCarModalDidDismiss = useCallback(() => {
    setIsAddCarModalOpen(false);
    pendingQuickActionRef.current = null;
    logBackdropCount("add-car-dismissed");
  }, [logBackdropCount]);

  const handleOpenActionSelectorModal = useCallback((action: ActionType) => {
    clearScheduledActionOpen();
    pendingQuickActionRef.current = null;
    setSelectedAction(action);
    setSelectedCar(null);
    setActionSelectorModalKey((prev) => prev + 1);
    setIsActionSelectorModalOpen(true);
    logBackdropCount(`selector-open:${action}`);
  }, [clearScheduledActionOpen, logBackdropCount]);

  const handleQueueActionOpen = useCallback(() => {
    clearScheduledActionOpen();
    actionOpenTimerRef.current = window.setTimeout(() => {
      actionOpenTimerRef.current = null;
      if (!isCarsActiveRef.current) {
        return;
      }
      setActionModalKey((prev) => prev + 1);
      setIsActionModalOpen(true);
      logBackdropCount("action-open");
    }, 0);
  }, [clearScheduledActionOpen, logBackdropCount]);

  const handleCloseActionModal = useCallback((response?: QuickActionResponse) => {
    clearScheduledActionOpen();
    setIsActionModalOpen(false);
    pendingQuickActionRef.current = null;
    logBackdropCount("action-close");

    if (!response) return;
  }, [clearScheduledActionOpen, logBackdropCount]);

  const handleCloseActionSelectorModal = useCallback(() => {
    clearScheduledActionOpen();
    setIsActionSelectorModalOpen(false);
    pendingQuickActionRef.current = null;
    setSelectedAction(null);
    setSelectedCar(null);
    logBackdropCount("selector-close");
  }, [clearScheduledActionOpen, logBackdropCount]);

  const handleActionModalDidDismiss = useCallback(() => {
    clearScheduledActionOpen();
    setIsActionModalOpen(false);
    pendingQuickActionRef.current = null;
    setSelectedAction(null);
    setSelectedCar(null);
    logBackdropCount("action-dismissed");
  }, [clearScheduledActionOpen, logBackdropCount]);

  const handleActionSelectorModalDidDismiss = useCallback(() => {
    setIsActionSelectorModalOpen(false);
    logBackdropCount("selector-dismissed");
  }, [logBackdropCount]);

  const handleActionPickerDidDismiss = useCallback(() => {
    setIsActionPickerOpen(false);
    setActionPickerEvent(undefined);
    logBackdropCount("popover-dismissed");

    const nextAction = pendingQuickActionRef.current;
    pendingQuickActionRef.current = null;

    if (!isCarsActiveRef.current || !nextAction) {
      return;
    }

    if (nextAction === "car") {
      handleOpenAddCarModal();
      return;
    }

    handleOpenActionSelectorModal(nextAction);
  }, [handleOpenActionSelectorModal, handleOpenAddCarModal, logBackdropCount]);

  const handleSelectQuickAction = useCallback(
    (action: ActionType | "car") => {
      pendingQuickActionRef.current = action;
      setIsActionPickerOpen(false);
    },
    []
  );

  const renderActionModalContent = () => {
    if (!selectedAction || !selectedCar) return null;

    const selectedCarId = selectedCar.id;
    const modalProps = {
      closeModal: handleCloseActionModal,
      carId:
        selectedCarId !== undefined && selectedCarId !== null
          ? String(selectedCarId)
          : undefined,
    };

    switch (selectedAction) {
      case "maintenance":
        return <MaintenanceAdd {...modalProps} />;
      case "reminder":
        return <ReminderAdd {...modalProps} />;
      case "income":
        return <IncomeAdd {...modalProps} />;
      case "expense":
        return <CarExpenseAdd {...modalProps} />;
      default:
        return null;
    }
  };

  const handleSelectActionCar = useCallback(
    (car: CarModel) => {
      clearScheduledActionOpen();
      setSelectedCar(car);
      setIsActionSelectorModalOpen(false);
      handleQueueActionOpen();
    },
    [clearScheduledActionOpen, handleQueueActionOpen]
  );

  const renderActionSelectorModalContent = () => {
    if (!selectedAction) return null;

    return (
      <IonPage id="cars-action-select-page">
        <IonHeader>
          <IonToolbar>
            <IonButtons slot="start">
              <IonButton
                className="app-semantic-btn app-semantic--neutral"
                fill="clear"
                onClick={handleCloseActionSelectorModal}
              >
                {TEXT.cancel}
              </IonButton>
            </IonButtons>

            <IonTitle>
              {selectedAction === "maintenance" && TEXT.addCarMaintenance}
              {selectedAction === "reminder" && TEXT.reminder}
              {selectedAction === "expense" && TEXT.carExpense}
              {selectedAction === "income" && TEXT.income}
            </IonTitle>
          </IonToolbar>
        </IonHeader>
        <IonContent>
          <div className="app-form-page__body">
            <div className="app-form-page__panel">
              <h3 className="app-form-page__title">{`${TEXT.select} ${TEXT.car}`}</h3>
              <CarSelector
                cars={carList}
                onSelect={handleSelectActionCar}
              />
            </div>
          </div>
        </IonContent>
      </IonPage>
    );
  };

  const quickActions = useMemo<QuickAction[]>(
    () => [
      { key: "car", title: TEXT.addCar, description: "Adicionar um novo veículo" },
      {
        key: "maintenance",
        title: TEXT.addCarMaintenance,
        description: "Registrar serviços e custos",
      },
      { key: "reminder", title: TEXT.reminder, description: "Criar alerta para o carro" },
      {
        key: "expense",
        title: `${TEXT.add} ${TEXT.carExpense}`,
        description: "Nova despesa do veículo",
      },
      { key: "income", title: `${TEXT.add} ${TEXT.income}`, description: "Adicionar receita" },
    ],
    []
  );

  useIonViewWillLeave(() => {
    isCarsActiveRef.current = false;
    cleanupCarsOverlays("cars-will-leave");
  }, [cleanupCarsOverlays]);

  useIonViewDidLeave(() => {
    isCarsActiveRef.current = false;
    cleanupCarsOverlays("cars-did-leave");
  }, [cleanupCarsOverlays]);

  useEffect(() => {
    const previousPathname = previousPathnameRef.current;
    const leftCarsPage =
      previousPathname === "/menu/carros" && location.pathname !== "/menu/carros";
    const enteredCarsPage =
      previousPathname !== "/menu/carros" && location.pathname === "/menu/carros";

    if (enteredCarsPage) {
      isCarsActiveRef.current = true;
    }

    if (leftCarsPage) {
      isCarsActiveRef.current = false;
      cleanupCarsOverlays("cars-path-change");
    }

    previousPathnameRef.current = location.pathname;
  }, [cleanupCarsOverlays, location.pathname]);

  useEffect(() => {
    return () => {
      clearScheduledActionOpen();
      isCarsActiveRef.current = false;
      resetCarsOverlayState();
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [clearScheduledActionOpen, resetCarsOverlayState]);

  return (
    <IonPage id="cars-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonMenuButton menu="main-menu" autoHide={false} />
          </IonButtons>

          <IonTitle>{TEXT.cars}</IonTitle>

          <IonButtons slot="end">
            <IonButton
              className="app-semantic-btn app-semantic--neutral"
              id="cars-action-trigger"
              fill="clear"
              onClick={(event) => handleOpenActionPicker(event.nativeEvent)}
            >
              <IonIcon slot="icon-only" icon={add} />
            </IonButton>
          </IonButtons>
        </IonToolbar>

        <IonToolbar>
          <div className="app-toolbar-search">
            <IonSearchbar
              debounce={500}
              placeholder={TEXT.search}
              value={searchValue}
              onIonChange={(e) => setSearchValue(e.detail.value || "")}
            />
          </div>
          {isLoading && <IonProgressBar type="indeterminate" />}
        </IonToolbar>
      </IonHeader>

      <IonContent scrollY forceOverscroll={true}>
        <div className="section-shell cars-shell cars-shell--list">
          <IonCard
            className={`cars-fleet-summary-card${
              isDashboardLoading ? " cars-fleet-summary-card--loading" : ""
            }`}
          >
            <div className="cars-fleet-summary">
              {fleetSummary.map((item) => (
                <div
                  key={item.key}
                  className={`cars-fleet-summary__item cars-fleet-summary__item--${item.tone}`}
                >
                  <span className="cars-fleet-summary__label">{item.label}</span>
                  <strong className="cars-fleet-summary__value">{item.value}</strong>
                </div>
              ))}
            </div>
          </IonCard>

          <div className="cars-list-section__header">
            <div>
              <h3 className="cars-list-section__title">Veículos ativos</h3>
              <p className="cars-list-section__caption">{carsListCaption}</p>
            </div>
          </div>

          <div className="cards-grid cards-grid--cars">
            {filteredList.map((car: CarModel, index) => (
              <CarListItem
                key={car.id ?? `car-${index}`}
                {...car}
                onDeleted={handleDeleteCar}
              />
            ))}
          </div>

          {!isLoading && filteredList.length === 0 && (
            <div className="cards-empty cards-empty--cars">
              <ItemNotFound />
            </div>
          )}
        </div>
      </IonContent>

      <IonPopover
        key={`cars-action-picker-${actionPickerKey}`}
        isOpen={isActionPickerOpen}
        event={actionPickerEvent}
        onDidDismiss={handleActionPickerDidDismiss}
        side="bottom"
        alignment="end"
        className="action-picker-popover"
        showBackdrop
      >
        <IonContent className="action-picker-content" scrollY>
          <IonList inset>
            {quickActions.map((action) => (
              <IonItem button key={action.key} onClick={() => handleSelectQuickAction(action.key)}>
                <IonIcon
                  slot="start"
                  icon={resolveQuickActionIcon(action.key)}
                  className={`app-semantic-icon app-semantic--${resolveQuickActionTone(
                    action.key
                  )} cars-action-picker__icon`}
                />
                <IonLabel>
                  <h3 className="cars-action-picker__title">{action.title}</h3>
                  <p className="cars-action-picker__description">{action.description}</p>
                </IonLabel>
              </IonItem>
            ))}
          </IonList>
        </IonContent>
      </IonPopover>

      <IonModal
        key={`cars-add-${addCarModalKey}`}
        isOpen={isAddCarModalOpen}
        onDidDismiss={handleAddCarModalDidDismiss}
        backdropDismiss={false}
        keepContentsMounted={false}
      >
        <CarAdd closeModal={handleCloseAddCarModal} />
      </IonModal>

      <IonModal
        key={`cars-action-selector-${actionSelectorModalKey}`}
        isOpen={isActionSelectorModalOpen}
        onDidDismiss={handleActionSelectorModalDidDismiss}
        backdropDismiss={false}
        keepContentsMounted={false}
      >
        {renderActionSelectorModalContent()}
      </IonModal>

      <IonModal
        key={`cars-action-${actionModalKey}`}
        isOpen={isActionModalOpen}
        onDidDismiss={handleActionModalDidDismiss}
        backdropDismiss={false}
        keepContentsMounted={false}
      >
        {renderActionModalContent()}
      </IonModal>
    </IonPage>
  );
};

export default Cars;
