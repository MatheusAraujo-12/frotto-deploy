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
  useIonViewWillEnter,
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
type CarListItem = CarModel & {
  car?: CarModel;
  carId?: number;
};

const Cars: React.FC = () => {
  const history = useHistory();
  const location = useLocation();
  const { showErrorAlert } = useAlert();

  const [isLoading, setIsLoading] = useState(false);
  const [isAddCarModalOpen, setIsAddCarModalOpen] = useState(false);
  const [isActionPickerOpen, setIsActionPickerOpen] = useState(false);
  const [isActionModalOpen, setIsActionModalOpen] = useState(false);
  const [selectedAction, setSelectedAction] = useState<ActionType>(null);
  const [selectedCar, setSelectedCar] = useState<CarModel | null>(null);
  const [searchValue, setSearchValue] = useState("");
  const [carList, setCarList] = useState<CarModel[]>([]);

  const abortControllerRef = useRef<AbortController | null>(null);

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
        let list: CarListItem[] = [];

        if (Array.isArray(data)) {
          list = data;
        } else if (data && typeof data === "object") {
          const anyData = data as {
            items?: CarListItem[];
            content?: CarListItem[];
            data?: CarListItem[];
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
    [history, location]
  );

  const handleOpenActionModal = useCallback((action: ActionType) => {
    setSelectedAction(action);
    setIsActionModalOpen(true);
  }, []);

  const handleCloseActionModal = useCallback((response?: QuickActionResponse) => {
    setIsActionModalOpen(false);
    setSelectedAction(null);
    setSelectedCar(null);

    if (!response) return;
  }, []);

  const handleSelectQuickAction = useCallback(
    (action: ActionType | "car") => {
      setIsActionPickerOpen(false);

      if (action === "car") {
        const searchParams = new URLSearchParams(location.search);
        searchParams.set("modalOpened", "true");
        history.push({
          pathname: location.pathname,
          search: searchParams.toString(),
        });
        setIsAddCarModalOpen(true);
        return;
      }

      handleOpenActionModal(action);
    },
    [history, location, handleOpenActionModal]
  );

  const renderActionModalContent = () => {
    if (!selectedAction) return null;

    if (!selectedCar) {
      return (
        <>
          <IonHeader>
            <IonToolbar>
              <IonButtons slot="start">
                <IonButton onClick={() => handleCloseActionModal()}>
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
                onSelect={(car) => setSelectedCar(car)}
              />
            </div>
          </IonContent>
        </>
      );
    }

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

  return (
    <IonPage id="cars-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonMenuButton menu="main-menu" />
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
      </IonContent>

      <IonPopover
        isOpen={isActionPickerOpen}
        onDidDismiss={() => setIsActionPickerOpen(false)}
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

      <IonModal isOpen={isAddCarModalOpen} onDidDismiss={() => handleCloseAddCarModal()}>
        <CarAdd closeModal={handleCloseAddCarModal} />
      </IonModal>

      <IonModal
        isOpen={isActionModalOpen}
        onDidDismiss={() => handleCloseActionModal()}
      >
        {renderActionModalContent()}
      </IonModal>
    </IonPage>
  );
};

export default Cars;
