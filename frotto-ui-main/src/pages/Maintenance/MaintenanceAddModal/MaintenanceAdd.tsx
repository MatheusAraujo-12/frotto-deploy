import {
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonContent,
  IonHeader,
  IonIcon,
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
import {
  buildOutline,
  carSportOutline,
  notificationsOutline,
} from "ionicons/icons";
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
import {
  calculateMaintenanceCost,
  initialMaintenanceValues,
  maintenanceAddValidationSchema,
} from "./maintenanceValidationSchema";
import ServiceAddModal from "../ServiceAddModal/ServiceAddModal";
import { currencyFormat } from "../../../services/currencyFormat";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import ReminderAdd from "../../Reminders/ReminderAddModal/reminderAdd";
import "./MaintenanceAdd.css";

interface MaintenanceAddModalProps {
  closeModal: (response?: MaintenanceModel) => void;
  initialValues?: MaintenanceModel;
  carId?: string;
}

const MaintenanceAdd: React.FC<MaintenanceAddModalProps> = ({ closeModal, initialValues, carId }) => {
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

  const closeServiceModal = useCallback(
    (newServices?: MaintenanceServiceModel[]) => {
      setIsServiceModalOpen(false);

      if (!newServices) return;

      setValue("services", newServices, {
        shouldValidate: true,
        shouldDirty: true,
        shouldTouch: true,
      });
    },
    [setValue]
  );

  const closeReminderModal = useCallback(
    (reminder?: ReminderModel) => {
      setIsReminderModalOpen(false);
      setActiveReminder(null);

      if (!reminder) return;

      if (!formInitial.id) loadReminders();
    },
    [formInitial.id, loadReminders]
  );

  const openServiceModal = useCallback(() => {
    setIsServiceModalOpen(true);
  }, []);

  const openReminderModal = useCallback(
    (reminder?: ReminderModel) => {
      setActiveReminder(reminder || null);
      setIsReminderModalOpen(true);
    },
    []
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
      closeModal({ id: formInitial.id, delete: true });
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
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonButton
              fill="clear"
              className="app-outline-btn"
              onClick={handleClose}
              disabled={isLoading}
            >
              {TEXT.cancel}
            </IonButton>
          </IonButtons>

          <IonTitle>{titleText}</IonTitle>

          <IonButtons slot="end">
            <IonButton
              className="app-primary-btn"
              disabled={isLoading || !isValid || (!isDirty && !!formInitial.id)}
              onClick={handleSubmit(onSubmit)}
            >
              {TEXT.save}
            </IonButton>
          </IonButtons>

          {isLoading && <IonProgressBar type="indeterminate" />}
        </IonToolbar>
      </IonHeader>

      <IonContent className="maintenance-add-content">
        <div className="app-shell app-shell--compact maintenance-add-shell">
          <section className="app-section">
            <div className="maintenance-add-section-head">
              <h2 className="app-section-title">{titleText}</h2>
              <p className="app-section-subtitle">
                Revise data, odometro, local, servicos e lembretes desta
                manutencao.
              </p>
            </div>

            {!selectedCar && !carId && (
              <IonCard className="app-panel-card">
                <IonCardHeader className="app-panel-header">
                  <div className="app-soft-icon">
                    <IonIcon icon={carSportOutline} />
                  </div>
                  <div className="app-panel-header__content">
                    <IonCardTitle className="app-panel-title">
                      {`${TEXT.select} ${TEXT.car}`}
                    </IonCardTitle>
                    <IonCardSubtitle className="app-panel-subtitle">
                      Escolha o veiculo para continuar.
                    </IonCardSubtitle>
                  </div>
                </IonCardHeader>
                <IonCardContent>
                  <div className="maintenance-add-car-selector">
                    <CarSelector onSelect={handleSelectCar} />
                  </div>
                </IonCardContent>
              </IonCard>
            )}

            {selectedCar && !carId && (
              <IonCard className="app-panel-card app-panel-card--soft">
                <IonCardHeader className="app-panel-header">
                  <div className="app-soft-icon">
                    <IonIcon icon={carSportOutline} />
                  </div>
                  <div className="app-panel-header__content">
                    <IonCardTitle className="app-panel-title">
                      {selectedCar.name}
                    </IonCardTitle>
                    <IonCardSubtitle className="app-panel-subtitle">
                      {selectedCar.plate || "Sem placa"}
                    </IonCardSubtitle>
                  </div>
                </IonCardHeader>
                <IonCardContent>
                  <div className="maintenance-add-car-summary">
                    <IonButton
                      size="small"
                      fill="clear"
                      className="app-outline-btn"
                      onClick={handleResetCar}
                    >
                      Trocar veiculo
                    </IonButton>
                  </div>
                </IonCardContent>
              </IonCard>
            )}

            {(selectedCar || carId) && (
              <>
                <IonCard className="app-panel-card app-panel-card--soft">
                  <IonCardHeader className="app-panel-header">
                    <div className="app-soft-icon">
                      <IonIcon icon={buildOutline} />
                    </div>
                    <div className="app-panel-header__content">
                      <IonCardTitle className="app-panel-title">
                        Resumo da manutencao
                      </IonCardTitle>
                      <IonCardSubtitle className="app-panel-subtitle">
                        {services?.filter((service) => !!service)?.length || 0}{" "}
                        servico(s) registrado(s)
                      </IonCardSubtitle>
                    </div>
                  </IonCardHeader>
                  <IonCardContent>
                    <div className="maintenance-add-summary">
                      <div className="app-soft-box app-soft-box--neutral">
                        <span className="maintenance-add-summary__label">
                          {TEXT.date}
                        </span>
                        <strong className="maintenance-add-summary__value">
                          {date || "-"}
                        </strong>
                      </div>
                      <div className="app-soft-box app-soft-box--neutral">
                        <span className="maintenance-add-summary__label">
                          {TEXT.odometer}
                        </span>
                        <strong className="maintenance-add-summary__value">
                          {`${odometer || 0} ${TEXT.km}`}
                        </strong>
                      </div>
                      <div className="app-soft-box app-soft-box--neutral">
                        <span className="maintenance-add-summary__label">
                          {TEXT.local}
                        </span>
                        <strong className="maintenance-add-summary__value">
                          {local || "-"}
                        </strong>
                      </div>
                      <div className="app-soft-box app-soft-box--success">
                        <span className="maintenance-add-summary__label">
                          {TEXT.total}
                        </span>
                        <strong className="maintenance-add-summary__value">
                          {currencyFormat(totalCost)}
                        </strong>
                      </div>
                    </div>
                  </IonCardContent>
                </IonCard>

                <IonCard className="app-panel-card">
                  <IonCardHeader className="app-panel-header">
                    <div className="app-soft-icon">
                      <IonIcon icon={buildOutline} />
                    </div>
                    <div className="app-panel-header__content">
                      <IonCardTitle className="app-panel-title">
                        Dados da manutencao
                      </IonCardTitle>
                      <IonCardSubtitle className="app-panel-subtitle">
                        Preencha os campos e edite a lista de servicos.
                      </IonCardSubtitle>
                    </div>
                  </IonCardHeader>
                  <IonCardContent>
                    <form
                      className="app-form-grid maintenance-add-form"
                      onSubmit={(e) => e.preventDefault()}
                    >
                      <div className="maintenance-add-form__main">
                        <div className="maintenance-add-field">
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
                        </div>

                        <div className="maintenance-add-field">
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
                        </div>

                        <div className="maintenance-add-field">
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
                        </div>
                      </div>

                      <IonList className="maintenance-add-services-list">
                        <IonListHeader className="maintenance-add-services-list__header">
                          <IonLabel>
                            <h2>{TEXT.maintenanceServices}</h2>
                            {totalCost > 0 && (
                              <IonText
                                color="primary"
                                className="maintenance-add-services-list__total"
                              >
                                ({currencyFormat(totalCost)})
                              </IonText>
                            )}
                          </IonLabel>
                          <IonButton
                            size="small"
                            fill="clear"
                            className="app-outline-btn"
                            onClick={openServiceModal}
                            disabled={isLoading}
                          >
                            {TEXT.edit}
                          </IonButton>
                        </IonListHeader>

                        {services?.length ? (
                          services.map((service, index) => {
                            const desc = (service as any)?.description;
                            return (
                              <IonItem
                                key={`service-${index}`}
                                lines="none"
                                className="maintenance-add-services-list__item"
                              >
                                <IonLabel
                                  slot="start"
                                  className="maintenance-add-services-list__label ion-text-wrap"
                                >
                                  <h3 className="maintenance-add-services-list__name">
                                    <IonText>{service.name}</IonText>
                                  </h3>
                                  {!!desc && (
                                    <p className="maintenance-add-services-list__desc">
                                      {desc}
                                    </p>
                                  )}
                                </IonLabel>
                                <IonLabel
                                  slot="end"
                                  className="maintenance-add-services-list__value"
                                >
                                  <IonText>
                                    {currencyFormat(service.cost || 0)}
                                  </IonText>
                                </IonLabel>
                              </IonItem>
                            );
                          })
                        ) : (
                          <div className="app-empty-state maintenance-add-empty-state">
                            <strong>{TEXT.maintenanceServices}</strong>
                            <span>Nenhum servico adicionado.</span>
                          </div>
                        )}
                      </IonList>
                    </form>
                  </IonCardContent>
                </IonCard>
              </>
            )}

            {!formInitial.id && (
              <IonCard className="app-panel-card">
                <IonCardHeader className="app-panel-header">
                  <div className="app-soft-icon">
                    <IonIcon icon={notificationsOutline} />
                  </div>
                  <div className="app-panel-header__content">
                    <IonCardTitle className="app-panel-title">
                      {TEXT.reminders}
                    </IonCardTitle>
                    <IonCardSubtitle className="app-panel-subtitle">
                      Ajuste ou crie lembretes relacionados a esta manutencao.
                    </IonCardSubtitle>
                  </div>
                </IonCardHeader>
                <IonCardContent>
                  <IonList className="maintenance-add-reminders">
                    <IonListHeader className="maintenance-add-reminders__header">
                      <IonLabel>
                        <h2>{TEXT.reminders}</h2>
                      </IonLabel>
                      <IonButton
                        size="small"
                        fill="clear"
                        className="app-outline-btn"
                        onClick={() => openReminderModal()}
                        disabled={isLoading}
                      >
                        {TEXT.add}
                      </IonButton>
                    </IonListHeader>

                    {reminderList.length > 0 ? (
                      reminderList.map((reminder, index) => {
                        const reminderDate = (reminder as any)?.date;
                        return (
                          <IonItem
                            button
                            detail={false}
                            lines="none"
                            key={`reminder-${index}`}
                            className="maintenance-add-reminders__item"
                            onClick={() => openReminderModal(reminder)}
                            disabled={isLoading}
                          >
                            <IonLabel className="ion-text-wrap">
                              <p className="maintenance-add-reminders__message">
                                {reminder.message}
                              </p>
                              {!!reminderDate && (
                                <p className="maintenance-add-reminders__date">
                                  {reminderDate}
                                </p>
                              )}
                            </IonLabel>
                          </IonItem>
                        );
                      })
                    ) : (
                      <div className="app-empty-state maintenance-add-reminders-empty">
                        <strong>{TEXT.reminders}</strong>
                        <span>{TEXT.noReminders}</span>
                      </div>
                    )}
                  </IonList>
                </IonCardContent>
              </IonCard>
            )}

            {formInitial.id && (
              <div className="maintenance-add-delete">
                <FormDeleteButton
                  label={`${TEXT.delete} ${String(TEXT.maintenance).toLowerCase()}`}
                  message={TEXT.maintenance}
                  callBackFunc={onDelete}
                />
              </div>
            )}
          </section>
        </div>
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
