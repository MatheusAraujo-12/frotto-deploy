import {
  IonBadge, IonButton, IonButtons, IonCard, IonCardContent, IonContent, IonHeader,
  IonIcon, IonInput, IonMenuButton, IonModal, IonPage, IonProgressBar, IonSkeletonText,
  IonSpinner, IonTitle, IonToolbar,
} from "@ionic/react";
import { cardOutline, closeOutline, informationCircleOutline } from "ionicons/icons";
import { useCallback, useEffect, useRef, useState } from "react";
import { BillingMeDTO, PLAN_LABELS, PlanDTO, PricePreviewDTO } from "../../constants/BillingModels";
import { getApiErrorMessage } from "../../services/apiErrorMessage";
import billingService from "../../services/billingService";
import { getToken, subscribeToTokenChanges } from "../../services/localStorage/localstorage";
import { fleetUsage, friendlyPlan, isPlanCompatible, money, sourceDetail, sourceLabel, statusLabel, usageState, vehicleRange } from "./myPlanLogic";
import "./MyPlanPage.css";

const MyPlanPage: React.FC = () => {
  const [billing, setBilling] = useState<BillingMeDTO | null>(null);
  const [plans, setPlans] = useState<PlanDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [vehicleInput, setVehicleInput] = useState("");
  const [preview, setPreview] = useState<PricePreviewDTO | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState("");
  const [selectedPlan, setSelectedPlan] = useState<PlanDTO | null>(null);
  const plansRef = useRef<HTMLDivElement>(null);
  const loadId = useRef(0);

  const load = useCallback(async () => {
    const id = ++loadId.current;
    setBilling(null); setPlans([]); setPreview(null); setError(""); setLoading(true);
    if (!getToken()) { setLoading(false); return; }
    try {
      const [me, availablePlans] = await Promise.all([billingService.getMyBilling(), billingService.getPlans()]);
      if (id !== loadId.current) return;
      setBilling(me); setPlans(availablePlans); setVehicleInput(String(me.activeVehicleCount));
    } catch (requestError) {
      if (id === loadId.current) setError(getApiErrorMessage(requestError, "Não foi possível carregar os dados do seu plano."));
    } finally {
      if (id === loadId.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const unsubscribe = subscribeToTokenChanges(() => void load());
    return () => { loadId.current += 1; unsubscribe(); };
  }, [load]);

  useEffect(() => {
    const count = Number(vehicleInput);
    setPreview(null); setPreviewError("");
    if (!Number.isInteger(count) || count < 0) return;
    const timer = window.setTimeout(async () => {
      setPreviewLoading(true);
      try { setPreview(await billingService.getPricePreview(count)); }
      catch (requestError) { setPreviewError(getApiErrorMessage(requestError, "Não foi possível calcular a estimativa.")); }
      finally { setPreviewLoading(false); }
    }, 450);
    return () => window.clearTimeout(timer);
  }, [vehicleInput]);

  const openUpgrade = (plan: PlanDTO) => setSelectedPlan(plan);

  if (loading) return <IonPage id="my-plan-page"><PageHeader /><IonContent><div className="section-shell"><Loading /></div></IonContent></IonPage>;
  if (error || !billing) return <IonPage id="my-plan-page"><PageHeader /><IonContent><div className="section-shell"><div className="my-plan-state"><IonIcon icon={informationCircleOutline} /><h2>Não foi possível carregar seu plano</h2><p>{error || "Entre novamente para consultar seus dados."}</p><IonButton onClick={() => void load()}>Tentar novamente</IonButton></div></div></IonContent></IonPage>;

  const state = usageState(billing);
  const progress = billing.vehicleLimit == null ? 0 : Math.min(1, billing.activeVehicleCount / billing.vehicleLimit);
  const currentPrice = billing.subscriptionSource === "ADMIN_GRANT" ? "Sem cobrança" : money(billing.currentMonthlyPrice);

  return <IonPage id="my-plan-page">
    <PageHeader />
    <IonContent>
      <div className="section-shell my-plan-shell">
        {billing.subscriptionStatus === "PAST_DUE" && <div className="my-plan-alert" role="alert"><strong>Há uma pendência na sua assinatura.</strong><span>Entre em contato com o suporte.</span></div>}
        <IonCard className="my-plan-current">
          <IonCardContent>
            <div className="my-plan-current__header"><div><span className="my-plan-eyebrow">Seu plano</span><h1>{friendlyPlan(billing.planCode)}</h1><p>{sourceDetail(billing)}</p></div><IonBadge>{sourceLabel(billing.subscriptionSource)}</IonBadge></div>
            <div className="my-plan-facts"><div><span>Preço atual</span><strong>{currentPrice}</strong></div><div><span>Status</span><strong>{statusLabel(billing.subscriptionStatus)}</strong></div><div><span>Uso da frota</span><strong>{fleetUsage(billing)}</strong></div></div>
            <div className={`my-plan-usage my-plan-usage--${state}`}><div><strong>{state === "reached" ? "Limite atingido" : state === "near" ? "Próximo do limite" : "Dentro do limite"}</strong><span>{fleetUsage(billing)}</span></div>{billing.vehicleLimit != null && <IonProgressBar value={progress} aria-label={fleetUsage(billing)} />}</div>
            {!billing.canAddVehicle && <div className="my-plan-limit"><span>Você atingiu o limite de veículos do seu plano.</span><IonButton size="small" onClick={() => plansRef.current?.scrollIntoView({ behavior: "smooth" })}>Ver opções de upgrade</IonButton></div>}
            {billing.planCode !== billing.requiredPlanCode && <div className="my-plan-recommendation">Com a quantidade atual de veículos, o plano recomendado é {friendlyPlan(billing.requiredPlanCode)}.</div>}
          </IonCardContent>
        </IonCard>

        <section ref={plansRef} className="my-plan-section"><div className="my-plan-heading"><span className="my-plan-eyebrow">Compare</span><h2>Planos disponíveis</h2><p>Escolha uma estrutura que acompanhe o tamanho da sua frota.</p></div><div className="my-plan-grid">
          {plans.map((plan) => { const current = plan.code === billing.planCode; const recommended = plan.code === billing.requiredPlanCode; const compatible = isPlanCompatible(plan, billing.activeVehicleCount); return <IonCard key={plan.code} className={`my-plan-plan${recommended ? " my-plan-plan--recommended" : ""}`}><IonCardContent>
            <div className="my-plan-plan__badges">{current && <IonBadge color="primary">Seu plano atual</IonBadge>}{recommended && <IonBadge color="success">Recomendado para sua frota</IonBadge>}</div><h3>{PLAN_LABELS[plan.code]}</h3><p className="my-plan-range">{vehicleRange(plan)}</p><div className="my-plan-price">{plan.billingModel === "PROGRESSIVE" && <small>Base </small>}<strong>{money(plan.monthlyBasePrice)}</strong><small>/mês</small></div><p>{plan.billingModel === "PROGRESSIVE" ? "Cobrança progressiva conforme a frota" : "Valor mensal fixo"}</p>
            {plan.billingModel === "PROGRESSIVE" && plan.tiers.map((tier) => <small key={`${tier.fromVehicleCount}-${tier.toVehicleCount}`}>+ {money(tier.pricePerVehicle)} por veículo de {tier.fromVehicleCount}{tier.toVehicleCount ? ` a ${tier.toVehicleCount}` : " em diante"}</small>)}
            {!current && <IonButton expand="block" fill={compatible ? "solid" : "outline"} disabled={!compatible} onClick={() => openUpgrade(plan)}>Escolher plano</IonButton>}{!compatible && <p className="my-plan-incompatible">Sua frota atual excede o limite deste plano.</p>}
          </IonCardContent></IonCard>; })}
        </div></section>

        <section className="my-plan-simulator"><div className="my-plan-heading"><span className="my-plan-eyebrow">Planeje</span><h2>Simule sua frota</h2><p>O preço final é calculado pelo backend, sem estimativas locais.</p></div><label htmlFor="fleet-size">Quantos veículos você pretende gerenciar?</label><IonInput id="fleet-size" type="number" min="0" inputmode="numeric" value={vehicleInput} onIonChange={(event) => setVehicleInput(event.detail.value || "")} />
          {previewLoading && <div className="my-plan-preview-loading"><IonSpinner name="crescent" /> Calculando...</div>}{previewError && <p className="my-plan-preview-error">{previewError}</p>}{preview && !previewLoading && <div className="my-plan-preview"><div><span>Plano recomendado</span><strong>{friendlyPlan(preview.planCode)}</strong></div><div><span>Preço mensal estimado</span><strong>{money(preview.monthlyPrice)}</strong></div><div><span>Média por veículo</span><strong>{money(preview.averagePricePerVehicle)}</strong></div>{preview.components.length > 0 && <div className="my-plan-components"><h3>Composição do preço</h3>{preview.components.map((component, index) => <p key={`${component.fromVehicleCount}-${index}`}>{component.quantity} veículos × {money(component.unitPrice)}: <strong>{money(component.subtotal)}</strong></p>)}<p>Total: <strong>{money(preview.monthlyPrice)}</strong></p></div>}</div>}
        </section>
      </div>
    </IonContent>
    <IonModal isOpen={Boolean(selectedPlan)} onDidDismiss={() => setSelectedPlan(null)} className="my-plan-modal"><IonHeader><IonToolbar><IonTitle>Resumo do plano</IonTitle><IonButtons slot="end"><IonButton aria-label="Fechar" onClick={() => setSelectedPlan(null)}><IonIcon slot="icon-only" icon={closeOutline} /></IonButton></IonButtons></IonToolbar></IonHeader><IonContent>{selectedPlan && <div className="my-plan-modal__body"><IonIcon icon={cardOutline} /><h2>{PLAN_LABELS[selectedPlan.code]}</h2><div><span>Frota atual</span><strong>{billing.activeVehicleCount} veículos</strong></div><div><span>Preço estimado</span><strong>{preview?.vehicleCount === billing.activeVehicleCount && preview.planCode === selectedPlan.code ? money(preview.monthlyPrice) : `${money(selectedPlan.monthlyBasePrice)} (base)`}</strong></div><div><span>Ciclo</span><strong>Mensal</strong></div><p>O pagamento online será disponibilizado na próxima etapa.</p><IonButton expand="block" disabled>Continuar para pagamento</IonButton></div>}</IonContent></IonModal>
  </IonPage>;
};

const PageHeader = () => <IonHeader><IonToolbar><IonButtons slot="start"><IonMenuButton menu="main-menu" autoHide={false} /></IonButtons><IonTitle>Meu Plano</IonTitle></IonToolbar></IonHeader>;
const Loading = () => <div className="my-plan-loading" aria-label="Carregando plano"><IonSkeletonText animated style={{ width: "34%", height: 24 }} /><IonSkeletonText animated style={{ width: "100%", height: 180 }} /><IonSkeletonText animated style={{ width: "100%", height: 260 }} /></div>;
export default MyPlanPage;
