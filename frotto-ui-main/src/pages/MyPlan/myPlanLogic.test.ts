import { BillingMeDTO, BillingPaymentStateDTO, PlanDTO, SubscriptionCancellationState, SubscriptionStatus } from "../../constants/BillingModels";
import { checkoutBlocksPurchase, checkoutNeedsRefresh, fleetUsage, hasPendingPlanChange, isCheckoutInProgressError, isNoticeRedundantWithGrantedPlan, isPlanChangeBlockedByCancellation, isRecurringSubscriptionExistsError, isPlanCompatible, isSubscriptionCancelable, paymentNotice, planChangeErrorMessage, planDirection, pendingPlanChangeMessage, recurringSubscriptionExistsMessage, remoteCancellationState, remoteCurrentPeriodEnd, resumableCheckoutUrl, sourceDetail, sourceLabel, usageState, vehicleRange } from "./myPlanLogic";
const billing=(changes:Partial<BillingMeDTO>={}):BillingMeDTO=>({planCode:"FREE",planName:"Free",subscriptionStatus:null,billingCycle:null,subscriptionSource:null,activeVehicleCount:0,vehicleLimit:2,canAddVehicle:true,needsUpgrade:false,requiredPlanCode:"FREE",requiredPlanName:"Free",currentMonthlyPrice:0,currentPeriodStart:null,currentPeriodEnd:null,grantExpiresAt:null,cancelAtPeriodEnd:false,cancellationState:"NONE",...changes});
const plan=(changes:Partial<PlanDTO>={}):PlanDTO=>({code:"BRONZE",name:"Bronze",minVehicles:3,maxVehicles:5,monthlyBasePrice:29.9,billingModel:"FLAT",tiers:[],...changes});
const subscription=(status:SubscriptionStatus,financiallyCovered=true,canCancel=true,cancellationState:SubscriptionCancellationState="NONE",currentPeriodEnd:string|null=null):BillingPaymentStateDTO=>({paymentProviderSubscription:{status,planCode:"BRONZE",billingCycle:"MONTHLY",financiallyCovered,canCancel,cancellationState,currentPeriodEnd},latestCheckout:null});
const checkout=(status:NonNullable<BillingPaymentStateDTO["latestCheckout"]>["status"],resume:Partial<Pick<NonNullable<BillingPaymentStateDTO["latestCheckout"]>,"canResume"|"checkoutUrl">> ={}):BillingPaymentStateDTO=>({paymentProviderSubscription:null,latestCheckout:{status,planCode:"SILVER",createdAt:"2026-08-28T12:00:00Z",canResume:false,checkoutUrl:null,...resume}});
describe("myPlanLogic",()=>{
 it.each([[0,true,"within"],[2,false,"reached"]] as const)("classifies FREE %s/2",(count,canAdd,expected)=>expect(usageState(billing({activeVehicleCount:count,canAddVehicle:canAdd}))).toBe(expected));
 it("classifies limits",()=>{expect(usageState(billing({activeVehicleCount:5,vehicleLimit:5,canAddVehicle:false}))).toBe("reached");expect(usageState(billing({activeVehicleCount:4,vehicleLimit:5}))).toBe("near")});
 it("renders unlimited Frotta",()=>expect(fleetUsage(billing({activeVehicleCount:150,vehicleLimit:null}))).toContain("Sem limite fixo"));
 it("uses friendly sources",()=>{expect(sourceLabel(null)).toBe("Plano gratuito");expect(sourceLabel("PAYMENT_PROVIDER")).toBe("Assinatura");expect(sourceLabel("GRANDFATHERED")).not.toMatch(/GRANDFATHERED/)});
 it("describes grants",()=>{expect(sourceDetail(billing({subscriptionSource:"ADMIN_GRANT"}))).toContain("sem data");expect(sourceDetail(billing({subscriptionSource:"ADMIN_GRANT",grantExpiresAt:"2026-08-30T00:00:00Z"}))).toContain("30/08/2026")});
 it("uses backend maximum for downgrade",()=>{expect(isPlanCompatible(plan(),10)).toBe(false);expect(isPlanCompatible(plan(),5)).toBe(true);expect(isPlanCompatible(plan({minVehicles:101,maxVehicles:null}),2)).toBe(true)});
 it("formats ranges",()=>{expect(vehicleRange(plan({minVehicles:0,maxVehicles:2}))).toBe("Até 2 veículos");expect(vehicleRange(plan())).toBe("3 a 5 veículos");expect(vehicleRange(plan({minVehicles:101,maxVehicles:null}))).toBe("101+ veículos")});
 it.each([["ACTIVE","Assinatura ativa"],["PAST_DUE","Pagamento pendente"],["PAUSED","Assinatura pausada"],["CANCELED","Assinatura encerrada"]] as const)("presents subscription %s commercially",(status,title)=>expect(paymentNotice(subscription(status))?.title).toBe(title));
 it("never claims a confirmed plan from status=ACTIVE alone (5G: Subscription.status is not financial proof)",()=>{
  const notice=paymentNotice(subscription("ACTIVE",false));
  expect(notice?.title).toBe("Pagamento em processamento");
  expect(notice?.title).not.toBe("Assinatura ativa");
  expect(notice?.detail).not.toContain("confirmado");
 });
 it("shows the confirmed banner once financial evidence backs the active status",()=>{
  expect(paymentNotice(subscription("ACTIVE",true))?.title).toBe("Assinatura ativa");
 });
 it("5G.11: suppresses 'pagamento em processamento' once BillingMeDTO already granted a PAYMENT_PROVIDER/ACTIVE plan (5G.10)",()=>{
  const notice=paymentNotice(subscription("ACTIVE",false));
  expect(notice?.title).toBe("Pagamento em processamento");
  expect(isNoticeRedundantWithGrantedPlan(notice,billing({subscriptionSource:"PAYMENT_PROVIDER",subscriptionStatus:"ACTIVE"}))).toBe(true);
 });
 it("5G.11: keeps the PROVIDER_PENDING checkout warning even when a granted plan exists elsewhere",()=>{
  const notice=paymentNotice(checkout("PROVIDER_PENDING"));
  expect(notice?.title).toBe("Aguardando confirmação do Mercado Pago");
  expect(isNoticeRedundantWithGrantedPlan(notice,billing({subscriptionSource:"PAYMENT_PROVIDER",subscriptionStatus:"ACTIVE"}))).toBe(false);
 });
 it("5G.11: never suppresses the processing notice when BillingMeDTO has not (yet) granted the plan",()=>{
  const notice=paymentNotice(subscription("ACTIVE",false));
  expect(isNoticeRedundantWithGrantedPlan(notice,billing())).toBe(false);
  expect(isNoticeRedundantWithGrantedPlan(notice,billing({subscriptionSource:"ADMIN_GRANT",subscriptionStatus:null}))).toBe(false);
 });
 it("5G.11: null notice is never treated as redundant",()=>{
  expect(isNoticeRedundantWithGrantedPlan(null,billing({subscriptionSource:"PAYMENT_PROVIDER",subscriptionStatus:"ACTIVE"}))).toBe(false);
 });
 it.each([["CREATED","Pagamento em preparação",true],["PROVIDER_PENDING","Aguardando confirmação do Mercado Pago",true],["PROVIDER_UNKNOWN","Estamos confirmando seu pagamento",true],["FAILED","Não foi possível iniciar o pagamento.",false],["CANCELED",null,false],["AUTHORIZED","Confirmação em andamento",false]] as const)("presents checkout %s without technical enums",(status,title,blocked)=>{const state=checkout(status);expect(paymentNotice(state)?.title||null).toBe(title);expect(checkoutBlocksPurchase(state)).toBe(blocked)});
 it("refreshes open states and authorized without a confirmed subscription",()=>{expect(checkoutNeedsRefresh(checkout("CREATED"))).toBe(true);expect(checkoutNeedsRefresh(checkout("PROVIDER_PENDING"))).toBe(true);expect(checkoutNeedsRefresh(checkout("PROVIDER_UNKNOWN"))).toBe(true);expect(checkoutNeedsRefresh(checkout("AUTHORIZED"))).toBe(true);expect(checkoutNeedsRefresh(subscription("ACTIVE"))).toBe(false)});
 it("recognizes only the stable backend conflict",()=>{expect(isCheckoutInProgressError({response:{status:409,data:{message:"error.BILLING_CHECKOUT_IN_PROGRESS"}}})).toBe(true);expect(isCheckoutInProgressError({response:{status:409,data:{message:"other"}}})).toBe(false)});
 it("5G.9-A: recognizes only the stable BILLING_RECURRING_SUBSCRIPTION_EXISTS conflict",()=>{
  expect(isRecurringSubscriptionExistsError({response:{status:409,data:{message:"error.BILLING_RECURRING_SUBSCRIPTION_EXISTS"}}})).toBe(true);
  expect(isRecurringSubscriptionExistsError({response:{status:409,data:{errorKey:"error.BILLING_RECURRING_SUBSCRIPTION_EXISTS"}}})).toBe(true);
  expect(isRecurringSubscriptionExistsError({response:{status:409,data:{message:"error.BILLING_CHECKOUT_IN_PROGRESS"}}})).toBe(false);
  expect(isRecurringSubscriptionExistsError({response:{status:400,data:{message:"error.BILLING_RECURRING_SUBSCRIPTION_EXISTS"}}})).toBe(false);
  expect(isRecurringSubscriptionExistsError(new Error("network"))).toBe(false);
 });
 it("5G.9-A: recurringSubscriptionExistsMessage adapts to payment-state without exposing provider identifiers",()=>{
  const base="Você já possui uma assinatura recorrente vinculada à sua conta.";
  expect(recurringSubscriptionExistsMessage(null)).toBe(`${base} Conclua ou cancele a assinatura atual antes de contratar outro plano.`);
  expect(recurringSubscriptionExistsMessage(subscription("ACTIVE",true,true,"NONE"))).toBe(`${base} Cancele a assinatura atual antes de contratar outro plano.`);
  expect(recurringSubscriptionExistsMessage(subscription("ACTIVE",true,true,"PENDING_CONFIRMATION"))).toContain("Estamos confirmando o cancelamento");
  expect(recurringSubscriptionExistsMessage(subscription("PAST_DUE",false,false,"NONE"))).toBe(`${base} Conclua ou cancele a assinatura atual antes de contratar outro plano.`);
  for(const message of [recurringSubscriptionExistsMessage(subscription("ACTIVE",true,true,"NONE")),recurringSubscriptionExistsMessage(null)]){
   expect(message).not.toMatch(/pre-|provider|external|idempotency/i);
  }
 });
 it.each(["ADMIN_GRANT","GRANDFATHERED",null] as const)("keeps effective source %s separate from a paused paid subscription",source=>{expect(sourceLabel(source)).not.toContain("PAYMENT_PROVIDER");expect(paymentNotice(subscription("PAUSED"))?.title).toBe("Assinatura pausada")});
 it("5G.9-B: cancelability comes only from payment-state.canCancel, never from BillingMeDTO's effective entitlement",()=>{
  // Reproduces the staging bug: preapproval authorized, Subscription PAYMENT_PROVIDER=ACTIVE,
  // no financial evidence yet -> /api/billing/me falls back to FREE/subscriptionSource=null,
  // but /api/billing/payment-state still sees the real remote contract and must drive the button.
  const uncoveredActiveButCancelable=subscription("ACTIVE",false,true);
  expect(isSubscriptionCancelable(uncoveredActiveButCancelable)).toBe(true);
  expect(isSubscriptionCancelable(null)).toBe(false);
  expect(isSubscriptionCancelable({paymentProviderSubscription:null,latestCheckout:null})).toBe(false);
  expect(isSubscriptionCancelable(subscription("CANCELED",false,false))).toBe(false);
 });
 it("5G.9-B: prefers payment-state's cancellationState/currentPeriodEnd, falling back to BillingMeDTO's",()=>{
  const paymentState=subscription("ACTIVE",false,true,"PENDING_CONFIRMATION","2026-10-01T12:00:00Z");
  const plainBilling=billing({cancellationState:"NONE",currentPeriodEnd:null});
  expect(remoteCancellationState(plainBilling,paymentState)).toBe("PENDING_CONFIRMATION");
  expect(remoteCurrentPeriodEnd(plainBilling,paymentState)).toBe("2026-10-01T12:00:00Z");
  // No payment-state contract (e.g. request failed) falls back to BillingMeDTO's own fields.
  expect(remoteCancellationState(billing({cancellationState:"CONFIRMED"}),null)).toBe("CONFIRMED");
  expect(remoteCurrentPeriodEnd(billing({currentPeriodEnd:"2026-11-01T12:00:00Z"}),null)).toBe("2026-11-01T12:00:00Z");
 });
 it("trusts the backend canResume flag instead of re-deriving eligibility",()=>{
  expect(resumableCheckoutUrl(checkout("PROVIDER_PENDING",{canResume:true,checkoutUrl:"https://mp.test/resume"}))).toBe("https://mp.test/resume");
  expect(resumableCheckoutUrl(checkout("CREATED",{canResume:false,checkoutUrl:null}))).toBeNull();
  expect(resumableCheckoutUrl(checkout("FAILED",{canResume:false,checkoutUrl:null}))).toBeNull();
  expect(resumableCheckoutUrl(subscription("ACTIVE"))).toBeNull();
  expect(resumableCheckoutUrl(null)).toBeNull();
 });

 // --- 5G.12: plan change --------------------------------------------------------------------
 it("5G.12: determines direction structurally from minVehicles, never from price",()=>{
  const bronze=plan({code:"BRONZE",minVehicles:3,maxVehicles:10,monthlyBasePrice:59.9});
  const silver=plan({code:"SILVER",minVehicles:11,maxVehicles:20,monthlyBasePrice:99.9});
  expect(planDirection(bronze,silver)).toBe("UPGRADE");
  expect(planDirection(silver,bronze)).toBe("DOWNGRADE");
  // A progressive plan's base price can be lower than a flat plan's while still being structurally higher.
  const platinum=plan({code:"PLATINUM",minVehicles:31,maxVehicles:100,monthlyBasePrice:79.9});
  const frotta=plan({code:"FROTTA",minVehicles:101,maxVehicles:null,monthlyBasePrice:10});
  expect(planDirection(platinum,frotta)).toBe("UPGRADE");
 });
 it("5G.12: reports a pending plan change and its banner text only when one exists",()=>{
  expect(hasPendingPlanChange(billing())).toBe(false);
  const pendingBilling=billing({pendingPlanCode:"BRONZE",pendingPlanName:"Bronze",pendingPlanPrice:15.9,planChangeEffectiveAt:"2026-10-09T00:00:00Z"});
  expect(hasPendingPlanChange(pendingBilling)).toBe(true);
  expect(pendingPlanChangeMessage(pendingBilling)).toBe("Mudança para Bronze agendada para 09/10/2026.");
  expect(pendingPlanChangeMessage(billing())).toBeNull();
 });
 it("5G.12: falls back to 'fim do período atual' when no effective date is known",()=>{
  expect(pendingPlanChangeMessage(billing({pendingPlanCode:"BRONZE",pendingPlanName:"Bronze",pendingPlanPrice:15.9,planChangeEffectiveAt:null})))
    .toBe("Mudança para Bronze agendada para o fim do período atual.");
 });
 it("5G.12: an already-scheduled cancellation (confirmed or pending) blocks plan-change actions",()=>{
  expect(isPlanChangeBlockedByCancellation("NONE")).toBe(false);
  expect(isPlanChangeBlockedByCancellation("PENDING_CONFIRMATION")).toBe(true);
  expect(isPlanChangeBlockedByCancellation("CONFIRMED")).toBe(true);
 });
 it("5G.12: maps every backend change-plan error key to a friendly message",()=>{
  const errorWith=(key:string)=>({response:{status:409,data:{message:`error.${key}`}}});
  expect(planChangeErrorMessage(errorWith("BILLING_PLAN_CHANGE_AMBIGUOUS_SUBSCRIPTION"))).toContain("múltiplas assinaturas ativas");
  expect(planChangeErrorMessage(errorWith("BILLING_PLAN_CHANGE_ALREADY_PENDING"))).toContain("mudança de plano agendada");
  expect(planChangeErrorMessage(errorWith("BILLING_PLAN_CHANGE_PROVIDER_REJECTED"))).toContain("não confirmou a alteração");
  expect(planChangeErrorMessage(errorWith("BILLING_PLAN_CHANGE_NOOP"))).toBe("Você já está no plano selecionado.");
  expect(planChangeErrorMessage({response:{status:500}})).toBe("Não foi possível concluir a mudança de plano. Tente novamente.");
 });
});
