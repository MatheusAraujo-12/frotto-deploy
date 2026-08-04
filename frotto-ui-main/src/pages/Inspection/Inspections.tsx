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
import { InspectionModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import { RouteComponentProps } from "react-router";
import InspectionAdd from "./InspectionAddModal/InspectionAdd";
import { formatDateView } from "../../services/dateFormat";
import { currencyFormat } from "../../services/currencyFormat";
import { clipboardOutline } from "ionicons/icons";
import "./Inspections.css";

interface InspectionDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Inspections: React.FC<InspectionDetail> = ({ match }) => {
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalInspection, setModalInspection] = useState<InspectionModel>({});
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [inspectionList, setInspectionsList] = useState<InspectionModel[]>([]);

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

  useIonViewWillLeave(() => {
    setIsModalOpen(false);
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(inspectionList, searchValue);
  }, [inspectionList, searchValue]);

  const closeModal = useCallback((response?: InspectionModel) => {
    setIsModalOpen(false);

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
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.inspections}</IonTitle>
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
            <div className="inspections-section-head">
              <h2 className="app-section-title">{TEXT.inspections}</h2>
              <p className="app-section-subtitle">
                Histórico de inspeções registradas para este veículo.
              </p>
            </div>

            <div className="inspections-list">
              {filteredList.map((inspection: InspectionModel, index) => {
                return (
                  <IonItem
                    key={inspection.id ?? `inspection-${index}`}
                    button
                    detail={false}
                    lines="none"
                    className="inspection-list-item"
                    onClick={() => {
                      setModalInspection(inspection);
                      setIsModalOpen(true);
                    }}
                  >
                    <div className="inspection-list-item__wrap">
                      <div className="app-soft-icon">
                        <IonIcon icon={clipboardOutline} />
                      </div>

                      <div className="inspection-list-item__content">
                        <div className="inspection-list-item__main ion-text-wrap">
                          <h3 className="inspection-list-item__title">
                            {formatDateView(inspection.date)}
                          </h3>
                          <p className="inspection-list-item__meta">
                            {inspection.driverName || TEXT.inspection}
                          </p>
                          <p className="inspection-list-item__hint">
                            {`${inspection.odometer || 0} ${TEXT.km}`}
                          </p>
                        </div>

                        <div className="inspection-list-item__aside ion-text-wrap">
                          <p className="inspection-list-item__value">
                            {currencyFormat(inspection.cost)}
                          </p>
                          <p className="inspection-list-item__meta">
                            {TEXT.totalCost}
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
                <strong>{TEXT.noInspection}</strong>
                <span>Nenhuma inspeção encontrada para os filtros atuais.</span>
              </div>
            )}
          </section>
        </div>
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
