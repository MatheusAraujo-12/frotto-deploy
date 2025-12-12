import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonList,
  IonListHeader,
  IonModal,
  IonPage,
  IonProgressBar,
  IonText,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import { TEXT } from "../../../constants/texts";
import { useCallback, useEffect, useState } from "react";
import { useAlert } from "../../../services/hooks/useAlert";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import FormDate from "../../../components/Form/FormDate";
import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import {
  InspectionModel,
  MaintenanceModel,
  MaintenanceServiceModel,
  ReminderModel,
} from "../../../constants/CarModels";
import FormInput from "../../../components/Form/FormInput";
import ItemNotFound from "../../../components/List/ItemNotFound";
import {
  calculateMaintenanceCost,
  initialMaintenanceValues,
  maintenanceAddValidationSchema,
} from "./maintenanceValidationSchema";
import ServiceAddModal from "../ServiceAddModal/ServiceAddModal";
import { useLocation, useHistory } from "react-router";
import { currencyFormat } from "../../../services/currencyFormat";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import ReminderAdd from "../../Reminders/ReminderAddModal/reminderAdd";

interface MaintenanceAddModalProps {
  closeModal: Function;
  initialValues?: InspectionModel;
  carId: String;
}

const MaintenanceAdd: React.FC<MaintenanceAddModalProps> = ({
  closeModal,
  initialValues,
  carId,
}) => {
  const location = useLocation();
  const nav = useHistory();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [isServiceModalOpen, setIsServiceModalOpen] = useState(false);
  const [reminderList, setRemindersList] = useState<ReminderModel[]>([]);
  const [isReminderModalOpen, setIsReminderModalOpen] = useState(false);
  const [activeReminder, setActiveReminder] = useState({});
  const formInitial = initialMaintenanceValues(initialValues || {});

  const {
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(maintenanceAddValidationSchema),
    defaultValues: formInitial,
  });

  const loadReminders = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.REMINDERS({
          pathVariables: {
            id: carId,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setRemindersList(data);
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadRemindersFailed);
    }
  };

  useEffect(() => {
    if (!location.search.includes("modalServiceOpened=true")) {
      setIsServiceModalOpen(false);
    }
    if (!location.search.includes("modalReminderOpened=true")) {
      setIsReminderModalOpen(false);
    }
  }, [location]);

  useEffect(() => {
    if (formInitial.id === undefined) {
      loadReminders();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const closeServiceModal = useCallback(
    (newServices: MaintenanceServiceModel[]) => {
      if (newServices) {
        setValue("services", newServices);
      }
      setIsServiceModalOpen(false);
      nav.goBack();
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [setValue]
  );

  const closeReminderModal = useCallback(
    (reminder: ReminderModel) => {
      if (reminder) {
        loadReminders();
      }
      nav.goBack();
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  );

  const onSubmit = async (newMaintenance: MaintenanceModel) => {
    setisLoading(true);
    newMaintenance.cost = calculateMaintenanceCost(newMaintenance);
    try {
      let responseMaintenance: MaintenanceModel;
      if (newMaintenance.id) {
        const urlPatch = endpoints.MAINTENANCES_EDIT({
          pathVariables: {
            id: newMaintenance.id,
          },
        });
        const response = await api.put(urlPatch, newMaintenance);
        responseMaintenance = response.data;
      } else {
        const urlPost = endpoints.MAINTENANCES({
          pathVariables: {
            id: carId,
          },
        });
        const response = await api.post(urlPost, newMaintenance);
        responseMaintenance = response.data;
      }
      setisLoading(false);
      closeModal(responseMaintenance);
    } catch (e: any) {
      setisLoading(false);
      showErrorAlert(TEXT.saveFailed);
    }
  };

  const onDelete = async () => {
    setisLoading(true);
    try {
      await api.delete(
        endpoints.MAINTENANCES_EDIT({ pathVariables: { id: formInitial.id } })
      );
      setisLoading(false);
      closeModal({});
    } catch (e) {
      setisLoading(false);
      showErrorAlert(TEXT.deleteFailed);
    }
  };

  return (
    <IonPage id="car-maintenance-add-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonButton
              color="danger"
              onClick={() => {
                closeModal();
              }}
            >
              {TEXT.cancel}
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.addCarMaintenance}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              disabled={isLoading}
              strong={true}
              onClick={handleSubmit(onSubmit)}
            >
              {TEXT.save}
            </IonButton>
          </IonButtons>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <form>
          <FormDate
            id="date-maintenance-add"
            initialValue={watch("date").toString()}
            label={TEXT.date}
            presentation="date"
            formCallBack={(value: string) => {
              setValue("date", value);
            }}
          />
          <FormInput
            label={TEXT.odometer}
            errorsObj={errors}
            errorName="odometer"
            initialValue={watch("odometer")}
            maxlength={15}
            type="number"
            changeCallback={(value: number) => {
              setValue("odometer", value);
            }}
            required
          />
          <FormInput
            label={TEXT.local}
            errorsObj={errors}
            errorName="local"
            initialValue={watch("local")}
            maxlength={50}
            changeCallback={(value: string) => {
              setValue("local", value);
            }}
            required
          />
          <IonList>
            <IonListHeader>
              <IonLabel>
                <h1>{TEXT.maintenanceServices}</h1>
              </IonLabel>
              <IonButton
                onClick={(e) => {
                  e.preventDefault();
                  setIsServiceModalOpen(true);
                  nav.push(
                    nav.location.pathname +
                      "?modalOpened=true&modalServiceOpened=true"
                  );
                }}
              >
                {TEXT.edit}
              </IonButton>
            </IonListHeader>
            {watch("services").map(
              (service: MaintenanceServiceModel, index) => {
                if (service) {
                  return (
                    <IonItem key={index}>
                      <IonLabel slot="end">
                        <IonText color="dark">
                          {currencyFormat(service.cost)}
                        </IonText>
                      </IonLabel>
                      <IonLabel class="ion-text-wrap">
                        <h2>
                          <IonText color="medium">{service.name}</IonText>
                        </h2>
                      </IonLabel>
                    </IonItem>
                  );
                } else {
                  return <ItemNotFound text={TEXT.noServices} />;
                }
              }
            )}
            {watch("services") && watch("services").length === 0 && (
              <ItemNotFound text={TEXT.noServices} />
            )}
          </IonList>
        </form>
        {formInitial.id && (
          <FormDeleteButton
            label={`${TEXT.delete} ${TEXT.maintenance}`}
            message={TEXT.maintenance}
            callBackFunc={onDelete}
          />
        )}

        {formInitial.id === undefined && (
          <>
            <IonList>
              <IonListHeader>
                <IonLabel>
                  <h1>{TEXT.reminders}</h1>
                </IonLabel>
                <IonButton
                  onClick={(e) => {
                    e.preventDefault();
                    setActiveReminder({});
                    setIsReminderModalOpen(true);
                    nav.push(
                      nav.location.pathname +
                        "?modalOpened=true&modalReminderOpened=true"
                    );
                  }}
                >
                  {TEXT.add}
                </IonButton>
              </IonListHeader>
              {reminderList.map((reminder: ReminderModel, index) => {
                return (
                  <IonItem
                    button
                    key={index}
                    onClick={(e) => {
                      e.preventDefault();
                      setActiveReminder(reminder);
                      setIsReminderModalOpen(true);
                      nav.push(
                        nav.location.pathname +
                          "?modalOpened=true&modalReminderOpened=true"
                      );
                    }}
                  >
                    <p>{reminder.message}</p>
                  </IonItem>
                );
              })}
              {reminderList.length === 0 && (
                <ItemNotFound text={TEXT.noReminders} />
              )}
            </IonList>
          </>
        )}
      </IonContent>
      <IonModal isOpen={isServiceModalOpen} backdropDismiss={false}>
        <ServiceAddModal
          closeModal={closeServiceModal}
          initialValues={watch("services")}
        />
      </IonModal>
      <IonModal isOpen={isReminderModalOpen} backdropDismiss={false}>
        <ReminderAdd
          carId={carId}
          closeModal={closeReminderModal}
          initialValues={activeReminder}
        />
      </IonModal>
    </IonPage>
  );
};

export default MaintenanceAdd;
