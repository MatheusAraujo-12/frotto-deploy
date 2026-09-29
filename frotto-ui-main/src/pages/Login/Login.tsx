import { IonButton, IonIcon, useIonRouter, useIonViewDidEnter } from "@ionic/react";
import { logInOutline } from "ionicons/icons";
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
import AuthLayout from "./AuthLayout";

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
    <AuthLayout
      pageId="login-page"
      title={TEXT.login}
      description="Acesse para acompanhar sua frota na Frotto."
      isLoading={isLoading}
    >
      <form className="app-form-grid auth-form" onSubmit={handleSubmit(onSubmit)} aria-busy={isLoading}>
        <FormInput
          label={TEXT.email}
          type="email"
          errorsObj={errors}
          errorName="email"
          autocomplete="email"
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
          autocomplete="current-password"
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
            <IonIcon icon={logInOutline} slot="start" aria-hidden="true" />
            {TEXT.login}
          </IonButton>

        </div>
      </form>
      <footer className="auth-footer">
        <span>Ainda não tem uma conta?</span>
        <IonButton className="auth-link" fill="clear" routerLink="/cadastro">{TEXT.doRegister}</IonButton>
      </footer>
    </AuthLayout>
  );
};

export default Login;
