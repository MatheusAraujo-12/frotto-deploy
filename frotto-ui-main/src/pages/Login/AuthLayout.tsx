import { IonBackButton, IonButtons, IonContent, IonHeader, IonPage, IonProgressBar, IonTitle, IonToolbar } from "@ionic/react";
import { ReactNode, useEffect, useRef } from "react";
import { TEXT } from "../../constants/texts";
import "./AuthPages.css";

interface AuthLayoutProps {
  pageId: string;
  title: string;
  description: string;
  isLoading: boolean;
  back?: boolean;
  children: ReactNode;
}

// Presentation only. Each page owns its form, requests and navigation.
const AuthLayout: React.FC<AuthLayoutProps> = ({ pageId, title, description, isLoading, back, children }) => {
  const panelRef = useRef<HTMLElement>(null);

  useEffect(() => {
    let active = true;
    // Ionic 6 only inherits ARIA on initial mount. Keep native inputs associated
    // with subsequent Form* errors, scoped to this page in Ionic's route cache.
    panelRef.current?.querySelectorAll("ion-input").forEach(async (host) => {
      const input = await host.getInputElement?.();
      if (!active || !input) return;
      const error = host.closest(".app-form-field")?.querySelector<HTMLElement>(".app-form-error");
      if (error) {
        if (!error.id.startsWith(`${pageId}-`)) error.id = `${pageId}-${error.id}`;
        host.setAttribute("aria-describedby", error.id);
        input.setAttribute("aria-invalid", "true");
        input.setAttribute("aria-describedby", error.id);
      } else {
        input.removeAttribute("aria-invalid");
        input.removeAttribute("aria-describedby");
      }
    });
    return () => { active = false; };
  }, [children, pageId]);

  return (
  <IonPage id={pageId} className="auth-page">
    <IonHeader className="ion-no-border">
      <IonToolbar className="auth-toolbar">
        {back && <IonButtons slot="start"><IonBackButton defaultHref="/" text="Voltar" aria-label="Voltar para Login" /></IonButtons>}
        <IonTitle>{back ? TEXT.doRegister : TEXT.appTitle}</IonTitle>
        {isLoading && <IonProgressBar type="indeterminate" aria-label="Processando solicitação" />}
      </IonToolbar>
    </IonHeader>
    <IonContent className="auth-content">
      <main className="auth-shell">
        <aside className="auth-brand" aria-label="Frotto — gestão de frota">
          <img className="auth-brand__logo" src={`${process.env.PUBLIC_URL || ""}/assets/icon/icon.png`} alt={TEXT.appTitle} />
          <div className="auth-brand__context">
            <p className="auth-brand__eyebrow">GESTÃO DE FROTA</p>
            <h2>Sua frota.<br />Uma visão completa.</h2>
            <p>Veículos, motoristas e manutenção em um só lugar.</p>
          </div>
        </aside>
        <section ref={panelRef} className="auth-panel" aria-labelledby={`${pageId}-heading`}>
          <header className="auth-panel__header">
            <h1 id={`${pageId}-heading`}>{title}</h1>
            <p>{description}</p>
          </header>
          {children}
          <div className="auth-status" role="status" aria-live="polite">
            {isLoading ? "Processando solicitação…" : ""}
          </div>
        </section>
      </main>
    </IonContent>
  </IonPage>
  );
};

export default AuthLayout;
