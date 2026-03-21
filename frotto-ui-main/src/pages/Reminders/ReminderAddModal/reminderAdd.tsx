import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonPage,
  IonProgressBar,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
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

  // Buscar informações do carro se carId for fornecido
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
        if (error?.name === "AbortError" || error?.code === "ERR_CANCELED") return;

        // eslint-disable-next-line no-console
        console.error("Erro ao buscar carro:", error);
        setFetchError("Não foi possível carregar informações do veículo");
        showErrorAlert("Erro ao carregar dados do veículo");
      }
    };

    fetchCar();

    return () => {
      abortControllerRef.current?.abort();
    };
  }, [carId, showErrorAlert]);

  // Resetar formulário quando initialValues mudar
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
          // EDITAR
          const url = endpoints.REMINDERS_EDIT({
            pathVariables: { id: formData.id },
          });
          const response = await api.put(url, formData);
          responseReminder = response.data;
        } else {
          // CRIAR NOVO
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
              strong={true}
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
          {/* Seletor de Carro (apenas se não veio com carId) */}
          {!selectedCar && !carId && (
            <div className="app-form-page__panel">
              {fetchError && (
                <div className="app-inline-alert app-inline-alert--danger">
                  {fetchError}
                </div>
              )}

              <h3 className="app-form-page__title">
                {selectVehicleText}
              </h3>

              <CarSelector onSelect={handleSelectCar} />
            </div>
          )}

          {/* Info do Carro Selecionado */}
          {selectedCar && (
            <div className="app-selected-car">
              <div>
                  <strong className="app-selected-car__title">
                    {selectedCar.name}
                  </strong>
                  <div className="app-selected-car__meta">
                    {selectedCar.plate || "Sem placa"}
                  </div>
                </div>

              {!carId && (
                <IonButton
                  size="small"
                  fill="clear"
                  color="medium"
                  onClick={handleResetCar}
                >
                  Trocar
                </IonButton>
              )}
            </div>
          )}

          {/* Formulário (apenas se tiver carro selecionado ou carId) */}
          {(selectedCar || carId) && (
            <FormInputArea
              label={TEXT.reminder}
              errorsObj={errors}
              errorName="message"
              initialValue={message}
              maxlength={200}
              rows={4}
              placeholder="Ex: Trocar óleo a cada 10.000km, Calibrar pneus mensalmente..."
              changeCallback={(value: string) => {
                setValue("message", value, { shouldValidate: true });
              }}
              required
              disabled={isLoading}
              counter={true}
              maxCounter={200}
            />
          )}
        </form>

        {/* Botão de deletar (apenas para edição) */}
        {formInitial.id && (
          <FormDeleteButton
            label={`${TEXT.delete} ${String(TEXT.reminder).toLowerCase()}`}
            message={confirmDeleteMessage}
            callBackFunc={onDelete}
            disabled={isLoading}
          />
        )}

        {/* Dicas */}
        {!formInitial.id && (selectedCar || carId) && (
          <div className="app-help-card">
            <p className="app-help-card__title">Dicas para lembrete:</p>
            <ul className="app-help-card__list">
              <li>Troca de óleo a cada 10.000km</li>
              <li>Calibragem de pneus semanal</li>
              <li>Revisão anual obrigatória</li>
              <li>Troca de pastilhas de freio</li>
              <li>Alinhamento e balanceamento</li>
            </ul>
          </div>
        )}
      </IonContent>
    </IonPage>
  );
};

export default ReminderAdd;
