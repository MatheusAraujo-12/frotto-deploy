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
import { useState } from "react";
import { useAlert } from "../../../services/hooks/useAlert";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";

import FormDate from "../../../components/Form/FormDate";
import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import { CarExpenseModel } from "../../../constants/CarModels";
import FormInput from "../../../components/Form/FormInput";
import {
  carExpenseAddValidationSchema,
  initialCarExpenseValues,
} from "./carExpenseAddValidationSchema";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import FormCurrency from "../../../components/Form/FormCurrency";
import FormToggle from "../../../components/Form/FormToggle";

interface CarExpenseAddModalProps {
  closeModal: Function;
  initialValues?: CarExpenseModel;
  carId: String;
}

const CarExpenseAdd: React.FC<CarExpenseAddModalProps> = ({
  closeModal,
  initialValues,
  carId,
}) => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);

  const [replicateForAllCars, setReplicateForAllCars] = useState(false);

  const formInitial = initialCarExpenseValues(initialValues || {});

  const {
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(carExpenseAddValidationSchema),
    defaultValues: formInitial,
  });

  const onSubmit = async (newCarExpense: CarExpenseModel) => {
    setisLoading(true);
    try {
      let responseCarExpense: CarExpenseModel;
      if (newCarExpense.id) {
        const urlPatch = endpoints.CAR_EXPENSES_EDIT({
          pathVariables: {
            id: newCarExpense.id,
          },
        });
        const response = await api.put(urlPatch, newCarExpense);
        responseCarExpense = response.data;
      } else {
        let urlPost = endpoints.CAR_EXPENSES({
          pathVariables: {
            id: carId,
          },
        });
        if (replicateForAllCars) {
          urlPost = endpoints.CAR_EXPENSES_ALL();
        }
        const response = await api.post(urlPost, newCarExpense);
        responseCarExpense = response.data;
      }
      setisLoading(false);
      closeModal(responseCarExpense);
    } catch (e: any) {
      setisLoading(false);
      showErrorAlert(TEXT.saveFailed);
    }
  };

  const onDelete = async () => {
    setisLoading(true);
    try {
      await api.delete(
        endpoints.CAR_EXPENSES_EDIT({ pathVariables: { id: formInitial.id } })
      );
      setisLoading(false);
      closeModal({});
    } catch (e) {
      setisLoading(false);
      showErrorAlert(TEXT.deleteFailed);
    }
  };

  return (
    <IonPage id="car-carExpense-add-page">
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
          <IonTitle>{TEXT.carExpense}</IonTitle>
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
            id="date-carExpense-add"
            initialValue={watch("date").toString()}
            label={TEXT.date}
            presentation="date"
            formCallBack={(value: string) => {
              setValue("date", value);
            }}
          />
          <FormInput
            label={TEXT.carExpenseName}
            errorsObj={errors}
            errorName="name"
            initialValue={watch("name")}
            maxlength={50}
            changeCallback={(value: string) => {
              setValue("name", value);
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
              setValue("cost", value);
            }}
            required
          />
          {!formInitial.id && (
            <FormToggle
              label={TEXT.replicateExpense}
              initialValue={replicateForAllCars}
              changeCallback={(value: boolean) => {
                setReplicateForAllCars(value);
              }}
            />
          )}
        </form>
        {formInitial.id && (
          <FormDeleteButton
            label={`${TEXT.delete} ${TEXT.carExpense}`}
            message={TEXT.carExpense}
            callBackFunc={onDelete}
          />
        )}
      </IonContent>
    </IonPage>
  );
};

export default CarExpenseAdd;
