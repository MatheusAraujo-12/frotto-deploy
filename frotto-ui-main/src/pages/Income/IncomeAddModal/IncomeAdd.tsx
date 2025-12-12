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
import { IncomeModel } from "../../../constants/CarModels";
import {
  incomeAddValidationSchema,
  initialIncomeValues,
} from "./incomeAddValidationSchema";
import FormSelectFilterAdd from "../../../components/Form/FormSelectFilterAdd";
import { INCOMES } from "../../../constants/selectOptions";
import { INCOME_KEY } from "../../../services/localStorage/localstorage";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import FormCurrency from "../../../components/Form/FormCurrency";

interface IncomeAddModalProps {
  closeModal: Function;
  initialValues?: IncomeModel;
  carId: String;
}

const IncomeAdd: React.FC<IncomeAddModalProps> = ({
  closeModal,
  initialValues,
  carId,
}) => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);

  const formInitial = initialIncomeValues(initialValues || {});

  const {
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(incomeAddValidationSchema),
    defaultValues: formInitial,
  });

  const onSubmit = async (newIncome: IncomeModel) => {
    setisLoading(true);
    try {
      let responseIncome: IncomeModel;
      if (newIncome.id) {
        const urlPatch = endpoints.INCOMES_EDIT({
          pathVariables: {
            id: newIncome.id,
          },
        });
        const response = await api.put(urlPatch, newIncome);
        responseIncome = response.data;
      } else {
        const urlPost = endpoints.INCOMES({
          pathVariables: {
            id: carId,
          },
        });
        const response = await api.post(urlPost, newIncome);
        responseIncome = response.data;
      }
      setisLoading(false);
      closeModal(responseIncome);
    } catch (e: any) {
      setisLoading(false);
      showErrorAlert(TEXT.saveFailed);
    }
  };

  const onDelete = async () => {
    setisLoading(true);
    try {
      await api.delete(
        endpoints.INCOMES_EDIT({ pathVariables: { id: formInitial.id } })
      );
      setisLoading(false);
      closeModal({});
    } catch (e) {
      setisLoading(false);
      showErrorAlert(TEXT.deleteFailed);
    }
  };

  return (
    <IonPage id="car-income-add-page">
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
          <IonTitle>{TEXT.income}</IonTitle>
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
            id="date-income-add"
            initialValue={watch("date").toString()}
            label={TEXT.date}
            presentation="date"
            formCallBack={(value: string) => {
              setValue("date", value);
            }}
          />
          <FormSelectFilterAdd
            label={TEXT.incomeName}
            errorsObj={errors}
            errorName="name"
            formCallBack={(value: string) => {
              setValue("name", value);
            }}
            initialValue={watch("name")}
            options={INCOMES}
            storageToken={INCOME_KEY}
            required
          />
          <FormCurrency
            label={TEXT.value}
            errorsObj={errors}
            errorName="cost"
            initialValue={watch('cost')}
            maxlength={15}
            changeCallback={(value: number) => {
              setValue("cost", value);
            }}
            required
          />
        </form>
        {formInitial.id && (
          <FormDeleteButton
            label={`${TEXT.delete} ${TEXT.income}`}
            message={TEXT.income}
            callBackFunc={onDelete}
          />
        )}
      </IonContent>
    </IonPage>
  );
};

export default IncomeAdd;
