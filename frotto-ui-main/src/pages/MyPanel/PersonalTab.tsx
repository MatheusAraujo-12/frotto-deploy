import {
  IonAvatar,
  IonButton,
  IonIcon,
  IonInput,
  IonItem,
} from "@ionic/react";
import { imageOutline } from "ionicons/icons";
import { maskCPF, maskPhone } from "../../services/profileFormat";
import { FormErrors, PersonalForm } from "./profilePanelUtils";
import FormInputLabel from "../../components/Form/FormInputLabel";
import FormError from "../../components/Form/FormError";
import { getFormErrorId } from "../../components/Form/FormItemWrapper";
import ItemNotFound from "../../components/List/ItemNotFound";

interface PersonalTabProps {
  form: PersonalForm;
  touched: Record<keyof PersonalForm, boolean>;
  errors: FormErrors<keyof PersonalForm>;
  hasData: boolean;
  avatarPreviewUrl: string;
  canRemoveAvatar: boolean;
  onTouch: (field: keyof PersonalForm) => void;
  onChange: (form: PersonalForm) => void;
  onQuickSave: () => void;
  onChangeAvatar: () => void;
  onRemoveAvatar: () => void;
}

const getInputValue = (event: any): string =>
  event?.detail?.value ?? event?.target?.value ?? event?.currentTarget?.value ?? "";

const PersonalTab: React.FC<PersonalTabProps> = ({
  form,
  touched,
  errors,
  hasData,
  avatarPreviewUrl,
  canRemoveAvatar,
  onTouch,
  onChange,
  onQuickSave,
  onChangeAvatar,
  onRemoveAvatar,
}) => {
  const fieldError = (field: keyof PersonalForm) => {
    const show = touched[field] && Boolean(errors[field]);
    const id = getFormErrorId(field);
    return {
      show,
      id,
      wrapClassName: show ? "app-form-field app-form-field--invalid" : "app-form-field",
      ariaInvalid: show ? ("true" as const) : undefined,
      ariaDescribedby: show ? id : undefined,
      node: show ? <FormError id={id} message={errors[field] as string} /> : null,
    };
  };

  const nameField = fieldError("personalName");
  const cpfField = fieldError("personalCpf");
  const birthField = fieldError("personalBirthDate");
  const emailField = fieldError("personalEmail");
  const phoneField = fieldError("personalPhone");

  return (
    <>
      {!hasData && (
        <ItemNotFound
          title="Nenhum dado pessoal salvo"
          description="Preencha os campos abaixo para começar."
          actionLabel="Salvar agora"
          onAction={onQuickSave}
        />
      )}

      <div className="app-avatar-block">
        <IonAvatar className="app-avatar-block__avatar">
          {avatarPreviewUrl ? (
            <img src={avatarPreviewUrl} alt="Avatar do usuário" />
          ) : (
            <div className="app-avatar-block__placeholder">
              <IonIcon icon={imageOutline} />
            </div>
          )}
        </IonAvatar>
        <div className="app-avatar-block__content">
          <h2>Foto de perfil</h2>
        </div>
      </div>

      <div className="app-actions-row">
        <IonButton fill="outline" className="app-outline-btn" onClick={onChangeAvatar}>
          Alterar foto
        </IonButton>
        <IonButton
          fill="clear"
          color="danger"
          disabled={!canRemoveAvatar}
          onClick={onRemoveAvatar}
        >
          Remover
        </IonButton>
      </div>

      <div className="app-form-grid">
        <div className={nameField.wrapClassName}>
          <IonItem className="app-form-item">
            <FormInputLabel name="Nome" />
            <IonInput
              value={form.personalName}
              placeholder="Ex.: Matheus Silva"
              aria-invalid={nameField.ariaInvalid}
              aria-describedby={nameField.ariaDescribedby}
              onIonInput={(event: any) => {
                onTouch("personalName");
                onChange({ ...form, personalName: getInputValue(event) });
              }}
              onIonBlur={() => onTouch("personalName")}
            />
          </IonItem>
          {nameField.node}
        </div>

        <div className={cpfField.wrapClassName}>
          <IonItem className="app-form-item">
            <FormInputLabel name="CPF" />
            <IonInput
              value={form.personalCpf}
              placeholder="000.000.000-00"
              aria-invalid={cpfField.ariaInvalid}
              aria-describedby={cpfField.ariaDescribedby}
              onIonInput={(event: any) => {
                onTouch("personalCpf");
                onChange({ ...form, personalCpf: maskCPF(getInputValue(event)) });
              }}
              onIonBlur={() => onTouch("personalCpf")}
            />
          </IonItem>
          {cpfField.node}
        </div>

        <div className={birthField.wrapClassName}>
          <IonItem className="app-form-item">
            <FormInputLabel name="Data de nascimento" />
            <IonInput
              type="date"
              value={form.personalBirthDate}
              aria-invalid={birthField.ariaInvalid}
              aria-describedby={birthField.ariaDescribedby}
              onIonInput={(event: any) => {
                onTouch("personalBirthDate");
                onChange({ ...form, personalBirthDate: getInputValue(event) });
              }}
              onIonBlur={() => onTouch("personalBirthDate")}
            />
          </IonItem>
          {birthField.node}
        </div>

        <div className={emailField.wrapClassName}>
          <IonItem className="app-form-item">
            <FormInputLabel name="E-mail" />
            <IonInput
              type="email"
              value={form.personalEmail}
              placeholder="voce@email.com"
              aria-invalid={emailField.ariaInvalid}
              aria-describedby={emailField.ariaDescribedby}
              onIonInput={(event: any) => {
                onTouch("personalEmail");
                onChange({ ...form, personalEmail: getInputValue(event) });
              }}
              onIonBlur={() => onTouch("personalEmail")}
            />
          </IonItem>
          {emailField.node}
        </div>

        <div className={phoneField.wrapClassName}>
          <IonItem className="app-form-item">
            <FormInputLabel name="Telefone" />
            <IonInput
              value={form.personalPhone}
              placeholder="(11) 99999-9999"
              aria-invalid={phoneField.ariaInvalid}
              aria-describedby={phoneField.ariaDescribedby}
              onIonInput={(event: any) => {
                onTouch("personalPhone");
                onChange({ ...form, personalPhone: maskPhone(getInputValue(event)) });
              }}
              onIonBlur={() => onTouch("personalPhone")}
            />
          </IonItem>
          {phoneField.node}
        </div>
      </div>
    </>
  );
};

export default PersonalTab;
