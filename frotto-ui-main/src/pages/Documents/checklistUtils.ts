export type ChecklistItem = {
  key: string;
  label: string;
  ok: boolean;
  note?: string;
};

type ChecklistBaseItem = {
  key: string;
  label: string;
};

const CHECKLIST_DEFAULT_BASE: ChecklistBaseItem[] = [
  { key: "vidros_eletricos", label: "Vidros elétricos" },
  { key: "travas", label: "Travas" },
  { key: "ar_condicionado", label: "Ar condicionado" },
  { key: "buzina", label: "Buzina" },
  { key: "nivel_oleo", label: "Nível de óleo" },
  { key: "nivel_oleo_freio", label: "Nível do óleo de freio" },
  { key: "nivel_arrefecimento", label: "Nível do arrefecimento" },
  { key: "nivel_oleo_direcao", label: "Nível do óleo de direção" },
  { key: "macaco", label: "Macaco" },
  { key: "chave_roda", label: "Chave de roda" },
  { key: "estepe", label: "Estepe" },
  { key: "triangulo", label: "Triângulo" },
  { key: "interior_limpeza", label: "Interior (limpeza)" },
  { key: "exterior_limpeza", label: "Exterior (limpeza)" },
  { key: "pneus", label: "Pneus" },
  { key: "luzes", label: "Luzes" },
  { key: "som_multimidia", label: "Som/Multimídia" },
];

const CHECKLIST_DEFAULT_BY_KEY = new Map(
  CHECKLIST_DEFAULT_BASE.map((item) => [item.key, item])
);
const CHECKLIST_DEFAULT_KEY_BY_LABEL = new Map(
  CHECKLIST_DEFAULT_BASE.map((item) => [normalizeLookupText(item.label), item.key])
);

export const CHECKLIST_DEFAULT_ITEMS: ChecklistItem[] =
  CHECKLIST_DEFAULT_BASE.map((item) => ({
    ...item,
    ok: true,
  }));

function parseChecklistItemsJson(value: string): unknown[] {
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch (_error) {
    return [];
  }
}

function normalizeChecklistNote(value: unknown): string | undefined {
  const note = `${value || ""}`.trim();
  return note || undefined;
}

