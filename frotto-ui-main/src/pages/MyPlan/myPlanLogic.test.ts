import { BillingMeDTO, BillingPaymentStateDTO, PlanDTO, SubscriptionStatus } from "../../constants/BillingModels";
import { checkoutBlocksPurchase, checkoutNeedsRefresh, fleetUsage, isCheckoutInProgressError, isPlanCompatible, paymentNotice, resumableCheckoutUrl, sourceDetail, sourceLabel, usageState, vehicleRange } from "./myPlanLogic";
const billing=(changes:Partial<BillingMeDTO>={}):BillingMeDTO=>({planCode:"FREE",planName:"Free",subscriptionStatus:null,billingCycle:null,subscriptionSource:null,activeVehicleCount:0,vehicleLimit:2,canAddVehicle:true,needsUpgrade:false,requiredPlanCode:"FREE",requiredPlanName:"Free",currentMonthlyPrice:0,currentPeriodStart:null,currentPeriodEnd:null,grantExpiresAt:null,cancelAtPeriodEnd:false,...changes});
const plan=(changes:Partial<PlanDTO>={}):PlanDTO=>({code:"BRONZE",name:"Bronze",minVehicles:3,maxVehicles:5,monthlyBasePrice:29.9,billingModel:"FLAT",tiers:[],...changes});
const subscription=(status:SubscriptionStatus):BillingPaymentStateDTO=>({paymentProviderSubscription:{status,planCode:"BRONZE",billingCycle:"MONTHLY"},latestCheckout:null});
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
 it.each([["CREATED","Pagamento em preparação",true],["PROVIDER_PENDING","Aguardando confirmação do Mercado Pago",true],["PROVIDER_UNKNOWN","Estamos confirmando seu pagamento",true],["FAILED","Não foi possível iniciar o pagamento.",false],["CANCELED",null,false],["AUTHORIZED","Confirmação em andamento",false]] as const)("presents checkout %s without technical enums",(status,title,blocked)=>{const state=checkout(status);expect(paymentNotice(state)?.title||null).toBe(title);expect(checkoutBlocksPurchase(state)).toBe(blocked)});
 it("refreshes open states and authorized without a confirmed subscription",()=>{expect(checkoutNeedsRefresh(checkout("CREATED"))).toBe(true);expect(checkoutNeedsRefresh(checkout("PROVIDER_PENDING"))).toBe(true);expect(checkoutNeedsRefresh(checkout("PROVIDER_UNKNOWN"))).toBe(true);expect(checkoutNeedsRefresh(checkout("AUTHORIZED"))).toBe(true);expect(checkoutNeedsRefresh(subscription("ACTIVE"))).toBe(false)});
 it("recognizes only the stable backend conflict",()=>{expect(isCheckoutInProgressError({response:{status:409,data:{message:"error.BILLING_CHECKOUT_IN_PROGRESS"}}})).toBe(true);expect(isCheckoutInProgressError({response:{status:409,data:{message:"other"}}})).toBe(false)});
 it.each(["ADMIN_GRANT","GRANDFATHERED",null] as const)("keeps effective source %s separate from a paused paid subscription",source=>{expect(sourceLabel(source)).not.toContain("PAYMENT_PROVIDER");expect(paymentNotice(subscription("PAUSED"))?.title).toBe("Assinatura pausada")});
 it("trusts the backend canResume flag instead of re-deriving eligibility",()=>{
  expect(resumableCheckoutUrl(checkout("PROVIDER_PENDING",{canResume:true,checkoutUrl:"https://mp.test/resume"}))).toBe("https://mp.test/resume");
  expect(resumableCheckoutUrl(checkout("CREATED",{canResume:false,checkoutUrl:null}))).toBeNull();
  expect(resumableCheckoutUrl(checkout("FAILED",{canResume:false,checkoutUrl:null}))).toBeNull();
  expect(resumableCheckoutUrl(subscription("ACTIVE"))).toBeNull();
  expect(resumableCheckoutUrl(null)).toBeNull();
 });
});
