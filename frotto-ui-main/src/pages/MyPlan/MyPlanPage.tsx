import {
  IonBadge, IonButton, IonButtons, IonCard, IonCardContent, IonContent, IonHeader,
  IonIcon, IonInput, IonMenuButton, IonModal, IonPage, IonProgressBar, IonSkeletonText,
  IonSpinner, IonTitle, IonToolbar,
} from "@ionic/react";
import { cardOutline, closeOutline, informationCircleOutline } from "ionicons/icons";
import { useCallback, useEffect, useRef, useState } from "react";
import { SubscriptionCancellationResultDTO, BillingMeDTO, BillingPaymentStateDTO, PLAN_LABELS, PlanChangeType, PlanDTO, PricePreviewDTO } from "../../constants/BillingModels";
import { getApiErrorMessage } from "../../services/apiErrorMessage";
import billingService from "../../services/billingService";
import { getToken, subscribeToTokenChanges } from "../../services/localStorage/localstorage";
import { formatDate, hasPendingPlanChange, isSubscriptionCancelable, checkoutBlocksPurchase, checkoutNeedsRefresh, fleetUsage, friendlyPlan, isCheckoutInProgressError, isNoticeRedundantWithGrantedPlan, isPlanChangeBlockedByCancellation, isRecurringSubscriptionExistsError, isPlanCompatible, money, paymentNotice, planChangeErrorMessage, planDirection, pendingPlanChangeMessage, recurringSubscriptionExistsMessage, remoteCancellationState, remoteCurrentPeriodEnd, resumableCheckoutUrl, sourceDetail, sourceLabel, statusLabel, usageState, vehicleRange } from "./myPlanLogic";
import "./MyPlanPage.css";
import { navigateToCheckout } from "./checkoutNavigation";

