import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonCard,
  IonCardSubtitle,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonList,
  IonModal,
  IonNote,
  IonPage,
  IonProgressBar,
  IonText,
  IonTitle,
  IonToolbar,
  useIonRouter,
  useIonViewWillEnter,
} from "@ionic/react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { RouteComponentProps, useHistory, useLocation } from "react-router";
import endpoints from "../../constants/endpoints";
import { TEXT } from "../../constants/texts";
import api from "../../services/axios/axios";
import { useAlert } from "../../services/hooks/useAlert";
import CarAdd from "./CarAddModal/CarAdd";
import {
  CarDriverModel,
  CarModel,
  InspectionModel,
  MaintenanceModel,
} from "../../constants/CarModels";
import DriverAdd from "../Driver/DriverAddModal/DriverAdd";
import InspectionAdd from "../Inspection/InspectionAddModal/InspectionAdd";
import { formatDateView } from "../../services/dateFormat";
import {
  IonLabekRight,
  IonLabelLeft,
} from "../../components/List/IonLabekRight";
import MaintenanceAdd from "../Maintenance/MaintenanceAddModal/MaintenanceAdd";
import { currencyFormat } from "../../services/currencyFormat";
import { servicesToString } from "../../services/toString";
import { formatCPF, formatTel } from "../../services/iMaskFormat";

interface CarDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Car: React.FC<CarDetail> = ({ match }) => {
  const location = useLocation();
  const nav = useHistory();
  const history = useIonRouter();
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [car, setCar] = useState<CarModel>({});
  const [editCarModalOpen, setEditCarModalOpen] = useState(false);
  const [editDriverModalOpen, setEditDriverModalOpen] = useState(false);
  const [addInspectionModalOpen, setAddInspectionModalOpen] = useState(false);
  const [addMaintenanceModalOpen, setAddMaintenanceModalOpen] = useState(false);
  const [driver, setDriver] = useState<CarDriverModel | undefined>(undefined);
  const [inspection, setInspection] = useState<InspectionModel | undefined>(
    undefined
  );
  const [maintenance, setMaintenance] = useState<MaintenanceModel | undefined>(
    undefined
  );

  useEffect(() => {
    if (!location.search.includes("modalOpened=true")) {
      setEditCarModalOpen(false);
      setEditDriverModalOpen(false);
      setAddInspectionModalOpen(false);
      setAddMaintenanceModalOpen(false);
    }
  }, [location]);

