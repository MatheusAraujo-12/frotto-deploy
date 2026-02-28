import { useCallback } from "react";
import { useIonRouter } from "@ionic/react";
import { RouteComponentProps, useLocation } from "react-router";
import { MaintenanceModel } from "../../constants/CarModels";
import MaintenanceAdd from "./MaintenanceAddModal/MaintenanceAdd";

interface MaintenanceCreatePageState {
  initialMaintenanceValues?: MaintenanceModel;
}

interface MaintenanceCreatePageProps
  extends RouteComponentProps<{
    id: string;
  }> {}

const MaintenanceCreatePage: React.FC<MaintenanceCreatePageProps> = ({
  match,
}) => {
  const router = useIonRouter();
  const location = useLocation<MaintenanceCreatePageState>();

  const handleClose = useCallback(() => {
    router.push(`/menu/carros/${match.params.id}`, "back", "replace");
  }, [router, match.params.id]);

  return (
    <MaintenanceAdd
      carId={match.params.id}
      closeModal={handleClose}
      initialValues={location.state?.initialMaintenanceValues}
    />
  );
};

export default MaintenanceCreatePage;
