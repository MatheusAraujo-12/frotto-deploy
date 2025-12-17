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
  carId?: string; // Tornando opcional
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

  // Observar valor do campo message
  const message = watch("message") || "";

  // Buscar informações do carro se carId for fornecido
  useEffect(() => {
    if (!carId) return;

    // Cancelar requisição anterior se existir
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
        if (error.name === 'AbortError' || error.code === 'ERR_CANCELED') {
          return;
        }
        console.error("Erro ao buscar carro:", error);
        setFetchError("Não foi possível carregar informações do veículo");
        showErrorAlert("Erro ao carregar dados do veículo");
      }
    };

    fetchCar();

    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [carId, showErrorAlert]);

  // Resetar formulário quando initialValues mudar
  useEffect(() => {
    if (initialValues) {
      reset(initialReminderValues(initialValues));
    }
  }, [initialValues, reset]);

  const onSubmit = useCallback(async (formData: ReminderModel) => {
    if (!isValid) {
      showErrorAlert("Preencha o campo de lembrete corretamente");
      return;
    }

    const targetCarId = selectedCar?.id?.toString() || carId;
    if (!targetCarId) {
      showErrorAlert(TEXT.selectVehicle || "Selecione um veículo");
      return;
    }

    setIsLoading(true);
    try {
      let responseReminder: ReminderModel;
      
      if (formData.id) {
        // EDITAR
        const url = endpoints.REMINDERS_EDIT({
          pathVariables: { id: formData.id }
        });
        const response = await api.put(url, formData);
        responseReminder = response.data;
      } else {
        // CRIAR NOVO
        const url = endpoints.REMINDERS({
          pathVariables: { id: targetCarId }
        });
        const response = await api.post(url, formData);
        responseReminder = response.data;
      }
      
      setIsLoading(false);
      closeModal(responseReminder);
    } catch (error: any) {
      setIsLoading(false);
      console.error("Erro ao salvar lembrete:", error);
      
      const errorMessage = error.response?.data?.message 
        || error.message 
        || TEXT.saveFailed;
      showErrorAlert(errorMessage);
    }
  }, [isValid, selectedCar, carId, closeModal, showErrorAlert]);

  const onDelete = useCallback(async () => {
    if (!formInitial.id) return;
    
    setIsLoading(true);
    try {
      await api.delete(
        endpoints.REMINDERS_EDIT({ 
          pathVariables: { id: formInitial.id } 
        })
      );
      setIsLoading(false);
      closeModal();
    } catch (error: any) {
      setIsLoading(false);
      console.error("Erro ao deletar lembrete:", error);
      
      const errorMessage = error.response?.data?.message 
        || error.message 
        || TEXT.deleteFailed;
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
    if (isLoading) return; // Impede fechar durante loading
    closeModal();
  }, [isLoading, closeModal]);

  return (
    <IonPage id="car-reminder-add-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonButton
              color="medium"
              onClick={handleClose}
              disabled={isLoading}
            >
              {TEXT.cancel}
            </IonButton>
          </IonButtons>
          
          <IonTitle>
            {formInitial.id ? TEXT.editReminder : TEXT.newReminder || "Novo Lembrete"}
          </IonTitle>
          
          <IonButtons slot="end">
            <IonButton
              disabled={isLoading || !isValid || (!isDirty && formInitial.id)}
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
            <div style={{ padding: 16 }}>
              {fetchError && (
                <div style={{ 
                  color: "var(--ion-color-danger)", 
                  marginBottom: 12,
                  fontSize: 14 
                }}>
                  {fetchError}
                </div>
              )}
              
              <h3 style={{ 
                marginTop: 0, 
                marginBottom: 16,
                fontSize: 16,
                fontWeight: 600 
              }}>
                {TEXT.selectVehicle || "Selecione um veículo"}
              </h3>
              
              <CarSelector onSelect={handleSelectCar} />
            </div>
          )}

          {/* Info do Carro Selecionado */}
          {selectedCar && (
            <div style={{ 
              padding: 16, 
              backgroundColor: "var(--ion-color-light)",
              marginBottom: 16 
            }}>
              <div style={{ 
                display: "flex", 
                justifyContent: "space-between",
                alignItems: "center"
              }}>
                <div>
                  <strong style={{ fontSize: 16, display: "block" }}>
                    {selectedCar.name}
                  </strong>
                  <div style={{ 
                    fontSize: 14, 
                    color: "var(--ion-color-medium)",
                    marginTop: 4 
                  }}>
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
                    {TEXT.change || "Trocar"}
                  </IonButton>
                )}
              </div>
            </div>
          )}

          {/* Formulário (apenas se tiver carro selecionado ou carId) */}
          {(selectedCar || carId) && (
            <FormInputArea
              label={TEXT.reminder || "Lembrete"}
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
          <div style={{ padding: 16, marginTop: 24 }}>
            <FormDeleteButton
              label={`${TEXT.delete || "Excluir"} ${TEXT.reminder?.toLowerCase() || "lembrete"}`}
              message={TEXT.confirmDeleteReminder || "Tem certeza que deseja excluir este lembrete?"}
              callBackFunc={onDelete}
              disabled={isLoading}
            />
          </div>
        )}
        
        {/* Dicas para o usuário */}
        {!formInitial.id && (selectedCar || carId) && (
          <div style={{ 
            padding: 16, 
            marginTop: 24,
            backgroundColor: "var(--ion-color-light)",
            borderRadius: 8
          }}>
            <p style={{ 
              fontSize: 14, 
              color: "var(--ion-color-medium)",
              margin: 0 
            }}>
              <strong>Dicas para lembrete:</strong>
            </p>
            <ul style={{ 
              fontSize: 12, 
              color: "var(--ion-color-medium)",
              margin: "8px 0 0 16px",
              padding: 0 
            }}>
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