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
import { ReminderModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import ItemNotFound from "../../components/List/ItemNotFound";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import ReminderAdd from "./ReminderAddModal/reminderAdd";
import { add } from "ionicons/icons";

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

  const closeModal = useCallback((newReminder?: ReminderModel) => {
    if (newReminder) {
      loadReminders();
    }
    setIsModalOpen(false);
    nav.goBack();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <IonPage id="car-Reminders-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.reminders}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              strong={true}
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
          {filteredList.map((reminder: ReminderModel, index) => {
            return (
              <IonItem
                key={index}
                button
                onClick={() => {
                  setModalReminderValue(reminder);
                  setIsModalOpen(true);
                  nav.push(nav.location.pathname + "?modalOpened=true");
                }}
              >
                <p>{reminder.message}</p>
              </IonItem>
            );
          })}
          {!isLoading && filteredList.length === 0 && <ItemNotFound />}
        </IonList>
      </IonContent>
      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
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
