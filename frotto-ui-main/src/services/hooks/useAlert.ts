import { useIonToast } from "@ionic/react";
import { useCallback } from "react";

export const useAlert = () => {
  const [present] = useIonToast();

  const showErrorAlert = useCallback(
    (message: string) => {
      present({
        message: message,
        duration: 4000,
        position: "top",
        color: "danger",
        buttons: [
          {
            text: "X",
            role: "cancel",
          },
        ],
      });
    },
    [present]
  );

  return { showErrorAlert };
};