function sanitizeChecklistKey(value: unknown): string {
  return `${value || ""}`
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

function parseChecklistBoolean(value: unknown, fallback = true): boolean {
  if (typeof value === "boolean") return value;
  if (typeof value === "number") return value !== 0;
  if (typeof value === "string") {
    const normalized = sanitizeChecklistKey(value);
    if (!normalized) return fallback;
    if (["false", "nao", "no", "off", "unchecked", "0"].includes(normalized)) {
      return false;
    }
    if (["true", "sim", "yes", "on", "checked", "ok", "1"].includes(normalized)) {
      return true;
    }
  }
  return fallback;
}

function normalizeChecklistItem(raw: unknown, index: number): ChecklistItem | null {
  if (typeof raw === "string") {
    const label = `${raw || ""}`.trim();
    if (!label) {
      return null;
    }
    const defaultKey = CHECKLIST_DEFAULT_KEY_BY_LABEL.get(normalizeLookupText(label));
    const fallbackKey = sanitizeChecklistKey(label) || `item_${index + 1}`;
    const key = defaultKey || fallbackKey;
    const defaultItem = key ? CHECKLIST_DEFAULT_BY_KEY.get(key) : undefined;
    return {
      key: defaultItem?.key || key,
      label: defaultItem?.label || label,
      ok: true,
      note: undefined,
    };
  }

  if (!raw || typeof raw !== "object") {
    return null;
  }

  const source = raw as Record<string, unknown>;
  const fallbackLabel = `${source.item || ""}`.trim();
  const labelCandidate = `${source.label || fallbackLabel}`.trim();
  const labelKey = CHECKLIST_DEFAULT_KEY_BY_LABEL.get(
    normalizeLookupText(labelCandidate)
  );

  const keyCandidate = `${source.key || ""}`.trim();
  const resolvedKey =
    keyCandidate ||
    labelKey ||
    sanitizeChecklistKey(labelCandidate) ||
    `item_${index + 1}`;

  const defaultItem =
    CHECKLIST_DEFAULT_BY_KEY.get(resolvedKey) ||
    (labelKey ? CHECKLIST_DEFAULT_BY_KEY.get(labelKey) : undefined);

  const ok = parseChecklistBoolean(source.ok, true);

  const note =
    normalizeChecklistNote(source.note) ||
    normalizeChecklistNote(source.observacao) ||
    normalizeChecklistNote(source.observation);

  return {
    key: defaultItem?.key || resolvedKey,
    label: defaultItem?.label || labelCandidate || `Item ${index + 1}`,
    ok,
    note,
  };
}

function resolveChecklistSource(payload: Record<string, any>): unknown[] {
  if (!payload || typeof payload !== "object") {
    return [];
  }

  if (Array.isArray(payload.checklistItens)) {
    return payload.checklistItens;
  }

  if (Array.isArray(payload.checklist)) {
    return payload.checklist;
  }

  if (typeof payload.checklistItens === "string") {
    return parseChecklistItemsJson(payload.checklistItens);
  }

  if (typeof payload.checklist === "string") {
    return parseChecklistItemsJson(payload.checklist);
  }

  if (typeof payload.checklistItensJson === "string") {
    return parseChecklistItemsJson(payload.checklistItensJson);
  }

  return [];
}

function resolveDefaultChecklistKey(item: ChecklistItem): string | null {
  if (CHECKLIST_DEFAULT_BY_KEY.has(item.key)) {
    return item.key;
  }

  const byLabel = CHECKLIST_DEFAULT_KEY_BY_LABEL.get(
    normalizeLookupText(item.label)
  );
  if (byLabel) {
    return byLabel;
  }

  return null;
}

export function hydrateChecklistItems(payload: Record<string, any>): ChecklistItem[] {
  const defaultsMap = new Map<string, ChecklistItem>(
    CHECKLIST_DEFAULT_ITEMS.map((item) => [item.key, { ...item }])
  );
  const customItems: ChecklistItem[] = [];
  const source = resolveChecklistSource(payload);

  source.forEach((raw, index) => {
    const normalized = normalizeChecklistItem(raw, index);
    if (!normalized) {
      return;
    }

    const defaultKey = resolveDefaultChecklistKey(normalized);
    if (defaultKey) {
      const defaultItem = CHECKLIST_DEFAULT_BY_KEY.get(defaultKey);
      defaultsMap.set(defaultKey, {
        key: defaultKey,
        label: defaultItem?.label || normalized.label,
        ok: normalized.ok,
        note: normalized.ok ? undefined : normalized.note,
      });
      return;
    }

    const duplicated = customItems.some(
      (item) =>
        item.key === normalized.key ||
        normalizeLookupText(item.label) === normalizeLookupText(normalized.label)
    );
    if (duplicated) {
      return;
    }

    customItems.push({
      key: normalized.key,
      label: normalized.label,
      ok: normalized.ok,
      note: normalized.ok ? undefined : normalized.note,
    });
  });

  return [
    ...CHECKLIST_DEFAULT_BASE.map((item) => defaultsMap.get(item.key) || { ...item, ok: true }),
    ...customItems,
  ];
}

export function serializeChecklistItems(payload: Record<string, any>): ChecklistItem[] {
  return hydrateChecklistItems(payload).map((item) => ({
    key: item.key,
    label: item.label,
    ok: Boolean(item.ok),
    note: item.ok ? undefined : normalizeChecklistNote(item.note),
  }));
}

export function isChecklistDefaultKey(key: string): boolean {
  return CHECKLIST_DEFAULT_BY_KEY.has(key);
}

function normalizeLookupText(value: any): string {
  return `${value || ""}`
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase();
}
