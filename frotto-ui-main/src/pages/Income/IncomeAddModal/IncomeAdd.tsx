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
      closeModal({} as IncomeModel);
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

  return (
    <IonPage id="car-income-add-page">
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
              {fetchError && (
                <div style={{ color: "var(--ion-color-danger)", marginBottom: 12, fontSize: 14 }}>
                  {fetchError}
                </div>
              )}

              <h3 style={{ marginTop: 0, marginBottom: 16, fontSize: 16, fontWeight: 600 }}>
                {`${TEXT.select} ${TEXT.car}`}
              </h3>

              <CarSelector onSelect={handleCarSelect} />
            </div>
          )}

          {(selectedCar || carId) && (
            <>
              <FormDate
                id="date-income-add"
                initialValue={incomeDate}
                label={TEXT.date}
                presentation="date"
                formCallBack={(value: string) => setValue("date", value)}
                required
              />

              <FormSelectFilterAdd
                label={TEXT.incomeName}
                errorsObj={errors}
                errorName="name"
                formCallBack={(value: string) => setValue("name", value)}
                initialValue={incomeName}
                options={INCOMES}
                storageToken={INCOME_KEY}
                required
              />

              <FormCurrency
                label={TEXT.value}
                errorsObj={errors}
                errorName="cost"
                initialValue={incomeValue}
                maxlength={15}
                changeCallback={(value: number) => setValue("cost", value)}
                required
              />
            </>
          )}
        </form>

        {formInitial.id && (
          <div style={{ padding: 16, marginTop: 24 }}>
            <FormDeleteButton
              label={`${TEXT.delete} ${String(TEXT.income).toLowerCase()}`}
              message={TEXT.income}
              callBackFunc={onDelete}
            />
          </div>
        )}
      </IonContent>
    </IonPage>
  );
};

export default IncomeAdd;
