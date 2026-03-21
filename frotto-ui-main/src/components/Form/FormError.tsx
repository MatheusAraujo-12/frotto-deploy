import { IonText } from "@ionic/react";

export interface ErrorProps {
  message: string;
}

const FormError: React.FC<ErrorProps> = ({ message }) => {
  return (
    <IonText color="danger" className="app-form-error">
      {message}
    </IonText>
  );
};

export default FormError;
