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
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import { receiptOutline } from "ionicons/icons";
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
import { useCallback, useEffect } from "react";
import { useAlert } from "../../../services/hooks/useAlert";

interface ExpenseAddModalProps {
  closeModal: (response?: ExpenseModelActive) => void;
  initialValues: ExpenseModelActive;
}

const ExpenseAddModal: React.FC<ExpenseAddModalProps> = ({
  closeModal,
  initialValues,
}) => {
  const { showErrorAlert } = useAlert();
  const formInitial = initialExpenseValues(initialValues || {});

  const {
    watch,
    setValue,
    reset,
    handleSubmit,
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(expenseAddValidationSchema),
    defaultValues: formInitial,
  });

  const updateField = useCallback(
    (field: keyof ExpenseModelActive, value: any) => {
      setValue(field as any, value, {
        shouldValidate: true,
        shouldDirty: true,
        shouldTouch: true,
      });
    },
    [setValue]
  );

  useEffect(() => {
    reset(initialExpenseValues(initialValues || {}));
  }, [initialValues, reset]);

  const onSubmit = async (newExpense: ExpenseModelActive) => {
    closeModal(newExpense);
  };

  const onInvalid = () => {
    showErrorAlert(TEXT.formHasErrors);
  };

  return (
    <IonPage id="expense-add-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonButton
              fill="clear"
              color="danger"
              onClick={() => closeModal({ ...formInitial, delete: true })}
            >
              {TEXT.delete}
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.expense}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              className="app-primary-btn"
              onClick={handleSubmit(onSubmit, onInvalid)}
            >
              {TEXT.save}
            </IonButton>
          </IonButtons>
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="app-shell app-shell--compact">
          <section className="app-section">
            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon">
                  <IonIcon icon={receiptOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.expense}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Informe o produto, a quantidade e o valor total.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <form
                  className="app-form-grid"
                  onSubmit={(e) => e.preventDefault()}
                >
                  <FormSelectFilterAdd
                    label={TEXT.product}
                    errorsObj={errors}
                    errorName="name"
                    formCallBack={(value: string) => {
                      updateField("name", value);
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
                      updateField("ammount", value);
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
                      updateField("cost", value);
                    }}
                    required
                  />
                </form>
              </IonCardContent>
            </IonCard>
          </section>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default ExpenseAddModal;
