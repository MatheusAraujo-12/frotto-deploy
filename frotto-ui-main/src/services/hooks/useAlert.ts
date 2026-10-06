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

  const showSuccessAlert = useCallback((message: string) => {
    present({ message, duration: 7000, position: "top", color: "success" });
  }, [present]);

  /** Completed operation the user must still know something about (not an error). */
  const showWarningAlert = useCallback(
    (message: string) => {
      present({
        message,
        duration: 10000,
        position: "top",
        color: "warning",
        buttons: [{ text: "X", role: "cancel" }],
      });
    },
    [present]
  );

  return { showErrorAlert, showSuccessAlert, showWarningAlert };
};
