import { IonLabel, IonText } from "@ionic/react";

interface InputLabelProps {
  name: string;
  header?: string;
  required?: boolean;
}

const FormInputLabel: React.FC<InputLabelProps> = ({
  required,
  name,
  header,
}) => {
  return (
    <IonLabel>
      {header !== undefined && (
        <h2>
          <strong>{header}</strong>
        </h2>
      )}
      {name} {required && <IonText color="danger"> *</IonText>}
    </IonLabel>
  );
};

export default FormInputLabel;
