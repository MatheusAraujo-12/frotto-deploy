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
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { TEXT } from "../../constants/texts";
import { add } from "ionicons/icons";
import { useCallback, useEffect, useMemo, useState } from "react";
import CarAdd from "./CarAddModal/CarAdd";
import MaintenanceAdd from "../Maintenance/MaintenanceAddModal/MaintenanceAdd";
import ReminderAdd from "../Reminders/ReminderAddModal/reminderAdd";
import IncomeAdd from "../Income/IncomeAddModal/IncomeAdd";
import CarExpenseAdd from "../CarExpense/CarExpenseAddModal/CarExpenseAdd";
import CarSelector from "../../components/Car/CarSelector";
import { useAlert } from "../../services/hooks/useAlert";
import { CarModel } from "../../constants/CarModels";
import CarListItem from "./CarListItem";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import { useLocation, useHistory } from "react-router";

const Cars: React.FC = () => {
  const location = useLocation();
  const nav = useHistory();
  const { showErrorAlert } = useAlert();

  const [isLoading, setIsLoading] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [actionSheetOpen, setActionSheetOpen] = useState(false);
  const [actionModalOpen, setActionModalOpen] = useState(false);
  const [actionType, setActionType] = useState<string | null>(null);
  const [selectedCarForAction, setSelectedCarForAction] =
    useState<CarModel | null>(null);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [carList, setCarList] = useState<CarModel[]>([]);

  // Fecha modal quando sair da URL ?modalOpened=true
  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  // Carregar carros do backend
  const loadCars = async () => {
    setIsLoading(true);
    try {
      const response = await api.get(endpoints.CARS_ACTIVE());
      const data = response?.data ?? [];

      const list = Array.isArray(data)
        ? data
        : data.items || data.content || data.data || [];

      setCarList(list);
      setIsLoading(false);
    } catch (error: any) {
      setIsLoading(false);
      showErrorAlert(
        TEXT.loadCarsFailed +
          (error?.message ? `: ${error.message}` : "")
      );
    }
  };

  // Carregar ao entrar na página
  useIonViewWillEnter(() => {
    if (!location.search.includes("modalOpened=true")) {
      loadCars();
    }
  }, [location]);

  // Filtro de busca
  const filteredList = useMemo(() => {
    return filterListObj(carList, searchValue);
  }, [carList, searchValue]);

  // Adicionar novo carro ao estado
  const addCarToList = useCallback(
    (newCar: CarModel) => {
      setCarList((prev) => [newCar, ...prev]);
    },
    []
  );

  // Remover carro da lista após deletar
  const removeCarFromList = useCallback((id: number) => {
    setCarList((prev) => prev.filter((car) => car.id !== id));
  }, []);

  // Fecha modal do CarAdd
  const closeModal = useCallback(
    (newCar: CarModel) => {
      if (newCar) {
        addCarToList(newCar);
      }
      setIsModalOpen(false);
      nav.goBack();
    },
    [addCarToList, nav]
  );

  return (
    <IonPage id="cars-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="secondary">
            <IonButton>
              <IonMenuButton />
            </IonButton>
          </IonButtons>

          <IonButtons slot="primary">
            <IonButton
              onClick={(e) => {
                e.preventDefault();
                setActionSheetOpen(true);
              }}
            >
              <IonIcon slot="icon-only" icon={add} />
            </IonButton>
          </IonButtons>

          <IonActionSheet
            isOpen={actionSheetOpen}
            onDidDismiss={() => setActionSheetOpen(false)}
            buttons={[
              {
                text: TEXT.addCar,
                handler: () => {
                  setActionSheetOpen(false);
                  nav.push(nav.location.pathname + "?modalOpened=true");
                  setIsModalOpen(true);
                },
              },
              {
                text: TEXT.addCarMaintenance,
                handler: () => {
                  setActionSheetOpen(false);
                  setActionType("maintenance");
                  setActionModalOpen(true);
                },
              },
              {
                text: TEXT.reminder,
                handler: () => {
                  setActionSheetOpen(false);
                  setActionType("reminder");
                  setActionModalOpen(true);
                },
              },
              {
                text: `${TEXT.add} ${TEXT.carExpense}`,
                handler: () => {
                  setActionSheetOpen(false);
                  setActionType("expense");
                  setActionModalOpen(true);
                },
              },
              {
                text: `${TEXT.add} ${TEXT.income}`,
                handler: () => {
                  setActionSheetOpen(false);
                  setActionType("income");
                  setActionModalOpen(true);
                },
              },
              {
                text: "Cancelar",
                role: "cancel",
              },
            ]}
          />

          <IonModal
            isOpen={actionModalOpen}
            onDidDismiss={() => {
              setActionModalOpen(false);
              setSelectedCarForAction(null);
              setActionType(null);
            }}
          >
            <IonHeader>
              <IonToolbar>
                <IonButtons slot="start">
                  <IonButton
                    onClick={() => {
                      setSelectedCarForAction(null);
                      setActionModalOpen(false);
                      setActionType(null);
                    }}
                  >
                    {TEXT.cancel}
                  </IonButton>
                </IonButtons>

                <IonTitle>
                  {actionType === "maintenance"
                    ? TEXT.addCarMaintenance
                    : actionType === "reminder"
                    ? TEXT.reminder
                    : actionType === "expense"
                    ? TEXT.carExpense
                    : actionType === "income"
                    ? TEXT.income
                    : TEXT.add}
                </IonTitle>

                <IonButtons slot="end">
                  <IonButton onClick={() => setSelectedCarForAction(null)}>
                    {TEXT.all}
                  </IonButton>
                </IonButtons>
              </IonToolbar>
            </IonHeader>

            <IonContent>
              {!selectedCarForAction && (
                <div style={{ padding: 12 }}>
                  <h3 style={{ marginTop: 0 }}>{TEXT.select}</h3>
                  <CarSelector
                    onSelect={(car) => setSelectedCarForAction(car)}
                  />
                </div>
              )}

              {selectedCarForAction && actionType === "maintenance" && (
                <MaintenanceAdd
                  closeModal={() => {
                    setActionModalOpen(false);
                    setSelectedCarForAction(null);
                    setActionType(null);
                  }}
                  carId={String(selectedCarForAction.id)}
                />
              )}

              {selectedCarForAction && actionType === "reminder" && (
                <ReminderAdd
                  closeModal={() => {
                    setActionModalOpen(false);
                    setSelectedCarForAction(null);
                    setActionType(null);
                  }}
                  carId={String(selectedCarForAction.id)}
                />
              )}

              {selectedCarForAction && actionType === "income" && (
                <IncomeAdd
                  closeModal={() => {
                    setActionModalOpen(false);
                    setSelectedCarForAction(null);
                    setActionType(null);
                  }}
                  carId={String(selectedCarForAction.id)}
                />
              )}

              {selectedCarForAction && actionType === "expense" && (
                <CarExpenseAdd
                  closeModal={() => {
                    setActionModalOpen(false);
                    setSelectedCarForAction(null);
                    setActionType(null);
                  }}
                  carId={String(selectedCarForAction.id)}
                />
              )}
            </IonContent>
          </IonModal>

          <IonTitle>{TEXT.cars}</IonTitle>
        </IonToolbar>

        <IonToolbar>
          <IonSearchbar
            debounce={500}
            placeholder={TEXT.search}
            onIonChange={(e) => setSearchValue(e.detail.value)}
          />

          {isLoading && <IonProgressBar type="indeterminate" />}
        </IonToolbar>
      </IonHeader>

      <IonContent>
        <div className="cards-grid">
          {filteredList.map((car: CarModel, index) => (
            <CarListItem
              key={car.id ?? index}
              {...car}
              onDeleted={removeCarFromList}
            />
          ))}
        </div>

        {!isLoading && filteredList.length === 0 && (
          <div className="cards-empty">
            <ItemNotFound />
          </div>
        )}
      </IonContent>

      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
        <CarAdd closeModal={closeModal} />
      </IonModal>
    </IonPage>
  );
};

export default Cars;
