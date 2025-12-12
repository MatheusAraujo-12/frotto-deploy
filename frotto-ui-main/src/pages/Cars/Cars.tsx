import {
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
      const response = await api.get<CarModel[]>(endpoints.CARS_ACTIVE());
      setCarList(response.data);
      setIsLoading(false);
    } catch (error) {
      setIsLoading(false);
      showErrorAlert(TEXT.loadCarsFailed);
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
      const newList = [newCar, ...carList];
      setCarList(newList);
    },
    [carList]
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
              <IonMenuButton></IonMenuButton>
            </IonButton>
          </IonButtons>

          <IonButtons slot="primary">
            <IonButton
              onClick={(e) => {
                e.preventDefault();
                nav.push(nav.location.pathname + "?modalOpened=true");
                setIsModalOpen(true);
              }}
            >
              <IonIcon slot="icon-only" icon={add}></IonIcon>
            </IonButton>
          </IonButtons>

          <IonTitle>{TEXT.cars}</IonTitle>
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