  const loadCar = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.CAR({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setCar(data.car);
        setMaintenance(data.lastMaintenance);
        setInspection(data.lastInspection);
        setDriver(data.activeDriver);
      } else {
        history.push("/menu", "none", "replace");
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadCarFailed);
    }
  };

  useIonViewWillEnter(() => {
    if (!location.search.includes("modalOpened=true")) {
      loadCar();
    }
  }, [location]);

  const closeEditCarModal = useCallback((response?: CarModel) => {
    setEditCarModalOpen(false);
    nav.goBack();

    if (!response) return;

    setCar(response);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const closeEditDriverModal = useCallback((response?: CarDriverModel) => {
    setEditDriverModalOpen(false);
    nav.goBack();

    if (!response) return;

    if (response.concluded) {
      setDriver(undefined);
    } else {
      setDriver(response);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const newInspection = useMemo((): InspectionModel => {
    if (inspection) {
      return {
        ...inspection,
        id: undefined,
        date: undefined,
        leftBack: {
          ...inspection.leftBack,
          id: undefined,
        },
        rightBack: {
          ...inspection.rightBack,
          id: undefined,
        },
        leftFront: {
          ...inspection.leftFront,
          id: undefined,
        },
        rightFront: {
          ...inspection.rightFront,
          id: undefined,
        },
        spare: {
          ...inspection.spare,
          id: undefined,
        },
        carBodyDamages: [],
        expenses: [],
      };
    }
    return {};
  }, [inspection]);

  const closeAddInspectionModal = useCallback(
    (response?: InspectionModel) => {
      setAddInspectionModalOpen(false);
      nav.goBack();

      if (!response) return;

      loadCar();
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [car]
  );

  const closeAddMaintenanceModal = useCallback(
    (response?: MaintenanceModel) => {
      setAddMaintenanceModalOpen(false);
      nav.goBack();

      if (!response) return;

      loadCar();
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [car]
  );

  return (
    <IonPage id="car-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.carInfo}</IonTitle>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <IonCard>
          <IonCardSubtitle className="ion-margin-horizontal ion-margin-top">
            <IonText color="medium">
              <strong>{TEXT.carData}</strong>
            </IonText>
          </IonCardSubtitle>
          <IonList lines="none">
            <IonItem>
              <IonLabelLeft class="ion-text-wrap">
                <h2>{`${car?.name}`}</h2>
                <p>{car?.color}</p>
                <p>{`${car?.odometer} ${TEXT.km}`}</p>
              </IonLabelLeft>
              <IonLabekRight>
                <p>{`${car?.year}`}</p>
                <p>{car?.plate}</p>
                <p>{car?.group}</p>
              </IonLabekRight>
            </IonItem>
          </IonList>
          <IonButton
            fill="clear"
            onClick={() => {
              nav.push(nav.location.pathname + "?modalOpened=true");
              setEditCarModalOpen(true);
            }}
          >
            {TEXT.edit}
          </IonButton>
          <IonButton
            fill="clear"
            color="success"
            routerLink={"/menu/carros/" + match.params.id + "/receitas"}
          >
            {TEXT.incomes}
          </IonButton>
          <IonButton
            fill="clear"
            color="danger"
            routerLink={"/menu/carros/" + match.params.id + "/despesas"}
          >
            {TEXT.carExpenses}
          </IonButton>
        </IonCard>
        <IonCard>
          <IonCardSubtitle className="ion-margin-horizontal ion-margin-top">
            <IonText color="medium">
              <strong>{TEXT.driver}</strong>
            </IonText>
          </IonCardSubtitle>
          <IonList lines="none">
            {driver && (
              <IonItem>
                <IonLabelLeft class="ion-text-wrap">
                  <h2>{driver?.driver?.name}</h2>
                  <p>{formatCPF(driver?.driver?.cpf)}</p>
                  <p>{formatTel(driver?.driver?.contact)}</p>
                </IonLabelLeft>
                <IonLabekRight>
                  <p>{`${formatDateView(driver?.startDate)}`}</p>
                  <p>{currencyFormat(driver?.warranty)}</p>
                  <p>{`${driver?.driver?.email}`}</p>
                </IonLabekRight>
              </IonItem>
            )}
            {!driver && (
              <IonItem>
                <IonLabel class="ion-text-wrap">
                  <h2>{TEXT.noDriver}</h2>
                </IonLabel>
              </IonItem>
            )}
          </IonList>
          <IonButton
            fill="clear"
            onClick={() => {
              nav.push(nav.location.pathname + "?modalOpened=true");
              setEditDriverModalOpen(true);
            }}
          >
            {driver ? TEXT.edit : TEXT.new}
          </IonButton>
          <IonButton
            fill="clear"
            routerLink={"/menu/carros/" + match.params.id + "/motoristas"}
          >
            {TEXT.all}
          </IonButton>
          {driver && (
            <IonButton
              fill="clear"
              color="secondary"
              routerLink={"/menu/carros/motorista/" + driver.id + "/pendencias"}
            >
              {TEXT.driverPendencies}
            </IonButton>
          )}
        </IonCard>
        <IonCard>
          <IonCardSubtitle className="ion-margin-horizontal ion-margin-top">
            <IonText color="medium">
              <strong>{TEXT.lastInspection}</strong>
            </IonText>
          </IonCardSubtitle>
          <IonList lines="none">
            {inspection && (
              <IonItem>
                <IonLabelLeft class="ion-text-wrap">
                  <h2>{formatDateView(inspection.date)}</h2>
                  <p>{`${inspection?.odometer} ${TEXT.km}`}</p>
                </IonLabelLeft>
                <IonLabekRight>
                  <p>{currencyFormat(inspection.cost)}</p>
                </IonLabekRight>
              </IonItem>
            )}
            {!inspection && (
              <IonItem>
                <IonLabel class="ion-text-wrap">
                  <h2>{TEXT.noInspection}</h2>
                </IonLabel>
              </IonItem>
            )}
          </IonList>
          <IonButton
            fill="clear"
            onClick={() => {
              nav.push(nav.location.pathname + "?modalOpened=true");
              setAddInspectionModalOpen(true);
            }}
          >
            {TEXT.new}
          </IonButton>
          <IonButton
            fill="clear"
            routerLink={"/menu/carros/" + match.params.id + "/inspecoes"}
          >
            {TEXT.all}
          </IonButton>
          <IonButton
            fill="clear"
            color="secondary"
            routerLink={"/menu/carros/" + match.params.id + "/danos"}
          >
            {TEXT.damage}
          </IonButton>
        </IonCard>
        <IonCard>
          <IonCardSubtitle className="ion-margin-horizontal ion-margin-top">
            <IonText color="medium">
              <strong>{TEXT.lastMaintenance}</strong>
            </IonText>
          </IonCardSubtitle>
          <IonList lines="none">
            {maintenance && (
              <IonItem>
                <IonLabel class="ion-text-wrap">
                  <h2>{formatDateView(maintenance.date)}</h2>
                  <p>
                    {`${maintenance.odometer} ${TEXT.km}`}
                    &nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;
                    {currencyFormat(maintenance.cost)}
                  </p>
                  <p>{maintenance.local}</p>
                  <IonNote>{servicesToString(maintenance.services)}</IonNote>
                </IonLabel>
              </IonItem>
            )}
            {!maintenance && (
              <IonItem>
                <IonLabel class="ion-text-wrap">
                  <h2>{TEXT.noMaintenance}</h2>
                </IonLabel>
              </IonItem>
            )}
          </IonList>
          <IonButton
            fill="clear"
            onClick={() => {
              nav.push(nav.location.pathname + "?modalOpened=true");
              setAddMaintenanceModalOpen(true);
            }}
          >
            {TEXT.new}
          </IonButton>
          <IonButton
            fill="clear"
            routerLink={"/menu/carros/" + match.params.id + "/manutencoes"}
          >
            {TEXT.all}
          </IonButton>
          <IonButton
            fill="clear"
            color="secondary"
            routerLink={"/menu/carros/" + match.params.id + "/lembretes"}
          >
            {TEXT.reminders}
          </IonButton>
        </IonCard>
      </IonContent>
      <IonModal isOpen={editCarModalOpen} backdropDismiss={false}>
        <CarAdd closeModal={closeEditCarModal} initialValues={car} />
      </IonModal>
      <IonModal isOpen={editDriverModalOpen} backdropDismiss={false}>
        <DriverAdd
          carId={match.params.id}
          closeModal={closeEditDriverModal}
          initialValues={driver}
        />
      </IonModal>
      <IonModal isOpen={addInspectionModalOpen} backdropDismiss={false}>
        <InspectionAdd
          carId={match.params.id}
          closeModal={closeAddInspectionModal}
          initialValues={{
            ...newInspection,
            odometer: car.odometer,
            driverName: driver?.driver?.name,
          }}
        />
      </IonModal>
      <IonModal isOpen={addMaintenanceModalOpen} backdropDismiss={false}>
        <MaintenanceAdd
          carId={match.params.id}
          closeModal={closeAddMaintenanceModal}
          initialValues={{
            odometer: car.odometer,
          }}
        />
      </IonModal>
    </IonPage>
  );
};

export default Car;
