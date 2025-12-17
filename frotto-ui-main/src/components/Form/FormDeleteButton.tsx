import { IonButton, useIonActionSheet } from "@ionic/react";
import { TEXT } from "../../constants/texts";

export interface DeleteButtoProps {
  label: string;
  message: string;
  callBackFunc: () => void | Promise<void>;
  disabled?: boolean; // ✅ novo
}

const FormDeleteButton: React.FC<DeleteButtoProps> = ({
  label,
  message,
  callBackFunc,
  disabled = false,
}) => {
  const [present] = useIonActionSheet();

  return (
    <IonButton
      color="danger"
      expand="block"
      class="ion-margin-top"
      disabled={disabled}
      onClick={() =>
        present({
          header: TEXT.deleteDefault + message + "?",
          buttons: [
            {
              text: TEXT.delete,
              role: "destructive",
              data: { action: "delete" },
            },
            {
              text: TEXT.cancel,
              role: "cancel",
              data: { action: "cancel" },
            },
          ],
          onDidDismiss: ({ detail }) => {
            if (detail.role === "destructive") {
              // suporta async também
              void Promise.resolve(callBackFunc());
            }
          },
        })
      }
    >
      {label}
    </IonButton>
  );
};

export default FormDeleteButton;
