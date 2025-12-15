import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonPage,
  IonProgressBar,
  IonTitle,
  IonToolbar,
  useIonRouter,
} from "@ionic/react";
import { FieldValues, useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import { registerValidationSchema } from "./registerValidationSchema";
import { TEXT } from "../../constants/texts";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import FormInput from "../../components/Form/FormInput";

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
      <IonHeader translucent>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/" />
          </IonButtons>
          <IonTitle>{TEXT.appTitle}</IonTitle>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent className="auth-content" fullscreen>
        <div className="auth-grid">
          <section className="auth-card">
            <h2>{TEXT.doRegister}</h2>
            <p>Crie sua conta para acessar o painel completo do Frotto.</p>
            <form onSubmit={handleSubmit(onSubmit)}>
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
                <IonButton type="submit" expand="block" disabled={isLoading}>
                  {TEXT.registerAction}
                </IonButton>
              </div>
            </form>
          </section>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default Register;
