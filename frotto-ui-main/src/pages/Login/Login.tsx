import {
  IonButton,
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
  useIonViewDidEnter,
} from "@ionic/react";
import { logInOutline, personAddOutline } from "ionicons/icons";
import { FieldValues, useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import { loginValidationSchema } from "./loginValidationSchema";
import { TEXT } from "../../constants/texts";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { useState } from "react";
import { getToken, setToken } from "../../services/localStorage/localstorage";
import { useAlert } from "../../services/hooks/useAlert";
import FormInput from "../../components/Form/FormInput";
import "./AuthPages.css";

const Login: React.FC = () => {
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const publicUrl = process.env.PUBLIC_URL || "";
  const {
    watch,
    setValue,
    handleSubmit,
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(loginValidationSchema),
  });

  const onSubmit = async (data: FieldValues) => {
    setisLoading(true);
    try {
      const response = await api.post(endpoints.AUTH(), {
        username: data.email,
        password: data.password,
        rememberMe: true,
      });
      const token = "Bearer " + response.data["id_token"];
      setToken(token);
      history.push("/menu", "none", "replace");
      setisLoading(false);
    } catch (e) {
      setisLoading(false);
      showErrorAlert(TEXT.loginFailed);
    }
  };
  useIonViewDidEnter(() => {
    const token = getToken();
    if (token) {
      history.push("/menu", "none", "replace");
    }
  });

  return (
    <IonPage id="login-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonTitle>{TEXT.appTitle}</IonTitle>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent className="auth-content">
        <div className="app-shell app-shell--compact auth-shell">
          <IonCard className="app-panel-card auth-card">
            <IonCardHeader className="app-panel-header auth-card__header">
              <div className="auth-logo">
                <img
                  src={`${publicUrl}/assets/icon/icon.png`}
                  alt={TEXT.appTitle}
                />
              </div>
              <div className="app-panel-header__content">
                <IonCardTitle className="app-panel-title">
                  {TEXT.login}
                </IonCardTitle>
                <IonCardSubtitle className="app-panel-subtitle">
                  Acesse para acompanhar sua frota na Frotto.
                </IonCardSubtitle>
              </div>
            </IonCardHeader>
            <IonCardContent>
              <form className="app-form-grid auth-form" onSubmit={handleSubmit(onSubmit)}>
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
                    <IonIcon icon={logInOutline} slot="start" />
                    {TEXT.login}
                  </IonButton>
                  <IonButton
                    className="app-outline-btn"
                    expand="block"
                    fill="clear"
                    routerLink="/cadastro"
                  >
                    <IonIcon icon={personAddOutline} slot="start" />
                    {TEXT.doRegister}
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

export default Login;
