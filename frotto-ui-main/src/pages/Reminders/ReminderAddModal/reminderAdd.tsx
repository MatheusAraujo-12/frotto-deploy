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
import { ReminderModel } from "../../../constants/CarModels";
import {
  reminderAddValidationSchema,
  initialReminderValues,
} from "./reminderAddValidationSchema";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import FormInputArea from "../../../components/Form/FormInputArea";

interface ReminderAddModalProps {
  closeModal: Function;
  initialValues?: ReminderModel;
  carId: String;
}

const ReminderAdd: React.FC<ReminderAddModalProps> = ({
  closeModal,
  initialValues,
  carId,
}) => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);

  const formInitial = initialReminderValues(initialValues || {});

  const {
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(reminderAddValidationSchema),
    defaultValues: formInitial,
  });

  const onSubmit = async (newReminder: ReminderModel) => {
    setisLoading(true);
    try {
      let responseReminder: ReminderModel;
      if (newReminder.id) {
        const urlPatch = endpoints.REMINDERS_EDIT({
          pathVariables: {
            id: newReminder.id,
          },
        });
        const response = await api.put(urlPatch, newReminder);
        responseReminder = response.data;
      } else {
        const urlPost = endpoints.REMINDERS({
          pathVariables: {
            id: carId,
          },
        });
        const response = await api.post(urlPost, newReminder);
        responseReminder = response.data;
      }
      setisLoading(false);
      closeModal(responseReminder);
    } catch (e: any) {
      setisLoading(false);
      showErrorAlert(TEXT.saveFailed);
    }
  };

  const onDelete = async () => {
    setisLoading(true);
    try {
      await api.delete(
        endpoints.REMINDERS_EDIT({ pathVariables: { id: formInitial.id } })
      );
      setisLoading(false);
      closeModal({});
    } catch (e) {
      setisLoading(false);
      showErrorAlert(TEXT.deleteFailed);
    }
  };

  return (
    <IonPage id="car-Reminder-add-page">
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
          <IonTitle>{TEXT.reminder}</IonTitle>
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
          <FormInputArea
            label={TEXT.reminder}
            errorsObj={errors}
            errorName="message"
            initialValue={watch("message")}
            maxlength={200}
            changeCallback={(value: string) => {
              setValue("message", value);
            }}
            required
          />
        </form>
        {formInitial.id && (
          <FormDeleteButton
            label={`${TEXT.delete} ${TEXT.reminder}`}
            message={TEXT.reminder}
            callBackFunc={onDelete}
          />
        )}
      </IonContent>
    </IonPage>
  );
};

export default ReminderAdd;
