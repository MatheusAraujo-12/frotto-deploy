import billingService from "./billingService";
import { money } from "../pages/MyPlan/myPlanLogic";

export async function fleetBillingFeedback(action: "added" | "deleted"): Promise<string> {
  const message = action === "added" ? "Veículo adicionado com sucesso." : "Veículo excluído. O histórico foi preservado.";
  try {
    const billing = await billingService.getMyBilling();
    if ((billing.planCode === "PLATINUM" || billing.planCode === "FROTTA") && billing.projectedNextRenewalPrice != null) {
      return `${message} Com ${billing.billableVehicleCount ?? billing.activeVehicleCount} veículos cadastrados, a mensalidade estimada ${billing.nextRenewalLockedAt ? "do ciclo seguinte" : "da próxima renovação"} será de ${money(billing.projectedNextRenewalPrice)}. Nenhuma cobrança imediata ou estorno foi gerado.`;
    }
    return message;
  } catch { return `${message} Consulte Meu Plano para atualizar a estimativa da próxima mensalidade.`; }
}
