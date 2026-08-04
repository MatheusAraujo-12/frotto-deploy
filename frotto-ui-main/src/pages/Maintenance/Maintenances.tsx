import {
  IonBackButton,
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
  useIonViewWillLeave,
} from "@ionic/react";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { TEXT } from "../../constants/texts";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import { MaintenanceModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import { RouteComponentProps } from "react-router";
import { formatDateView } from "../../services/dateFormat";
import MaintenanceAdd from "./MaintenanceAddModal/MaintenanceAdd";
import { currencyFormat } from "../../services/currencyFormat";
import { servicesToString } from "../../services/toString";
import { buildOutline } from "ionicons/icons";
import "./Maintenances.css";

interface MaintenanceDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Maintenances: React.FC<MaintenanceDetail> = ({ match }) => {
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

  useIonViewWillLeave(() => {
    setIsModalOpen(false);
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(maintenanceList, searchValue);
  }, [maintenanceList, searchValue]);

  const closeModal = useCallback((response?: MaintenanceModel) => {
    setIsModalOpen(false);

    if (!response) return;

    setMaintenanceList((prev) => {
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
    <IonPage id="car-maintenances-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.maintenances}</IonTitle>
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
            <div className="maintenances-section-head">
              <h2 className="app-section-title">{TEXT.maintenances}</h2>
              <p className="app-section-subtitle">
                Histórico de manutenções registradas para este veículo.
              </p>
            </div>

            <div className="maintenances-list">
              {filteredList.map((maintenance: MaintenanceModel, index) => {
                return (
                  <IonItem
                    key={maintenance.id ?? `maintenance-${index}`}
                    button
                    detail={false}
                    className="maintenance-list-item"
                    onClick={() => {
                      setModalMaintenance(maintenance);
                      setIsModalOpen(true);
                    }}
                  >
                    <div className="maintenance-list-item__wrap">
                      <div className="app-soft-icon">
                        <IonIcon icon={buildOutline} />
                      </div>

                      <div className="maintenance-list-item__content">
                        <div className="maintenance-list-item__main ion-text-wrap">
                          <div className="maintenance-list-item__details">
                            <p className="maintenance-list-item__detail">
                              <span className="maintenance-list-item__detail-label">
                                {TEXT.date}:
                              </span>{" "}
                              <span className="maintenance-list-item__detail-value">
                                {formatDateView(maintenance.date) || "-"}
                              </span>
                            </p>
                            <p className="maintenance-list-item__detail">
                              <span className="maintenance-list-item__detail-label">
                                {TEXT.odometer}:
                              </span>{" "}
                              <span className="maintenance-list-item__detail-value">
                                {`${maintenance.odometer || 0} ${TEXT.km}`}
                              </span>
                            </p>
                            <p className="maintenance-list-item__detail">
                              <span className="maintenance-list-item__detail-label">
                                {TEXT.local}:
                              </span>{" "}
                              <span className="maintenance-list-item__detail-value">
                                {maintenance.local || "-"}
                              </span>
                            </p>
                          </div>
                          <p className="maintenance-list-item__services">
                            {servicesToString(maintenance.services)}
                          </p>
                        </div>

                        <div className="maintenance-list-item__aside ion-text-wrap">
                          <p className="maintenance-list-item__value">
                            {currencyFormat(maintenance.cost)}
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
                <strong>{TEXT.noMaintenance}</strong>
                <span>Nenhuma manutenção encontrada para os filtros atuais.</span>
              </div>
            )}
          </section>
        </div>
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
