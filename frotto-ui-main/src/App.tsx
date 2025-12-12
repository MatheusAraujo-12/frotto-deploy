/* Core CSS required for Ionic components to work properly */
import "@ionic/react/css/core.css";

/* Basic CSS for apps built with Ionic */
import "@ionic/react/css/normalize.css";
import "@ionic/react/css/structure.css";
import "@ionic/react/css/typography.css";

/* Optional CSS utils that can be commented out */
import "@ionic/react/css/padding.css";
import "@ionic/react/css/float-elements.css";
import "@ionic/react/css/text-alignment.css";
import "@ionic/react/css/text-transformation.css";
import "@ionic/react/css/flex-utils.css";
import "@ionic/react/css/display.css";

/* Theme variables */
import "./theme/variables.css";
import "./theme/global.css";

import { useEffect } from "react";
import { Route } from "react-router-dom";
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
import Menu from "./components/Menu/Menu";
import Register from "./pages/Register/Register";

setupIonicReact();

const App: React.FC = () => {
  const history = useIonRouter();

  useEffect(() => {
    api.interceptors.response.use(
      (config) => {
        return config;
      },
      (error) => {
        const status = error.response.status;
        if (status === 401) {
          removeToken();
          history.push("/", "none", "replace");
        }
        return Promise.reject(error);
      }
    );

    const token = getToken();
    if (token) {
      api.defaults.headers.common["Authorization"] = token;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <IonApp>
      <IonReactRouter>
        <IonRouterOutlet>
          <Route exact path="/" component={Login} />
          <Route exact path="/cadastro" component={Register} />
          <Route path="/menu" component={Menu} />
        </IonRouterOutlet>
      </IonReactRouter>
    </IonApp>
  );
};

export default App;
