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
import { useCallback, useEffect, useRef, useState } from "react";
import { useAlert } from "../../../services/hooks/useAlert";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import FormDate from "../../../components/Form/FormDate";
import CarSelector from "../../../components/Car/CarSelector";
import {
  CarModel,
  MaintenanceModel,
  MaintenanceServiceModel,
  ReminderModel,
} from "../../../constants/CarModels";
import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import FormInput from "../../../components/Form/FormInput";
import ItemNotFound from "../../../components/List/ItemNotFound";
import {
  calculateMaintenanceCost,
  initialMaintenanceValues,
  maintenanceAddValidationSchema,
} from "./maintenanceValidationSchema";
import ServiceAddModal from "../ServiceAddModal/ServiceAddModal";
import { useHistory, useLocation } from "react-router-dom";
import { currencyFormat } from "../../../services/currencyFormat";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import ReminderAdd from "../../Reminders/ReminderAddModal/reminderAdd";

interface MaintenanceAddModalProps {
  closeModal: (response?: MaintenanceModel) => void;
  initialValues?: MaintenanceModel;
  carId?: string;
}

const MaintenanceAdd: React.FC<MaintenanceAddModalProps> = ({ closeModal, initialValues, carId }) => {
  const history = useHistory();
  const location = useLocation();
  const { showErrorAlert } = useAlert();

  const [isLoading, setIsLoading] = useState(false);
  const [isServiceModalOpen, setIsServiceModalOpen] = useState(false);
  const [isReminderModalOpen, setIsReminderModalOpen] = useState(false);
  const [reminderList, setReminderList] = useState<ReminderModel[]>([]);
  const [selectedCar, setSelectedCar] = useState<CarModel | null>(null);
  const [activeReminder, setActiveReminder] = useState<ReminderModel | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);

  const formInitial = initialMaintenanceValues(initialValues || {});

  const {
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors, isValid, isDirty },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(maintenanceAddValidationSchema),
    defaultValues: formInitial,
    mode: "onTouched",
  });

  const services = watch("services");
  const date = watch("date");
  const odometer = watch("odometer");
  const local = watch("local");

  const loadReminders = useCallback(async () => {
    if (!carId || formInitial.id) return;

    const controller = new AbortController();

    try {
      setIsLoading(true);
      const { data } = await api.get(
        endpoints.REMINDERS({ pathVariables: { id: carId } }),
        { signal: controller.signal }
      );
      if (Array.isArray(data)) setReminderList(data);
    } catch (error: any) {
      if (error?.name !== "AbortError") {
        // eslint-disable-next-line no-console
        console.error("Erro ao carregar lembretes:", error);
        showErrorAlert(TEXT.loadRemindersFailed);
      }
    } finally {
      setIsLoading(false);
    }

    return () => controller.abort();
  }, [carId, formInitial.id, showErrorAlert]);

  const loadCar = useCallback(async () => {
    const normalizedCarId = carId?.toString().trim();
    if (!normalizedCarId || normalizedCarId === "undefined" || normalizedCarId === "null") {
      return;
    }

    if (abortControllerRef.current) abortControllerRef.current.abort();
    abortControllerRef.current = new AbortController();

    try {
      const response = await api.get(
        endpoints.CAR({ pathVariables: { id: normalizedCarId } }),
        {
        signal: abortControllerRef.current.signal,
        }
      );
      setSelectedCar(response.data);
    } catch (error: any) {
      const isCanceled =
        error?.name === "AbortError" || error?.code === "ERR_CANCELED";
      if (isCanceled) return;
      // eslint-disable-next-line no-console
      console.error("Erro ao carregar carro:", error);
      showErrorAlert("Erro ao carregar informações do veículo");
    }
  }, [carId, showErrorAlert]);

  useEffect(() => {
    loadCar();
    if (!formInitial.id) loadReminders();

    return () => {
      if (abortControllerRef.current) abortControllerRef.current.abort();
    };
  }, [loadCar, loadReminders, formInitial.id]);

  useEffect(() => {
    if (initialValues) reset(initialMaintenanceValues(initialValues));
  }, [initialValues, reset]);

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    setIsServiceModalOpen(searchParams.get("modalServiceOpened") === "true");
    setIsReminderModalOpen(searchParams.get("modalReminderOpened") === "true");
  }, [location.search]);

  const closeServiceModal = useCallback(
    (newServices?: MaintenanceServiceModel[]) => {
      setIsServiceModalOpen(false);

      const searchParams = new URLSearchParams(location.search);
      searchParams.delete("modalServiceOpened");
      history.replace({ pathname: location.pathname, search: searchParams.toString() });

      if (!newServices) return;

      setValue("services", newServices, {
        shouldValidate: true,
        shouldDirty: true,
        shouldTouch: true,
      });
    },
    [history, location, setValue]
  );

  const closeReminderModal = useCallback(
    (reminder?: ReminderModel) => {
      setIsReminderModalOpen(false);
      setActiveReminder(null);

      const searchParams = new URLSearchParams(location.search);
      searchParams.delete("modalReminderOpened");
      history.replace({ pathname: location.pathname, search: searchParams.toString() });

      if (!reminder) return;

      if (!formInitial.id) loadReminders();
    },
    [history, location, formInitial.id, loadReminders]
  );

  const openServiceModal = useCallback(() => {
    const searchParams = new URLSearchParams(location.search);
    searchParams.set("modalServiceOpened", "true");
    history.push({ pathname: location.pathname, search: searchParams.toString() });
    setIsServiceModalOpen(true);
  }, [history, location]);

  const openReminderModal = useCallback(
    (reminder?: ReminderModel) => {
      setActiveReminder(reminder || null);
      const searchParams = new URLSearchParams(location.search);
      searchParams.set("modalReminderOpened", "true");
      history.push({ pathname: location.pathname, search: searchParams.toString() });
      setIsReminderModalOpen(true);
    },
    [history, location]
  );

  const onSubmit = useCallback(
    async (formData: MaintenanceModel) => {
      if (!isValid) {
        showErrorAlert("Preencha todos os campos obrigatórios");
        return;
      }

      const targetCarId = selectedCar?.id?.toString() || carId;
      if (!targetCarId) {
        showErrorAlert(`${TEXT.select} ${TEXT.car}`);
        return;
      }

      setIsLoading(true);
      try {
        const maintenanceData = { ...formData, cost: calculateMaintenanceCost(formData) };

        let response: MaintenanceModel;
        if (maintenanceData.id) {
          const url = endpoints.MAINTENANCES_EDIT({ pathVariables: { id: maintenanceData.id } });
          const apiResponse = await api.put(url, maintenanceData);
          response = apiResponse.data;
        } else {
          const url = endpoints.MAINTENANCES({ pathVariables: { id: targetCarId } });
          const apiResponse = await api.post(url, maintenanceData);
          response = apiResponse.data;
        }

        setIsLoading(false);
        closeModal(response);
      } catch (error: any) {
        setIsLoading(false);
        // eslint-disable-next-line no-console
        console.error("Erro ao salvar manutenção:", error);
        showErrorAlert(error?.response?.data?.message || error?.message || TEXT.saveFailed);
      }
    },
    [isValid, selectedCar, carId, closeModal, showErrorAlert]
  );

  const onDelete = useCallback(async () => {
    if (!formInitial.id) return;

    setIsLoading(true);
    try {
      await api.delete(endpoints.MAINTENANCES_EDIT({ pathVariables: { id: formInitial.id } }));
      setIsLoading(false);
      closeModal();
    } catch (error: any) {
      setIsLoading(false);
      // eslint-disable-next-line no-console
      console.error("Erro ao deletar manutenção:", error);
      showErrorAlert(error?.response?.data?.message || error?.message || TEXT.deleteFailed);
    }
  }, [formInitial.id, closeModal, showErrorAlert]);

  const handleSelectCar = useCallback((car: CarModel) => setSelectedCar(car), []);
  const handleResetCar = useCallback(() => setSelectedCar(null), []);
  const handleClose = useCallback(() => {
    if (!isLoading) closeModal();
  }, [isLoading, closeModal]);

  const totalCost = calculateMaintenanceCost(watch());

  const reminderCarId = (carId || selectedCar?.id?.toString() || "").trim();

  const titleText = formInitial.id ? `${TEXT.edit} ${TEXT.maintenance}` : TEXT.addCarMaintenance;

  return (
    <IonPage id="car-maintenance-add-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonButton color="medium" onClick={handleClose} disabled={isLoading}>
              {TEXT.cancel}
            </IonButton>
          </IonButtons>

          <IonTitle>{titleText}</IonTitle>

          <IonButtons slot="end">
            <IonButton
              disabled={isLoading || !isValid || (!isDirty && !!formInitial.id)}
              strong
              onClick={handleSubmit(onSubmit)}
            >
              {TEXT.save}
            </IonButton>
          </IonButtons>

          {isLoading && <IonProgressBar type="indeterminate" />}
        </IonToolbar>
      </IonHeader>

      <IonContent>
        <form onSubmit={(e) => e.preventDefault()}>
          {!selectedCar && !carId && (
            <div style={{ padding: 16 }}>
              <h3 style={{ marginTop: 0, marginBottom: 16, fontSize: 16, fontWeight: 600 }}>
                {`${TEXT.select} ${TEXT.car}`}
              </h3>
              <CarSelector onSelect={handleSelectCar} />
            </div>
          )}

          {selectedCar && !carId && (
            <div style={{ padding: 16, backgroundColor: "var(--ion-color-light)", marginBottom: 16 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <strong style={{ fontSize: 16, display: "block" }}>{selectedCar.name}</strong>
                  <div style={{ fontSize: 14, color: "var(--ion-color-medium)", marginTop: 4 }}>
                    {selectedCar.plate || "Sem placa"}
                  </div>
                </div>
                <IonButton size="small" fill="clear" color="medium" onClick={handleResetCar}>
                  Trocar
                </IonButton>
              </div>
            </div>
          )}

          {(selectedCar || carId) && (
            <>
              <FormDate
                id="date-maintenance-add"
                initialValue={date}
                label={TEXT.date}
                presentation="date"
                formCallBack={(value: string) =>
                  setValue("date", value, {
                    shouldValidate: true,
                    shouldDirty: true,
                    shouldTouch: true,
                  })
                }
                required
              />

              <FormInput
                label={TEXT.odometer}
                errorsObj={errors}
                errorName="odometer"
                initialValue={odometer}
                maxlength={15}
                type="number"
                changeCallback={(value: number) =>
                  setValue("odometer", value, {
                    shouldValidate: true,
                    shouldDirty: true,
                    shouldTouch: true,
                  })
                }
                required
              />

              <FormInput
                label={TEXT.local}
                errorsObj={errors}
                errorName="local"
                initialValue={local}
                maxlength={50}
                changeCallback={(value: string) =>
                  setValue("local", value, {
                    shouldValidate: true,
                    shouldDirty: true,
                    shouldTouch: true,
                  })
                }
                required
              />

              <IonList>
                <IonListHeader>
                  <IonLabel>
                    <h2>{TEXT.maintenanceServices}</h2>
                    {totalCost > 0 && (
                      <IonText color="primary" style={{ marginLeft: 8 }}>
                        ({currencyFormat(totalCost)})
                      </IonText>
                    )}
                  </IonLabel>
                  <IonButton onClick={openServiceModal} disabled={isLoading}>
                    {TEXT.edit}
                  </IonButton>
                </IonListHeader>

                {services?.length ? (
                  services.map((service, index) => {
                    const desc = (service as any)?.description;
                    return (
                      <IonItem key={`service-${index}`}>
                        <IonLabel slot="start" class="ion-text-wrap">
                          <h3 style={{ margin: 0 }}>
                            <IonText color="dark">{service.name}</IonText>
                          </h3>
                          {!!desc && (
                            <p style={{ fontSize: 12, color: "var(--ion-color-medium)", margin: 0 }}>
                              {desc}
                            </p>
                          )}
                        </IonLabel>
                        <IonLabel slot="end">
                          <IonText color="dark" style={{ fontWeight: 600 }}>
                            {currencyFormat(service.cost || 0)}
                          </IonText>
                        </IonLabel>
                      </IonItem>
                    );
                  })
                ) : (
                  <ItemNotFound />
                )}
              </IonList>
            </>
          )}
        </form>

        {formInitial.id && (
          <div style={{ padding: 16, marginTop: 24 }}>
            <FormDeleteButton
              label={`${TEXT.delete} ${String(TEXT.maintenance).toLowerCase()}`}
              message={TEXT.maintenance}
              callBackFunc={onDelete}
            />
          </div>
        )}

        {!formInitial.id && reminderList.length > 0 && (
          <IonList style={{ marginTop: 24 }}>
            <IonListHeader>
              <IonLabel>
                <h2>{TEXT.reminders}</h2>
              </IonLabel>
              <IonButton onClick={() => openReminderModal()} disabled={isLoading}>
                {TEXT.add}
              </IonButton>
            </IonListHeader>

            {reminderList.map((reminder, index) => {
              const reminderDate = (reminder as any)?.date;
              return (
                <IonItem
                  button
                  key={`reminder-${index}`}
                  onClick={() => openReminderModal(reminder)}
                  disabled={isLoading}
                >
                  <IonLabel>
                    <p style={{ margin: 0 }}>{reminder.message}</p>
                    {!!reminderDate && (
                      <p style={{ fontSize: 12, color: "var(--ion-color-medium)", margin: 0 }}>
                        {reminderDate}
                      </p>
                    )}
                  </IonLabel>
                </IonItem>
              );
            })}
          </IonList>
        )}

        {!formInitial.id && reminderList.length === 0 && (
          <div style={{ padding: 16, textAlign: "center" }}>
            <p style={{ color: "var(--ion-color-medium)" }}>{TEXT.noReminders}</p>
          </div>
        )}
      </IonContent>

      <IonModal
        isOpen={isServiceModalOpen}
        onDidDismiss={() => closeServiceModal()}
        backdropDismiss={false}
      >
        <ServiceAddModal closeModal={closeServiceModal} initialValues={services} />
      </IonModal>

      <IonModal
        isOpen={isReminderModalOpen}
        onDidDismiss={() => closeReminderModal()}
        backdropDismiss={false}
      >
        <ReminderAdd
          carId={reminderCarId}
          closeModal={closeReminderModal}
          initialValues={activeReminder || undefined}
        />
      </IonModal>
    </IonPage>
  );
};

export default MaintenanceAdd;
