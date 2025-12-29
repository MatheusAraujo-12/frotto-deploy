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
import { IncomeModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import { formatDateView } from "../../services/dateFormat";
import {
  IonLabelLeft,
  IonLabekRight,
} from "../../components/List/IonLabekRight";
import IncomeAdd from "./IncomeAddModal/IncomeAdd";
import { currencyFormat } from "../../services/currencyFormat";
import { add } from "ionicons/icons";
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
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.incomes}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              strong={true}
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
        <IonList>
          {filteredList.map((income: IncomeModel, index) => {
            return (
              <IonItem
                key={index}
                button
                onClick={() => {
                  setModalIncomeValue(income);
                  setIsModalOpen(true);
                  nav.push(nav.location.pathname + "?modalOpened=true");
                }}
              >
                <IonLabelLeft class="ion-text-wrap">
                  <h2>{formatDateView(income.date)}</h2>
                  <p>{income.name}</p>
                </IonLabelLeft>
                <IonLabekRight>
                  <p>{currencyFormat(income.cost)}</p>
                </IonLabekRight>
              </IonItem>
            );
          })}
          {!isLoading && filteredList.length === 0 && <ItemNotFound />}
        </IonList>
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
