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
import { carSportOutline, receiptOutline } from "ionicons/icons";
import { TEXT } from "../../../constants/texts";
import { useState, useEffect } from "react";
import { useAlert } from "../../../services/hooks/useAlert";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";

import FormDate from "../../../components/Form/FormDate";
import CarSelector from "../../../components/Car/CarSelector";
import { CarModel, CarExpenseModel } from "../../../constants/CarModels";
import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import FormInput from "../../../components/Form/FormInput";
import {
  carExpenseAddValidationSchema,
  initialCarExpenseValues,
} from "./carExpenseAddValidationSchema";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import FormCurrency from "../../../components/Form/FormCurrency";
import FormToggle from "../../../components/Form/FormToggle";
import "./CarExpenseAdd.css";

interface CarExpenseAddModalProps {
  closeModal: (response?: CarExpenseModel) => void;
  initialValues?: CarExpenseModel;
  carId?: string;
}

const CarExpenseAdd: React.FC<CarExpenseAddModalProps> = ({
  closeModal,
  initialValues,
  carId,
}) => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setIsLoading] = useState(false);
  const [replicateForAllCars, setReplicateForAllCars] = useState(false);
  const [selectedCar, setSelectedCar] = useState<CarModel | null>(null);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const formInitial = initialCarExpenseValues(initialValues || {});

  useEffect(() => {
    if (!carId) return;

    let mounted = true;
    const controller = new AbortController();

    const fetchCar = async () => {
      try {
        setFetchError(null);
        const response = await api.get(
          endpoints.CAR({ pathVariables: { id: carId } }),
          { signal: controller.signal }
        );
        if (mounted) {
          setSelectedCar(response.data);
        }
      } catch (error: any) {
        if (error?.name === "AbortError") return;
        if (mounted) {
          // eslint-disable-next-line no-console
          console.error("Erro ao buscar carro:", error);
          setFetchError("Nao foi possivel carregar informacoes do veiculo");
        }
      }
    };

    fetchCar();

    return () => {
      mounted = false;
      controller.abort();
    };
  }, [carId]);

  const {
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(carExpenseAddValidationSchema),
    defaultValues: formInitial,
    mode: "onTouched",
  });

  const onSubmit = async (newCarExpense: CarExpenseModel) => {
    setIsLoading(true);
    try {
      let responseCarExpense: CarExpenseModel;

      if (newCarExpense.id) {
        const url = endpoints.CAR_EXPENSES_EDIT({
          pathVariables: { id: newCarExpense.id },
        });
        const response = await api.put(url, newCarExpense);
        responseCarExpense = response.data;
      } else {
        let url: string;

        if (replicateForAllCars) {
          url = endpoints.CAR_EXPENSES_ALL();
        } else {
          const targetCarId = selectedCar?.id || carId;
          if (!targetCarId) {
            showErrorAlert(TEXT.select);
            setIsLoading(false);
            return;
          }
          url = endpoints.CAR_EXPENSES({
            pathVariables: { id: String(targetCarId) },
          });
        }

        const response = await api.post(url, newCarExpense);
        responseCarExpense = response.data;
      }

      setIsLoading(false);
      closeModal(responseCarExpense);
    } catch (error: any) {
      setIsLoading(false);
      // eslint-disable-next-line no-console
      console.error("Erro ao salvar despesa:", error);

      const errorMessage =
        error?.response?.data?.message || error?.message || TEXT.saveFailed;
      showErrorAlert(errorMessage);
    }
  };

  const onDelete = async () => {
    if (!formInitial.id) return;

    setIsLoading(true);
    try {
      await api.delete(
        endpoints.CAR_EXPENSES_EDIT({
          pathVariables: { id: formInitial.id },
        })
      );
      setIsLoading(false);
      closeModal({ id: formInitial.id, delete: true });
    } catch (error: any) {
      setIsLoading(false);
      // eslint-disable-next-line no-console
      console.error("Erro ao deletar despesa:", error);

      const errorMessage =
        error?.response?.data?.message || error?.message || TEXT.deleteFailed;
      showErrorAlert(errorMessage);
    }
  };

  const handleCarSelect = (car: CarModel) => {
    setSelectedCar(car);
  };

  const handleReplicateToggle = (value: boolean) => {
    setReplicateForAllCars(value);
    if (value) setSelectedCar(null);
  };

  const handleClose = (response?: CarExpenseModel) => {
    if (isLoading) return;
    closeModal(response);
  };

  const titleText = formInitial.id
    ? `${TEXT.edit} ${TEXT.carExpense}`
    : `${TEXT.newCarExpense} ${TEXT.carExpense}`;

  const confirmDeleteMessage =
    (TEXT as any).confirmDeleteExpense ||
    `Deseja excluir esta ${String(TEXT.carExpense || "despesa").toLowerCase()}?`;

  const selectVehicleText =
    (TEXT as any).selectVehicle || `${TEXT.select} ${TEXT.car}`;

  const changeVehicleText = (TEXT as any).changeVehicle || "Trocar veiculo";

  return (
    <IonPage id="car-expense-add-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonButton
              fill="clear"
              className="app-outline-btn"
              onClick={() => handleClose()}
              disabled={isLoading}
            >
              {TEXT.cancel}
            </IonButton>
          </IonButtons>

          <IonTitle>{titleText}</IonTitle>

          <IonButtons slot="end">
            <IonButton
              className="app-primary-btn"
              disabled={isLoading}
              onClick={handleSubmit(onSubmit)}
            >
              {TEXT.save}
            </IonButton>
          </IonButtons>

          {isLoading && <IonProgressBar type="indeterminate" />}
        </IonToolbar>
      </IonHeader>

      <IonContent className="car-expense-add-content">
        <div className="app-shell app-shell--compact car-expense-add-shell">
          <section className="app-section">
            <div className="car-expense-add-section-head">
              <h2 className="app-section-title">{titleText}</h2>
              <p className="app-section-subtitle">
                Padronize data, descricao e valor da despesa com o mesmo layout
                dos demais lancamentos.
              </p>
            </div>

            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon">
                  <IonIcon icon={receiptOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    Dados da despesa
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Informe os campos principais e, se necessario, replique para
                    toda a frota.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <form
                  className="app-form-grid car-expense-add-form"
                  onSubmit={(e) => e.preventDefault()}
                >
                  <div className="car-expense-add-field">
                    <FormDate
                      id="date-car-expense-add"
                      initialValue={watch("date")}
                      label={TEXT.date}
                      presentation="date"
                      formCallBack={(value: string) => {
                        setValue("date", value, {
                          shouldValidate: true,
                          shouldDirty: true,
                          shouldTouch: true,
                        });
                      }}
                      error={errors.date?.message as any}
                      required
                    />
                  </div>

                  <div className="car-expense-add-field">
                    <FormInput
                      label={TEXT.carExpenseName}
                      errorsObj={errors}
                      errorName="name"
                      initialValue={watch("name")}
                      maxlength={50}
                      changeCallback={(value: string) => {
                        setValue("name", value, {
                          shouldValidate: true,
                          shouldDirty: true,
                          shouldTouch: true,
                        });
                      }}
                      required
                    />
                  </div>

                  <div className="car-expense-add-field">
                    <FormCurrency
                      label={TEXT.value}
                      errorsObj={errors}
                      errorName="cost"
                      initialValue={watch("cost")}
                      maxlength={15}
                      changeCallback={(value: number) => {
                        setValue("cost", value, {
                          shouldValidate: true,
                          shouldDirty: true,
                          shouldTouch: true,
                        });
                      }}
                      required
                    />
                  </div>

                  {!formInitial.id && (
                    <div className="car-expense-add-field">
                      <FormToggle
                        label={TEXT.replicateExpense}
                        initialValue={replicateForAllCars}
                        changeCallback={handleReplicateToggle}
                        disabled={!!carId}
                      />
                    </div>
                  )}
                </form>
              </IonCardContent>
            </IonCard>

            {!formInitial.id && !replicateForAllCars && (
              <>
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
                          Vincule a despesa a um veiculo especifico.
                        </IonCardSubtitle>
                      </div>
                    </IonCardHeader>
                    <IonCardContent>
                      {fetchError && (
                        <div className="app-inline-alert app-inline-alert--danger car-expense-add-alert">
                          {fetchError}
                        </div>
                      )}

                      <CarSelector onSelect={handleCarSelect} />
                    </IonCardContent>
                  </IonCard>
                )}

                {(selectedCar || carId) && (
                  <IonCard className="app-panel-card app-panel-card--soft">
                    <IonCardHeader className="app-panel-header">
                      <div className="app-soft-icon">
                        <IonIcon icon={carSportOutline} />
                      </div>
                      <div className="app-panel-header__content">
                        <IonCardTitle className="app-panel-title">
                          {selectedCar?.name || "Veiculo selecionado"}
                        </IonCardTitle>
                        <IonCardSubtitle className="app-panel-subtitle">
                          {selectedCar?.plate || carId || "ID do veiculo"}
                        </IonCardSubtitle>
                      </div>
                    </IonCardHeader>
                    {!carId && (
                      <IonCardContent>
                        <div className="car-expense-add-selected-car">
                          <IonButton
                            size="small"
                            fill="clear"
                            className="app-outline-btn"
                            onClick={() => setSelectedCar(null)}
                          >
                            {changeVehicleText}
                          </IonButton>
                        </div>
                      </IonCardContent>
                    )}
                  </IonCard>
                )}
              </>
            )}

            {formInitial.id && (
              <div className="car-expense-add-delete">
                <FormDeleteButton
                  label={`${TEXT.delete} ${String(
                    TEXT.carExpense
                  ).toLowerCase()}`}
                  message={confirmDeleteMessage}
                  callBackFunc={onDelete}
                  disabled={isLoading}
                />
              </div>
            )}
          </section>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default CarExpenseAdd;
