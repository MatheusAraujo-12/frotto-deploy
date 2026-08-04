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
import { IncomeModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import { formatDateView } from "../../services/dateFormat";
import IncomeAdd from "./IncomeAddModal/IncomeAdd";
import { currencyFormat } from "../../services/currencyFormat";
import { add, cashOutline } from "ionicons/icons";
import "./Incomes.css";

interface IncomeDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Incomes: React.FC<IncomeDetail> = ({ match }) => {
  const location = useLocation();
  const nav = useHistory();
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalIncomeValue, setModalIncomeValue] = useState<IncomeModel>({});
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [incomeList, setIncomesList] = useState<IncomeModel[]>([]);

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  const loadIncomes = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.INCOMES({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setIncomesList(data);
      } else {
        history.push("/menu", "none", "replace");
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadIncomesFailed);
    }
  };

  useEffect(() => {
    loadIncomes();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(incomeList, searchValue);
  }, [incomeList, searchValue]);

  const closeModal = useCallback((response?: IncomeModel) => {
    setIsModalOpen(false);
    nav.goBack();

    if (!response) return;

    setIncomesList((prev) => {
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
    <IonPage id="car-incomes-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.incomes}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              className="app-primary-btn incomes-add-btn"
              onClick={() => {
                setModalIncomeValue({});
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
            <div className="incomes-section-head">
              <h2 className="app-section-title">{TEXT.incomes}</h2>
              <p className="app-section-subtitle">
                Histórico de receitas cadastradas para este veículo.
              </p>
            </div>

            <div className="incomes-list">
              {filteredList.map((income: IncomeModel, index) => {
                return (
                  <IonItem
                    key={income.id ?? `income-${index}`}
                    button
                    detail={false}
                    lines="none"
                    className="income-list-item"
                    onClick={() => {
                      setModalIncomeValue(income);
                      setIsModalOpen(true);
                      nav.push(nav.location.pathname + "?modalOpened=true");
                    }}
                  >
                    <div className="income-list-item__wrap">
                      <div className="app-soft-icon app-soft-icon--success">
                        <IonIcon icon={cashOutline} />
                      </div>

                      <div className="income-list-item__content">
                        <div className="income-list-item__main ion-text-wrap">
                          <h3 className="income-list-item__title">
                            {income.name || "-"}
                          </h3>
                          <p className="income-list-item__meta">
                            {formatDateView(income.date)}
                          </p>
                        </div>

                        <div className="income-list-item__aside ion-text-wrap">
                          <p className="income-list-item__value">
                            {currencyFormat(income.cost)}
                          </p>
                          <p className="income-list-item__hint">
                            {TEXT.income}
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
                <strong>{TEXT.itensNotFound}</strong>
                <span>Nenhuma receita encontrada para os filtros atuais.</span>
              </div>
            )}
          </section>
        </div>
      </IonContent>
      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
        <IncomeAdd
          carId={match.params.id}
          closeModal={closeModal}
          initialValues={modalIncomeValue}
        />
      </IonModal>
    </IonPage>
  );
};

export default Incomes;
