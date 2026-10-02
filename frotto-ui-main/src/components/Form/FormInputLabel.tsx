import { IonLabel, IonText } from "@ionic/react";

interface InputLabelProps {
  name: string;
  header?: string;
  required?: boolean;
  position?: "stacked";
}

const FormInputLabel: React.FC<InputLabelProps> = ({
  required,
  name,
  header,
  position,
}) => {
  return (
    <IonLabel position={position}>
      {header !== undefined && (
        <h2>
          <strong>{header}</strong>
        </h2>
      )}
      {name}{" "}
      {required && (
        <IonText color="danger" aria-hidden="true">
          {" "}
          *
        </IonText>
      )}
    </IonLabel>
  );
};

export default FormInputLabel;
