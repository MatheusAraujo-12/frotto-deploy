import { useIonToast } from "@ionic/react";

export const useAlert = () => {
  const [present] = useIonToast();

  const showErrorAlert = (message: string) => {
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
  };

  return { showErrorAlert };
};
