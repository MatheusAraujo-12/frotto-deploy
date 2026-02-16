import {
  IonAvatar,
  IonButton,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonMenu,
  IonMenuToggle,
  IonPage,
  IonRouterOutlet,
  IonSkeletonText,
  IonTitle,
  IonToggle,
  IonToolbar,
  useIonRouter,
} from "@ionic/react";
import { Redirect, Route } from "react-router";
import React, { useEffect, useState } from "react";
import { car, clipboard, logOutOutline, personCircleOutline } from "ionicons/icons";

import BodyDamages from "../../pages/BodyDamage/BodyDamages";
import Car from "../../pages/Cars/Car";
import Cars from "../../pages/Cars/Cars";
import Drivers from "../../pages/Driver/Drivers";
import Inspections from "../../pages/Inspection/Inspections";
import Maintenances from "../../pages/Maintenance/Maintenances";
import Incomes from "../../pages/Income/Incomes";
import CarExpenses from "../../pages/CarExpense/CarExpenses";
import Reports from "../../pages/Reports/Reports";
import Reminders from "../../pages/Reminders/Reminders";
import DriverPendencies from "../../pages/DriverPendency/DriverPendencies";
import MyPanelPage from "../../pages/MyPanel/MyPanelPage";

import { removeToken } from "../../services/localStorage/localstorage";
import profileService, { MeResponseDTO } from "../../services/profileService";
import { TEXT } from "../../constants/texts";

const resolveMenuDisplayName = (profile: MeResponseDTO | null): string => {
  if (!profile) {
    return "";
  }

  const taxPersonType = `${profile.taxPersonType || ""}`.toUpperCase();
  if (taxPersonType === "CNPJ") {
    return `${profile.taxCompanyName || ""}`.trim();
  }

  if (taxPersonType === "CPF") {
    return `${profile.taxLandlordName || ""}`.trim();
  }

  return (
    `${profile.taxCompanyName || ""}`.trim() ||
    `${profile.taxLandlordName || ""}`.trim() ||
    `${profile.firstName || ""} ${profile.lastName || ""}`.trim()
  );
};

const resolveMenuAvatarUrl = (profile: MeResponseDTO | null): string => {
  if (!profile) {
    return "";
  }

  const rawValue = `${(profile as any).avatarUrl ?? profile.imageUrl ?? ""}`.trim();
  if (!rawValue) {
    return "";
  }

  if (
    rawValue.startsWith("http://") ||
    rawValue.startsWith("https://") ||
    rawValue.startsWith("blob:") ||
    rawValue.startsWith("data:")
  ) {
    return rawValue;
  }

  const s3Base = `${process.env.REACT_APP_S3_URL || ""}`.trim();
  return s3Base ? `${s3Base}${rawValue}` : rawValue;
};

const toInitials = (name: string): string => {
  const parts = name
    .trim()
    .split(/\s+/)
    .filter(Boolean);

  if (!parts.length) {
    return "PF";
  }

  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }

  return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
};

const Menu: React.FC = () => {
  const history = useIonRouter();
  const [isDark, setIsDark] = useState<boolean>(false);
  const [profile, setProfile] = useState<MeResponseDTO | null>(null);
  const [isProfileLoading, setIsProfileLoading] = useState<boolean>(true);

  useEffect(() => {
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

  useEffect(() => {
    let isMounted = true;

    const loadProfile = async () => {
      try {
        const data = await profileService.getMe();
        if (isMounted) {
          setProfile(data);
        }
      } catch (_error) {
        if (isMounted) {
          setProfile(null);
        }
      } finally {
        if (isMounted) {
          setIsProfileLoading(false);
        }
      }
    };

    loadProfile();

    return () => {
      isMounted = false;
    };
  }, []);

  const onToggleTheme = (checked: boolean) => {
    setIsDark(checked);
    import("../../services/theme").then((mod) => {
      mod.setTheme(checked ? "dark" : "light");
    });
  };

  const displayName = resolveMenuDisplayName(profile) || "Perfil";
  const avatarUrl = resolveMenuAvatarUrl(profile);
  const initials = toInitials(displayName);

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
              <div
                className="menu-hero"
                style={{
                  paddingTop: 18,
                  paddingBottom: 14,
                  textAlign: "center",
                }}
              >
                {isProfileLoading ? (
                  <>
                    <IonSkeletonText
                      animated
                      style={{
                        width: 92,
                        height: 92,
                        borderRadius: "50%",
                        margin: "0 auto",
                      }}
                    />
                    <IonSkeletonText
                      animated
                      style={{
                        width: "74%",
                        maxWidth: 220,
                        height: 18,
                        margin: "12px auto 0",
                        borderRadius: 8,
                      }}
                    />
                  </>
                ) : (
                  <>
                    <IonAvatar
                      style={{
                        width: 92,
                        height: 92,
                        margin: "0 auto",
                        background: "var(--ion-color-step-150, var(--ion-color-light))",
                        color: "var(--ion-color-step-750, var(--ion-color-medium-shade))",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      {avatarUrl ? (
                        <img src={avatarUrl} alt={displayName} />
                      ) : (
                        <span
                          style={{
                            fontSize: 30,
                            fontWeight: 600,
                            letterSpacing: 0.4,
                          }}
                        >
                          {initials}
                        </span>
                      )}
                    </IonAvatar>

                    <div
                      style={{
                        marginTop: 12,
                        fontSize: 17,
                        fontWeight: 600,
                        lineHeight: 1.3,
                        color: "var(--ion-text-color)",
                        wordBreak: "break-word",
                      }}
                    >
                      {displayName}
                    </div>
                  </>
                )}
              </div>

              <div
                style={{
                  borderTop: "1px solid var(--ion-border-color)",
                  margin: "0 0 8px",
                }}
              />

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
          <Route exact path="/menu/carros/:id/despesas" component={CarExpenses} />
          <Route exact path="/menu/carros/:id/inspecoes" component={Inspections} />
          <Route exact path="/menu/carros/:id/manutencoes" component={Maintenances} />
          <Route exact path="/menu/carros/:id/lembretes" component={Reminders} />
          <Route exact path="/menu/carros/motorista/:id/pendencias" component={DriverPendencies} />
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
