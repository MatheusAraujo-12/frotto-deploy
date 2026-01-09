import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
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
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import { CarExpenseModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import { formatDateView } from "../../services/dateFormat";
import {
  IonLabelLeft,
  IonLabekRight,
} from "../../components/List/IonLabekRight";
import CarExpenseAdd from "./CarExpenseAddModal/CarExpenseAdd";
import { currencyFormat } from "../../services/currencyFormat";
import { add } from "ionicons/icons";

interface CarExpenseDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const CarExpenses: React.FC<CarExpenseDetail> = ({ match }) => {
  const location = useLocation();
  const nav = useHistory();
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalCarExpense, setModalCarExpense] = useState<CarExpenseModel>({});
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [carExpenseList, setCarExpensesList] = useState<CarExpenseModel[]>([]);

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  const loadCarExpenses = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.CAR_EXPENSES({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setCarExpensesList(data);
      } else {
        history.push("/menu", "none", "replace");
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadCarExpensesFailed);
    }
  };

  useEffect(() => {
    loadCarExpenses();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(carExpenseList, searchValue);
  }, [carExpenseList, searchValue]);

  const closeModal = useCallback((response?: CarExpenseModel) => {
    setIsModalOpen(false);
    nav.goBack();

    if (!response) return;

    setCarExpensesList((prev) => {
      if (response.delete && response.id !== undefined) {
        return prev.filter((item) => item.id !== response.id);
      }
      const exists = prev.some((item) => item.id === response.id);
      if (exists) {
        return prev.map((item) => (item.id === response.id ? response : item));
      }
      return [response, ...prev];
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <IonPage id="car-carExpenses-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.carExpenses}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              strong={true}
              onClick={() => {
                setModalCarExpense({});
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
            {filteredList.map((carExpense: CarExpenseModel, index) => {
              return (
                <IonItem
                  key={index}
                  button
                  onClick={() => {
                    setModalCarExpense(carExpense);
                    setIsModalOpen(true);
                    nav.push(nav.location.pathname + "?modalOpened=true");
                  }}
                >
                  <IonLabelLeft class="ion-text-wrap">
                    <h2>{formatDateView(carExpense.date)}</h2>
                    <p>{carExpense.name}</p>
                  </IonLabelLeft>
                  <IonLabekRight>
                    <p>{currencyFormat(carExpense.cost)}</p>
                  </IonLabekRight>
                </IonItem>
              );
            })}
            {!isLoading && filteredList.length === 0 && <ItemNotFound />}
          </IonList>
        </div>
      </IonContent>
      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
        <CarExpenseAdd
          carId={match.params.id}
          closeModal={closeModal}
          initialValues={modalCarExpense}
        />
      </IonModal>
    </IonPage>
  );
};

export default CarExpenses;
