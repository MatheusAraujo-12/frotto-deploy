import { IonButton, useIonActionSheet } from "@ionic/react";
import { TEXT } from "../../constants/texts";

export interface DeleteButtoProps {
  label: string;
  message: string;
  callBackFunc: Function;
}

const FormDeleteButton: React.FC<DeleteButtoProps> = ({
  label,
  message,
  callBackFunc,
}) => {
  const [present] = useIonActionSheet();

  return (
    <>
      <IonButton
        color="danger"
        expand="block"
        class="ion-margin-top"
        onClick={() =>
          present({
            header: TEXT.deleteDefault + message + "?",
            buttons: [
              {
                text: TEXT.delete,
                role: "destructive",
                data: {
                  action: "delete",
                },
              },
              {
                text: TEXT.cancel,
                role: "cancel",
                data: {
                  action: "cancel",
                },
              },
            ],
            onDidDismiss: ({ detail }) => {
              if (detail.role === "destructive") {
                callBackFunc();
              }
            },
          })
        }
      >
        {label}
      </IonButton>
    </>
  );
};

export default FormDeleteButton;
