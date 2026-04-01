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
import { carSportOutline, cashOutline } from "ionicons/icons";
import { TEXT } from "../../../constants/texts";
import { useState, useEffect, useCallback } from "react";
import { useAlert } from "../../../services/hooks/useAlert";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";

import FormDate from "../../../components/Form/FormDate";
import CarSelector from "../../../components/Car/CarSelector";
import { CarModel, IncomeModel } from "../../../constants/CarModels";
import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import { incomeAddValidationSchema, initialIncomeValues } from "./incomeAddValidationSchema";
import FormSelectFilterAdd from "../../../components/Form/FormSelectFilterAdd";
import { INCOMES } from "../../../constants/selectOptions";
import { INCOME_KEY } from "../../../services/localStorage/localstorage";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import FormCurrency from "../../../components/Form/FormCurrency";
import "./IncomeAdd.css";

interface IncomeAddModalProps {
  closeModal: (response?: IncomeModel) => void;
  initialValues?: IncomeModel;
  carId?: string;
}

const IncomeAdd: React.FC<IncomeAddModalProps> = ({ closeModal, initialValues, carId }) => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setIsLoading] = useState(false);
  const [selectedCar, setSelectedCar] = useState<CarModel | null>(null);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const formInitial = initialIncomeValues(initialValues || {});

  const {
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors, isValid, isDirty },
  } = useForm({
    resolver: yupResolver(incomeAddValidationSchema),
    defaultValues: formInitial,
    mode: "onTouched",
  });

  useEffect(() => {
    if (!carId) return;

    let mounted = true;
    const controller = new AbortController();

    const fetchCar = async () => {
      try {
        setFetchError(null);
        const response = await api.get(endpoints.CAR({ pathVariables: { id: carId } }), {
          signal: controller.signal,
        });
        if (mounted) setSelectedCar(response.data);
      } catch (error: any) {
        if (error?.name === "AbortError") return;
        if (mounted) {
          // eslint-disable-next-line no-console
          console.error("Erro ao buscar carro:", error);
          setFetchError("Não foi possível carregar informações do veículo");
          showErrorAlert("Erro ao carregar dados do veículo");
        }
      }
    };

    fetchCar();

    return () => {
      mounted = false;
      controller.abort();
    };
  }, [carId, showErrorAlert]);

  useEffect(() => {
    if (initialValues) reset(initialIncomeValues(initialValues));
  }, [initialValues, reset]);

  const onSubmit = useCallback(
    async (formData: IncomeModel) => {
      if (!isValid) {
        showErrorAlert("Preencha todos os campos obrigatórios corretamente");
        return;
      }

      setIsLoading(true);
      try {
        let responseIncome: IncomeModel;

        if (formData.id) {
          const url = endpoints.INCOMES_EDIT({ pathVariables: { id: formData.id } });
          const response = await api.put(url, formData);
          responseIncome = response.data;
        } else {
          const targetCarId = selectedCar?.id?.toString() || carId;
          if (!targetCarId) {
            showErrorAlert(`${TEXT.select} ${TEXT.car}`);
            setIsLoading(false);
            return;
          }

          const url = endpoints.INCOMES({ pathVariables: { id: targetCarId } });
          const response = await api.post(url, formData);
          responseIncome = response.data;
        }

        setIsLoading(false);
        closeModal(responseIncome);
      } catch (error: any) {
        setIsLoading(false);
        // eslint-disable-next-line no-console
        console.error("Erro ao salvar receita:", error);
        showErrorAlert(error?.response?.data?.message || error?.message || TEXT.saveFailed);
      }
    },
    [isValid, selectedCar, carId, closeModal, showErrorAlert]
  );

  const onDelete = useCallback(async () => {
    if (!formInitial.id) return;

    setIsLoading(true);
    try {
      await api.delete(endpoints.INCOMES_EDIT({ pathVariables: { id: formInitial.id } }));
      setIsLoading(false);
      closeModal({ id: formInitial.id, delete: true });
    } catch (error: any) {
      setIsLoading(false);
      // eslint-disable-next-line no-console
      console.error("Erro ao deletar receita:", error);
      showErrorAlert(error?.response?.data?.message || error?.message || TEXT.deleteFailed);
    }
  }, [formInitial.id, closeModal, showErrorAlert]);

  const handleCarSelect = useCallback((car: CarModel) => setSelectedCar(car), []);
  const handleClose = useCallback(() => {
    if (!isLoading) closeModal();
  }, [isLoading, closeModal]);

  const incomeName = watch("name");
  const incomeDate = watch("date");
  const incomeValue = watch("cost");

  const titleText = formInitial.id ? `${TEXT.edit} ${TEXT.income}` : `${TEXT.newIncome} ${TEXT.income}`;

  // Ajuste: Cria uma frase completa para a confirmação de exclusão
  const confirmDeleteMessage =
    (TEXT as any).confirmDeleteIncome ||
    `Deseja excluir esta ${String(TEXT.income || "receita").toLowerCase()}?`;

  return (
    <IonPage id="car-income-add-page">
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

      <IonContent className="income-add-content">
        <div className="app-shell app-shell--compact income-add-shell">
          <section className="app-section">
            <div className="income-add-section-head">
              <h2 className="app-section-title">{titleText}</h2>
              <p className="app-section-subtitle">
                Registre a receita com o mesmo padrao visual aplicado aos demais
                lancamentos.
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
                      Escolha o veiculo para vincular a receita.
                    </IonCardSubtitle>
                  </div>
                </IonCardHeader>
                <IonCardContent>
                  {fetchError && (
                    <div className="app-inline-alert app-inline-alert--danger income-add-alert">
                      {fetchError}
                    </div>
                  )}

                  <CarSelector onSelect={handleCarSelect} />
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
                  <div className="income-add-selected-car">
                    <IonButton
                      size="small"
                      fill="clear"
                      className="app-outline-btn"
                      onClick={() => setSelectedCar(null)}
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
                    <IonIcon icon={cashOutline} />
                  </div>
                  <div className="app-panel-header__content">
                    <IonCardTitle className="app-panel-title">
                      Dados da receita
                    </IonCardTitle>
                    <IonCardSubtitle className="app-panel-subtitle">
                      Informe data, categoria e valor para salvar o lancamento.
                    </IonCardSubtitle>
                  </div>
                </IonCardHeader>
                <IonCardContent>
                  <form
                    className="app-form-grid income-add-form"
                    onSubmit={(e) => e.preventDefault()}
                  >
                    <div className="income-add-field">
                      <FormDate
                        id="date-income-add"
                        initialValue={incomeDate}
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

                    <div className="income-add-field">
                      <FormSelectFilterAdd
                        label={TEXT.incomeName}
                        errorsObj={errors}
                        errorName="name"
                        formCallBack={(value: string) =>
                          setValue("name", value, {
                            shouldValidate: true,
                            shouldDirty: true,
                            shouldTouch: true,
                          })
                        }
                        initialValue={incomeName}
                        options={INCOMES}
                        storageToken={INCOME_KEY}
                        required
                      />
                    </div>

                    <div className="income-add-field">
                      <FormCurrency
                        label={TEXT.value}
                        errorsObj={errors}
                        errorName="cost"
                        initialValue={incomeValue}
                        maxlength={15}
                        changeCallback={(value: number) =>
                          setValue("cost", value, {
                            shouldValidate: true,
                            shouldDirty: true,
                            shouldTouch: true,
                          })
                        }
                        required
                      />
                    </div>
                  </form>
                </IonCardContent>
              </IonCard>
            )}

            {formInitial.id && (
              <div className="income-add-delete">
                <FormDeleteButton
                  label={`${TEXT.delete} ${String(TEXT.income).toLowerCase()}`}
                  message={confirmDeleteMessage}
                  callBackFunc={onDelete}
                />
              </div>
            )}
          </section>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default IncomeAdd;
