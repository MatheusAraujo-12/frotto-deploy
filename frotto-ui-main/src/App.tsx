/* CSS Principal necessário para os componentes Ionic funcionarem corretamente */
import "@ionic/react/css/core.css";

/* CSS Básico para aplicativos construídos com Ionic */
import "@ionic/react/css/normalize.css";
import "@ionic/react/css/structure.css";
import "@ionic/react/css/typography.css";

/* Utilitários CSS opcionais que podem ser comentados */
import "@ionic/react/css/padding.css";
import "@ionic/react/css/float-elements.css";
import "@ionic/react/css/text-alignment.css";
import "@ionic/react/css/text-transformation.css";
import "@ionic/react/css/flex-utils.css";
import "@ionic/react/css/display.css";

/* Variáveis de tema */
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

/**
 * Componente auxiliar para lidar com lógica que precisa do Contexto do Roteador.
 * Isso evita erro de "useIonRouter must be used within an IonRouterContext".
 */
const AppSetup: React.FC = () => {
  const router = useIonRouter();

  useEffect(() => {
    // Configura interceptador do Axios
    const interceptorId = api.interceptors.response.use(
      (config) => {
        return config;
      },
      (error) => {
        const status = error?.response?.status;

        if (status === 401) {
          removeToken();
          // Redireciona para login, limpando o histórico
          router.push("/", "none", "replace");
        } else if (error?.code === "ERR_CANCELED") {
          // Ignora requisições canceladas para evitar erros no console
          return Promise.reject(error);
        }
        return Promise.reject(error);
      }
    );

    // Configura token inicial se existir
    const token = getToken();
    if (token) {
      api.defaults.headers.common["Authorization"] = token;
    }

    // Inicializa o tema (persistido ou preferência do sistema)
    import("./services/theme").then((mod) => mod.initTheme());

    // Cleanup do interceptor ao desmontar (boa prática)
    return () => {
      api.interceptors.response.eject(interceptorId);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return null; // Este componente não renderiza nada visualmente
};

const App: React.FC = () => {
  return (
    <IonApp>
      <IonReactRouter>
        {/* AppSetup deve estar DENTRO do IonReactRouter para acessar o useIonRouter */}
        <AppSetup />
        
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