import { IonButton, IonIcon, useIonRouter } from "@ionic/react";
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
import AuthLayout from "../Login/AuthLayout";

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
    <AuthLayout
      pageId="register-page"
      title={TEXT.doRegister}
      description="Crie sua conta para acessar o painel completo do Frotto."
      isLoading={isLoading}
      back
    >
      <form className="app-form-grid auth-form" onSubmit={handleSubmit(onSubmit)} aria-busy={isLoading}>
        <FormInput
          label={TEXT.firstName}
          type="firstName"
          errorsObj={errors}
          errorName="firstName"
          autocomplete="name"
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
          autocomplete="new-password"
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
            <IonIcon icon={personAddOutline} slot="start" aria-hidden="true" />
            {TEXT.registerAction}
          </IonButton>
        </div>
      </form>
      <footer className="auth-footer">
        <span>Já tem uma conta?</span>
        <IonButton className="auth-link" fill="clear" routerLink="/">{TEXT.login}</IonButton>
      </footer>
    </AuthLayout>
  );
};

export default Register;
