export const ALL_GROUPS = "__ALL_GROUPS__";
export const ALL_GROUPS_LABEL = "Todos os grupos";

// Encode actual group names so even a name matching the sentinel stays selectable.
export const maintenanceGroupOptions = (groups: string[]) => [
  { label: ALL_GROUPS_LABEL, value: ALL_GROUPS },
  ...groups.map((group) => ({ label: group, value: JSON.stringify(group) })),
];

export const maintenanceGroupQuery = (selection: string, year: number) =>
  selection === ALL_GROUPS
    ? { allGroups: true, year }
    : { group: JSON.parse(selection) as string, year };

export const maintenanceGroupLabel = (selection: string): string =>
  selection === ALL_GROUPS ? ALL_GROUPS_LABEL : JSON.parse(selection);

export const groupAfterReportChange = (
  group: string,
  wasMaintenance: boolean,
  isMaintenance: boolean
): string => {
  if (!group || wasMaintenance === isMaintenance) return group;
  if (isMaintenance) return JSON.stringify(group);
  return group === ALL_GROUPS ? "" : maintenanceGroupLabel(group);
};
