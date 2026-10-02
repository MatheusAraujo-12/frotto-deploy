import {
  IonButton,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
} from "@ionic/react";
import { useEffect, useMemo, useState } from "react";
import { DriverPendencyModel } from "../../constants/CarModels";
import { TEXT } from "../../constants/texts";
import FormCurrency from "../../components/Form/FormCurrency";
import FrottoCard from "../../components/UI/FrottoCard";
import FrottoModal from "../../components/UI/FrottoModal";
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
        showErrorAlert("O valor parcial não pode ser maior que o saldo restante.");
        return;
      }
    }

    await onSubmit({
      mode: paymentMode,
      amount: paymentMode === "partial" ? partialAmount : undefined,
    });
  };

  return (
    <FrottoModal
      pageId="driver-pendency-payment-page"
      title={TEXT.settleDebt}
      onCancel={closeModal}
      cancelVariant="danger"
      primaryVariant="save"
      cancelDisabled={isLoading}
      primaryLabel={TEXT.confirm}
      onPrimaryAction={() => void handleSubmit()}
      primaryDisabled={isLoading}
      isLoading={isLoading}
    >
        <div className="app-form-page__body">
          <div className="app-form-page__panel">
            <h3 className="app-form-page__title">Como deseja registrar esta quitação?</h3>
            <div className="driver-pendency-payment-summary">
              <p className="driver-pendency-payment-summary__title">
                <strong>{pendency?.name || TEXT.driverPendency}</strong>
              </p>
              <p>Total da dívida: {currencyFormat(pendency?.cost || 0)}</p>
              <p>
                Já pago:{" "}
                <span className={paidAmount > 0 ? "app-text-financial-positive" : undefined}>
                  {currencyFormat(paidAmount)}
                </span>
              </p>
              <p>
                Saldo restante:{" "}
                <span
                  className={remainingAmount > 0 ? "app-text-financial-negative" : undefined}
                >
                  {currencyFormat(remainingAmount)}
                </span>
              </p>
            </div>

            <FrottoCard className="driver-pendency-payment-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">Forma de quitação</IonCardTitle>
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
                    O sistema vai quitar todo o saldo restante desta dívida.
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
            </FrottoCard>
          </div>
        </div>
    </FrottoModal>
  );
};

export default DriverPendencyPaymentModal;
