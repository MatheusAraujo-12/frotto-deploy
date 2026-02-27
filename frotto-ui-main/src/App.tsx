/* CSS Principal necessÃ¡rio para os componentes Ionic funcionarem corretamente */
import "@ionic/react/css/core.css";

/* CSS BÃ¡sico para aplicativos construÃ­dos com Ionic */
import "@ionic/react/css/normalize.css";
import "@ionic/react/css/structure.css";
import "@ionic/react/css/typography.css";

/* UtilitÃ¡rios CSS opcionais que podem ser comentados */
import "@ionic/react/css/padding.css";
import "@ionic/react/css/float-elements.css";
import "@ionic/react/css/text-alignment.css";
import "@ionic/react/css/text-transformation.css";
import "@ionic/react/css/flex-utils.css";
import "@ionic/react/css/display.css";

/* VariÃ¡veis de tema */
import "./theme/variables.css";
import "./theme/global.css";

import { useEffect, useRef } from "react";
import type { FC } from "react";
import { Redirect, Route, useLocation } from "react-router-dom";
import {
  IonApp,
  IonRouterOutlet,
  setupIonicReact,
  useIonRouter,
} from "@ionic/react";
import { IonReactRouter } from "@ionic/react-router";
import { getToken, removeToken } from "./services/localStorage/localstorage";
import api from "./services/axios/axios";
import Login from "./pages/Login/Login";
import Register from "./pages/Register/Register";
import Menu from "./components/Menu/Menu";
import BodyDamages from "./pages/BodyDamage/BodyDamages";
import Car from "./pages/Cars/Car";
import Cars from "./pages/Cars/Cars";
import CarExpenses from "./pages/CarExpense/CarExpenses";
import DocumentsPage from "./pages/Documents/DocumentsPage";
import Drivers from "./pages/Driver/Drivers";
import DriverPendencies from "./pages/DriverPendency/DriverPendencies";
import Incomes from "./pages/Income/Incomes";
import Inspections from "./pages/Inspection/Inspections";
import Maintenances from "./pages/Maintenance/Maintenances";
import MyPanelPage from "./pages/MyPanel/MyPanelPage";
import Reminders from "./pages/Reminders/Reminders";
import Reports from "./pages/Reports/Reports";
import { dismissAllOverlays } from "./services/overlayManager";

setupIonicReact();

const AppSetup: FC = () => {
  const router = useIonRouter();
  const location = useLocation();
  const previousPathnameRef = useRef(location.pathname);

  useEffect(() => {
    const interceptorId = api.interceptors.response.use(
      (config) => {
        return config;
      },
      (error) => {
        const status = error?.response?.status;

        if (status === 401) {
          removeToken();
          router.push("/", "none", "replace");
        } else if (error?.code === "ERR_CANCELED") {
          return Promise.reject(error);
        }

        return Promise.reject(error);
      }
    );

    const token = getToken();
    if (token) {
      api.defaults.headers.common["Authorization"] = token;
    }

    import("./services/theme").then((mod) => mod.initTheme());

    return () => {
      api.interceptors.response.eject(interceptorId);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const previousPathname = previousPathnameRef.current;

    if (previousPathname !== location.pathname) {
      void dismissAllOverlays(`route-change:${previousPathname}->${location.pathname}`);
    }

    previousPathnameRef.current = location.pathname;
  }, [location.pathname]);

  return null;
};

const App: FC = () => {
  return (
    <IonApp>
      <IonReactRouter>
        <AppSetup />

        <Menu />

        <IonRouterOutlet id="main">
          <Route exact path="/" component={Login} />
          <Route exact path="/cadastro" component={Register} />
          <Route exact path="/menu/carros" component={Cars} />
          <Route exact path="/menu/carros/:id" component={Car} />
          <Route exact path="/menu/carros/:id/danos" component={BodyDamages} />
          <Route exact path="/menu/carros/:id/motoristas" component={Drivers} />
          <Route exact path="/menu/carros/:id/receitas" component={Incomes} />
          <Route exact path="/menu/carros/:id/despesas" component={CarExpenses} />
          <Route exact path="/menu/carros/:id/inspecoes" component={Inspections} />
          <Route exact path="/menu/carros/:id/manutencoes" component={Maintenances} />
          <Route exact path="/menu/carros/:id/lembretes" component={Reminders} />
          <Route exact path="/menu/carros/motorista/:id/pendencias" component={DriverPendencies} />
          <Route exact path="/menu/relatorios" component={Reports} />
          <Route exact path="/documents" component={DocumentsPage} />
          <Route exact path="/menu/meu-painel" component={MyPanelPage} />
          <Route exact path="/meu-painel" component={MyPanelPage} />
          <Route exact path="/my-panel" component={MyPanelPage} />
          <Route exact path="/menu">
            <Redirect to="/menu/carros" />
          </Route>
        </IonRouterOutlet>
      </IonReactRouter>
    </IonApp>
  );
};

export default App;
