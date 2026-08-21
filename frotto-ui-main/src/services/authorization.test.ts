import { accountIsAdmin } from "./authorization";

describe("accountIsAdmin", () => {
  it("only accepts the exact ROLE_ADMIN authority", () => {
    expect(accountIsAdmin({ authorities: ["ROLE_ADMIN"] })).toBe(true);
    expect(accountIsAdmin({ authorities: ["ROLE_USER"] })).toBe(false);
    expect(accountIsAdmin({ authorities: ["ROLE_ADMINISTRATOR"] })).toBe(false);
  });

  it("fails closed for absent account data", () => {
    expect(accountIsAdmin()).toBe(false);
    expect(accountIsAdmin(null)).toBe(false);
    expect(accountIsAdmin({})).toBe(false);
    expect(accountIsAdmin({ authorities: [] })).toBe(false);
  });
});
