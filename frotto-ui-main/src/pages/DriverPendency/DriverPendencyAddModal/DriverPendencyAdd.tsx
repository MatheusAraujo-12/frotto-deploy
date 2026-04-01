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
import { receiptOutline } from "ionicons/icons";
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
import FormInput from "../../../components/Form/FormInput";
import FormInputArea from "../../../components/Form/FormInputArea";
import { currencyFormat } from "../../../services/currencyFormat";
import "./DriverPendencyAdd.css";

interface DriverPendencyAddModalProps {
  closeModal: (response?: DriverPendencyModel) => void;
  initialValues?: DriverPendencyModel;
  driverCarId: string;
}

const DriverPendencyAdd: React.FC<DriverPendencyAddModalProps> = ({
  closeModal,
  initialValues,
  driverCarId,
}) => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);

  const formInitial = initialDriverPendencyValues(initialValues || {});
  const pageTitle = formInitial.id
    ? TEXT.driverPendency
    : `${TEXT.add} ${TEXT.driverPendency}`;

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

  const costValue = Number(watch("cost") || 0);
  const paidAmountValue = Math.max(
    0,
    Math.min(Number(watch("paidAmount") || 0), costValue)
  );
  const remainingAmountValue = Math.max(costValue - paidAmountValue, 0);
  const statusLabel =
    remainingAmountValue === 0
      ? TEXT.debtPaid
      : paidAmountValue > 0
      ? TEXT.debtPartiallyPaid
      : TEXT.debtOpen;

  const onSubmit = async (newDriverPendency: DriverPendencyModel) => {
    setisLoading(true);
    try {
      const normalizedPaidAmount = Math.max(
        0,
        Math.min(Number(newDriverPendency.paidAmount || 0), Number(newDriverPendency.cost || 0))
      );
      const payload: DriverPendencyModel = {
        ...newDriverPendency,
        paidAmount: normalizedPaidAmount,
        remainingAmount: Math.max(Number(newDriverPendency.cost || 0) - normalizedPaidAmount, 0),
        paymentMethod:
          normalizedPaidAmount > 0 ? `${newDriverPendency.paymentMethod || ""}`.trim() : "",
      };
      let responseDriverPendency: DriverPendencyModel;
      if (payload.id) {
        const urlPatch = endpoints.DRIVER_PENDENCIES_EDIT({
          pathVariables: {
            id: payload.id,
          },
        });
        const response = await api.put(urlPatch, payload);
        responseDriverPendency = response.data;
      } else {
        const urlPost = endpoints.DRIVER_PENDENCIES({
          pathVariables: {
            id: driverCarId,
          },
        });
        const response = await api.post(urlPost, payload);
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
      closeModal({ id: formInitial.id, delete: true });
    } catch (e) {
      setisLoading(false);
      showErrorAlert(TEXT.deleteFailed);
    }
  };

  const handleReversePayment = () => {
    setValue("paidAmount", 0);
    setValue("paymentMethod", "");
  };

  return (
    <IonPage id="driver-pendency-add-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonButton
              fill="clear"
              className="app-outline-btn driver-pendency-add-cancel-btn"
              onClick={() => {
                closeModal();
              }}
            >
              {TEXT.cancel}
            </IonButton>
          </IonButtons>
          <IonTitle>{pageTitle}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              className="app-primary-btn driver-pendency-add-save-btn"
              disabled={isLoading}
              onClick={handleSubmit(onSubmit)}
            >
              {TEXT.save}
            </IonButton>
          </IonButtons>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="app-shell app-shell--compact driver-pendency-add-shell">
          <section className="app-section">
            <div className="driver-pendency-add-section-head">
              <h2 className="app-section-title">{pageTitle}</h2>
              <p className="app-section-subtitle">
                Preencha os dados da pendência em um fluxo direto e sem ruído
                visual.
              </p>
            </div>

            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon">
                  <IonIcon icon={receiptOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    Dados da pendência
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Informe data, descrição, valor e observações.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <form className="app-form-grid driver-pendency-add-form">
                  <FormDate
                    id="date-pendency-add"
                    initialValue={watch("date")?.toString()}
                    label={TEXT.date}
                    presentation="date"
                    error={errors?.date?.message?.toString()}
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
                  <FormInputArea
                    label={TEXT.note}
                    errorsObj={errors}
                    errorName="note"
                    initialValue={watch("note") || ""}
                    maxlength={255}
                    changeCallback={(value: string) => {
                      setValue("note", value);
                    }}
                  />
                </form>
              </IonCardContent>
            </IonCard>

            {formInitial.id && (
              <IonCard className="app-panel-card">
                <IonCardHeader className="app-panel-header">
                  <div className="app-panel-header__content">
                    <IonCardTitle className="app-panel-title">
                      Ajuste do pagamento
                    </IonCardTitle>
                    <IonCardSubtitle className="app-panel-subtitle">
                      Corrija o valor pago ou reverta a quitação quando necessário.
                    </IonCardSubtitle>
                  </div>
                </IonCardHeader>
                <IonCardContent>
                  <div className="app-soft-box app-soft-box--warning driver-pendency-add-lock-note">
                    <strong>Status atual:</strong> {statusLabel}
                    <br />
                    <strong>{TEXT.pendingAmount}:</strong> {currencyFormat(remainingAmountValue)}
                  </div>

                  <form className="app-form-grid driver-pendency-add-form">
                    <FormCurrency
                      label="Valor pago"
                      errorsObj={errors}
                      errorName="paidAmount"
                      initialValue={paidAmountValue}
                      maxlength={15}
                      changeCallback={(value: number) => {
                        setValue("paidAmount", value);
                      }}
                    />
                    <FormInput
                      label={TEXT.paymentMethod}
                      errorsObj={errors}
                      errorName="paymentMethod"
                      initialValue={watch("paymentMethod") || ""}
                      maxlength={60}
                      changeCallback={(value: string) => {
                        setValue("paymentMethod", value || "");
                      }}
                    />
                  </form>

                  {paidAmountValue > 0 && (
                    <IonButton
                      className="app-semantic-btn app-semantic--warning ion-margin-top"
                      fill="outline"
                      onClick={handleReversePayment}
                    >
                      Reverter pagamento
                    </IonButton>
                  )}
                </IonCardContent>
              </IonCard>
            )}

            {formInitial.id && (
              <div className="driver-pendency-add-delete">
                <FormDeleteButton
                  label={`${TEXT.delete} ${TEXT.driverPendency}`}
                  message={TEXT.driverPendency}
                  callBackFunc={onDelete}
                />
              </div>
            )}
          </section>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default DriverPendencyAdd;
