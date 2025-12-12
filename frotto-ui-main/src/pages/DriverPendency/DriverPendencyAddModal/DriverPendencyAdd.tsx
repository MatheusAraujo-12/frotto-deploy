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

import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import { DriverPendencyModel } from "../../../constants/CarModels";
import {
  driverPendencyAddValidationSchema,
  initialDriverPendencyValues,
} from "./driverPendencyAddValidationSchema";
import FormSelectFilterAdd from "../../../components/Form/FormSelectFilterAdd";
import { DRIVER_PENDENCIES } from "../../../constants/selectOptions";
import { DRIVER_PENDENCIES_KEY } from "../../../services/localStorage/localstorage";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import FormCurrency from "../../../components/Form/FormCurrency";
import FormDate from "../../../components/Form/FormDate";

interface DriverPendencyAddModalProps {
  closeModal: Function;
  initialValues?: DriverPendencyModel;
  driverCarId: String;
}

const DriverPendencyAdd: React.FC<DriverPendencyAddModalProps> = ({
  closeModal,
  initialValues,
  driverCarId,
}) => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);

  const formInitial = initialDriverPendencyValues(initialValues || {});

  const {
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(driverPendencyAddValidationSchema),
    defaultValues: formInitial,
  });

  const onSubmit = async (newDriverPendency: DriverPendencyModel) => {
    setisLoading(true);
    try {
      let responseDriverPendency: DriverPendencyModel;
      if (newDriverPendency.id) {
        const urlPatch = endpoints.DRIVER_PENDENCIES_EDIT({
          pathVariables: {
            id: newDriverPendency.id,
          },
        });
        const response = await api.put(urlPatch, newDriverPendency);
        responseDriverPendency = response.data;
      } else {
        const urlPost = endpoints.DRIVER_PENDENCIES({
          pathVariables: {
            id: driverCarId,
          },
        });
        const response = await api.post(urlPost, newDriverPendency);
        responseDriverPendency = response.data;
      }
      setisLoading(false);
      closeModal(responseDriverPendency);
    } catch (e: any) {
      setisLoading(false);
      showErrorAlert(TEXT.saveFailed);
    }
  };

  const onDelete = async () => {
    setisLoading(true);
    try {
      await api.delete(
        endpoints.DRIVER_PENDENCIES_EDIT({
          pathVariables: { id: formInitial.id },
        })
      );
      setisLoading(false);
      closeModal({});
    } catch (e) {
      setisLoading(false);
      showErrorAlert(TEXT.deleteFailed);
    }
  };

  return (
    <IonPage id="driver-pendency-add-page">
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
          <IonTitle>{TEXT.driverPendency}</IonTitle>
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
            id="date-pendency-add"
            initialValue={watch("date").toString()}
            label={TEXT.date}
            presentation="date"
            formCallBack={(value: string) => {
              setValue("date", value);
            }}
          />
          <FormSelectFilterAdd
            label={TEXT.driverPendency}
            errorsObj={errors}
            errorName="name"
            formCallBack={(value: string) => {
              setValue("name", value);
            }}
            initialValue={watch("name")}
            options={DRIVER_PENDENCIES}
            storageToken={DRIVER_PENDENCIES_KEY}
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
        </form>
        {formInitial.id && (
          <FormDeleteButton
            label={`${TEXT.delete} ${TEXT.driverPendency}`}
            message={TEXT.driverPendency}
            callBackFunc={onDelete}
          />
        )}
      </IonContent>
    </IonPage>
  );
};

export default DriverPendencyAdd;
