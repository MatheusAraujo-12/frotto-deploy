import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonPage,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import { TEXT } from "../../../constants/texts";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import {
  expenseAddValidationSchema,
  ExpenseModelActive,
  initialExpenseValues,
} from "./expenseAddValidationSchema";
import { EXPENSES } from "../../../constants/selectOptions";
import FormInput from "../../../components/Form/FormInput";
import FormSelectFilterAdd from "../../../components/Form/FormSelectFilterAdd";
import { EXPENSES_KEY } from "../../../services/localStorage/localstorage";
import FormCurrency from "../../../components/Form/FormCurrency";

interface ExpenseAddModalProps {
  closeModal: Function;
  initialValues: ExpenseModelActive;
}

const ExpenseAddModal: React.FC<ExpenseAddModalProps> = ({
  closeModal,
  initialValues,
}) => {
  const formInitial = initialExpenseValues(initialValues || {});

  const {
    watch,
    setValue,
    handleSubmit,
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(expenseAddValidationSchema),
    defaultValues: formInitial,
  });

  const onSubmit = async (newExpense: ExpenseModelActive) => {
    closeModal(newExpense);
  };

  return (
    <IonPage id="expense-add-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonButton
              color="danger"
              onClick={() => closeModal({ ...formInitial, delete: true })}
            >
              {TEXT.delete}
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.expense}</IonTitle>
          <IonButtons slot="end">
            <IonButton strong={true} onClick={handleSubmit(onSubmit)}>
              {TEXT.save}
            </IonButton>
          </IonButtons>
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <form>
          <FormSelectFilterAdd
            label={TEXT.product}
            errorsObj={errors}
            errorName="name"
            formCallBack={(value: string) => {
              setValue("name", value);
            }}
            initialValue={watch("name")}
            options={EXPENSES}
            storageToken={EXPENSES_KEY}
            required
          />
          <FormInput
            label={TEXT.ammount}
            errorsObj={errors}
            errorName="ammount"
            initialValue={watch("ammount")}
            maxlength={15}
            type="number"
            changeCallback={(value: number) => {
              setValue("ammount", value);
            }}
            required
          />
          <FormCurrency
            label={TEXT.totalCost}
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
      </IonContent>
    </IonPage>
  );
};

export default ExpenseAddModal;
