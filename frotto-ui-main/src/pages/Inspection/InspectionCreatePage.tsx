import { useCallback } from "react";
import { useIonRouter } from "@ionic/react";
import { RouteComponentProps, useLocation } from "react-router";
import { InspectionModel } from "../../constants/CarModels";
import InspectionAdd from "./InspectionAddModal/InspectionAdd";

interface InspectionCreatePageState {
  initialInspectionValues?: InspectionModel;
}

interface InspectionCreatePageProps
  extends RouteComponentProps<{
    id: string;
  }> {}

const InspectionCreatePage: React.FC<InspectionCreatePageProps> = ({ match }) => {
  const router = useIonRouter();
  const location = useLocation<InspectionCreatePageState>();

  const handleClose = useCallback(() => {
    router.push(`/menu/carros/${match.params.id}`, "back", "replace");
  }, [router, match.params.id]);

  return (
    <InspectionAdd
      carId={match.params.id}
      closeModal={handleClose}
      initialValues={location.state?.initialInspectionValues}
    />
  );
};

export default InspectionCreatePage;
