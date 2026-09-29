import { fleetBillingFeedback } from "./fleetBillingFeedback";
import billingService from "./billingService";
jest.mock("./billingService");
const mocked = billingService as jest.Mocked<typeof billingService>;
it("usa preço do backend sem checkout ao cadastrar ou excluir", async () => {
  mocked.getMyBilling.mockResolvedValue({ planCode: "FROTTA", billableVehicleCount: 102, projectedNextRenewalPrice: 258.9 } as any);
  expect(await fleetBillingFeedback("added")).toContain("258,90");
  expect(await fleetBillingFeedback("deleted")).toContain("histórico foi preservado");
  expect(mocked.createCheckout).not.toHaveBeenCalled();
});
it("avisa que alterações após fechamento são do ciclo seguinte", async () => {
  mocked.getMyBilling.mockResolvedValue({ planCode: "PLATINUM", billableVehicleCount: 32, projectedNextRenewalPrice: 84.9, nextRenewalLockedAt: "2026-09-29" } as any);
  expect(await fleetBillingFeedback("added")).toContain("do ciclo seguinte");
});
