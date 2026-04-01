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
  IonPage,
  IonProgressBar,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import { useEffect, useMemo, useState } from "react";
import { DriverPendencyModel } from "../../constants/CarModels";
import { TEXT } from "../../constants/texts";
import FormCurrency from "../../components/Form/FormCurrency";
import { currencyFormat } from "../../services/currencyFormat";
import { useAlert } from "../../services/hooks/useAlert";
import "./DriverPendencyPaymentModal.css";

export type DriverPendencyPaymentMode = "full" | "partial";

export interface DriverPendencyPaymentRequest {
  mode: DriverPendencyPaymentMode;
  amount?: number;
}

interface DriverPendencyPaymentModalProps {
  pendency?: DriverPendencyModel;
  isLoading?: boolean;
  closeModal: () => void;
  onSubmit: (payment: DriverPendencyPaymentRequest) => Promise<void>;
}

const resolveRemainingAmount = (pendency?: DriverPendencyModel): number => {
  if (!pendency) {
    return 0;
  }
  if (typeof pendency.remainingAmount === "number") {
    return Math.max(pendency.remainingAmount, 0);
  }
  if (typeof pendency.cost === "number" && typeof pendency.paidAmount === "number") {
    return Math.max(pendency.cost - pendency.paidAmount, 0);
  }
  if (typeof pendency.cost === "number") {
    return pendency.status === "PAID" ? 0 : pendency.cost;
  }
  return 0;
};

const resolvePaidAmount = (pendency?: DriverPendencyModel): number => {
  if (!pendency) {
    return 0;
  }
  if (typeof pendency.paidAmount === "number") {
    return Math.max(pendency.paidAmount, 0);
  }
  if (typeof pendency.cost === "number") {
    return Math.max(pendency.cost - resolveRemainingAmount(pendency), 0);
  }
  return 0;
};

const DriverPendencyPaymentModal: React.FC<DriverPendencyPaymentModalProps> = ({
  pendency,
  isLoading = false,
  closeModal,
  onSubmit,
}) => {
  const { showErrorAlert } = useAlert();
  const [paymentMode, setPaymentMode] = useState<DriverPendencyPaymentMode>("full");
  const [partialAmount, setPartialAmount] = useState(0);

  const remainingAmount = useMemo(() => resolveRemainingAmount(pendency), [pendency]);
  const paidAmount = useMemo(() => resolvePaidAmount(pendency), [pendency]);

  useEffect(() => {
    setPaymentMode("full");
    setPartialAmount(0);
  }, [pendency?.id]);

  const handleSubmit = async () => {
    if (!pendency?.id) {
      return;
    }

    if (paymentMode === "partial") {
      if (partialAmount <= 0) {
        showErrorAlert("Informe um valor parcial maior que zero.");
        return;
      }
      if (partialAmount > remainingAmount) {
        showErrorAlert("O valor parcial nao pode ser maior que o saldo restante.");
        return;
      }
    }

    await onSubmit({
      mode: paymentMode,
      amount: paymentMode === "partial" ? partialAmount : undefined,
    });
  };

  return (
    <IonPage id="driver-pendency-payment-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonButton
              fill="clear"
              className="app-outline-btn"
              disabled={isLoading}
              onClick={closeModal}
            >
              {TEXT.cancel}
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.settleDebt}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              className="app-primary-btn"
              disabled={isLoading}
              onClick={() => void handleSubmit()}
            >
              {TEXT.confirm}
            </IonButton>
          </IonButtons>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="app-form-page__body">
          <div className="app-form-page__panel">
            <h3 className="app-form-page__title">Como deseja registrar esta quitacao?</h3>
            <div className="driver-pendency-payment-summary">
              <p className="driver-pendency-payment-summary__title">
                <strong>{pendency?.name || TEXT.driverPendency}</strong>
              </p>
              <p>Total da divida: {currencyFormat(pendency?.cost || 0)}</p>
              <p>Ja pago: {currencyFormat(paidAmount)}</p>
              <p>Saldo restante: {currencyFormat(remainingAmount)}</p>
            </div>

            <IonCard className="app-panel-card driver-pendency-payment-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">Forma de quitacao</IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Escolha entre quitar todo o saldo ou registrar apenas parte dele.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <div className="driver-pendency-payment-options">
                  <IonButton
                    expand="block"
                    fill={paymentMode === "full" ? "solid" : "outline"}
                    className="app-semantic-btn app-semantic--success driver-pendency-payment-options__button"
                    onClick={() => setPaymentMode("full")}
                  >
                    Quitar total
                  </IonButton>
                  <IonButton
                    expand="block"
                    fill={paymentMode === "partial" ? "solid" : "outline"}
                    className="app-semantic-btn app-semantic--warning driver-pendency-payment-options__button"
                    onClick={() => setPaymentMode("partial")}
                  >
                    Quitar parcial
                  </IonButton>
                </div>

                {paymentMode === "full" && (
                  <div className="app-soft-box app-soft-box--warning driver-pendency-payment-note">
                    O sistema vai quitar todo o saldo restante desta divida.
                  </div>
                )}

                {paymentMode === "partial" && (
                  <>
                    <FormCurrency
                      label="Valor pago agora"
                      initialValue={partialAmount}
                      changeCallback={(value: number) => {
                        setPartialAmount(value);
                      }}
                      maxlength={15}
                    />
                    <p className="driver-pendency-payment-hint">
                      Informe um valor entre {currencyFormat(0)} e {currencyFormat(remainingAmount)}.
                    </p>
                  </>
                )}
              </IonCardContent>
            </IonCard>
          </div>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default DriverPendencyPaymentModal;
