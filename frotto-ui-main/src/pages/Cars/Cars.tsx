import {
  IonActionSheet,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonMenuButton,
  IonModal,
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
import { CarModel } from "../../constants/CarModels";

type ActionType = "maintenance" | "reminder" | "expense" | "income" | null;

const Cars: React.FC = () => {
  const history = useHistory();
  const location = useLocation();
  const { showErrorAlert } = useAlert();

  const [isLoading, setIsLoading] = useState(false);
  const [isAddCarModalOpen, setIsAddCarModalOpen] = useState(false);
  const [isActionSheetOpen, setIsActionSheetOpen] = useState(false);
  const [isActionModalOpen, setIsActionModalOpen] = useState(false);
  const [selectedAction, setSelectedAction] = useState<ActionType>(null);
  const [selectedCar, setSelectedCar] = useState<CarModel | null>(null);
  const [searchValue, setSearchValue] = useState("");
  const [carList, setCarList] = useState<CarModel[]>([]);

  const abortControllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    const modalOpened = searchParams.get("modalOpened") === "true";
    setIsAddCarModalOpen(modalOpened);
  }, [location.search]);

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
        let list: CarModel[] = [];

        if (Array.isArray(data)) {
          list = data as CarModel[];
        } else if (data && typeof data === "object") {
          const anyData = data as any;
          list = (anyData.items ||
            anyData.content ||
            anyData.data ||
            []) as CarModel[];
        }

        setCarList(list);
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

  const handleAddCar = useCallback((newCar: CarModel) => {
    setCarList((prev) => [newCar, ...prev]);
  }, []);

  const handleDeleteCar = useCallback((deletedCarId: number) => {
    setCarList((prev) => prev.filter((car) => car.id !== deletedCarId));
  }, []);

  const handleCloseAddCarModal = useCallback(
    (newCar?: CarModel) => {
      setIsAddCarModalOpen(false);

      const searchParams = new URLSearchParams(location.search);
      searchParams.delete("modalOpened");
      history.replace({
        pathname: location.pathname,
        search: searchParams.toString(),
      });

      if (newCar) handleAddCar(newCar);
    },
    [history, location, handleAddCar]
  );

  const handleOpenActionModal = useCallback((action: ActionType) => {
    setSelectedAction(action);
    setIsActionSheetOpen(false);
    setIsActionModalOpen(true);
  }, []);

  const handleCloseActionModal = useCallback(() => {
    setIsActionModalOpen(false);
    setSelectedAction(null);
    setSelectedCar(null);
  }, []);

  const renderActionModalContent = () => {
    if (!selectedAction) return null;

    if (!selectedCar) {
      return (
        <div style={{ padding: 16 }}>
          <h3 style={{ marginTop: 0, marginBottom: 16 }}>
            {`${TEXT.select} ${TEXT.car}`}
          </h3>
          <CarSelector cars={carList} onSelect={(car) => setSelectedCar(car)} />
        </div>
      );
    }

    const modalProps = {
      closeModal: handleCloseActionModal,
      carId: String(selectedCar.id),
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

  const actionSheetButtons = useMemo(
    () => [
      {
        text: TEXT.addCar,
        handler: () => {
          const searchParams = new URLSearchParams(location.search);
          searchParams.set("modalOpened", "true");
          history.push({
            pathname: location.pathname,
            search: searchParams.toString(),
          });
          setIsAddCarModalOpen(true);
        },
      },
      { text: TEXT.addCarMaintenance, handler: () => handleOpenActionModal("maintenance") },
      { text: TEXT.reminder, handler: () => handleOpenActionModal("reminder") },
      { text: `${TEXT.add} ${TEXT.carExpense}`, handler: () => handleOpenActionModal("expense") },
      { text: `${TEXT.add} ${TEXT.income}`, handler: () => handleOpenActionModal("income") },
      { text: TEXT.cancel, role: "cancel" as const },
    ],
    [location, history, handleOpenActionModal]
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
            <IonButton onClick={() => setIsActionSheetOpen(true)}>
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

      <IonContent>
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

      <IonActionSheet
        isOpen={isActionSheetOpen}
        onDidDismiss={() => setIsActionSheetOpen(false)}
        header={TEXT.add}
        buttons={actionSheetButtons}
      />

      <IonModal isOpen={isAddCarModalOpen} onDidDismiss={() => handleCloseAddCarModal()}>
        <CarAdd closeModal={handleCloseAddCarModal} />
      </IonModal>

      <IonModal isOpen={isActionModalOpen} onDidDismiss={handleCloseActionModal}>
        <IonHeader>
          <IonToolbar>
            <IonButtons slot="start">
              <IonButton onClick={handleCloseActionModal}>{TEXT.cancel}</IonButton>
            </IonButtons>

            <IonTitle>
              {selectedAction === "maintenance" && TEXT.addCarMaintenance}
              {selectedAction === "reminder" && TEXT.reminder}
              {selectedAction === "expense" && TEXT.carExpense}
              {selectedAction === "income" && TEXT.income}
            </IonTitle>

            {selectedCar && (
              <IonButtons slot="end">
                <IonButton onClick={() => setSelectedCar(null)} color="medium">
                  Trocar
                </IonButton>
              </IonButtons>
            )}
          </IonToolbar>
        </IonHeader>

        <IonContent>{renderActionModalContent()}</IonContent>
      </IonModal>
    </IonPage>
  );
};

export default Cars;
