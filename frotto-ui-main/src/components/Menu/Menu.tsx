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
  IonTitle,
  IonToolbar,
  IonLabel,
  IonToggle,
  useIonRouter,
} from "@ionic/react";
import { Redirect, Route } from "react-router";
import React, { useState, useEffect } from "react";
import BodyDamages from "../../pages/BodyDamage/BodyDamages";
import Car from "../../pages/Cars/Car";
import Cars from "../../pages/Cars/Cars";
import Drivers from "../../pages/Driver/Drivers";
import Inspections from "../../pages/Inspection/Inspections";
import { car, clipboard, logOutOutline, personCircleOutline } from "ionicons/icons";
import { removeToken } from "../../services/localStorage/localstorage";
import { TEXT } from "../../constants/texts";
import Maintenances from "../../pages/Maintenance/Maintenances";
import Incomes from "../../pages/Income/Incomes";
import CarExpenses from "../../pages/CarExpense/CarExpenses";
import Reports from "../../pages/Reports/Reports";
import Reminders from "../../pages/Reminders/Reminders";
import DriverPendencies from "../../pages/DriverPendency/DriverPendencies";
import MyPanelPage from "../../pages/MyPanel/MyPanelPage";

const Menu: React.FC = () => {
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
                <small>Painel do Locador</small>
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
              <IonMenuToggle>
                <IonItem className="menu-item" routerLink="/meu-painel" routerDirection="none">
                  <IonIcon icon={personCircleOutline} slot="start"></IonIcon>
                  Meu Painel
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
          <Route exact path="/menu/carros" component={Cars} />
          <Route exact path="/menu/carros/:id" component={Car} />
          <Route exact path="/menu/carros/:id/danos" component={BodyDamages} />
          <Route exact path="/menu/carros/:id/motoristas" component={Drivers} />
          <Route exact path="/menu/carros/:id/receitas" component={Incomes} />
          <Route
            exact
            path="/menu/carros/:id/despesas"
            component={CarExpenses}
          />
          <Route
            exact
            path="/menu/carros/:id/inspecoes"
            component={Inspections}
          />
          <Route
            exact
            path="/menu/carros/:id/manutencoes"
            component={Maintenances}
          />
          <Route
            exact
            path="/menu/carros/:id/lembretes"
            component={Reminders}
          />
          <Route
            exact
            path="/menu/carros/motorista/:id/pendencias"
            component={DriverPendencies}
          />
          <Route exact path="/menu/relatorios" component={Reports} />
          <Route exact path="/menu/meu-painel" component={MyPanelPage} />
          <Route exact path="/meu-painel" component={MyPanelPage} />
          <Route exact path="/my-panel" component={MyPanelPage} />

          <Route exact path="/menu">
            <Redirect to="/menu/carros" />
          </Route>
        </IonRouterOutlet>
      </>
    </IonPage>
  );
};

export default Menu;
