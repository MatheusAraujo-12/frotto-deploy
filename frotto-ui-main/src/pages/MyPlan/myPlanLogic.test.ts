import { BillingMeDTO, PlanDTO } from "../../constants/BillingModels";
import { fleetUsage, isPlanCompatible, sourceDetail, sourceLabel, usageState, vehicleRange } from "./myPlanLogic";
const billing=(changes:Partial<BillingMeDTO>={}):BillingMeDTO=>({planCode:"FREE",planName:"Free",subscriptionStatus:null,billingCycle:null,subscriptionSource:null,activeVehicleCount:0,vehicleLimit:2,canAddVehicle:true,needsUpgrade:false,requiredPlanCode:"FREE",requiredPlanName:"Free",currentMonthlyPrice:0,currentPeriodStart:null,currentPeriodEnd:null,grantExpiresAt:null,cancelAtPeriodEnd:false,...changes});
const plan=(changes:Partial<PlanDTO>={}):PlanDTO=>({code:"BRONZE",name:"Bronze",minVehicles:3,maxVehicles:5,monthlyBasePrice:29.9,billingModel:"FLAT",tiers:[],...changes});
describe("myPlanLogic",()=>{
 it.each([[0,true,"within"],[2,false,"reached"]] as const)("classifies FREE %s/2",(count,canAdd,expected)=>expect(usageState(billing({activeVehicleCount:count,canAddVehicle:canAdd}))).toBe(expected));
 it("classifies limits",()=>{expect(usageState(billing({activeVehicleCount:5,vehicleLimit:5,canAddVehicle:false}))).toBe("reached");expect(usageState(billing({activeVehicleCount:4,vehicleLimit:5}))).toBe("near")});
 it("renders unlimited Frotta",()=>expect(fleetUsage(billing({activeVehicleCount:150,vehicleLimit:null}))).toContain("Sem limite fixo"));
 it("uses friendly sources",()=>{expect(sourceLabel(null)).toBe("Plano gratuito");expect(sourceLabel("PAYMENT_PROVIDER")).toBe("Assinatura");expect(sourceLabel("GRANDFATHERED")).not.toMatch(/GRANDFATHERED/)});
 it("describes grants",()=>{expect(sourceDetail(billing({subscriptionSource:"ADMIN_GRANT"}))).toContain("sem data");expect(sourceDetail(billing({subscriptionSource:"ADMIN_GRANT",grantExpiresAt:"2026-08-30T00:00:00Z"}))).toContain("30/08/2026")});
 it("uses backend maximum for downgrade",()=>{expect(isPlanCompatible(plan(),10)).toBe(false);expect(isPlanCompatible(plan(),5)).toBe(true);expect(isPlanCompatible(plan({minVehicles:101,maxVehicles:null}),2)).toBe(true)});
 it("formats ranges",()=>{expect(vehicleRange(plan({minVehicles:0,maxVehicles:2}))).toBe("Até 2 veículos");expect(vehicleRange(plan())).toBe("3 a 5 veículos");expect(vehicleRange(plan({minVehicles:101,maxVehicles:null}))).toBe("101+ veículos")});
});
