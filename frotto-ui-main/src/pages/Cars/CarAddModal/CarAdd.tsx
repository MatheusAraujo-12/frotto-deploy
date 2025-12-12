import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonPage,
  IonProgressBar,
  IonTitle,
  IonToolbar,
  useIonRouter,
} from "@ionic/react";
import { TEXT } from "../../../constants/texts";
import { useState } from "react";
import { useAlert } from "../../../services/hooks/useAlert";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import {
  carAddValidationSchema,
  initialCarValues,
} from "./carAddValidationSchema";
import FormDate from "../../../components/Form/FormDate";
import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import { CarModel } from "../../../constants/CarModels";
import { COLORS } from "../../../constants/selectOptions";
import FormInput from "../../../components/Form/FormInput";
import FormSelect from "../../../components/Form/FormSelect";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import FormCurrency from "../../../components/Form/FormCurrency";

interface CarAddModalProps {
  closeModal: Function;
  initialValues?: CarModel;
}

const CarAdd: React.FC<CarAddModalProps> = ({ closeModal, initialValues }) => {
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const formInitial = initialCarValues(initialValues || {});
  const {
    setValue,
    watch,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(carAddValidationSchema),
    defaultValues: formInitial,
  });

  const onSubmit = async (newCar: CarModel) => {
    setisLoading(true);
    try {
      let responseCar: CarModel;
      if (newCar.id) {
        const response = await api.patch(
          endpoints.CAR({
            pathVariables: {
              id: newCar.id,
            },
          }),
          {
            ...newCar,
          }
        );
        responseCar = response.data;
      } else {
        const response = await api.post(endpoints.CARS(), {
          ...newCar,
          active: true,
        });
        responseCar = response.data;
      }
      setisLoading(false);
      closeModal(responseCar);
    } catch (e) {
      setisLoading(false);
      showErrorAlert(TEXT.saveFailed);
    }
  };

  const onDelete = async () => {
    setisLoading(true);
    try {
      await api.delete(
        endpoints.CAR({ pathVariables: { id: formInitial.id } })
      );
      history.push("/", "none", "replace");
      setisLoading(false);
    } catch (e) {
      setisLoading(false);
      showErrorAlert(TEXT.deleteFailed);
    }
  };

  return (
    <IonPage id="car-add-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonButton color="danger" onClick={() => closeModal()}>
              {TEXT.cancel}
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.addCar}</IonTitle>
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
          <FormInput
            label={TEXT.carModel}
            errorsObj={errors}
            errorName="name"
            initialValue={watch("name")}
            maxlength={50}
            changeCallback={(value: string) => {
              setValue("name", value);
            }}
            required
          />
          <FormInput
            label={TEXT.plate}
            errorsObj={errors}
            errorName="plate"
            initialValue={watch("plate")}
            maxlength={15}
            changeCallback={(value: string) => {
              setValue("plate", value);
            }}
            required
          />
          <FormInput
            label={TEXT.odometer}
            errorsObj={errors}
            errorName="odometer"
            initialValue={watch("odometer")}
            maxlength={15}
            type="number"
            changeCallback={(value: number) => {
              setValue("odometer", value);
            }}
            required
          />
          <FormInput
            label={TEXT.group}
            errorsObj={errors}
            errorName="group"
            initialValue={watch("group")}
            maxlength={20}
            changeCallback={(value: string) => {
              setValue("group", value);
            }}
            required
          />
          <FormSelect
            label={TEXT.color}
            options={COLORS}
            errorsObj={errors}
            errorName="color"
            initialValue={watch("color")}
            changeCallback={(value: string) => {
              setValue("color", value);
            }}
            required
          />
          <FormInput
            label={TEXT.administrationFee}
            errorsObj={errors}
            errorName="administrationFee"
            initialValue={watch("administrationFee")}
            type="number"
            changeCallback={(value: number) => {
              setValue("administrationFee", value);
            }}
            required
          />
          <FormCurrency
            label={TEXT.initialValue}
            errorsObj={errors}
            errorName="initialValue"
            initialValue={watch("initialValue")}
            maxlength={15}
            changeCallback={(value: number) => {
              setValue("initialValue", value);
            }}
          />
          <FormDate
            id="year-car-add"
            initialValue={watch("year").toString()}
            label={TEXT.year}
            presentation="year"
            formCallBack={(value: number) => {
              setValue("year", value);
            }}
          />
        </form>

        {formInitial.id && (
          <FormDeleteButton
            label={`${TEXT.delete} ${TEXT.car}`}
            message={TEXT.car}
            callBackFunc={onDelete}
          />
        )}
      </IonContent>
    </IonPage>
  );
};

export default CarAdd;