const MyPlanPage: React.FC = () => {
  const [billing, setBilling] = useState<BillingMeDTO | null>(null);
  const [plans, setPlans] = useState<PlanDTO[]>([]);
  const [paymentState, setPaymentState] = useState<BillingPaymentStateDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [vehicleInput, setVehicleInput] = useState("");
  const [preview, setPreview] = useState<PricePreviewDTO | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState("");
  const [selectedPlan, setSelectedPlan] = useState<PlanDTO | null>(null);
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const [checkoutError, setCheckoutError] = useState("");
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [cancellation, setCancellation] = useState<SubscriptionCancellationResultDTO | null>(null);
  const [cancelLoading, setCancelLoading] = useState(false);
  const [cancelError, setCancelError] = useState("");
  const cancelInFlight = useRef(false);
  const [changePlanTarget, setChangePlanTarget] = useState<PlanDTO | null>(null);
  const [changePlanDirection, setChangePlanDirection] = useState<PlanChangeType | null>(null);
  const [changePlanPreviewPrice, setChangePlanPreviewPrice] = useState<number | null>(null);
  const [changePlanPreviewLoading, setChangePlanPreviewLoading] = useState(false);
  const [changePlanLoading, setChangePlanLoading] = useState(false);
  const [changePlanError, setChangePlanError] = useState("");
  const changePlanRequestId = useRef(0);
  const plansRef = useRef<HTMLDivElement>(null);
  const loadId = useRef(0);
  const previewLoadId = useRef(0);

  const load = useCallback(async () => {
    const id = ++loadId.current;
    previewLoadId.current += 1;
    setCancelModalOpen(false); setCancellation(null); setCancelLoading(false); setCancelError(""); cancelInFlight.current = false;
    setBilling(null); setPlans([]); setPaymentState(null); setPreview(null); setSelectedPlan(null); setCheckoutLoading(false); setCheckoutError(""); setError(""); setLoading(true);
    setChangePlanTarget(null); setChangePlanDirection(null); setChangePlanPreviewPrice(null); setChangePlanPreviewLoading(false);
    setChangePlanLoading(false); setChangePlanError("");
    if (!getToken()) { setLoading(false); return; }
    try {
      const [me, payment, availablePlans] = await Promise.all([billingService.getMyBilling(), billingService.getBillingPaymentState(), billingService.getPlans()]);
      if (id !== loadId.current) return;
      setBilling(me); setPaymentState(payment); setPlans(availablePlans); setVehicleInput(String(me.activeVehicleCount));
    } catch (requestError) {
      if (id === loadId.current) setError(getApiErrorMessage(requestError, "Não foi possível carregar os dados do seu plano."));
    } finally {
      if (id === loadId.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const unsubscribe = subscribeToTokenChanges(() => void load());
    return () => { loadId.current += 1; previewLoadId.current += 1; unsubscribe(); };
  }, [load]);

  useEffect(() => {
    const count = Number(vehicleInput);
    setPreview(null); setPreviewError("");
    if (!Number.isInteger(count) || count < 0) return;
    const requestId = ++previewLoadId.current;
    const timer = window.setTimeout(async () => {
      setPreviewLoading(true);
      try { const result = await billingService.getPricePreview(count); if (requestId === previewLoadId.current) setPreview(result); }
      catch (requestError) { if (requestId === previewLoadId.current) setPreviewError(getApiErrorMessage(requestError, "Não foi possível calcular a estimativa.")); }
      finally { if (requestId === previewLoadId.current) setPreviewLoading(false); }
    }, 450);
    return () => window.clearTimeout(timer);
  }, [vehicleInput]);

  const openUpgrade = (plan: PlanDTO) => setSelectedPlan(plan);
  const continueToPayment = async () => {
    if (!selectedPlan || checkoutLoading || checkoutBlocksPurchase(paymentState)) return;
    const sessionId = loadId.current;
    setCheckoutError(""); setCheckoutLoading(true);
    try {
      const checkout = await billingService.createCheckout(selectedPlan.code);
      if (sessionId !== loadId.current) return;
      navigateToCheckout(checkout.checkoutUrl);
    } catch (requestError) {
      if (sessionId !== loadId.current) return;
      if (isCheckoutInProgressError(requestError)) {
        setCheckoutError("Já existe um pagamento em andamento. Aguarde a confirmação antes de tentar novamente.");
        setCheckoutLoading(false);
        try { setPaymentState(await billingService.getBillingPaymentState()); } catch { /* Keep the safe blocked message. */ }
        return;
      }
      if (isRecurringSubscriptionExistsError(requestError)) {
        // 5G.9 section A: backend refused a second remote recurrence. Refresh payment-state so the
        // error message (and the cancel action, if eligible) reflects the real existing contract.
        let latestPaymentState = paymentState;
        try { latestPaymentState = await billingService.getBillingPaymentState(); setPaymentState(latestPaymentState); } catch { /* Keep the safe message built from the state we already have. */ }
        setCheckoutError(recurringSubscriptionExistsMessage(latestPaymentState));
        setCheckoutLoading(false);
        return;
      }
      setCheckoutError(getApiErrorMessage(requestError, "Não foi possível iniciar o pagamento. Tente novamente."));
      setCheckoutLoading(false);
    }
  };

  const cancelSubscription = async () => {
    if (!billing || !isSubscriptionCancelable(paymentState) || cancelInFlight.current || (cancellation?.state ?? remoteCancellationState(billing, paymentState)) === "CONFIRMED") return;
    const sessionId = loadId.current;
    cancelInFlight.current = true;
    setCancelLoading(true); setCancelError("");
    try {
      const result = await billingService.cancelSubscription();
      if (sessionId !== loadId.current) return;
      setCancellation(result);
      setCancelLoading(false);
      setCancelModalOpen(false);
      if (result.state === "CONFIRMED") {
        try {
          const me = await billingService.getMyBilling();
          if (sessionId === loadId.current) setBilling(me);
        } catch {
          if (sessionId === loadId.current) setCancelError("O cancelamento foi confirmado, mas não foi possível atualizar os dados do plano. Atualize a página para consultar os dados atuais.");
        }
      }
    } catch (requestError) {
      // Do not expose gateway details or identifiers from an HTTP error payload.
      if (sessionId !== loadId.current) return;
      const rejectedMessage = "O Mercado Pago não aceitou o cancelamento neste momento. Sua assinatura permanece ativa e nenhuma alteração de cobrança foi confirmada.";
      const message = getApiErrorMessage(requestError, "", { BILLING_CANCELLATION_PROVIDER_REJECTED: rejectedMessage });
      if (message === rejectedMessage) {
        setCancellation(null);
        setBilling({ ...billing, cancelAtPeriodEnd: false, cancellationState: "NONE" });
        setPaymentState((current) => current?.paymentProviderSubscription
          ? { ...current, paymentProviderSubscription: { ...current.paymentProviderSubscription, cancellationState: "NONE" } }
          : current);
        setCancelLoading(false); setCancelModalOpen(false);
        setCancelError(rejectedMessage);
        try {
          const [me, payment] = await Promise.all([billingService.getMyBilling(), billingService.getBillingPaymentState()]);
          if (sessionId === loadId.current) { setBilling(me); setPaymentState(payment); }
        } catch { /* The controlled rejection already establishes the rollback. */ }
      } else {
        setCancelError("Não foi possível solicitar o cancelamento. Tente novamente.");
      }
    } finally {
      if (sessionId === loadId.current) { cancelInFlight.current = false; setCancelLoading(false); }
    }
  };

  /** 5G.12: prices exactly the target plan for the user's own fleet - never the flat base price, never computed on the frontend. */
  const openChangePlanModal = (plan: PlanDTO, direction: PlanChangeType) => {
    if (!billing) return;
    setChangePlanTarget(plan); setChangePlanDirection(direction); setChangePlanError("");
    setChangePlanPreviewPrice(null); setChangePlanPreviewLoading(true);
    const requestId = ++changePlanRequestId.current;
    billingService.getPricePreview(billing.activeVehicleCount, plan.code)
      .then((result) => { if (requestId === changePlanRequestId.current) setChangePlanPreviewPrice(result.monthlyPrice); })
      .catch(() => { if (requestId === changePlanRequestId.current) setChangePlanPreviewPrice(null); })
      .finally(() => { if (requestId === changePlanRequestId.current) setChangePlanPreviewLoading(false); });
  };

  const confirmChangePlan = async () => {
    if (!changePlanTarget || changePlanLoading) return;
    const sessionId = loadId.current;
    setChangePlanLoading(true); setChangePlanError("");
    try {
      const result = await billingService.changePlan(changePlanTarget.code);
      if (sessionId !== loadId.current) return;
      setChangePlanLoading(false);
      if (!result.pending) setChangePlanTarget(null);
      try {
        const [me, payment] = await Promise.all([billingService.getMyBilling(), billingService.getBillingPaymentState()]);
        if (sessionId === loadId.current) { setBilling(me); setPaymentState(payment); }
      } catch { /* Keep the just-applied local result; a manual refresh will pick up the latest state. */ }
    } catch (requestError) {
      if (sessionId !== loadId.current) return;
      setChangePlanError(planChangeErrorMessage(requestError));
      setChangePlanLoading(false);
      try {
        const [me, payment] = await Promise.all([billingService.getMyBilling(), billingService.getBillingPaymentState()]);
        if (sessionId === loadId.current) { setBilling(me); setPaymentState(payment); }
      } catch { /* The error message already reflects the safe outcome. */ }
    }
  };

  if (loading) return <IonPage id="my-plan-page"><PageHeader /><IonContent><div className="section-shell"><Loading /></div></IonContent></IonPage>;
  if (error || !billing) return <IonPage id="my-plan-page"><PageHeader /><IonContent><div className="section-shell"><div className="my-plan-state"><IonIcon icon={informationCircleOutline} /><h2>Não foi possível carregar seu plano</h2><p>{error || "Entre novamente para consultar seus dados."}</p><IonButton onClick={() => void load()}>Tentar novamente</IonButton></div></div></IonContent></IonPage>;

  const state = usageState(billing);
  const progress = billing.vehicleLimit == null ? 0 : Math.min(1, billing.activeVehicleCount / billing.vehicleLimit);
  const currentPrice = billing.subscriptionSource === "ADMIN_GRANT" ? "Sem cobrança" : money(billing.currentMonthlyPrice);
  const rawNotice = paymentNotice(paymentState);
  const notice = isNoticeRedundantWithGrantedPlan(rawNotice, billing) ? null : rawNotice;
  const checkoutBlocked = checkoutBlocksPurchase(paymentState);
  const cancelable = isSubscriptionCancelable(paymentState);
  const cancellationState = cancellation?.state ?? remoteCancellationState(billing, paymentState);
  const confirmed = cancellationState === "CONFIRMED";
  const pending = cancellationState === "PENDING_CONFIRMATION";
  const periodEnd = cancellation?.currentPeriodEnd ?? remoteCurrentPeriodEnd(billing, paymentState);
  const endDate = periodEnd && formatDate(periodEnd) !== "—" ? formatDate(periodEnd) : null;
  const resumeUrl = resumableCheckoutUrl(paymentState);
  // 5G.12: only a PAYMENT_PROVIDER contract that is actually the effective plan right now can have
  // its plan changed in place - everyone else (FREE, ADMIN_GRANT, GRANDFATHERED) keeps using the
  // existing checkout/no-op flows below, never the change-plan modal.
  const canChangePlan = billing.subscriptionSource === "PAYMENT_PROVIDER" && billing.subscriptionStatus === "ACTIVE";
  const changePlanBlockedByCancellation = isPlanChangeBlockedByCancellation(cancellationState);
  const pendingChange = hasPendingPlanChange(billing);
  const pendingChangeBanner = pendingPlanChangeMessage(billing);
  const currentPlanObj = plans.find((plan) => plan.code === billing.planCode) || null;

  return <IonPage id="my-plan-page">
    <PageHeader />
    <IonContent>
      <div className="section-shell my-plan-shell">
        {notice && <div className={`my-plan-alert my-plan-alert--${notice.tone}`} role={notice.tone === "warning" || notice.tone === "danger" ? "alert" : "status"}><strong>{notice.title}</strong><span>{notice.detail}</span>{resumeUrl && <IonButton size="small" onClick={() => navigateToCheckout(resumeUrl)}>Continuar pagamento</IonButton>}{checkoutNeedsRefresh(paymentState) && <IonButton size="small" fill="outline" onClick={() => void load()}>Atualizar status</IonButton>}</div>}
        <IonCard className="my-plan-current">
          <IonCardContent>
            <div className="my-plan-current__header"><div><span className="my-plan-eyebrow">Seu plano</span><h1>{friendlyPlan(billing.planCode)}</h1><p>{sourceDetail(billing)}</p></div><IonBadge>{sourceLabel(billing.subscriptionSource)}</IonBadge></div>
            {cancellation?.hasResidualActiveContract ? <div className="my-plan-alert" role="alert"><span>Uma recorrência foi cancelada, mas ainda existe outra assinatura recorrente ativa. Atualize o estado e tente cancelar novamente.</span><IonButton fill="outline" onClick={() => void load()}>Atualizar estado</IonButton></div> : confirmed && <div className="my-plan-alert" role="status"><IonBadge color="success">Cancelamento agendado</IonBadge><span>{endDate ? `Seu plano ficará ativo até ${endDate}. Não haverá nova renovação.` : "Seu plano ficará ativo até o fim do período atual. Não haverá nova renovação."}</span></div>}
            {pending && <div className="my-plan-alert" role="status">Estamos confirmando o cancelamento com o Mercado Pago.</div>}
            {!confirmed && !pending && pendingChangeBanner && <div className="my-plan-alert" role="status">{pendingChangeBanner}</div>}
            {changePlanBlockedByCancellation && <div className="my-plan-alert" role="status">Sua assinatura já está programada para encerrar em {endDate || "breve"}.</div>}
            {cancelable && !confirmed && <>
              <IonButton fill="outline" disabled={cancelLoading} onClick={() => { if (pending) void cancelSubscription(); else { setCancelError(""); setCancelModalOpen(true); } }}>{cancelLoading ? <><IonSpinner name="crescent" /> Cancelando...</> : pending ? "Tentar novamente" : "Cancelar assinatura"}</IonButton>
            </>}
            {cancelError && !cancelModalOpen && <p className="my-plan-preview-error" role="alert">{cancelError}</p>}
            <div className="my-plan-facts"><div><span>Preço atual</span><strong>{currentPrice}</strong></div><div><span>Status</span><strong>{statusLabel(billing.subscriptionStatus)}</strong></div><div><span>Uso da frota</span><strong>{fleetUsage(billing)}</strong></div></div>
            <div className={`my-plan-usage my-plan-usage--${state}`}><div><strong>{state === "reached" ? "Limite atingido" : state === "near" ? "Próximo do limite" : "Dentro do limite"}</strong><span>{fleetUsage(billing)}</span></div>{billing.vehicleLimit != null && <IonProgressBar value={progress} aria-label={fleetUsage(billing)} />}</div>
            {!billing.canAddVehicle && <div className="my-plan-limit"><span>Você atingiu o limite de veículos do seu plano.</span><IonButton size="small" onClick={() => plansRef.current?.scrollIntoView({ behavior: "smooth" })}>Ver opções de upgrade</IonButton></div>}
            {billing.planCode !== billing.requiredPlanCode && <div className="my-plan-recommendation">Com a quantidade atual de veículos, o plano recomendado é {friendlyPlan(billing.requiredPlanCode)}.</div>}
          </IonCardContent>
        </IonCard>

        <section ref={plansRef} className="my-plan-section"><div className="my-plan-heading"><span className="my-plan-eyebrow">Compare</span><h2>Planos disponíveis</h2><p>Escolha uma estrutura que acompanhe o tamanho da sua frota.</p></div><div className="my-plan-grid">
          {plans.map((plan) => {
            const current = plan.code === billing.planCode;
            const recommended = plan.code === billing.requiredPlanCode;
            const compatible = isPlanCompatible(plan, billing.activeVehicleCount);
            const actionsBlocked = changePlanBlockedByCancellation || (pendingChange && !current);
            let action: React.ReactNode = null;
            let incompatibleReason: React.ReactNode = null;
            if (current) {
              // No action - "Seu plano atual" badge above already communicates this.
            } else if (!canChangePlan) {
              // FREE (or no effective PAYMENT_PROVIDER contract yet): the only path to a paid plan is checkout.
              action = <IonButton expand="block" fill={compatible ? "solid" : "outline"} disabled={!compatible || checkoutBlocked} onClick={() => openUpgrade(plan)}>Escolher plano</IonButton>;
              if (!compatible) incompatibleReason = <p className="my-plan-incompatible">Sua frota atual excede o limite deste plano.</p>;
              else if (checkoutBlocked) incompatibleReason = <p className="my-plan-payment-blocked">Aguarde a confirmação do pagamento em andamento.</p>;
            } else if (plan.code === "FREE") {
              action = <IonButton expand="block" fill="outline" disabled={actionsBlocked || cancelLoading} onClick={() => { setCancelError(""); setCancelModalOpen(true); }}>Mudar para Gratuito</IonButton>;
            } else if (!compatible) {
              action = <IonButton expand="block" fill="outline" disabled>Fazer downgrade</IonButton>;
              incompatibleReason = <p className="my-plan-incompatible">Sua frota atual excede o limite deste plano.</p>;
            } else {
              const direction = currentPlanObj ? planDirection(currentPlanObj, plan) : "UPGRADE";
              const label = direction === "UPGRADE" ? "Fazer upgrade" : "Fazer downgrade";
              action = <IonButton expand="block" fill={direction === "UPGRADE" ? "solid" : "outline"} disabled={actionsBlocked} onClick={() => openChangePlanModal(plan, direction)}>{label}</IonButton>;
              if (pendingChange && !current) incompatibleReason = <p className="my-plan-payment-blocked">Conclua ou cancele a mudança de plano já agendada antes de solicitar outra.</p>;
            }
            return <IonCard key={plan.code} className={`my-plan-plan${recommended ? " my-plan-plan--recommended" : ""}`}><IonCardContent>
            <div className="my-plan-plan__badges">{current && <IonBadge color="primary">Seu plano atual</IonBadge>}{recommended && <IonBadge color="success">Recomendado para sua frota</IonBadge>}</div><h3>{PLAN_LABELS[plan.code]}</h3><p className="my-plan-range">{vehicleRange(plan)}</p><div className="my-plan-price">{plan.billingModel === "PROGRESSIVE" && <small>Base </small>}<strong>{money(plan.monthlyBasePrice)}</strong><small>/mês</small></div><p>{plan.billingModel === "PROGRESSIVE" ? "Cobrança progressiva conforme a frota" : "Valor mensal fixo"}</p>
            {plan.billingModel === "PROGRESSIVE" && plan.tiers.map((tier) => <small key={`${tier.fromVehicleCount}-${tier.toVehicleCount}`}>+ {money(tier.pricePerVehicle)} por veículo de {tier.fromVehicleCount}{tier.toVehicleCount ? ` a ${tier.toVehicleCount}` : " em diante"}</small>)}
            {action}{incompatibleReason}
          </IonCardContent></IonCard>; })}
        </div></section>

        <section className="my-plan-simulator"><div className="my-plan-heading"><span className="my-plan-eyebrow">Planeje</span><h2>Simule sua frota</h2><p>O preço final é calculado pelo próprio site, sem estimativas locais.</p></div><label htmlFor="fleet-size">Quantos veículos você pretende gerenciar?</label><IonInput id="fleet-size" type="number" min="0" inputmode="numeric" value={vehicleInput} onIonChange={(event) => setVehicleInput(event.detail.value || "")} />
          {previewLoading && <div className="my-plan-preview-loading"><IonSpinner name="crescent" /> Calculando...</div>}{previewError && <p className="my-plan-preview-error">{previewError}</p>}{preview && !previewLoading && <div className="my-plan-preview"><div><span>Plano recomendado</span><strong>{friendlyPlan(preview.planCode)}</strong></div><div><span>Preço mensal estimado</span><strong>{money(preview.monthlyPrice)}</strong></div><div><span>Média por veículo</span><strong>{money(preview.averagePricePerVehicle)}</strong></div>{preview.components.length > 0 && <div className="my-plan-components"><h3>Composição do preço</h3>{preview.components.map((component, index) => <p key={`${component.fromVehicleCount}-${index}`}>{component.quantity} veículos × {money(component.unitPrice)}: <strong>{money(component.subtotal)}</strong></p>)}<p>Total: <strong>{money(preview.monthlyPrice)}</strong></p></div>}</div>}
        </section>
      </div>
    </IonContent>
    <IonModal isOpen={cancelModalOpen} canDismiss={!cancelLoading} backdropDismiss={!cancelLoading} onDidDismiss={() => { if (!cancelInFlight.current) setCancelModalOpen(false); }} className="my-plan-modal">
      <IonHeader><IonToolbar><IonTitle>Cancelar assinatura</IonTitle></IonToolbar></IonHeader>
      <IonContent><div className="my-plan-modal__body">
        <h2>Cancelar sua assinatura?</h2>
        <p>{endDate ? `Você continuará com acesso ao plano até ${endDate}.` : "Você continuará com acesso ao plano até o fim do período atual."} Após essa data, não haverá nova cobrança e sua conta seguirá o plano disponível conforme as regras atuais do Frotto.</p>
        {cancelError && <p className="my-plan-preview-error" role="alert">{cancelError}</p>}
        <IonButton expand="block" fill="outline" disabled={cancelLoading} onClick={() => setCancelModalOpen(false)}>Manter assinatura</IonButton>
        <IonButton expand="block" disabled={cancelLoading} onClick={() => void cancelSubscription()}>{cancelLoading ? <><IonSpinner name="crescent" /> Cancelando...</> : "Confirmar cancelamento"}</IonButton>
      </div></IonContent>
    </IonModal>
    <IonModal isOpen={Boolean(selectedPlan)} onDidDismiss={() => { if (!checkoutLoading) { setSelectedPlan(null); setCheckoutError(""); } }} className="my-plan-modal"><IonHeader><IonToolbar><IonTitle>Resumo do plano</IonTitle><IonButtons slot="end"><IonButton aria-label="Fechar" disabled={checkoutLoading} onClick={() => setSelectedPlan(null)}><IonIcon slot="icon-only" icon={closeOutline} /></IonButton></IonButtons></IonToolbar></IonHeader><IonContent>{selectedPlan && <div className="my-plan-modal__body"><IonIcon icon={cardOutline} /><h2>{PLAN_LABELS[selectedPlan.code]}</h2><div><span>Frota atual</span><strong>{billing.activeVehicleCount} veículos</strong></div><div><span>Preço estimado</span><strong>{preview?.vehicleCount === billing.activeVehicleCount && preview.planCode === selectedPlan.code ? money(preview.monthlyPrice) : `${money(selectedPlan.monthlyBasePrice)} (base)`}</strong></div><div><span>Ciclo</span><strong>Mensal</strong></div><p>Você será direcionado ao ambiente seguro do Mercado Pago.</p>{checkoutError && <p className="my-plan-preview-error" role="alert">{checkoutError}</p>}<IonButton expand="block" disabled={checkoutLoading || checkoutBlocked} onClick={() => void continueToPayment()}>{checkoutLoading ? <><IonSpinner name="crescent" /> Processando...</> : "Continuar para pagamento"}</IonButton></div>}</IonContent></IonModal>
    <IonModal isOpen={Boolean(changePlanTarget)} canDismiss={!changePlanLoading} backdropDismiss={!changePlanLoading} onDidDismiss={() => { if (!changePlanLoading) { setChangePlanTarget(null); setChangePlanError(""); } }} className="my-plan-modal">
      <IonHeader><IonToolbar><IonTitle>{changePlanDirection === "UPGRADE" ? "Confirmar upgrade" : "Agendar downgrade"}</IonTitle></IonToolbar></IonHeader>
      <IonContent>{changePlanTarget && <div className="my-plan-modal__body">
        <h2>Alterar de {friendlyPlan(billing.planCode)} para {friendlyPlan(changePlanTarget.code)}</h2>
        {changePlanDirection === "UPGRADE" ? <>
          <div><span>Novo valor</span><strong>{changePlanPreviewLoading ? "Calculando..." : changePlanPreviewPrice != null ? `${money(changePlanPreviewPrice)}/mês` : "—"}</strong></div>
          <p>O plano {friendlyPlan(changePlanTarget.code)} será liberado imediatamente. O novo valor será utilizado nas próximas renovações. Não haverá cobrança proporcional pelo período atual.</p>
        </> : <>
          <p>Seu plano {friendlyPlan(billing.planCode)} continuará disponível{endDate ? ` até ${endDate}` : " até o fim do período atual"}. Depois dessa data, sua assinatura passará para {friendlyPlan(changePlanTarget.code)} por {changePlanPreviewLoading ? "..." : changePlanPreviewPrice != null ? `${money(changePlanPreviewPrice)}/mês` : "—"}, após a renovação do próximo ciclo.</p>
        </>}
        {changePlanError && <p className="my-plan-preview-error" role="alert">{changePlanError}</p>}
        <IonButton expand="block" fill="outline" disabled={changePlanLoading} onClick={() => { setChangePlanTarget(null); setChangePlanError(""); }}>{changePlanDirection === "UPGRADE" ? "Cancelar" : "Voltar"}</IonButton>
        <IonButton expand="block" disabled={changePlanLoading || changePlanPreviewLoading} onClick={() => void confirmChangePlan()}>{changePlanLoading ? <><IonSpinner name="crescent" /> Processando...</> : changePlanDirection === "UPGRADE" ? "Confirmar upgrade" : "Agendar downgrade"}</IonButton>
      </div>}</IonContent>
    </IonModal>
  </IonPage>;
};

const PageHeader = () => <IonHeader><IonToolbar><IonButtons slot="start"><IonMenuButton menu="main-menu" autoHide={false} /></IonButtons><IonTitle>Meu Plano</IonTitle></IonToolbar></IonHeader>;
const Loading = () => <div className="my-plan-loading" aria-label="Carregando plano"><IonSkeletonText animated style={{ width: "34%", height: 24 }} /><IonSkeletonText animated style={{ width: "100%", height: 180 }} /><IonSkeletonText animated style={{ width: "100%", height: 260 }} /></div>;
export default MyPlanPage;
