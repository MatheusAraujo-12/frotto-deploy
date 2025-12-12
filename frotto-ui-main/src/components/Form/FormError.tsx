import { IonText } from "@ionic/react";

export interface ErrorProps {
  message: string;
}

const FormError: React.FC<ErrorProps> = ({ message }) => {
  return (
    <IonText color="danger" className="ion-padding-start">
      {message}
    </IonText>
  );
};

export default FormError;
