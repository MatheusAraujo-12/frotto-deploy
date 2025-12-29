import {
  IonBackButton,
  IonButtons,
  IonContent,
  IonHeader,
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
import { InspectionModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import InspectionAdd from "./InspectionAddModal/InspectionAdd";
import { formatDateView } from "../../services/dateFormat";
import {
  IonLabelLeft,
  IonLabekRight,
} from "../../components/List/IonLabekRight";
import { currencyFormat } from "../../services/currencyFormat";

interface InspectionDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Inspections: React.FC<InspectionDetail> = ({ match }) => {
  const location = useLocation();
  const nav = useHistory();
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalInspection, setModalInspection] = useState<InspectionModel>({});
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [inspectionList, setInspectionsList] = useState<InspectionModel[]>([]);

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  const loadInspections = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.INSPECTIONS({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setInspectionsList(data);
      } else {
        history.push("/menu", "none", "replace");
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadInspectionsFailed);
    }
  };

  useEffect(() => {
    loadInspections();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(inspectionList, searchValue);
  }, [inspectionList, searchValue]);

  const closeModal = useCallback((response?: InspectionModel) => {
    setIsModalOpen(false);
    nav.goBack();

    if (!response) return;

    setInspectionsList((prev) => {
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
    <IonPage id="car-inspections-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.inspections}</IonTitle>
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
          {filteredList.map((inspection: InspectionModel, index) => {
            return (
              <IonItem
                key={index}
                button
                onClick={() => {
                  setModalInspection(inspection);
                  setIsModalOpen(true);
                  nav.push(nav.location.pathname + "?modalOpened=true");
                }}
              >
                <IonLabelLeft class="ion-text-wrap">
                  <h2>{formatDateView(inspection.date)}</h2>
                  <p>{`${inspection.odometer} ${TEXT.km}`}</p>
                </IonLabelLeft>
                <IonLabekRight>
                  <p>{currencyFormat(inspection.cost)}</p>
                </IonLabekRight>
              </IonItem>
            );
          })}
          {!isLoading && filteredList.length === 0 && <ItemNotFound />}
        </IonList>
      </IonContent>
      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
        <InspectionAdd
          carId={match.params.id}
          closeModal={closeModal}
          initialValues={modalInspection}
        />
      </IonModal>
    </IonPage>
  );
};

export default Inspections;
