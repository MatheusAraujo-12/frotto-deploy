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
import React, { useEffect, useMemo, useState } from "react";
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

import api from "../../services/axios/axios";
import { removeToken } from "../../services/localStorage/localstorage";
import profileService, { MeResponseDTO } from "../../services/profileService";
import { resolveApiUrl } from "../../services/resolveApiUrl";
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

const resolveMenuAvatarPaths = (profile: MeResponseDTO | null): string[] => {
  if (!profile) {
    return [];
  }

  const values = [`${profile.avatarUrl || ""}`.trim(), `${profile.imageUrl || ""}`.trim()];
  return values.filter((item, index) => Boolean(item) && values.indexOf(item) === index);
};

const resolveS3AvatarUrl = (path: string): string => {
  const value = `${path || ""}`.trim();
  if (!value) {
    return "";
  }

  if (
    value.startsWith("http://") ||
    value.startsWith("https://") ||
    value.startsWith("blob:") ||
    value.startsWith("data:")
  ) {
    return value;
  }

  const s3Base = `${process.env.REACT_APP_S3_URL || ""}`.trim().replace(/\/$/, "");
  if (!s3Base) {
    return "";
  }

  const normalizedPath = value.startsWith("/") ? value : `/${value}`;
  return `${s3Base}${normalizedPath}`;
};

const buildAvatarCandidates = (rawPath: string): string[] => {
  const value = `${rawPath || ""}`.trim();
  if (!value) {
    return [];
  }

  const candidates = [resolveApiUrl(value), resolveS3AvatarUrl(value)];
  return candidates.filter((item, index) => Boolean(item) && candidates.indexOf(item) === index);
};

const buildAvatarCandidatesFromPaths = (paths: string[]): string[] => {
  const allCandidates = paths.flatMap((path) => buildAvatarCandidates(path));
  return allCandidates.filter(
    (item, index) => Boolean(item) && allCandidates.indexOf(item) === index
  );
};

const pickDirectAvatarCandidate = (candidates: string[]): string => {
  if (!candidates.length) {
    return "";
  }

  const s3Base = `${process.env.REACT_APP_S3_URL || ""}`.trim().replace(/\/$/, "");
  if (s3Base) {
    const s3Candidate = candidates.find((candidate) => candidate.startsWith(s3Base));
    if (s3Candidate) {
      return s3Candidate;
    }
  }

  const apiBase = `${api.defaults.baseURL || ""}`.trim().replace(/\/$/, "");
  if (apiBase) {
    const nonApiAbsolute = candidates.find(
      (candidate) =>
        (candidate.startsWith("http://") || candidate.startsWith("https://")) &&
        !candidate.startsWith(apiBase)
    );
    if (nonApiAbsolute) {
      return nonApiAbsolute;
    }
  } else {
    const absoluteCandidate = candidates.find(
      (candidate) => candidate.startsWith("http://") || candidate.startsWith("https://")
    );
    if (absoluteCandidate) {
      return absoluteCandidate;
    }
  }

  return candidates[candidates.length - 1];
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
  const [menuAvatarSrc, setMenuAvatarSrc] = useState<string>("");
  const [menuAvatarLoadFailed, setMenuAvatarLoadFailed] = useState<boolean>(false);

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

  const avatarPaths = useMemo(
    () => resolveMenuAvatarPaths(profile),
    [profile]
  );

  useEffect(() => {
    let isMounted = true;
    let objectUrlToRevoke = "";

    const loadAvatar = async () => {
      const avatarCandidates = buildAvatarCandidatesFromPaths(avatarPaths);
      setMenuAvatarLoadFailed(false);

      if (!avatarCandidates.length) {
        if (isMounted) {
          setMenuAvatarSrc("");
        }
        return;
      }

      const directFallbackCandidate = pickDirectAvatarCandidate(avatarCandidates);
      if (directFallbackCandidate.startsWith("blob:") || directFallbackCandidate.startsWith("data:")) {
        if (isMounted) {
          setMenuAvatarSrc(directFallbackCandidate);
        }
        return;
      }

      for (const candidate of avatarCandidates) {
        try {
          const response = await api.get<Blob>(candidate, { responseType: "blob" });
          if (!isMounted) {
            return;
          }

          objectUrlToRevoke = URL.createObjectURL(response.data);
          setMenuAvatarSrc(objectUrlToRevoke);
          return;
        } catch (_error) {
          // tenta o proximo candidato
        }
      }

      if (isMounted) {
        // Fallback final para imagem publica.
        setMenuAvatarSrc(directFallbackCandidate);
      }
    };

    void loadAvatar();

    return () => {
      isMounted = false;
      if (objectUrlToRevoke) {
        URL.revokeObjectURL(objectUrlToRevoke);
      }
    };
  }, [avatarPaths]);

  const onToggleTheme = (checked: boolean) => {
    setIsDark(checked);
    import("../../services/theme").then((mod) => {
      mod.setTheme(checked ? "dark" : "light");
    });
  };

  const displayName = resolveMenuDisplayName(profile) || "Perfil";
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
                      {menuAvatarSrc && !menuAvatarLoadFailed ? (
                        <img
                          src={menuAvatarSrc}
                          alt={displayName}
                          onError={() => {
                            setMenuAvatarLoadFailed(true);
                          }}
                        />
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
