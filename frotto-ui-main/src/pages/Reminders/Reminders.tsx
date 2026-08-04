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
import { ReminderModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import ReminderAdd from "./ReminderAddModal/reminderAdd";
import { add, notificationsOutline } from "ionicons/icons";
import "./Reminders.css";

interface ReminderDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Reminders: React.FC<ReminderDetail> = ({ match }) => {
  const location = useLocation();
  const nav = useHistory();
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalReminderValue, setModalReminderValue] = useState<ReminderModel>(
    {}
  );
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [ReminderList, setRemindersList] = useState<ReminderModel[]>([]);

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setIsModalOpen(false);
    }
  }, [location]);

  const loadReminders = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.REMINDERS({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setRemindersList(data);
      } else {
        history.push("/menu", "none", "replace");
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadRemindersFailed);
    }
  };

  useEffect(() => {
    loadReminders();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(ReminderList, searchValue);
  }, [ReminderList, searchValue]);

  const closeModal = useCallback((response?: ReminderModel) => {
    setIsModalOpen(false);
    nav.goBack();

    if (!response) return;

    setRemindersList((prev) => {
      const exists = prev.some((item) => item.id === response.id);
      if (exists) {
        return prev.map((item) => (item.id === response.id ? response : item));
      }
      return [response, ...prev];
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <IonPage id="car-Reminders-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.reminders}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              className="app-primary-btn reminders-add-btn"
              onClick={() => {
                setModalReminderValue({});
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
            <div className="reminders-section-head">
              <h2 className="app-section-title">{TEXT.reminders}</h2>
              <p className="app-section-subtitle">
                Lista de lembretes configurados para este veículo.
              </p>
            </div>

            <div className="reminders-list">
              {filteredList.map((reminder: ReminderModel, index) => {
                return (
                  <IonItem
                    key={reminder.id ?? `reminder-${index}`}
                    button
                    detail={false}
                    className="reminder-list-item"
                    onClick={() => {
                      setModalReminderValue(reminder);
                      setIsModalOpen(true);
                      nav.push(nav.location.pathname + "?modalOpened=true");
                    }}
                  >
                    <div className="reminder-list-item__wrap">
                      <div className="app-soft-icon app-soft-icon--warning">
                        <IonIcon icon={notificationsOutline} />
                      </div>

                      <div className="reminder-list-item__content">
                        <h3 className="reminder-list-item__title">
                          {reminder.message || "-"}
                        </h3>
                        <p className="reminder-list-item__meta">
                          {TEXT.reminder}
                        </p>
                      </div>
                    </div>
                  </IonItem>
                );
              })}
            </div>

            {!isLoading && filteredList.length === 0 && (
              <div className="app-empty-state">
                <strong>{TEXT.noReminders}</strong>
                <span>Nenhum lembrete encontrado para os filtros atuais.</span>
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
        <ReminderAdd
          carId={match.params.id}
          closeModal={closeModal}
          initialValues={modalReminderValue}
        />
      </IonModal>
    </IonPage>
  );
};

export default Reminders;
