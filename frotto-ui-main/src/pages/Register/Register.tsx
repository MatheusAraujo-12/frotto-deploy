import {
  IonBackButton,
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
  useIonRouter,
} from "@ionic/react";
import { personAddOutline } from "ionicons/icons";
import { FieldValues, useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import { registerValidationSchema } from "./registerValidationSchema";
import { TEXT } from "../../constants/texts";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import FormInput from "../../components/Form/FormInput";
import "../Login/AuthPages.css";

const Register: React.FC = () => {
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const {
    watch,
    setValue,
    handleSubmit,
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(registerValidationSchema),
  });

  const onSubmit = async (data: FieldValues) => {
    setisLoading(true);
    try {
      await api.post(endpoints.REGISTER(), {
        firstName: data.firstName,
        email: data.email,
        password: data.password,
      });
      history.push("/", "back", "pop");
    } catch (e) {
      setisLoading(false);
      showErrorAlert(TEXT.registerFailed);
      return;
    }
  };

  return (
    <IonPage id="register-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonBackButton defaultHref="/" />
          </IonButtons>
          <IonTitle>{TEXT.doRegister}</IonTitle>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent className="auth-content">
        <div className="app-shell app-shell--compact auth-shell">
          <IonCard className="app-panel-card auth-card">
            <IonCardHeader className="app-panel-header">
              <div className="app-soft-icon">
                <IonIcon icon={personAddOutline} />
              </div>
              <div className="app-panel-header__content">
                <IonCardTitle className="app-panel-title">
                  {TEXT.doRegister}
                </IonCardTitle>
                <IonCardSubtitle className="app-panel-subtitle">
                  Crie sua conta para acessar o painel completo do Frotto.
                </IonCardSubtitle>
              </div>
            </IonCardHeader>
            <IonCardContent>
              <form className="app-form-grid auth-form" onSubmit={handleSubmit(onSubmit)}>
                <FormInput
                  label={TEXT.firstName}
                  type="firstName"
                  errorsObj={errors}
                  errorName="firstName"
                  initialValue={watch("firstName")}
                  maxlength={50}
                  changeCallback={(value: string) => {
                    setValue("firstName", value);
                  }}
                  required
                />
                <FormInput
                  label={TEXT.email}
                  type="email"
                  errorsObj={errors}
                  errorName="email"
                  initialValue={watch("email")}
                  maxlength={50}
                  changeCallback={(value: string) => {
                    setValue("email", value);
                  }}
                  required
                />
                <FormInput
                  label={TEXT.password}
                  type="password"
                  errorsObj={errors}
                  errorName="password"
                  initialValue={watch("password")}
                  maxlength={40}
                  changeCallback={(value: string) => {
                    setValue("password", value);
                  }}
                  required
                />

                <div className="auth-actions">
                  <IonButton
                    className="app-primary-btn"
                    type="submit"
                    expand="block"
                    disabled={isLoading}
                  >
                    <IonIcon icon={personAddOutline} slot="start" />
                    {TEXT.registerAction}
                  </IonButton>
                </div>
              </form>
            </IonCardContent>
          </IonCard>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default Register;
