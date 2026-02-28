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
import { dismissAllOverlays } from "../../services/overlayManager";

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

  const abortControllerRef = useRef<AbortController | null>(null);
  const actionOpenTimerRef = useRef<number | null>(null);
  const pendingQuickActionRef = useRef<QuickAction["key"] | null>(null);
  const isCarsActiveRef = useRef(location.pathname === "/menu/carros");
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
    setActionPickerEvent(undefined);
    setIsActionSelectorModalOpen(false);
    setIsActionModalOpen(false);
    pendingQuickActionRef.current = null;
    setSelectedAction(null);
    setSelectedCar(null);
  }, []);

  const dismissCarsOverlays = useCallback(
    (reason: string) => {
      clearScheduledActionOpen();
      logBackdropCount(`${reason}:before`);
      void dismissAllOverlays(reason);
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

  const handleDeleteCar = useCallback((deletedCarId: number) => {
    setCarList((prev) => prev.filter((car) => car.id !== deletedCarId));
  }, []);

  const handleOpenActionPicker = useCallback((event?: Event) => {
    if (!isCarsActiveRef.current) {
      return;
    }

    clearScheduledActionOpen();
    pendingQuickActionRef.current = null;
    dismissOverlayElement(actionPickerRef.current);
    dismissOverlayElement(actionSelectorModalRef.current);
    dismissOverlayElement(actionModalRef.current);
    dismissOverlayElement(addCarModalRef.current);
    setIsAddCarModalOpen(false);
    setIsActionSelectorModalOpen(false);
    setIsActionModalOpen(false);
    setSelectedAction(null);
    setSelectedCar(null);

    setActionPickerEvent(event);
    setActionPickerKey((prev) => prev + 1);
    setIsActionPickerOpen(true);
    logBackdropCount("popover-open");
  }, [clearScheduledActionOpen, dismissOverlayElement, logBackdropCount]);

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

      setCarList((prev) => {
        const exists = prev.some((item) => item.id === response.id);
        if (exists) {
          return prev.map((item) => (item.id === response.id ? response : item));
        }
        return [response, ...prev];
      });
    },
    [logBackdropCount]
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
            <IonButton
              id="cars-action-trigger"
              onClick={(event) => handleOpenActionPicker(event.nativeEvent)}
            >
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
        key={`cars-action-picker-${actionPickerKey}`}
        ref={actionPickerRef}
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
        key={`cars-add-${addCarModalKey}`}
        ref={addCarModalRef}
        isOpen={isAddCarModalOpen}
        onDidDismiss={handleAddCarModalDidDismiss}
        backdropDismiss={false}
        keepContentsMounted={false}
      >
        <CarAdd closeModal={handleCloseAddCarModal} />
      </IonModal>

      <IonModal
        key={`cars-action-selector-${actionSelectorModalKey}`}
        ref={actionSelectorModalRef}
        isOpen={isActionSelectorModalOpen}
        onDidDismiss={handleActionSelectorModalDidDismiss}
        backdropDismiss={false}
        keepContentsMounted={false}
      >
        {renderActionSelectorModalContent()}
      </IonModal>

      <IonModal
        key={`cars-action-${actionModalKey}`}
        ref={actionModalRef}
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
