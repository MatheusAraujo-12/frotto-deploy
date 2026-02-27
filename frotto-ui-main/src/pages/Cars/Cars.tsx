import {
  IonButton,
  IonButtons,
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
import { add } from "ionicons/icons";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useHistory, useLocation } from "react-router-dom";
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

type ActionType = "maintenance" | "reminder" | "expense" | "income" | null;
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

type DismissibleOverlay = {
  dismiss: () => Promise<boolean>;
};

const Cars: React.FC = () => {
  const history = useHistory();
  const location = useLocation();
  const { showErrorAlert } = useAlert();

  const [isLoading, setIsLoading] = useState(false);
  const [isAddCarModalOpen, setIsAddCarModalOpen] = useState(false);
  const [isActionPickerOpen, setIsActionPickerOpen] = useState(false);
  const [isActionSelectorModalOpen, setIsActionSelectorModalOpen] = useState(false);
  const [isActionModalOpen, setIsActionModalOpen] = useState(false);
  const [actionSelectorModalKey, setActionSelectorModalKey] = useState(0);
  const [actionModalKey, setActionModalKey] = useState(0);
  const [pendingQuickAction, setPendingQuickAction] = useState<QuickAction["key"] | null>(null);
  const [selectedAction, setSelectedAction] = useState<ActionType>(null);
  const [selectedCar, setSelectedCar] = useState<CarModel | null>(null);
  const [searchValue, setSearchValue] = useState("");
  const [carList, setCarList] = useState<CarModel[]>([]);

  const abortControllerRef = useRef<AbortController | null>(null);
  const actionOpenTimerRef = useRef<number | null>(null);
  const previousPathnameRef = useRef(location.pathname);
  const addCarModalRef = useRef<HTMLIonModalElement | null>(null);
  const actionPickerRef = useRef<HTMLIonPopoverElement | null>(null);
  const actionSelectorModalRef = useRef<HTMLIonModalElement | null>(null);
  const actionModalRef = useRef<HTMLIonModalElement | null>(null);

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

  const dismissOverlayElement = useCallback((overlay: DismissibleOverlay | null) => {
    if (!overlay) {
      return;
    }

    void overlay.dismiss().catch(() => undefined);
  }, []);

  const resetCarsOverlayState = useCallback(() => {
    setIsAddCarModalOpen(false);
    setIsActionPickerOpen(false);
    setIsActionSelectorModalOpen(false);
    setIsActionModalOpen(false);
    setPendingQuickAction(null);
    setSelectedAction(null);
    setSelectedCar(null);
  }, []);

  const dismissCarsOverlays = useCallback(
    (reason: string) => {
      clearScheduledActionOpen();
      logBackdropCount(`${reason}:before`);
      dismissOverlayElement(actionPickerRef.current);
      dismissOverlayElement(actionSelectorModalRef.current);
      dismissOverlayElement(actionModalRef.current);
      dismissOverlayElement(addCarModalRef.current);
      logBackdropCount(`${reason}:after`);
    },
    [clearScheduledActionOpen, dismissOverlayElement, logBackdropCount]
  );

  const cleanupCarsOverlays = useCallback(
    (reason: string) => {
      dismissCarsOverlays(reason);
      resetCarsOverlayState();
    },
    [dismissCarsOverlays, resetCarsOverlayState]
  );

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    const modalOpened = searchParams.get("modalOpened") === "true";
    const isCarsPage = location.pathname === "/menu/carros";
    setIsAddCarModalOpen(isCarsPage && modalOpened);
  }, [location.pathname, location.search]);

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

        const data = response?.data ?? [];
        let list: CarListItemData[] = [];

        if (Array.isArray(data)) {
          list = data;
        } else if (data && typeof data === "object") {
          const anyData = data as {
            items?: CarListItemData[];
            content?: CarListItemData[];
            data?: CarListItemData[];
          };
          list = anyData.items || anyData.content || anyData.data || [];
        }

        setCarList(
          list.map((item) => {
            const { car, carId, ...rest } = item;
            const baseCar = car ?? rest;
            const resolvedId = baseCar.id ?? rest.id ?? carId;
            return { ...rest, ...baseCar, id: resolvedId };
          })
        );
      } catch (error: any) {
        if (error?.name === "AbortError" || error?.code === "ERR_CANCELED") return;

        // eslint-disable-next-line no-console
        console.error("Erro ao carregar carros:", error);
        showErrorAlert(
          `${TEXT.loadCarsFailed}${error?.message ? `: ${error.message}` : ""}`
        );
        setCarList([]);
      } finally {
        setIsLoading(false);
      }
    },
    [showErrorAlert]
  );

  useIonViewWillEnter(() => {
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

  const handleDeleteCar = useCallback((deletedCarId: number) => {
    setCarList((prev) => prev.filter((car) => car.id !== deletedCarId));
  }, []);

  const handleCloseAddCarModal = useCallback(
    (response?: CarModel) => {
      logBackdropCount("add-car-close");
      setIsAddCarModalOpen(false);

      const isCarsPage = location.pathname === "/menu/carros";
      if (isCarsPage) {
        const searchParams = new URLSearchParams(location.search);
        searchParams.delete("modalOpened");
        history.replace({
          pathname: location.pathname,
          search: searchParams.toString(),
        });
      }

      if (!response) return;

      setCarList((prev) => {
        const exists = prev.some((item) => item.id === response.id);
        if (exists) {
          return prev.map((item) => (item.id === response.id ? response : item));
        }
        return [response, ...prev];
      });
    },
    [history, location, logBackdropCount]
  );

  const handleOpenActionSelectorModal = useCallback((action: ActionType) => {
    clearScheduledActionOpen();
    setSelectedAction(action);
    setSelectedCar(null);
    setActionSelectorModalKey((prev) => prev + 1);
    setIsActionSelectorModalOpen(true);
    logBackdropCount(`selector-open:${action}`);
  }, [clearScheduledActionOpen, logBackdropCount]);

  const handleCloseActionModal = useCallback((response?: QuickActionResponse) => {
    clearScheduledActionOpen();
    setIsActionModalOpen(false);
    logBackdropCount("action-close");

    if (!response) return;
  }, [clearScheduledActionOpen, logBackdropCount]);

  const handleCloseActionSelectorModal = useCallback(() => {
    clearScheduledActionOpen();
    setIsActionSelectorModalOpen(false);
    setPendingQuickAction(null);
    setSelectedAction(null);
    setSelectedCar(null);
    logBackdropCount("selector-close");
  }, [clearScheduledActionOpen, logBackdropCount]);

  const handleActionModalDidDismiss = useCallback(() => {
    clearScheduledActionOpen();
    setIsActionModalOpen(false);
    setPendingQuickAction(null);
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
    logBackdropCount("popover-dismissed");

    if (!pendingQuickAction) {
      return;
    }

    if (pendingQuickAction === "car") {
      const searchParams = new URLSearchParams(location.search);
      searchParams.set("modalOpened", "true");
      history.push({
        pathname: location.pathname,
        search: searchParams.toString(),
      });
      setIsAddCarModalOpen(true);
      logBackdropCount("add-car-open");
      setPendingQuickAction(null);
      return;
    }

    handleOpenActionSelectorModal(pendingQuickAction);
    setPendingQuickAction(null);
  }, [handleOpenActionSelectorModal, history, location, logBackdropCount, pendingQuickAction]);

  const handleSelectQuickAction = useCallback(
    (action: ActionType | "car") => {
      setPendingQuickAction(action);
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
      actionOpenTimerRef.current = window.setTimeout(() => {
        setActionModalKey((prev) => prev + 1);
        setIsActionModalOpen(true);
        actionOpenTimerRef.current = null;
        logBackdropCount("action-open");
      }, 0);
    },
    [clearScheduledActionOpen, logBackdropCount]
  );

  const renderActionSelectorModalContent = () => {
    if (!selectedAction) return null;

    return (
      <IonPage id="cars-action-select-page">
        <IonHeader>
          <IonToolbar>
            <IonButtons slot="start">
              <IonButton onClick={handleCloseActionSelectorModal}>
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
          <div style={{ padding: 16 }}>
            <h3 style={{ marginTop: 0, marginBottom: 16 }}>
              {`${TEXT.select} ${TEXT.car}`}
            </h3>
            <CarSelector
              cars={carList}
              onSelect={handleSelectActionCar}
            />
          </div>
        </IonContent>
      </IonPage>
    );
  };

  const quickActions = useMemo<QuickAction[]>(
    () => [
      { key: "car", title: TEXT.addCar, description: "Adicionar um novo veículo" },
      { key: "maintenance", title: TEXT.addCarMaintenance, description: "Registrar serviços e custos" },
      { key: "reminder", title: TEXT.reminder, description: "Criar alerta para o carro" },
      { key: "expense", title: `${TEXT.add} ${TEXT.carExpense}`, description: "Nova despesa do veículo" },
      { key: "income", title: `${TEXT.add} ${TEXT.income}`, description: "Adicionar receita" },
    ],
    []
  );

  useIonViewWillLeave(() => {
    cleanupCarsOverlays("cars-will-leave");
  }, [cleanupCarsOverlays]);

  useIonViewDidLeave(() => {
    cleanupCarsOverlays("cars-did-leave");
  }, [cleanupCarsOverlays]);

  useEffect(() => {
    const previousPathname = previousPathnameRef.current;
    const leftCarsPage =
      previousPathname === "/menu/carros" && location.pathname !== "/menu/carros";

    if (leftCarsPage) {
      cleanupCarsOverlays("cars-path-change");
    }

    previousPathnameRef.current = location.pathname;
  }, [cleanupCarsOverlays, location.pathname]);

  useEffect(() => {
    return () => {
      clearScheduledActionOpen();
      dismissCarsOverlays("cars-unmount");
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [clearScheduledActionOpen, dismissCarsOverlays]);

  return (
    <IonPage id="cars-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonMenuButton menu="main-menu" autoHide={false} />
          </IonButtons>

          <IonTitle>{TEXT.cars}</IonTitle>

          <IonButtons slot="end">
            <IonButton id="cars-action-trigger" onClick={() => setIsActionPickerOpen(true)}>
              <IonIcon slot="icon-only" icon={add} />
            </IonButton>
          </IonButtons>
        </IonToolbar>

        <IonToolbar>
          <IonSearchbar
            debounce={500}
            placeholder={TEXT.search}
            value={searchValue}
            onIonChange={(e) => setSearchValue(e.detail.value || "")}
          />
          {isLoading && <IonProgressBar type="indeterminate" />}
        </IonToolbar>
      </IonHeader>

      <IonContent scrollY forceOverscroll={true}>
        <div className="section-shell">
          <div className="cards-grid">
            {filteredList.map((car: CarModel, index) => (
              <CarListItem
                key={car.id ?? `car-${index}`}
                {...car}
                onDeleted={handleDeleteCar}
              />
            ))}
          </div>

          {!isLoading && filteredList.length === 0 && (
            <div className="cards-empty">
              <ItemNotFound />
            </div>
          )}
        </div>
      </IonContent>

      <IonPopover
        ref={actionPickerRef}
        isOpen={isActionPickerOpen}
        onDidDismiss={handleActionPickerDidDismiss}
        trigger="cars-action-trigger"
        triggerAction="click"
        side="bottom"
        alignment="end"
        className="action-picker-popover"
        showBackdrop
      >
        <IonContent className="action-picker-content" scrollY>
          <IonList inset>
            {quickActions.map((action) => (
              <IonItem button key={action.key} onClick={() => handleSelectQuickAction(action.key)}>
                <IonLabel>
                  <h3 style={{ margin: 0 }}>{action.title}</h3>
                  <p style={{ margin: 0, color: "var(--ion-color-medium)" }}>{action.description}</p>
                </IonLabel>
              </IonItem>
            ))}
          </IonList>
        </IonContent>
      </IonPopover>

      <IonModal
        ref={addCarModalRef}
        isOpen={isAddCarModalOpen}
        onDidDismiss={() => handleCloseAddCarModal()}
      >
        <CarAdd closeModal={handleCloseAddCarModal} />
      </IonModal>

      <IonModal
        key={`cars-action-selector-${actionSelectorModalKey}`}
        ref={actionSelectorModalRef}
        isOpen={isActionSelectorModalOpen}
        onDidDismiss={handleActionSelectorModalDidDismiss}
        keepContentsMounted={false}
      >
        {renderActionSelectorModalContent()}
      </IonModal>

      <IonModal
        key={`cars-action-${actionModalKey}`}
        ref={actionModalRef}
        isOpen={isActionModalOpen}
        onDidDismiss={handleActionModalDidDismiss}
        keepContentsMounted={false}
      >
        {renderActionModalContent()}
      </IonModal>
    </IonPage>
  );
};

export default Cars;
