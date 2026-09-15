import {
  ALL_GROUPS,
  ALL_GROUPS_LABEL,
  groupAfterReportChange,
  maintenanceGroupLabel,
  maintenanceGroupOptions,
  maintenanceGroupQuery,
} from "./maintenanceGroups";
import { createContentReportMaintenance } from "./ReportUtils";
import { initialReportsValues, reportsValidationSchema } from "./reportsValidationSchema";
import { REPORTS } from "../../constants/selectOptions";
import endpoints from "../../constants/endpoints";

test("offers all groups first and sends an explicit flag with the year", () => {
  const options = maintenanceGroupOptions(["Grupo A", "Grupo B"]);
  expect(options.map((option) => option.label)).toEqual([ALL_GROUPS_LABEL, "Grupo A", "Grupo B"]);
  const query = maintenanceGroupQuery(options[0].value, 2026);
  expect(query).toEqual({ allGroups: true, year: 2026 });
  expect(endpoints.REPORTS_MAINTENANCE({ query })).toContain("allGroups=true&year=2026");
});

test.each(["Grupo A", ALL_GROUPS, 'Grupo "especial"'])(
  "preserves the exact specific group name %s in the API and PDF",
  (group) => {
    const selection = maintenanceGroupOptions([group])[1].value;
    expect(maintenanceGroupQuery(selection, 2026)).toEqual({ group, year: 2026 });
    expect(maintenanceGroupLabel(selection)).toBe(group);
  }
);

test("keeps the maintenance PDF layout and uses the consolidated label", () => {
  const content = createContentReportMaintenance([], maintenanceGroupLabel(ALL_GROUPS), "2026");
  expect(content[0].text).toContain("2026");
  expect(content[1].text).toBe("Todos os grupos");
});

test("requires a selection and accepts all groups for maintenance", async () => {
  const form = { ...initialReportsValues(), report: REPORTS.maintenance };
  expect(await reportsValidationSchema.isValid(form)).toBe(false);
  expect(await reportsValidationSchema.isValid({ ...form, group: ALL_GROUPS })).toBe(true);
});

test("clears all groups when leaving maintenance and preserves specific groups", () => {
  expect(groupAfterReportChange(ALL_GROUPS, true, false)).toBe("");
  expect(groupAfterReportChange('"Grupo A"', true, false)).toBe("Grupo A");
  expect(groupAfterReportChange("Grupo A", false, true)).toBe('"Grupo A"');
  expect(groupAfterReportChange("Grupo A", false, false)).toBe("Grupo A");
  expect(groupAfterReportChange("", false, true)).toBe("");
});
