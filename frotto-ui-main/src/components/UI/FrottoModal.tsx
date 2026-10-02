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
import React from "react";
import { TEXT } from "../../constants/texts";

export interface FrottoModalProps {
  /** Forwarded to IonPage so existing page-scoped CSS (#car-add-page etc.) keeps working. */
  pageId: string;
  title: string;
  onCancel: () => void;
  cancelLabel?: string;
  cancelDisabled?: boolean;
  /**
   * "danger": Cancelar em vermelho (.app-cancel-btn), regra de UX de
   * Cancelar/Fechar. "default" mantém o visual anterior — opt-in explícito
   * porque há consumidores (Billing) que não devem mudar nesta etapa.
   */
  cancelVariant?: "default" | "danger";
  primaryLabel: string;
  primaryVariant?: "default" | "save";
  onPrimaryAction: () => void;
  primaryDisabled?: boolean;
  isLoading?: boolean;
  children: React.ReactNode;
}

/**
 * Shared header/shell for the app's full-page form modals (Cancelar / título
 * / ação primária, com barra de progresso opcional). Não decide o que a ação
 * primária faz (salvar, validar, chamar API) — isso continua no consumidor.
 * O corpo é 100% `children`: cada formulário mantém sua própria composição
 * interna (app-shell/app-section, app-form-page__body, etc.).
 */
const FrottoModal: React.FC<FrottoModalProps> = ({
  pageId,
  title,
  onCancel,
  cancelLabel = TEXT.cancel,
  cancelDisabled = false,
  cancelVariant = "default",
  primaryLabel,
  primaryVariant = "default",
  onPrimaryAction,
  primaryDisabled = false,
  isLoading = false,
  children,
}) => {
  return (
    <IonPage id={pageId}>
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonButton
              fill="clear"
              className={cancelVariant === "danger" ? "app-cancel-btn" : "app-outline-btn"}
              disabled={cancelDisabled}
              onClick={onCancel}
            >
              {cancelLabel}
            </IonButton>
          </IonButtons>
          <IonTitle>{title}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              className={primaryVariant === "save" ? "app-save-btn" : "app-primary-btn"}
              disabled={primaryDisabled}
              onClick={onPrimaryAction}
            >
              {primaryLabel}
            </IonButton>
          </IonButtons>
          {isLoading && <IonProgressBar type="indeterminate" aria-label="Carregando"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>{children}</IonContent>
    </IonPage>
  );
};

export default FrottoModal;
