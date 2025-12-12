import {
  IonBackButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonList,
  IonModal,
  IonNote,
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
import { MaintenanceModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import { formatDateView } from "../../services/dateFormat";
import MaintenanceAdd from "./MaintenanceAddModal/MaintenanceAdd";
import { currencyFormat } from "../../services/currencyFormat";
import { servicesToString } from "../../services/toString";

interface MaintenanceDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Maintenances: React.FC<MaintenanceDetail> = ({ match }) => {
  const location = useLocation();
  const nav = useHistory();
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalMaintenance, setModalMaintenance] = useState<MaintenanceModel>(
    {}
  );
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [maintenanceList, setMaintenanceList] = useState<MaintenanceModel[]>(
    []
  );

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  const loadMaintenances = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.MAINTENANCES({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setMaintenanceList(data);
      } else {
        history.push("/menu", "none", "replace");
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadInspectionsFailed);
    }
  };

  useEffect(() => {
    loadMaintenances();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(maintenanceList, searchValue);
  }, [maintenanceList, searchValue]);

  const closeModal = useCallback((newMaintenance: MaintenanceModel) => {
    if (newMaintenance) {
      loadMaintenances();
    }
    setIsModalOpen(false);
    nav.goBack();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <IonPage id="car-maintenances-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.maintenances}</IonTitle>
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
          {filteredList.map((maintenance: MaintenanceModel, index) => {
            return (
              <IonItem
                key={index}
                button
                onClick={() => {
                  setModalMaintenance(maintenance);
                  setIsModalOpen(true);
                  nav.push(nav.location.pathname + "?modalOpened=true");
                }}
              >
                <IonLabel class="ion-text-wrap">
                  <h2>{formatDateView(maintenance.date)}</h2>
                  <p>
                    {`${maintenance.odometer} ${TEXT.km}`}
                    &nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;
                    {currencyFormat(maintenance.cost)}
                  </p>
                  <p>{maintenance.local}</p>
                  <IonNote>{servicesToString(maintenance.services)}</IonNote>
                </IonLabel>
              </IonItem>
            );
          })}
          {!isLoading && filteredList.length === 0 && <ItemNotFound />}
        </IonList>
      </IonContent>
      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
        <MaintenanceAdd
          carId={match.params.id}
          closeModal={closeModal}
          initialValues={modalMaintenance}
        />
      </IonModal>
    </IonPage>
  );
};

export default Maintenances;
