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
  IonPage,
  IonProgressBar,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import {
  bulbOutline,
  carSportOutline,
  notificationsOutline,
} from "ionicons/icons";
import { TEXT } from "../../../constants/texts";
import { useState, useEffect, useCallback, useRef } from "react";
import { useAlert } from "../../../services/hooks/useAlert";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import CarSelector from "../../../components/Car/CarSelector";
import { CarModel, ReminderModel } from "../../../constants/CarModels";
import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import {
  reminderAddValidationSchema,
  initialReminderValues,
} from "./reminderAddValidationSchema";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import FormInputArea from "../../../components/Form/FormInputArea";
import "./reminderAdd.css";

interface ReminderAddModalProps {
  closeModal: (response?: ReminderModel) => void;
  initialValues?: ReminderModel;
  carId?: string;
}

const ReminderAdd: React.FC<ReminderAddModalProps> = ({
  closeModal,
  initialValues,
  carId,
}) => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setIsLoading] = useState(false);
  const [selectedCar, setSelectedCar] = useState<CarModel | null>(null);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);

  const formInitial = initialReminderValues(initialValues || {});

  const {
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors, isValid, isDirty },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(reminderAddValidationSchema),
    defaultValues: formInitial,
    mode: "onTouched",
  });

  const message = watch("message") || "";

  useEffect(() => {
    if (!carId) return;

    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    abortControllerRef.current = new AbortController();

    const fetchCar = async () => {
      try {
        setFetchError(null);

        const response = await api.get(
          endpoints.CAR({ pathVariables: { id: carId } }),
          { signal: abortControllerRef.current!.signal }
        );

        setSelectedCar(response.data);
      } catch (error: any) {
        if (error?.name === "AbortError" || error?.code === "ERR_CANCELED") {
          return;
        }

        // eslint-disable-next-line no-console
        console.error("Erro ao buscar carro:", error);
        setFetchError("Nao foi possivel carregar informacoes do veiculo");
        showErrorAlert("Erro ao carregar dados do veiculo");
      }
    };

    fetchCar();

    return () => {
      abortControllerRef.current?.abort();
    };
  }, [carId, showErrorAlert]);

  useEffect(() => {
    if (initialValues) {
      reset(initialReminderValues(initialValues));
    }
  }, [initialValues, reset]);

  const onSubmit = useCallback(
    async (formData: ReminderModel) => {
      if (!isValid) {
        showErrorAlert("Preencha o campo de lembrete corretamente");
        return;
      }

      const targetCarId = selectedCar?.id?.toString() || carId;
      if (!targetCarId) {
        showErrorAlert(`${TEXT.select} ${TEXT.car}`);
        return;
      }

      setIsLoading(true);
      try {
        let responseReminder: ReminderModel;

        if (formData.id) {
          const url = endpoints.REMINDERS_EDIT({
            pathVariables: { id: formData.id },
          });
          const response = await api.put(url, formData);
          responseReminder = response.data;
        } else {
          const url = endpoints.REMINDERS({
            pathVariables: { id: targetCarId },
          });
          const response = await api.post(url, formData);
          responseReminder = response.data;
        }

        setIsLoading(false);
        closeModal(responseReminder);
      } catch (error: any) {
        setIsLoading(false);
        // eslint-disable-next-line no-console
        console.error("Erro ao salvar lembrete:", error);

        const errorMessage =
          error?.response?.data?.message || error?.message || TEXT.saveFailed;
        showErrorAlert(errorMessage);
      }
    },
    [isValid, selectedCar, carId, closeModal, showErrorAlert]
  );

  const onDelete = useCallback(async () => {
    if (!formInitial.id) return;

    setIsLoading(true);
    try {
      await api.delete(
        endpoints.REMINDERS_EDIT({
          pathVariables: { id: formInitial.id },
        })
      );
      setIsLoading(false);
      closeModal();
    } catch (error: any) {
      setIsLoading(false);
      // eslint-disable-next-line no-console
      console.error("Erro ao deletar lembrete:", error);

      const errorMessage =
        error?.response?.data?.message || error?.message || TEXT.deleteFailed;
      showErrorAlert(errorMessage);
    }
  }, [formInitial.id, closeModal, showErrorAlert]);

  const handleSelectCar = useCallback((car: CarModel) => {
    setSelectedCar(car);
  }, []);

  const handleResetCar = useCallback(() => {
    setSelectedCar(null);
  }, []);

  const handleClose = useCallback(() => {
    if (isLoading) return;
    closeModal();
  }, [isLoading, closeModal]);

  const titleText = formInitial.id
    ? `${TEXT.edit} ${TEXT.reminder}`
    : `${TEXT.new} ${TEXT.reminder}`;

  const selectVehicleText = `${TEXT.select} ${TEXT.car}`;

  const confirmDeleteMessage = `Tem certeza que deseja excluir este ${String(
    TEXT.reminder
  ).toLowerCase()}?`;

  return (
    <IonPage id="car-reminder-add-page">
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

      <IonContent className="reminder-add-content">
        <div className="app-shell app-shell--compact reminder-add-shell">
          <section className="app-section">
            <div className="reminder-add-section-head">
              <h2 className="app-section-title">{titleText}</h2>
              <p className="app-section-subtitle">
                Organize lembretes com o mesmo padrao visual dos demais modulos
                de cadastro.
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
                      {selectVehicleText}
                    </IonCardTitle>
                    <IonCardSubtitle className="app-panel-subtitle">
                      Escolha o veiculo para salvar o lembrete.
                    </IonCardSubtitle>
                  </div>
                </IonCardHeader>
                <IonCardContent>
                  {fetchError && (
                    <div className="app-inline-alert app-inline-alert--danger reminder-add-alert">
                      {fetchError}
                    </div>
                  )}

                  <CarSelector onSelect={handleSelectCar} />
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
                  <div className="reminder-add-selected-car">
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
              <IonCard className="app-panel-card">
                <IonCardHeader className="app-panel-header">
                  <div className="app-soft-icon">
                    <IonIcon icon={notificationsOutline} />
                  </div>
                  <div className="app-panel-header__content">
                    <IonCardTitle className="app-panel-title">
                      Conteudo do lembrete
                    </IonCardTitle>
                    <IonCardSubtitle className="app-panel-subtitle">
                      Registre uma orientacao clara e facil de consultar depois.
                    </IonCardSubtitle>
                  </div>
                </IonCardHeader>
                <IonCardContent>
                  <form
                    className="app-form-grid reminder-add-form"
                    onSubmit={(e) => e.preventDefault()}
                  >
                    <FormInputArea
                      label={TEXT.reminder}
                      errorsObj={errors}
                      errorName="message"
                      initialValue={message}
                      maxlength={200}
                      rows={4}
                      placeholder="Ex: Trocar oleo a cada 10.000km, calibrar pneus mensalmente..."
                      changeCallback={(value: string) => {
                        setValue("message", value, { shouldValidate: true });
                      }}
                      required
                      disabled={isLoading}
                      counter={true}
                      maxCounter={200}
                    />
                  </form>
                </IonCardContent>
              </IonCard>
            )}

            {formInitial.id && (
              <div className="reminder-add-delete">
                <FormDeleteButton
                  label={`${TEXT.delete} ${String(TEXT.reminder).toLowerCase()}`}
                  message={confirmDeleteMessage}
                  callBackFunc={onDelete}
                  disabled={isLoading}
                />
              </div>
            )}

            {!formInitial.id && (selectedCar || carId) && (
              <IonCard className="app-panel-card app-panel-card--soft">
                <IonCardHeader className="app-panel-header">
                  <div className="app-soft-icon">
                    <IonIcon icon={bulbOutline} />
                  </div>
                  <div className="app-panel-header__content">
                    <IonCardTitle className="app-panel-title">
                      Dicas de preenchimento
                    </IonCardTitle>
                    <IonCardSubtitle className="app-panel-subtitle">
                      Exemplos curtos para manter os lembretes objetivos.
                    </IonCardSubtitle>
                  </div>
                </IonCardHeader>
                <IonCardContent>
                  <ul className="reminder-add-tips">
                    <li>Troca de oleo a cada 10.000km</li>
                    <li>Calibragem de pneus semanal</li>
                    <li>Revisao anual obrigatoria</li>
                    <li>Troca de pastilhas de freio</li>
                    <li>Alinhamento e balanceamento</li>
                  </ul>
                </IonCardContent>
              </IonCard>
            )}
          </section>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default ReminderAdd;
