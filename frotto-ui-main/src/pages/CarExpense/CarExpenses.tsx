import {
  IonBackButton,
  IonButton,
  IonButtons,
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
} from "@ionic/react";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { TEXT } from "../../constants/texts";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import { CarExpenseModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import { formatDateView } from "../../services/dateFormat";
import CarExpenseAdd from "./CarExpenseAddModal/CarExpenseAdd";
import { currencyFormat } from "../../services/currencyFormat";
import { add, walletOutline } from "ionicons/icons";
import "./CarExpenses.css";

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
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.carExpenses}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              className="app-primary-btn car-expenses-add-btn"
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
            <div className="car-expenses-section-head">
              <h2 className="app-section-title">{TEXT.carExpenses}</h2>
              <p className="app-section-subtitle">
                Histórico de despesas cadastradas para este veículo.
              </p>
            </div>

            <div className="car-expenses-list">
              {filteredList.map((carExpense: CarExpenseModel, index) => {
                return (
                  <IonItem
                    key={carExpense.id ?? `car-expense-${index}`}
                    button
                    detail={false}
                    lines="none"
                    className="car-expense-list-item"
                    onClick={() => {
                      setModalCarExpense(carExpense);
                      setIsModalOpen(true);
                      nav.push(nav.location.pathname + "?modalOpened=true");
                    }}
                  >
                    <div className="car-expense-list-item__wrap">
                      <div className="app-soft-icon app-soft-icon--danger">
                        <IonIcon icon={walletOutline} />
                      </div>

                      <div className="car-expense-list-item__content">
                        <div className="car-expense-list-item__main ion-text-wrap">
                          <h3 className="car-expense-list-item__title">
                            {carExpense.name || "-"}
                          </h3>
                          <p className="car-expense-list-item__meta">
                            {formatDateView(carExpense.date)}
                          </p>
                        </div>

                        <div className="car-expense-list-item__aside ion-text-wrap">
                          <p className="car-expense-list-item__value">
                            {currencyFormat(carExpense.cost)}
                          </p>
                          <p className="car-expense-list-item__hint">
                            {TEXT.carExpense}
                          </p>
                        </div>
                      </div>
                    </div>
                  </IonItem>
                );
              })}
            </div>

            {!isLoading && filteredList.length === 0 && (
              <div className="app-empty-state">
                <strong>{TEXT.noExpenses}</strong>
                <span>Nenhuma despesa encontrada para os filtros atuais.</span>
              </div>
            )}
          </section>
        </div>
      </IonContent>
      <IonModal
        isOpen={isModalOpen}
        onDidDismiss={() => setIsModalOpen(false)}
        backdropDismiss={false}
      >
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
