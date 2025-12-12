import {
  IonButton,
  IonContent,
  IonHeader,
  IonPage,
  IonProgressBar,
  IonTitle,
  IonToolbar,
  useIonRouter,
  useIonViewDidEnter,
} from "@ionic/react";
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

const Login: React.FC = () => {
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
      api.defaults.headers.common["Authorization"] = token;
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
      <IonHeader translucent>
        <IonToolbar>
          <IonTitle>{TEXT.appTitle}</IonTitle>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent className="auth-content" fullscreen>
        <div className="auth-grid">
          <section className="auth-card">
            <h2>{TEXT.login}</h2>
            <p>Acesse para acompanhar sua frota com o novo visual da Loca+.</p>
            <form onSubmit={handleSubmit(onSubmit)}>
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
                <IonButton type="submit" expand="block" disabled={isLoading}>
                  {TEXT.login}
                </IonButton>
                <IonButton expand="block" fill="clear" routerLink="/cadastro">
                  {TEXT.doRegister}
                </IonButton>
              </div>
            </form>
          </section>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default Login;
