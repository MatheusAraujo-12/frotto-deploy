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

interface CarExpenseAddModalProps {
  closeModal: (response?: CarExpenseModel) => void;
  initialValues?: CarExpenseModel;
  carId?: string; // opcional
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

  // Buscar informações do carro se carId for fornecido
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
          setFetchError("Não foi possível carregar informações do veículo");
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
    formState: { errors, isValid },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(carExpenseAddValidationSchema),
    defaultValues: formInitial,
    mode: "onTouched",
  });

  const onSubmit = async (newCarExpense: CarExpenseModel) => {
    if (!isValid) {
      showErrorAlert("Preencha todos os campos obrigatórios corretamente");
      return;
    }

    setIsLoading(true);
    try {
      let responseCarExpense: CarExpenseModel;

      // EDITAR
      if (newCarExpense.id) {
        const url = endpoints.CAR_EXPENSES_EDIT({
          pathVariables: { id: newCarExpense.id },
        });
        const response = await api.put(url, newCarExpense);
        responseCarExpense = response.data;
      }
      // CRIAR NOVO
      else {
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
      closeModal({} as CarExpenseModel);
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
    `Deseja excluir esta ${String(TEXT.carExpense).toLowerCase()}?`;

  const selectVehicleText =
    (TEXT as any).selectVehicle || `${TEXT.select} ${TEXT.car}`;

  const changeVehicleText =
    (TEXT as any).changeVehicle || "Trocar veículo";

  return (
    <IonPage id="car-expense-add-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonButton
              color="medium"
              onClick={() => handleClose()}
              disabled={isLoading}
            >
              {TEXT.cancel}
            </IonButton>
          </IonButtons>

          <IonTitle>{titleText}</IonTitle>

          <IonButtons slot="end">
            <IonButton
              disabled={isLoading}
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
          <FormDate
            id="date-car-expense-add"
            initialValue={watch("date")}
            label={TEXT.date}
            presentation="date"
            formCallBack={(value: string) => {
              setValue("date", value, { shouldValidate: true });
            }}
            error={errors.date?.message as any}
            required
          />

          <FormInput
            label={TEXT.carExpenseName}
            errorsObj={errors}
            errorName="name"
            initialValue={watch("name")}
            maxlength={50}
            changeCallback={(value: string) => {
              setValue("name", value, { shouldValidate: true });
            }}
            required
          />

          <FormCurrency
            label={TEXT.value}
            errorsObj={errors}
            errorName="cost"
            initialValue={watch("cost")}
            maxlength={15}
            changeCallback={(value: number) => {
              setValue("cost", value, { shouldValidate: true });
            }}
            required
          />

          {/* Mostrar seletor de carro apenas para novas despesas */}
          {!formInitial.id && (
            <>
              <FormToggle
                label={TEXT.replicateExpense}
                initialValue={replicateForAllCars}
                changeCallback={handleReplicateToggle}
                disabled={!!carId}
              />

              {!replicateForAllCars && (
                <div style={{ padding: 12 }}>
                  {fetchError && (
                    <div
                      style={{
                        color: "var(--ion-color-danger)",
                        marginBottom: 12,
                      }}
                    >
                      {fetchError}
                    </div>
                  )}

                  {!selectedCar && !carId ? (
                    <>
                      <h3 style={{ marginTop: 0, marginBottom: 12 }}>
                        {selectVehicleText}
                      </h3>
                      <CarSelector onSelect={handleCarSelect} />
                    </>
                  ) : (
                    <div>
                      <strong>{selectedCar?.name || "Veículo selecionado"}</strong>
                      <div
                        style={{
                          fontSize: 12,
                          color: "var(--ion-color-medium)",
                          marginBottom: 8,
                        }}
                      >
                        {selectedCar?.plate || carId || "ID do veículo"}
                      </div>

                      {!carId && (
                        <IonButton
                          size="small"
                          fill="outline"
                          onClick={() => setSelectedCar(null)}
                        >
                          {changeVehicleText}
                        </IonButton>
                      )}
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </form>

        {formInitial.id && (
          <div style={{ padding: 16 }}>
            <FormDeleteButton
              label={`${TEXT.delete} ${String(TEXT.carExpense).toLowerCase()}`}
              message={confirmDeleteMessage}
              callBackFunc={onDelete}
              disabled={isLoading}
            />
          </div>
        )}
      </IonContent>
    </IonPage>
  );
};

export default CarExpenseAdd;
