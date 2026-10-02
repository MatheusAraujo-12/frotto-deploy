import { IonButton, IonIcon } from "@ionic/react";
import { createOutline } from "ionicons/icons";

export const NOT_INFORMED = "Não informado";

export interface SummaryItem {
  label: string;
  value: string;
}

/** Resumo somente-leitura de uma seção (modo visualização). */
export const SummaryList: React.FC<{ title?: string; items: SummaryItem[] }> = ({ title, items }) => (
  <div className="cadastro-group">
    {title && <h3 className="cadastro-group__title">{title}</h3>}
    <dl className="cadastro-summary">
      {items.map((item) => (
        <div key={item.label} className="cadastro-summary__item">
          <dt>{item.label}</dt>
          <dd className={item.value.trim() ? undefined : "cadastro-summary__empty"}>
            {item.value.trim() || NOT_INFORMED}
          </dd>
        </div>
      ))}
    </dl>
  </div>
);

/** Ação de entrada no modo edição (secundária, depois do resumo). */
export const SectionViewActions: React.FC<{ label: string; disabled?: boolean; onEdit: () => void }> = ({
  label,
  disabled,
  onEdit,
}) => (
  <div className="cadastro-section-actions">
    <IonButton fill="outline" className="app-outline-btn" disabled={disabled} onClick={onEdit}>
      <IonIcon icon={createOutline} slot="start" />
      {label}
    </IonButton>
  </div>
);

/** Rodapé do modo edição: Cancelar (vermelho, secundário) + Salvar (primário), sempre depois dos campos. */
export const SectionEditActions: React.FC<{
  saveLabel?: string;
  isSaving: boolean;
  saveDisabled?: boolean;
  onCancel: () => void;
  onSave: () => void;
}> = ({ saveLabel = "Salvar", isSaving, saveDisabled, onCancel, onSave }) => (
  <div className="cadastro-section-actions cadastro-section-actions--edit">
    <IonButton fill="clear" className="app-cancel-btn" disabled={isSaving} onClick={onCancel}>
      Cancelar
    </IonButton>
    <IonButton className="app-save-btn" disabled={isSaving || saveDisabled} onClick={onSave}>
      {isSaving ? "Salvando..." : saveLabel}
    </IonButton>
  </div>
);

export const formatBirthDate = (value: string): string => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || "");
  return match ? `${match[3]}/${match[2]}/${match[1]}` : value || "";
};
