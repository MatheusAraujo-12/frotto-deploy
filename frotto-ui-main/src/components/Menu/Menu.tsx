import {
  IonButton,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonMenu,
  IonMenuToggle,
  IonPage,
  IonRouterOutlet,
  IonSpinner,
  IonTitle,
  IonToolbar,
  IonLabel,
  IonToggle,
  useIonRouter,
} from "@ionic/react";
import { Redirect, Route } from "react-router";
import { Suspense, lazy, useEffect, useState } from "react";
import type { ComponentType, FC } from "react";
import { car, clipboard, logOutOutline } from "ionicons/icons";
import { removeToken } from "../../services/localStorage/localstorage";
import { TEXT } from "../../constants/texts";

const BodyDamages = lazy(() => import("../../pages/BodyDamage/BodyDamages"));
const Car = lazy(() => import("../../pages/Cars/Car"));
const Cars = lazy(() => import("../../pages/Cars/Cars"));
const Drivers = lazy(() => import("../../pages/Driver/Drivers"));
const Inspections = lazy(() => import("../../pages/Inspection/Inspections"));
const Maintenances = lazy(() => import("../../pages/Maintenance/Maintenances"));
const Incomes = lazy(() => import("../../pages/Income/Incomes"));
const CarExpenses = lazy(() => import("../../pages/CarExpense/CarExpenses"));
const Reports = lazy(() => import("../../pages/Reports/Reports"));
const Reminders = lazy(() => import("../../pages/Reminders/Reminders"));
const DriverPendencies = lazy(() => import("../../pages/DriverPendency/DriverPendencies"));

const PageFallback: FC = () => (
  <IonPage>
    <IonContent>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          height: "100%",
        }}
      >
        <IonSpinner name="crescent" />
      </div>
    </IonContent>
  </IonPage>
);

const renderWithSuspense =
  (Component: ComponentType<any>) =>
  (props: any) => (
    <Suspense fallback={<PageFallback />}>
      <Component {...props} />
    </Suspense>
  );

const Menu: FC = () => {
  const history = useIonRouter();
  const [isDark, setIsDark] = useState<boolean>(false);

  useEffect(() => {
    // lazy import theme util to avoid circular issues
    import("../../services/theme").then((mod) => {
      const saved = mod.getTheme();
      if (saved) {
        mod.applyTheme(saved);
        setIsDark(saved === "dark");
        return;
      }

      const prefersDark =
        typeof window !== "undefined" &&
        window.matchMedia &&
        window.matchMedia("(prefers-color-scheme: dark)").matches;

      const themeToUse = prefersDark ? "dark" : "light";
      mod.applyTheme(themeToUse);
      setIsDark(themeToUse === "dark");
    });
  }, []);

  const onToggleTheme = (checked: boolean) => {
    setIsDark(checked);
    import("../../services/theme").then((mod) => {
      mod.setTheme(checked ? "dark" : "light");
    });
  };

  return (
    <IonPage>
      <>
        <IonMenu contentId="main" menuId="main-menu" className="app-menu">
          <IonHeader translucent>
            <IonToolbar className="menu-toolbar">
              <IonTitle>{TEXT.appTitle}</IonTitle>
            </IonToolbar>
          </IonHeader>
          <IonContent className="menu-content">
            <div className="menu-inner">
              <div className="menu-hero">
                <h3>Controle sua frota sem esforço</h3>
              </div>
              <IonMenuToggle>
                <IonItem className="menu-item" routerLink="/menu/carros" routerDirection="none">
                  <IonIcon icon={car} slot="start"></IonIcon>
                  {TEXT.cars}
                </IonItem>
              </IonMenuToggle>
              <IonMenuToggle>
                <IonItem className="menu-item" routerLink="/menu/relatorios" routerDirection="none">
                  <IonIcon icon={clipboard} slot="start"></IonIcon>
                  {TEXT.reports}
                </IonItem>
              </IonMenuToggle>
              <div className="menu-footer">
                <IonItem lines="none">
                  <IonLabel>{TEXT.theme}</IonLabel>
                  <IonToggle checked={isDark} onIonChange={(e) => onToggleTheme(e.detail.checked)} />
                </IonItem>
                <IonMenuToggle>
                  <IonButton
                    color="danger"
                    fill="outline"
                    expand="block"
                    onClick={() => {
                      removeToken();
                      history.push("/", "none", "replace");
                    }}
                  >
                    <IonIcon icon={logOutOutline} slot="start"></IonIcon>
                    {TEXT.logout}
                  </IonButton>
                </IonMenuToggle>
              </div>
            </div>
          </IonContent>
        </IonMenu>
        <IonRouterOutlet id="main">
          <Route exact path="/menu/carros" render={renderWithSuspense(Cars)} />
          <Route exact path="/menu/carros/:id" render={renderWithSuspense(Car)} />
          <Route exact path="/menu/carros/:id/danos" render={renderWithSuspense(BodyDamages)} />
          <Route exact path="/menu/carros/:id/motoristas" render={renderWithSuspense(Drivers)} />
          <Route exact path="/menu/carros/:id/receitas" render={renderWithSuspense(Incomes)} />
          <Route
            exact
            path="/menu/carros/:id/despesas"
            render={renderWithSuspense(CarExpenses)}
          />
          <Route
            exact
            path="/menu/carros/:id/inspecoes"
            render={renderWithSuspense(Inspections)}
          />
          <Route
            exact
            path="/menu/carros/:id/manutencoes"
            render={renderWithSuspense(Maintenances)}
          />
          <Route
            exact
            path="/menu/carros/:id/lembretes"
            render={renderWithSuspense(Reminders)}
          />
          <Route
            exact
            path="/menu/carros/motorista/:id/pendencias"
            render={renderWithSuspense(DriverPendencies)}
          />
          <Route exact path="/menu/relatorios" render={renderWithSuspense(Reports)} />

          <Route exact path="/menu">
            <Redirect to="/menu/carros" />
          </Route>
        </IonRouterOutlet>
      </>
    </IonPage>
  );
};

export default Menu;
