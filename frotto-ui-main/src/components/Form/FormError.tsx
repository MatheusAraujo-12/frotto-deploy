import { IonText } from "@ionic/react";

export interface ErrorProps {
  message: string;
  id?: string;
}

const FormError: React.FC<ErrorProps> = ({ message, id }) => {
  return (
    <IonText color="danger" className="app-form-error" id={id} role="alert">
      {message}
    </IonText>
  );
};

export default FormError;
