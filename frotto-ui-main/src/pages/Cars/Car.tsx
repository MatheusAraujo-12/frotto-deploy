import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonCardSubtitle,
  IonContent,
  IonHeader,
  IonModal,
  IonPage,
  IonProgressBar,
  IonTitle,
  IonToolbar,
  useIonRouter,
  useIonViewWillEnter,
  useIonViewWillLeave,
} from "@ionic/react";
import { useCallback, useMemo, useState } from "react";
import { RouteComponentProps } from "react-router";
import CarBrandMark from "../../components/Car/CarBrandMark";
import {
  normalizeCarRecord,
  resolveCarIdentity,
} from "../../components/Car/carIdentity";
import endpoints from "../../constants/endpoints";
import {
  CarDriverModel,
  CarModel,
  InspectionModel,
  MaintenanceModel,
} from "../../constants/CarModels";
import { TEXT } from "../../constants/texts";
import api from "../../services/axios/axios";
import { currencyFormat } from "../../services/currencyFormat";
import { formatDateView } from "../../services/dateFormat";
import { useAlert } from "../../services/hooks/useAlert";
import { formatCPF, formatTel } from "../../services/iMaskFormat";
import { servicesToString } from "../../services/toString";
import DriverAdd from "../Driver/DriverAddModal/DriverAdd";
import InspectionAdd from "../Inspection/InspectionAddModal/InspectionAdd";
import MaintenanceAdd from "../Maintenance/MaintenanceAddModal/MaintenanceAdd";
import CarAdd from "./CarAddModal/CarAdd";
import "./Car.css";

interface CarDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Car: React.FC<CarDetail> = ({ match }) => {
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
        setCar(normalizeCarRecord(data.car || {}));
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
    loadCar();
  }, []);

  useIonViewWillLeave(() => {
    setEditCarModalOpen(false);
    setEditDriverModalOpen(false);
    setAddInspectionModalOpen(false);
    setAddMaintenanceModalOpen(false);
  }, []);

  const closeEditCarModal = useCallback((response?: CarModel) => {
    setEditCarModalOpen(false);

    if (!response) return;

    setCar(normalizeCarRecord(response));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const closeEditDriverModal = useCallback((response?: CarDriverModel) => {
    setEditDriverModalOpen(false);

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

  const maintenanceInitialValues = useMemo(
    (): MaintenanceModel => ({
      odometer: car.odometer,
    }),
    [car.odometer]
  );

  const carIdentity = useMemo(() => resolveCarIdentity(car), [car]);
  const carBrand = carIdentity.brand;
  const carModel = carIdentity.model;
  const carHeadline = carIdentity.displayName;

  const carSubheadline = useMemo(() => {
    const items = [car?.plate, car?.group].filter(
      (value): value is string => Boolean(value?.trim())
    );

    return items.join(" - ") || "Sem dados principais do veículo";
  }, [car?.group, car?.plate]);

  const closeAddInspectionModal = useCallback(
    (response?: InspectionModel) => {
      setAddInspectionModalOpen(false);

      if (!response) return;

      loadCar();
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [car]
  );

  const closeAddMaintenanceModal = useCallback(
    (response?: MaintenanceModel) => {
      setAddMaintenanceModalOpen(false);

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
        <div className="section-shell car-page-shell">
          <IonCard className="car-page__card">
            <IonCardSubtitle className="car-page__eyebrow">
              {TEXT.carData}
            </IonCardSubtitle>
            <IonCardContent className="car-page__content">
              <div className="car-page__hero">
                <CarBrandMark
                  name={car?.name}
                  brand={car?.brand}
                  marca={car?.marca}
                  model={car?.model}
                  size="lg"
                  className="car-page__brand-mark"
                />

                <div className="car-page__headline-block">
                  <h2 className="car-page__headline">{carHeadline}</h2>
                  <p className="car-page__subheadline">{carSubheadline}</p>
                </div>
              </div>

              <div className="car-page__detail-grid">
                <DetailField label="Marca" value={carBrand} />
                <DetailField label="Modelo" value={carModel} />
                <DetailField label={TEXT.plate} value={car?.plate} />
                <DetailField label={TEXT.color} value={car?.color} />
                <DetailField label={TEXT.year} value={car?.year} />
                <DetailField label={TEXT.group} value={car?.group} />
                <DetailField
                  label={TEXT.odometer}
                  value={formatKm(car?.odometer)}
                />
                <DetailField
                  label={TEXT.adminStatus}
                  value={resolveAdminStatusLabel(car?.adminStatus)}
                />
              </div>

              <div className="app-actions-row car-page__actions">
                <IonButton
                  className="app-semantic-btn app-semantic--edit"
                  size="small"
                  fill="outline"
                  onClick={() => setEditCarModalOpen(true)}
                >
                  {TEXT.edit}
                </IonButton>
                <IonButton
                  className="app-semantic-btn app-semantic--success"
                  size="small"
                  fill="outline"
                  routerLink={`/menu/carros/${match.params.id}/receitas`}
                >
                  {TEXT.incomes}
                </IonButton>
                <IonButton
                  className="app-semantic-btn app-semantic--danger"
                  size="small"
                  fill="outline"
                  routerLink={`/menu/carros/${match.params.id}/despesas`}
                >
                  {TEXT.carExpenses}
                </IonButton>
              </div>
            </IonCardContent>
          </IonCard>

          <IonCard className="car-page__card">
            <IonCardSubtitle className="car-page__eyebrow">
              {TEXT.driver}
            </IonCardSubtitle>
            <IonCardContent className="car-page__content">
              {driver ? (
                <>
                  <div className="car-page__headline-block">
                    <h2 className="car-page__headline">
                      {driver?.driver?.name || TEXT.driver}
                    </h2>
                    <p className="car-page__subheadline">
                      {[
                        formatCPF(driver?.driver?.cpf),
                        formatTel(driver?.driver?.contact),
                      ]
                        .filter(Boolean)
                        .join(" - ") || "Sem contatos adicionais"}
                    </p>
                  </div>
                  <div className="car-page__detail-grid">
                    <DetailField
                      label={TEXT.dateStart}
                      value={formatDateView(driver?.startDate)}
                    />
                    <DetailField
                      label={TEXT.warranty}
                      value={currencyFormat(driver?.warranty)}
                    />
                    <DetailField
                      label={TEXT.email}
                      value={driver?.driver?.email}
                    />
                    <DetailField
                      label="Número do contrato"
                      value={driver?.contractNumber}
                    />
                  </div>
                </>
              ) : (
                <div className="car-page__empty">{TEXT.noDriver}</div>
              )}
              <div className="app-actions-row car-page__actions">
                <IonButton
                  className={`app-semantic-btn ${
                    driver ? "app-semantic--edit" : "app-semantic--neutral"
                  }`}
                  size="small"
                  fill="outline"
                  onClick={() => setEditDriverModalOpen(true)}
                >
                  {driver ? TEXT.edit : TEXT.new}
                </IonButton>
                <IonButton
                  className="app-semantic-btn app-semantic--neutral"
                  size="small"
                  fill="outline"
                  routerLink={`/menu/carros/${match.params.id}/motoristas`}
                >
                  {TEXT.all}
                </IonButton>
                <IonButton
                  className="app-semantic-btn app-semantic--warning"
                  size="small"
                  fill="outline"
                  routerLink={
                    driver
                      ? `/menu/carros/motorista/${driver.id}/pendencias`
                      : `/menu/carros/${match.params.id}/motoristas`
                  }
                >
                  {TEXT.driverPendencies}
                </IonButton>
              </div>
            </IonCardContent>
          </IonCard>

          <IonCard className="car-page__card">
            <IonCardSubtitle className="car-page__eyebrow">
              {TEXT.lastInspection}
            </IonCardSubtitle>
            <IonCardContent className="car-page__content">
              {inspection ? (
                <div className="car-page__detail-grid">
                  <DetailField
                    label={TEXT.date}
                    value={formatDateView(inspection.date)}
                    strong
                  />
                  <DetailField
                    label={TEXT.odometer}
                    value={formatKm(inspection?.odometer)}
                  />
                  <DetailField
                    label={TEXT.cost}
                    value={currencyFormat(inspection.cost)}
                  />
                </div>
              ) : (
                <div className="car-page__empty">{TEXT.noInspection}</div>
              )}
              <div className="app-actions-row car-page__actions">
                <IonButton
                  className="app-semantic-btn app-semantic--neutral"
                  size="small"
                  fill="outline"
                  onClick={() => setAddInspectionModalOpen(true)}
                >
                  {TEXT.new}
                </IonButton>
                <IonButton
                  className="app-semantic-btn app-semantic--neutral"
                  size="small"
                  fill="outline"
                  routerLink={`/menu/carros/${match.params.id}/inspecoes`}
                >
                  {TEXT.all}
                </IonButton>
                <IonButton
                  className="app-semantic-btn app-semantic--warning"
                  size="small"
                  fill="outline"
                  routerLink={`/menu/carros/${match.params.id}/danos`}
                >
                  {TEXT.damage}
                </IonButton>
              </div>
            </IonCardContent>
          </IonCard>

          <IonCard className="car-page__card">
            <IonCardSubtitle className="car-page__eyebrow">
              {TEXT.lastMaintenance}
            </IonCardSubtitle>
            <IonCardContent className="car-page__content">
              {maintenance ? (
                <>
                  <div className="car-page__detail-grid">
                    <DetailField
                      label={TEXT.date}
                      value={formatDateView(maintenance.date)}
                      strong
                    />
                    <DetailField
                      label={TEXT.odometer}
                      value={formatKm(maintenance?.odometer)}
                    />
                    <DetailField
                      label={TEXT.cost}
                      value={currencyFormat(maintenance.cost)}
                    />
                    <DetailField label={TEXT.local} value={maintenance.local} />
                  </div>
                  <p className="car-page__note">
                    {servicesToString(maintenance.services) ||
                      "Sem serviços informados"}
                  </p>
                </>
              ) : (
                <div className="car-page__empty">{TEXT.noMaintenance}</div>
              )}
              <div className="app-actions-row car-page__actions">
                <IonButton
                  className="app-semantic-btn app-semantic--neutral"
                  size="small"
                  fill="outline"
                  onClick={() => setAddMaintenanceModalOpen(true)}
                >
                  {TEXT.new}
                </IonButton>
                <IonButton
                  className="app-semantic-btn app-semantic--neutral"
                  size="small"
                  fill="outline"
                  routerLink={`/menu/carros/${match.params.id}/manutencoes`}
                >
                  {TEXT.all}
                </IonButton>
                <IonButton
                  className="app-semantic-btn app-semantic--warning"
                  size="small"
                  fill="outline"
                  routerLink={`/menu/carros/${match.params.id}/lembretes`}
                >
                  {TEXT.reminders}
                </IonButton>
              </div>
            </IonCardContent>
          </IonCard>
        </div>
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
          initialValues={maintenanceInitialValues}
        />
      </IonModal>
    </IonPage>
  );
};

export default Car;

const DetailField: React.FC<{
  label: string;
  value?: string | number | null;
  strong?: boolean;
}> = ({ label, value, strong = false }) => (
  <div className="car-page__detail">
    <span className="car-page__detail-label">{label}</span>
    <span
      className={`car-page__detail-value${
        strong ? " car-page__detail-value--strong" : ""
      }`}
    >
      {formatDetailValue(value)}
    </span>
  </div>
);

function formatDetailValue(value?: string | number | null): string {
  if (value === null || value === undefined) {
    return "--";
  }

  const text = `${value}`.trim();
  return text ? text : "--";
}

function formatKm(value?: number): string {
  if (typeof value !== "number") {
    return "--";
  }

  return `${value.toLocaleString("pt-BR")} ${TEXT.km}`;
}

function resolveAdminStatusLabel(value?: string): string {
  if (!value) {
    return "--";
  }

  if (value === "ATIVO") return "Ativo";
  if (value === "RETIRADO") return "Retirado";
  if (value === "A_VENDA") return "À venda";
  if (value === "MANUTENCAO") return "Manutenção";
  if (value === "BLOQUEADO") return "Bloqueado";

  return value;
}
