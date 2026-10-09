import {
  IonButton,
  IonButtons,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonCheckbox,
  IonContent,
  IonFooter,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonMenuButton,
  IonModal,
  IonProgressBar,
  IonTextarea,
  IonTitle,
  IonToolbar,
  IonPage,
  useIonToast,
} from "@ionic/react";
import {
  add,
  alertCircleOutline,
  checkmark,
  clipboardOutline,
  closeCircleOutline,
  constructOutline,
  documentTextOutline,
  filterOutline,
  receiptOutline,
  refreshOutline,
  trashOutline,
  walletOutline,
} from "ionicons/icons";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { CarDriverModel, DriverAssignmentType, DriverPendencyModel } from "../../constants/CarModels";
import { DebtItemTypeModel } from "../../constants/DebtItemTypeModels";
import {
  CarSearchModel,
  DOCUMENT_STATUSES,
  DOCUMENT_TYPES,
  DOCUMENT_TYPES_REQUIRING_CAR,
  DocumentModel,
  DocumentStatus,
  DocumentType,
  DriverSearchModel,
  wizardTypeOptions,
} from "../../constants/DocumentModels";
import debtItemTypeService from "../../services/debtItemTypeService";
import documentService from "../../services/documentService";
import { currencyFormat } from "../../services/currencyFormat";
import { useAlert } from "../../services/hooks/useAlert";
import { formatDecimalInput, parseDecimal, sanitizeDecimalInput } from "../../services/decimalPtBr";
import { generateDocumentPdf } from "./documentPdf";
import { RouteComponentProps } from "react-router";
import { ChecklistLaunch, parseChecklistLaunch, withLaunchDocument } from "./checklistLaunch";
import { TIRE_BRANDS } from "../../constants/selectOptions";
import FormSelectFilterAdd from "../../components/Form/FormSelectFilterAdd";
import { TIRE_BRANDS_KEY } from "../../services/localStorage/localstorage";
import {
  CHECKLIST_TIRE_POSITIONS,
  CHECKLIST_TYPE_OPTIONS,
  CLEANING_OPTIONS,
  ChecklistItem,
  TIRE_INTEGRITY_OPTIONS,
  cleaningLabel,
  FUEL_LEVEL_OPTIONS,
  checklistTypeLabel,
  formatChecklistDate,
  fuelLevelLabel,
  hydrateChecklistItems,
  isChecklistDefaultKey,
  parseChecklistKm,
  serializeChecklistItems,
  structuredChecklistErrors,
} from "./checklistUtils";
import DriverAssignmentChoice from "../Driver/DriverAssignmentChoice";
import {
  AssignmentConflict,
  DRIVER_CAR_ERROR_MESSAGES,
  describeReserveReturn,
  driverCarStatus,
  getAssignmentConflict,
} from "../../services/driverAssignmentService";
import { getApiErrorMessage } from "../../services/apiErrorMessage";
import { maskPhone, sanitizeDigits } from "../../services/profileFormat";
import endpoints from "../../constants/endpoints";
import api from "../../services/axios/axios";
import { resolveApiUrl } from "../../services/resolveApiUrl";
import ItemNotFound from "../../components/List/ItemNotFound";
import FrottoBadge, { FrottoBadgeVariant } from "../../components/UI/FrottoBadge";
import FrottoCard from "../../components/UI/FrottoCard";
import FormInput from "../../components/Form/FormInput";
import FormSelect from "../../components/Form/FormSelect";
import FormInputLabel from "../../components/Form/FormInputLabel";
import "./DocumentsPage.css";

const AUTOCOMPLETE_DELAY = 300;
const PAGE_SIZE = 30;
const DECIMAL_FIELDS_BY_TYPE: Record<DocumentType, string[]> = {
  MULTA: ["valor"],
  MANUTENCAO_COMPARTILHADA: ["valorTotal", "parteMotoristaValor"],
  RECIBO_ALUGUEL: ["valorAluguel", "descontos", "acrescimos", "valorFinal"],
  CONFISSAO_DIVIDA: ["valorTotal", "valorParcela", "valorItem"],
  ENTREGA_DEVOLUCAO_CHECKLIST: [],
};

type ConfissaoDebtItem = {
  typeId: number | null;
  typeNameSnapshot: string;
  descricaoItem?: string;
  valorItem: string;
};

type ChecklistEmergencyContact = {
  nome: string;
  telefone: string;
};

type ChecklistTireCondition = "" | "BOM" | "MEIA_VIDA" | "RUIM";

type ChecklistTirePosition = {
  posicao: string;
  marca?: string;
  estado?: string;
};

type ChecklistTires = {
  marca?: string;
  estado?: ChecklistTireCondition;
  observacoes?: string;
  /** CHECKLIST: the five positions filled in the structured form (the only tires carried to the inspection). */
  source?: "MANUAL" | "LAST_INSPECTION" | "CHECKLIST";
  positions?: ChecklistTirePosition[];
};

/** A damage already registered on the car, confirmed in the checklist (snapshot for the PDF; the backend reads the id). */
type ChecklistExistingDamage = { id: number; part?: string; date?: string };

const MIN_EMERGENCY_CONTACTS = 2;
const DOCUMENTS_DEV_LOG = process.env.NODE_ENV === "development";

// Mapeamento oficial de status (DESIGN_SYSTEM.md seção 22): rascunho é
// atenção (ainda incompleto), finalizado/enviado são estágios positivos
// distintos do mesmo fluxo (não usar Danger aqui), cancelado é um estado
// morto/inativo, não um erro nem uma ação destrutiva.
const DOCUMENT_STATUS_VARIANT: Record<DocumentStatus, FrottoBadgeVariant> = {
  DRAFT: "warning",
  FINAL: "success",
  SENT: "info",
  CANCELED: "neutral",
};

const DOCUMENT_STATUS_TONE_CLASS: Record<DocumentStatus, string> = {
  DRAFT: "app-soft-icon--warning",
  FINAL: "app-soft-icon--success",
  SENT: "app-soft-icon--info",
  CANCELED: "app-soft-icon--neutral",
};

const DOCUMENT_TYPE_ICON: Record<DocumentType, string> = {
  MULTA: alertCircleOutline,
  MANUTENCAO_COMPARTILHADA: constructOutline,
  RECIBO_ALUGUEL: receiptOutline,
  CONFISSAO_DIVIDA: walletOutline,
  ENTREGA_DEVOLUCAO_CHECKLIST: clipboardOutline,
};

const documentToneClass = (status: DocumentStatus): string =>
  DOCUMENT_STATUS_TONE_CLASS[status] || "";

/** Route props (absent when rendered outside a route): a URL launch request opens the checklist wizard directly. */
const DocumentsPage: React.FC<Partial<RouteComponentProps>> = ({ location, history }) => {
  const { showErrorAlert } = useAlert();
  const [presentToast] = useIonToast();

  const [isLoading, setIsLoading] = useState(false);
  const [isActionLoading, setIsActionLoading] = useState(false);
  const [documents, setDocuments] = useState<DocumentModel[]>([]);
  const [listPage, setListPage] = useState(0);

  const [filterType, setFilterType] = useState<DocumentType | "">("");
  const [filterStatus, setFilterStatus] = useState<DocumentStatus | "">("");
  const [filterDriverQuery, setFilterDriverQuery] = useState("");
  const [filterCarQuery, setFilterCarQuery] = useState("");
  const [filterDriver, setFilterDriver] = useState<DriverSearchModel | null>(null);
  const [filterCar, setFilterCar] = useState<CarSearchModel | null>(null);
  const [filterDriverOptions, setFilterDriverOptions] = useState<DriverSearchModel[]>([]);
  const [filterCarOptions, setFilterCarOptions] = useState<CarSearchModel[]>([]);

  const hasActiveFilters = Boolean(
    filterType || filterStatus || filterDriver || filterCar
  );

  const [viewDocument, setViewDocument] = useState<DocumentModel | null>(null);
  const [isViewModalOpen, setIsViewModalOpen] = useState(false);

  const [isWizardOpen, setIsWizardOpen] = useState(false);
  const [wizardStep, setWizardStep] = useState<1 | 2 | 3>(1);
  const [wizardType, setWizardType] = useState<DocumentType | "">("");
  const [wizardDriverQuery, setWizardDriverQuery] = useState("");
  const [wizardCarQuery, setWizardCarQuery] = useState("");
  const [wizardDriverOptions, setWizardDriverOptions] = useState<DriverSearchModel[]>([]);
  const [wizardCarOptions, setWizardCarOptions] = useState<CarSearchModel[]>([]);
  const [wizardDriver, setWizardDriver] = useState<DriverSearchModel | null>(null);
  const [wizardCar, setWizardCar] = useState<CarSearchModel | null>(null);
  const [wizardPayload, setWizardPayload] = useState<Record<string, any>>({});
  const [wizardFiles, setWizardFiles] = useState<File[]>([]);
  const [checklistNewPhotos, setChecklistNewPhotos] = useState<File[]>([]);
  const [savedDocument, setSavedDocument] = useState<DocumentModel | null>(null);
  /** Devolução: the contract being returned (chosen among the open contracts of the driver on the car). */
  const [checklistDriverCarId, setChecklistDriverCarId] = useState<number | null>(null);
  /** Contracts of the selected car (null while loading / no car). */
  const [checklistContracts, setChecklistContracts] = useState<CarDriverModel[] | null>(null);
  /** 409 of the finalization of an Entrega: the driver has an open contract on another car. */
  const [checklistConflict, setChecklistConflict] = useState<AssignmentConflict | null>(null);
  const [checklistFeedback, setChecklistFeedback] = useState("");
  /** A draft of the old wizard (no checklistType) becomes structured only when the user converts it explicitly. */
  const [checklistLegacyConversion, setChecklistLegacyConversion] = useState(false);
  /** Where to go back when a wizard opened from another screen (Inspeções) closes. */
  const [activeLaunch, setActiveLaunch] = useState<ChecklistLaunch | null>(null);
  /** Last saved (or opened) state of the checklist wizard: closing with changes after it asks before discarding. */
  const savedSnapshotRef = useRef<string | null>(null);
  const snapshotPendingRef = useRef(false);
  /** The checklist was finalized but its PDF could not be generated: Gerar PDF retries (never finalizes again). */
  const [checklistPdfFailed, setChecklistPdfFailed] = useState(false);
  /** Structured checklist form: 1 = condições do veículo, 2 = conferência e finalização. */
  const [checklistPart, setChecklistPart] = useState<1 | 2>(1);
  /** Tires of the car's last inspection: only offered as a suggestion, never copied by itself. */
  const [lastInspectionTires, setLastInspectionTires] = useState<ChecklistTirePosition[]>([]);
  /** Damages of the car not explicitly resolved (an unknown resolution - older records - included): can be confirmed. */
  const [carDamages, setCarDamages] = useState<Array<{ id: number; part?: string; date?: string; responsible?: string }>>([]);
  /** Checklist types already finalized on the contract being delivered (read from the backend for that contract). */
  const [contractFinalChecklists, setContractFinalChecklists] = useState<{ contractId: number; types: string[] } | null>(null);
  const [newChecklistLabel, setNewChecklistLabel] = useState("");
  const [debtItemTypes, setDebtItemTypes] = useState<DebtItemTypeModel[]>([]);
  const [isDebtItemTypesLoading, setIsDebtItemTypesLoading] = useState(false);
  const [isConfissaoPendenciesLoading, setIsConfissaoPendenciesLoading] = useState(false);
  const [confissaoImportFeedback, setConfissaoImportFeedback] = useState("");
  const [confissaoAutoImportedDriverId, setConfissaoAutoImportedDriverId] = useState<number | null>(null);
  const [checklistPhotoCandidateIndexByRef, setChecklistPhotoCandidateIndexByRef] = useState<
    Record<string, number>
  >({});
  const confissaoImportRequestIdRef = useRef(0);

  const logDocumentsDebug = useCallback((message: string, extra?: Record<string, any>) => {
    if (!DOCUMENTS_DEV_LOG) {
      return;
    }
    if (extra) {
      console.log(`[DocumentsWizard] ${message}`, extra);
      return;
    }
    console.log(`[DocumentsWizard] ${message}`);
  }, []);

  const wizardRequiresCar = useMemo(
    () => Boolean(wizardType && DOCUMENT_TYPES_REQUIRING_CAR.includes(wizardType)),
    [wizardType]
  );

  const showSuccessToast = useCallback(
    (message: string) => {
      presentToast({
        message,
        color: "success",
        duration: 2200,
        position: "top",
      });
    },
    [presentToast]
  );

  const loadDebtItemTypes = useCallback(async () => {
    setIsDebtItemTypesLoading(true);
    try {
      const data = await debtItemTypeService.listDebtItemTypes("true");
      setDebtItemTypes(data);
    } catch (_error) {
      setDebtItemTypes([]);
      showErrorAlert("Não foi possível carregar os tipos de dívida.");
    } finally {
      setIsDebtItemTypesLoading(false);
    }
  }, [showErrorAlert]);

  const loadDocuments = useCallback(async (pageOverride?: number) => {
    setIsLoading(true);
    try {
      const data = await documentService.listDocuments({
        driverId: filterDriver?.id,
        carId: filterCar?.id,
        type: filterType || undefined,
        status: filterStatus || undefined,
        limit: PAGE_SIZE,
        page: typeof pageOverride === "number" ? pageOverride : listPage,
      });
      setDocuments(data);
    } catch (_error) {
      showErrorAlert("Não foi possível carregar os documentos.");
    } finally {
      setIsLoading(false);
    }
  }, [filterCar?.id, filterDriver?.id, filterStatus, filterType, listPage, showErrorAlert]);

  useEffect(() => {
    void loadDocuments();
  }, [loadDocuments]);

  useEffect(() => {
    if (!isWizardOpen) {
      return;
    }
    if (wizardType !== "CONFISSAO_DIVIDA" && savedDocument?.type !== "CONFISSAO_DIVIDA") {
      return;
    }
    void loadDebtItemTypes();
  }, [isWizardOpen, loadDebtItemTypes, savedDocument?.type, wizardType]);

  useEffect(() => {
    const query = filterDriverQuery.trim();
    setFilterDriverOptions([]);
    // Entidade selecionada: o texto veio da seleção, não de uma nova busca.
    if (!query || filterDriver) {
      return;
    }

    let isActive = true;
    const handle = setTimeout(async () => {
      try {
        const results = await documentService.searchDrivers(query);
        if (isActive) {
          setFilterDriverOptions(results);
        }
      } catch (_error) {
        if (isActive) {
          setFilterDriverOptions([]);
        }
      }
    }, AUTOCOMPLETE_DELAY);

    return () => {
      isActive = false;
      clearTimeout(handle);
    };
  }, [filterDriverQuery, filterDriver]);

  useEffect(() => {
    const query = filterCarQuery.trim();
    setFilterCarOptions([]);
    // Entidade selecionada: o texto veio da seleção, não de uma nova busca.
    if (!query || filterCar) {
      return;
    }

    let isActive = true;
    const handle = setTimeout(async () => {
      try {
        const results = await documentService.searchCars(query);
        if (isActive) {
          setFilterCarOptions(results);
        }
      } catch (_error) {
        if (isActive) {
          setFilterCarOptions([]);
        }
      }
    }, AUTOCOMPLETE_DELAY);

    return () => {
      isActive = false;
      clearTimeout(handle);
    };
  }, [filterCarQuery, filterCar]);

  useEffect(() => {
    const query = wizardDriverQuery.trim();
    setWizardDriverOptions([]);
    // Entidade selecionada: o texto veio da seleção, não de uma nova busca.
    if (!query || wizardDriver) {
      return;
    }

    let isActive = true;
    const handle = setTimeout(async () => {
      try {
        const results = await documentService.searchDrivers(query);
        if (isActive) {
          setWizardDriverOptions(results);
        }
      } catch (_error) {
        if (isActive) {
          setWizardDriverOptions([]);
        }
      }
    }, AUTOCOMPLETE_DELAY);

    return () => {
      isActive = false;
      clearTimeout(handle);
    };
  }, [wizardDriverQuery, wizardDriver]);

  useEffect(() => {
    const query = wizardCarQuery.trim();
    setWizardCarOptions([]);
    // Entidade selecionada: o texto veio da seleção, não de uma nova busca.
    if (!query || wizardCar) {
      return;
    }

    let isActive = true;
    const handle = setTimeout(async () => {
      try {
        const results = await documentService.searchCars(query);
        if (isActive) {
          setWizardCarOptions(results);
        }
      } catch (_error) {
        if (isActive) {
          setWizardCarOptions([]);
        }
      }
    }, AUTOCOMPLETE_DELAY);

    return () => {
      isActive = false;
      clearTimeout(handle);
    };
  }, [wizardCarQuery, wizardCar]);

  const resetFilters = () => {
    setFilterType("");
    setFilterStatus("");
    setFilterDriver(null);
    setFilterCar(null);
    setFilterDriverQuery("");
    setFilterCarQuery("");
    setListPage(0);
    void loadDocuments(0);
  };

  const resetWizard = () => {
    confissaoImportRequestIdRef.current += 1;
    setWizardStep(1);
    setWizardType("");
    setWizardDriver(null);
    setWizardCar(null);
    setWizardDriverQuery("");
    setWizardCarQuery("");
    setWizardDriverOptions([]);
    setWizardCarOptions([]);
    setWizardPayload({});
    setWizardFiles([]);
    setChecklistNewPhotos([]);
    setSavedDocument(null);
    setChecklistDriverCarId(null);
    setChecklistContracts(null);
    setChecklistConflict(null);
    setChecklistFeedback("");
    setChecklistLegacyConversion(false);
    setChecklistPart(1);
    setLastInspectionTires([]);
    setIsConfissaoPendenciesLoading(false);
    setConfissaoImportFeedback("");
    setConfissaoAutoImportedDriverId(null);
  };

  const closeWizard = () => {
    if (!isWizardOpen) {
      return; // the dismissal of a wizard already closed (Fechar, then onDidDismiss): never a second return
    }
    if (isChecklistDirty() && !window.confirm("Descartar as alterações não salvas deste checklist?")) {
      return;
    }
    setIsWizardOpen(false);
    resetWizard();
    setChecklistPdfFailed(false);
    savedSnapshotRef.current = null;
    if (activeLaunch) {
      leaveLaunch(activeLaunch);
    }
  };

  /**
   * Back to the screen the checklist was started from (car page / inspections): the entry the app pushed is left
   * with goBack (no Documentos entry stays in the history); a URL opened directly (or reloaded without that entry)
   * is replaced by its origin. Never Documentos for a launch from Inspeções.
   */
  const leaveLaunch = (launch: ChecklistLaunch) => {
    setActiveLaunch(null);
    handledLaunchRef.current = null;
    const target = launch.returnTo || `/menu/carros/${launch.carId}`;
    const pushedByApp = Boolean((location?.state as any)?.checklistLaunch) && typeof window !== "undefined" && window.history.length > 1;
    if (pushedByApp && history?.goBack) {
      history.goBack();
    } else {
      history?.replace(target);
    }
  };

  const openWizard = () => {
    resetWizard();
    snapshotPendingRef.current = true;
    setIsWizardOpen(true);
  };

  const setPayload = (key: string, value: any) => {
    setWizardPayload((prev) => ({ ...prev, [key]: value }));
  };

  const syncMetaPayload = (base: Record<string, any> = {}) => {
    const nextPayload = {
      ...wizardPayload,
      ...base,
      driverName: wizardDriver?.name || wizardPayload.driverName || "",
      driverCpf: wizardDriver?.cpf || wizardPayload.driverCpf || "",
      carPlate: wizardCar?.plate || wizardPayload.carPlate || "",
      carModel: wizardCar?.model || wizardPayload.carModel || "",
    };
    setWizardPayload(nextPayload);
    return nextPayload;
  };

  const findDebtItemTypeById = useCallback(
    (value: any): DebtItemTypeModel | null => {
      const id = Number(value);
      if (!Number.isFinite(id)) {
        return null;
      }
      return debtItemTypes.find((item) => item.id === id) || null;
    },
    [debtItemTypes]
  );

  const findDebtItemTypeByName = useCallback(
    (value: any): DebtItemTypeModel | null => {
      const target = normalizeLookupText(value);
      if (!target) {
        return null;
      }
      return debtItemTypes.find((item) => normalizeLookupText(item.name) === target) || null;
    },
    [debtItemTypes]
  );

  const getFallbackDebtItemType = useCallback((): DebtItemTypeModel | null => {
    return findDebtItemTypeByName("Outros") || debtItemTypes[0] || null;
  }, [debtItemTypes, findDebtItemTypeByName]);

  const buildEmptyConfissaoItem = useCallback((): ConfissaoDebtItem => {
    const fallbackType = getFallbackDebtItemType();
    return {
      typeId: fallbackType?.id ?? null,
      typeNameSnapshot: fallbackType?.name || "Outros",
      descricaoItem: "",
      valorItem: "",
    };
  }, [getFallbackDebtItemType]);

  const calculateConfissaoTotal = useCallback((items: ConfissaoDebtItem[]) => {
    const total = items.reduce((sum, item) => sum + (parseDecimal(item.valorItem) || 0), 0);
    return formatDecimalInput(total);
  }, []);

  const findDebtItemTypeByPendency = useCallback(
    (pendency: DriverPendencyModel): DebtItemTypeModel | null => {
      const haystack = normalizeLookupText(`${pendency.name || ""} ${pendency.note || ""}`);
      if (haystack) {
        const matchedType =
          debtItemTypes.find((item) => {
            const typeName = normalizeLookupText(item.name);
            return Boolean(typeName && (haystack.includes(typeName) || typeName.includes(haystack)));
          }) || null;
        if (matchedType) {
          return matchedType;
        }
      }
      return getFallbackDebtItemType();
    },
    [debtItemTypes, getFallbackDebtItemType]
  );

  const importDriverPendenciesToConfissao = useCallback(
    async (driver: DriverSearchModel | null, force = false) => {
      if (!driver?.id) {
        return;
      }
      if (!force && confissaoAutoImportedDriverId === driver.id) {
        return;
      }

      const requestId = confissaoImportRequestIdRef.current + 1;
      confissaoImportRequestIdRef.current = requestId;
      setIsConfissaoPendenciesLoading(true);

      try {
        const pendencies = await documentService.listOpenPendenciesByDriver(driver.id);
        if (confissaoImportRequestIdRef.current !== requestId) {
          return;
        }

        const importedItems = pendencies
          .map((pendency) => {
            const amount = getDriverPendencyOutstandingAmount(pendency);
            if (amount <= 0) {
              return null;
            }

            const selectedType = findDebtItemTypeByPendency(pendency);
            return {
              typeId: selectedType?.id ?? null,
              typeNameSnapshot: selectedType?.name || "Outros",
              descricaoItem: buildConfissaoItemDescriptionFromPendency(pendency),
              valorItem: formatDecimalInput(amount),
            } as ConfissaoDebtItem;
          })
          .filter((item): item is ConfissaoDebtItem => Boolean(item));

        const nextItems = importedItems.length ? importedItems : [buildEmptyConfissaoItem()];
        const totalImported = importedItems.reduce((sum, item) => sum + (parseDecimal(item.valorItem) || 0), 0);

        setWizardPayload((current) => ({
          ...current,
          origemDaDivida:
            `${current?.origemDaDivida || ""}`.trim() ||
            "Pendências em aberto importadas automaticamente do motorista selecionado.",
          itensDaDivida: nextItems,
          valorTotal: calculateConfissaoTotal(nextItems),
        }));
        setConfissaoAutoImportedDriverId(driver.id);
        setConfissaoImportFeedback(
          importedItems.length
            ? `${importedItems.length} pendência(s) em aberto importada(s), total ${currencyFormat(totalImported)}.`
            : "Nenhuma pendência em aberto encontrada para o motorista selecionado."
        );
      } catch (_error) {
        if (confissaoImportRequestIdRef.current !== requestId) {
          return;
        }
        setConfissaoImportFeedback("");
        showErrorAlert("Não foi possível carregar as pendências do motorista selecionado.");
      } finally {
        if (confissaoImportRequestIdRef.current === requestId) {
          setIsConfissaoPendenciesLoading(false);
        }
      }
    },
    [
      buildEmptyConfissaoItem,
      calculateConfissaoTotal,
      confissaoAutoImportedDriverId,
      findDebtItemTypeByPendency,
      showErrorAlert,
    ]
  );

  const normalizeConfissaoItemsForEditor = useCallback(
    (payload: Record<string, any>): ConfissaoDebtItem[] => {
      const payloadItens = Array.isArray(payload?.itensDaDivida) ? payload.itensDaDivida : [];
      let sourceItens = payloadItens;

      if (!sourceItens.length) {
        const legacyTypeName = `${payload?.tipoItem || payload?.origemDaDivida || ""}`.trim();
        const legacyDescription = `${payload?.descricaoItem || ""}`.trim();
        const legacyValue = payload?.valorItem ?? payload?.valorTotal;
        if (
          legacyTypeName ||
          legacyDescription ||
          (legacyValue !== null && legacyValue !== undefined && `${legacyValue}` !== "")
        ) {
          sourceItens = [
            {
              typeNameSnapshot: legacyTypeName,
              descricaoItem: legacyDescription,
              valorItem: legacyValue,
            },
          ];
        }
      }

      return sourceItens.map((item: any) => {
        const typeById = findDebtItemTypeById(item?.typeId);
        const snapshotCandidate = `${item?.typeNameSnapshot || item?.typeName || item?.tipoItem || ""}`.trim();
        const typeByName = typeById ? null : findDebtItemTypeByName(snapshotCandidate);
        const fallbackType = getFallbackDebtItemType();
        const selectedType = typeById || typeByName || fallbackType;
        return {
          typeId: selectedType?.id ?? null,
          typeNameSnapshot: snapshotCandidate || selectedType?.name || "Outros",
          descricaoItem: `${item?.descricaoItem || ""}`.trim(),
          valorItem: formatDecimalInput(item?.valorItem),
        };
      });
    },
    [findDebtItemTypeById, findDebtItemTypeByName, getFallbackDebtItemType]
  );

  const normalizePayloadForApi = useCallback(
    (type: DocumentType, payload: Record<string, any>) => {
      const decimalFields = (DECIMAL_FIELDS_BY_TYPE[type] || []).filter((fieldName) => fieldName !== "valorItem");
      const normalizedPayload = { ...payload };
      decimalFields.forEach((fieldName) => {
        const parsed = parseDecimal(payload[fieldName]);
        if (parsed !== null) {
          normalizedPayload[fieldName] = parsed;
        }
      });

      if (type === "CONFISSAO_DIVIDA") {
        const normalizedItems = normalizeConfissaoItemsForEditor(payload)
          .map((item) => {
            const selectedType =
              findDebtItemTypeById(item.typeId) ||
              findDebtItemTypeByName(item.typeNameSnapshot) ||
              getFallbackDebtItemType();
            const parsedTypeId = Number(item.typeId);
            const typeId = selectedType?.id ?? (Number.isFinite(parsedTypeId) ? parsedTypeId : null);
            const snapshot = `${item.typeNameSnapshot || selectedType?.name || ""}`.trim() || "Outros";
            const descricaoItem = `${item.descricaoItem || ""}`.trim();
            return {
              typeId,
              typeNameSnapshot: snapshot,
              descricaoItem: descricaoItem || undefined,
              valorItem: parseDecimal(item.valorItem) || 0,
            };
          })
          .filter((item) => item.typeNameSnapshot || item.descricaoItem || item.valorItem > 0);

        const total = normalizedItems.reduce((sum, item) => sum + item.valorItem, 0);
        normalizedPayload.itensDaDivida = normalizedItems;
        normalizedPayload.valorTotal = total;
      }

      if (type === "ENTREGA_DEVOLUCAO_CHECKLIST") {
        const checklistRefs = resolveChecklistPhotoRefsFromSource(payload);
        normalizedPayload.checklistItens = serializeChecklistItems(payload || {});
        normalizedPayload.emergencyContacts = serializeChecklistEmergencyContacts(payload?.emergencyContacts);
        normalizedPayload.checklistPhotoRefs = checklistRefs;
        normalizedPayload.attachmentsChecklist = checklistRefs;
        normalizedPayload.tires = normalizeChecklistTiresForApi(payload?.tires);
        delete normalizedPayload.checklistItensJson;
      }

      return normalizedPayload;
    },
    [findDebtItemTypeById, findDebtItemTypeByName, getFallbackDebtItemType, normalizeConfissaoItemsForEditor]
  );

  const normalizePayloadForEditor = useCallback(
    (type: DocumentType, payload: Record<string, any>, attachments: string[] = []) => {
      const decimalFields = (DECIMAL_FIELDS_BY_TYPE[type] || []).filter((fieldName) => fieldName !== "valorItem");
      const normalizedPayload = { ...(payload || {}) };
      decimalFields.forEach((fieldName) => {
        normalizedPayload[fieldName] = formatDecimalInput(payload?.[fieldName]);
      });

      if (type === "CONFISSAO_DIVIDA") {
        const items = normalizeConfissaoItemsForEditor(payload || {});
        normalizedPayload.itensDaDivida = items;
        normalizedPayload.valorTotal = calculateConfissaoTotal(items);
      }

      if (type === "ENTREGA_DEVOLUCAO_CHECKLIST") {
        const checklistRefs = resolveChecklistPhotoRefsFromSource(payload, attachments);
        normalizedPayload.checklistItens = hydrateChecklistItems(payload || {});
        normalizedPayload.emergencyContacts = hydrateChecklistEmergencyContacts(payload?.emergencyContacts);
        normalizedPayload.checklistPhotoRefs = checklistRefs;
        normalizedPayload.attachmentsChecklist = checklistRefs;
        normalizedPayload.tires = normalizeChecklistTiresForEditor(payload?.tires);
        delete normalizedPayload.checklistItensJson;
      }

      return normalizedPayload;
    },
    [calculateConfissaoTotal, normalizeConfissaoItemsForEditor]
  );

  const confissaoItems = useMemo(() => {
    if (wizardType !== "CONFISSAO_DIVIDA") {
      return [];
    }

    const payloadItems = Array.isArray(wizardPayload?.itensDaDivida) ? wizardPayload.itensDaDivida : [];
    return payloadItems.map((item: any) => {
      const parsedTypeId = Number(item?.typeId);
      const typeId = Number.isFinite(parsedTypeId) ? parsedTypeId : null;

      return {
        typeId,
        typeNameSnapshot: `${item?.typeNameSnapshot || item?.typeName || item?.tipoItem || ""}`,
        descricaoItem: `${item?.descricaoItem ?? ""}`,
        valorItem: `${item?.valorItem ?? ""}`,
      } as ConfissaoDebtItem;
    });
  }, [wizardPayload?.itensDaDivida, wizardType]);

  const applyConfissaoItems = useCallback(
    (items: ConfissaoDebtItem[]) => {
      setWizardPayload((prev) => ({
        ...prev,
        itensDaDivida: items,
        valorTotal: calculateConfissaoTotal(items),
      }));
    },
    [calculateConfissaoTotal]
  );

  const addConfissaoItem = useCallback(() => {
    const nextItems = [
      ...confissaoItems,
      buildEmptyConfissaoItem(),
    ];
    applyConfissaoItems(nextItems);
  }, [applyConfissaoItems, buildEmptyConfissaoItem, confissaoItems]);

  const updateConfissaoItem = useCallback(
    (index: number, patch: Partial<ConfissaoDebtItem>) => {
      const nextItems = confissaoItems.map((item, currentIndex) => {
        if (index !== currentIndex) {
          return item;
        }
        return { ...item, ...patch };
      });
      applyConfissaoItems(nextItems);
    },
    [applyConfissaoItems, confissaoItems]
  );

  const removeConfissaoItem = useCallback(
    (index: number) => {
      const nextItems = confissaoItems.filter((_item, currentIndex) => currentIndex !== index);
      applyConfissaoItems(nextItems);
    },
    [applyConfissaoItems, confissaoItems]
  );

  const checklistItems = useMemo<ChecklistItem[]>(() => {
    if (wizardType !== "ENTREGA_DEVOLUCAO_CHECKLIST") {
      return [];
    }
    return hydrateChecklistItems(wizardPayload || {});
  }, [wizardPayload, wizardType]);

  const applyChecklistItems = useCallback((items: ChecklistItem[]) => {
    const normalizedItems = items.map((item) => ({
      key: item.key,
      label: item.label,
      ok: Boolean(item.ok),
      note: item.ok ? undefined : `${item.note || ""}`.trim() || undefined,
    }));

    setWizardPayload((prev) => {
      const { checklistItensJson: _legacyChecklistJson, ...rest } = prev;
      return {
        ...rest,
        checklistItens: normalizedItems,
      };
    });
  }, []);

  const toggleChecklistItem = useCallback(
    (key: string, checked: boolean) => {
      const nextItems = checklistItems.map((item) =>
        item.key === key
          ? {
              ...item,
              ok: checked,
              note: checked ? undefined : item.note || "",
            }
          : item
      );
      applyChecklistItems(nextItems);
    },
    [applyChecklistItems, checklistItems]
  );

  const updateChecklistItemNote = useCallback(
    (key: string, note: string) => {
      const nextItems = checklistItems.map((item) =>
        item.key === key
          ? {
              ...item,
              note,
            }
          : item
      );
      applyChecklistItems(nextItems);
    },
    [applyChecklistItems, checklistItems]
  );

  const removeCustomChecklistItem = useCallback(
    (key: string) => {
      if (!key || isChecklistDefaultKey(key)) {
        return;
      }
      const nextItems = checklistItems.filter((item) => item.key !== key);
      applyChecklistItems(nextItems);
    },
    [applyChecklistItems, checklistItems]
  );

  const addCustomChecklistItem = useCallback(() => {
    const label = `${newChecklistLabel || ""}`.trim();
    if (!label) {
      return;
    }

    const hasDuplicatedLabel = checklistItems.some(
      (item) => normalizeLookupText(item.label) === normalizeLookupText(label)
    );
    if (hasDuplicatedLabel) {
      showErrorAlert("Item do checklist já existe.");
      return;
    }

    const baseKey = sanitizeChecklistCustomKey(label) || "custom_item";
    let customKey = baseKey;
    let suffix = 1;
    const existingKeys = new Set(checklistItems.map((item) => item.key));
    while (existingKeys.has(customKey)) {
      customKey = `${baseKey}_${suffix}`;
      suffix += 1;
    }

    applyChecklistItems([
      ...checklistItems,
      {
        key: customKey,
        label,
        ok: true,
      },
    ]);
    setNewChecklistLabel("");
  }, [applyChecklistItems, checklistItems, newChecklistLabel, showErrorAlert]);

  const checklistEmergencyContacts = useMemo<ChecklistEmergencyContact[]>(() => {
    if (wizardType !== "ENTREGA_DEVOLUCAO_CHECKLIST") {
      return [];
    }
    return hydrateChecklistEmergencyContactsForEditor(wizardPayload?.emergencyContacts);
  }, [wizardPayload?.emergencyContacts, wizardType]);

  const applyChecklistEmergencyContacts = useCallback((contacts: ChecklistEmergencyContact[]) => {
    setWizardPayload((prev) => ({
      ...prev,
      emergencyContacts: hydrateChecklistEmergencyContactsForEditor(contacts),
    }));
  }, []);

  const updateChecklistEmergencyContact = useCallback(
    (index: number, patch: Partial<ChecklistEmergencyContact>) => {
      const next = checklistEmergencyContacts.map((item, currentIndex) =>
        currentIndex === index ? { ...item, ...patch } : item
      );
      applyChecklistEmergencyContacts(next);
    },
    [applyChecklistEmergencyContacts, checklistEmergencyContacts]
  );

  const addChecklistEmergencyContact = useCallback(() => {
    applyChecklistEmergencyContacts([...checklistEmergencyContacts, { nome: "", telefone: "" }]);
  }, [applyChecklistEmergencyContacts, checklistEmergencyContacts]);

  const removeChecklistEmergencyContact = useCallback(
    (index: number) => {
      if (checklistEmergencyContacts.length <= MIN_EMERGENCY_CONTACTS) {
        return;
      }
      const next = checklistEmergencyContacts.filter((_item, currentIndex) => currentIndex !== index);
      applyChecklistEmergencyContacts(next);
    },
    [applyChecklistEmergencyContacts, checklistEmergencyContacts]
  );

  const checklistPhotoRefs = useMemo<string[]>(() => {
    if (wizardType !== "ENTREGA_DEVOLUCAO_CHECKLIST") {
      return [];
    }
    return resolveChecklistPhotoRefsFromSource(wizardPayload);
  }, [wizardPayload, wizardType]);

  const applyChecklistPhotoRefs = useCallback((refs: string[]) => {
    setWizardPayload((prev) => ({
      ...prev,
      checklistPhotoRefs: dedupeStringList(refs),
    }));
  }, []);

  const removeChecklistPhotoRef = useCallback(
    (ref: string) => {
      applyChecklistPhotoRefs(checklistPhotoRefs.filter((item) => item !== ref));
    },
    [applyChecklistPhotoRefs, checklistPhotoRefs]
  );

  const removeChecklistNewPhoto = useCallback((index: number) => {
    setChecklistNewPhotos((current) => current.filter((_item, currentIndex) => currentIndex !== index));
  }, []);

  const checklistNewPhotoPreviews = useMemo(
    () =>
      checklistNewPhotos.map((file) => ({
        file,
        previewUrl: URL.createObjectURL(file),
      })),
    [checklistNewPhotos]
  );

  useEffect(() => {
    return () => {
      checklistNewPhotoPreviews.forEach((item) => URL.revokeObjectURL(item.previewUrl));
    };
  }, [checklistNewPhotoPreviews]);

  const checklistSavedPhotoItems = useMemo(
    () =>
      checklistPhotoRefs.map((ref) => {
        const candidates = buildChecklistPhotoPreviewCandidates(ref, savedDocument?.attachmentUrls);
        const index = checklistPhotoCandidateIndexByRef[ref] ?? 0;
        const boundedIndex = index < 0 ? -1 : Math.min(index, Math.max(0, candidates.length - 1));
        const previewUrl = boundedIndex >= 0 ? candidates[boundedIndex] || "" : "";
        return {
          ref,
          candidates,
          previewUrl,
        };
      }),
    [checklistPhotoCandidateIndexByRef, checklistPhotoRefs, savedDocument?.attachmentUrls]
  );

  useEffect(() => {
    setChecklistPhotoCandidateIndexByRef({});
  }, [checklistPhotoRefs]);

  const handleChecklistSavedPhotoLoadError = useCallback((ref: string, candidates: string[]) => {
    setChecklistPhotoCandidateIndexByRef((current) => {
      const currentIndex = current[ref] ?? 0;
      if (currentIndex < candidates.length - 1) {
        return {
          ...current,
          [ref]: currentIndex + 1,
        };
      }
      if (currentIndex >= 0) {
        return {
          ...current,
          [ref]: -1,
        };
      }
      return current;
    });
  }, []);

  const checklistTires = useMemo<ChecklistTires>(() => {
    if (wizardType !== "ENTREGA_DEVOLUCAO_CHECKLIST") {
      return {};
    }
    return normalizeChecklistTiresForEditor(wizardPayload?.tires);
  }, [wizardPayload?.tires, wizardType]);

  const patchChecklistTires = useCallback((patch: Partial<ChecklistTires>) => {
    setWizardPayload((prev) => {
      const currentTires = normalizeChecklistTiresForEditor(prev?.tires);
      const next = normalizeChecklistTiresForEditor({
        ...currentTires,
        ...patch,
        source: patch.source || "MANUAL",
      });
      return {
        ...prev,
        tires: next,
      };
    });
  }, []);

  const createDebtItemTypeInline = useCallback(async () => {
    const rawName = window.prompt("Nome do novo tipo de dívida:");
    if (rawName === null) {
      return;
    }

    const name = rawName.trim();
    if (!name) {
      showErrorAlert("Informe um nome válido para o tipo.");
      return;
    }

    setIsActionLoading(true);
    try {
      await debtItemTypeService.createDebtItemType({ name, active: true });
      await loadDebtItemTypes();
      showSuccessToast("Tipo de dívida criado.");
    } catch (error: any) {
      if (error?.response?.status === 409) {
        showErrorAlert("Já existe um tipo com esse nome.");
      } else {
        showErrorAlert("Falha ao criar tipo de dívida.");
      }
    } finally {
      setIsActionLoading(false);
    }
  }, [loadDebtItemTypes, showErrorAlert, showSuccessToast]);

  useEffect(() => {
    if (!isWizardOpen || wizardType !== "CONFISSAO_DIVIDA") {
      return;
    }

    setWizardPayload((current) => {
      const normalized = normalizePayloadForEditor("CONFISSAO_DIVIDA", current);
      const items = Array.isArray(normalized.itensDaDivida) ? normalized.itensDaDivida : [];
      if (items.length > 0) {
        return normalized;
      }

      const initialItems: ConfissaoDebtItem[] = [buildEmptyConfissaoItem()];

      return {
        ...normalized,
        itensDaDivida: initialItems,
        valorTotal: calculateConfissaoTotal(initialItems),
      };
    });
  }, [
    buildEmptyConfissaoItem,
    calculateConfissaoTotal,
    isWizardOpen,
    normalizePayloadForEditor,
    wizardType,
  ]);

  useEffect(() => {
    if (!isWizardOpen || wizardType !== "CONFISSAO_DIVIDA" || !wizardDriver?.id) {
      return;
    }
    void importDriverPendenciesToConfissao(wizardDriver);
  }, [importDriverPendenciesToConfissao, isWizardOpen, wizardDriver, wizardType]);

  useEffect(() => {
    if (!isWizardOpen || wizardType !== "ENTREGA_DEVOLUCAO_CHECKLIST") {
      return;
    }

    setWizardPayload((current) =>
      normalizePayloadForEditor("ENTREGA_DEVOLUCAO_CHECKLIST", current)
    );
  }, [isWizardOpen, normalizePayloadForEditor, wizardType]);

  useEffect(() => {
    if (!isWizardOpen || wizardType !== "ENTREGA_DEVOLUCAO_CHECKLIST" || !wizardCar?.id) {
      return;
    }

    let active = true;
    const loadLastInspectionSuggestion = async () => {
      try {
        const { data } = await api.get(
          endpoints.CAR({
            pathVariables: { id: wizardCar.id },
          })
        );
        if (!active) {
          return;
        }

        // Only a suggestion the user may apply: the checklist records the tires checked now, never a copy by itself.
        const tiresSuggestion = buildChecklistTiresFromInspection(data?.lastInspection);
        setLastInspectionTires(tiresSuggestion?.positions || []);
        logDocumentsDebug("checklist tires suggestion loaded", { carId: wizardCar.id, found: Boolean(tiresSuggestion) });
      } catch (error) {
        logDocumentsDebug("checklist tires suggestion failed", {
          carId: wizardCar.id,
          error: String(error),
        });
      }
    };

    void loadLastInspectionSuggestion();
    return () => {
      active = false;
    };
  }, [isWizardOpen, logDocumentsDebug, wizardCar?.id, wizardType]);

  const isChecklistWizard = wizardType === "ENTREGA_DEVOLUCAO_CHECKLIST";
  /** Draft of the old wizard: kept as it was (free-text fields, no contract / inspection) until converted. */
  const isLegacyChecklist =
    isChecklistWizard && !!savedDocument?.id && !savedDocument.checklistType && !checklistLegacyConversion;
  const isStructuredChecklist = isChecklistWizard && !isLegacyChecklist;
  const isChecklistFinal = isStructuredChecklist && !!savedDocument?.checklistType && savedDocument.status !== "DRAFT";
  const checklistType: "ENTREGA" | "DEVOLUCAO" | "" =
    wizardPayload.tipo === "ENTREGA" || wizardPayload.tipo === "DEVOLUCAO" ? wizardPayload.tipo : "";

  useEffect(() => {
    if (!isWizardOpen || wizardType !== "ENTREGA_DEVOLUCAO_CHECKLIST" || !wizardCar?.id) {
      setChecklistContracts(null);
      return;
    }
    let active = true;
    const carId = wizardCar.id;
    setChecklistContracts(null);
    const load = async () => {
      try {
        const { data } = await api.get<CarDriverModel[]>(endpoints.DRIVERS({ pathVariables: { id: carId } }));
        if (active) {
          setChecklistContracts(Array.isArray(data) ? data : []);
        }
      } catch (_error) {
        if (active) {
          setChecklistContracts([]);
        }
      }
    };
    void load();
    return () => {
      active = false;
    };
  }, [isWizardOpen, wizardType, wizardCar?.id, savedDocument?.status]);

  useEffect(() => {
    if (!isWizardOpen || wizardType !== "ENTREGA_DEVOLUCAO_CHECKLIST" || !wizardCar?.id) {
      setCarDamages([]);
      return;
    }
    let active = true;
    const carId = wizardCar.id;
    const load = async () => {
      try {
        // Every damage of the car (this account), but the explicitly resolved ones: older records without a
        // resolution (NULL) are shown, never hidden nor rewritten.
        const { data } = await api.get(endpoints.BODY_DAMAGE({ pathVariables: { id: carId } }));
        if (active) {
          setCarDamages((Array.isArray(data) ? data : []).filter((damage: any) => damage?.resolved !== true));
        }
      } catch (_error) {
        if (active) {
          setCarDamages([]);
        }
      }
    };
    void load();
    return () => {
      active = false;
    };
  }, [isWizardOpen, wizardType, wizardCar?.id, savedDocument?.status]);

  /** Open contracts of the selected driver on the selected car, and whoever else operates the car. */
  const checklistContractState = useMemo(() => {
    const contracts = checklistContracts || [];
    const ofDriver = contracts.filter(
      (contract) => contract.driver?.id === wizardDriver?.id && driverCarStatus(contract) !== "CONCLUDED"
    );
    return {
      loaded: checklistContracts !== null,
      active: ofDriver.filter((contract) => driverCarStatus(contract) === "ACTIVE"),
      suspended: ofDriver.filter((contract) => driverCarStatus(contract) === "SUSPENDED"),
      otherDriver: contracts.find(
        (contract) => contract.driver?.id !== wizardDriver?.id && driverCarStatus(contract) === "ACTIVE"
      ),
    };
  }, [checklistContracts, wizardDriver?.id]);

  // An Entrega on an existing contract: the checklist types already finalized on it (any number of documents).
  const deliveryContractId = checklistType === "ENTREGA" ? checklistContractState.active[0]?.id : undefined;
  useEffect(() => {
    if (!isStructuredChecklist || isChecklistFinal || !deliveryContractId) {
      setContractFinalChecklists(null);
      return;
    }
    let active = true;
    const load = async () => {
      try {
        const { data } = await api.get<string[]>(endpoints.DRIVER_CAR_FINAL_CHECKLISTS({ pathVariables: { id: deliveryContractId } }));
        if (active) {
          setContractFinalChecklists({ contractId: deliveryContractId, types: Array.isArray(data) ? data : [] });
        }
      } catch (_error) {
        if (active) {
          setContractFinalChecklists(null); // the finalization keeps the final word
        }
      }
    };
    void load();
    return () => {
      active = false;
    };
  }, [deliveryContractId, isChecklistFinal, isStructuredChecklist]);

  // Devolução: the only open contract of the driver on the car is preselected; a choice that no longer applies is cleared.
  useEffect(() => {
    if (!isStructuredChecklist || isChecklistFinal || !checklistContractState.loaded || checklistType !== "DEVOLUCAO") {
      return;
    }
    const ids = checklistContractState.active.map((contract) => contract.id);
    if (checklistDriverCarId && !ids.includes(checklistDriverCarId)) {
      setChecklistDriverCarId(null);
    } else if (!checklistDriverCarId && ids.length === 1 && ids[0]) {
      setChecklistDriverCarId(ids[0]);
    }
  }, [checklistContractState, checklistDriverCarId, checklistType, isChecklistFinal, isStructuredChecklist]);

  /** Why the operation cannot happen with this driver / car / contract (explained before finalizing), or "". */
  const checklistBlockingMessage = useMemo(() => {
    if (!isStructuredChecklist || !wizardDriver?.id || !wizardCar?.id || !checklistContractState.loaded || !checklistType) {
      return "";
    }
    const plate = wizardCar.plate || "selecionado";
    if (!checklistContractState.active.length && checklistContractState.suspended.length) {
      return "O vínculo deste motorista com este veículo está suspenso (o motorista está em um carro reserva). Devolva o carro reserva ou reative o vínculo antes.";
    }
    if (checklistType === "DEVOLUCAO" && !checklistContractState.active.length) {
      return `Este motorista não possui vínculo ativo com o veículo ${plate}. A devolução só pode ser registrada sobre um vínculo existente.`;
    }
    const deliveredContract = checklistContractState.active[0];
    if (
      checklistType === "ENTREGA" &&
      deliveredContract &&
      contractFinalChecklists !== null &&
      contractFinalChecklists.contractId === deliveredContract.id &&
      contractFinalChecklists.types.includes("ENTREGA")
    ) {
      return "Este vínculo já possui um Checklist de Entrega finalizado. Para outra via, use Emitir 2ª via (PDF) em Inspeções.";
    }
    if (checklistType === "ENTREGA" && !checklistContractState.active.length && checklistContractState.otherDriver) {
      const other = checklistContractState.otherDriver.driver?.name || "outro motorista";
      return `O veículo ${plate} está vinculado a ${other}. Registre a devolução ou encerre esse vínculo antes da entrega.`;
    }
    return "";
  }, [contractFinalChecklists, checklistContractState, checklistType, isStructuredChecklist, wizardCar, wizardDriver?.id]);

  // Launch from Inspeções (/documents?checklist=ENTREGA|DEVOLUCAO&carId=..&from=..): the existing wizard, already on
  // the checklist of that car; a Devolução also gets the driver of the car's active contract. The URL is consumed.
  // Consuming the URL changes location.search: the opening in progress must not be cancelled by that, so a launch is
  // handled once (by its URL) and only an unmount stops it.
  const handledLaunchRef = useRef<string | null>(null);
  const mountedRef = useRef(true);
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);
  useEffect(() => {
    const search = location?.search || "";
    const launch = parseChecklistLaunch(search);
    if (!launch) {
      handledLaunchRef.current = null; // the same request can be made again later
      return;
    }
    if (handledLaunchRef.current === search) {
      return;
    }
    handledLaunchRef.current = search;
    const open = async () => {
      const active = () => mountedRef.current;
      if (launch.documentId) {
        // Continuing a draft (from Inspeções, or a reload after its first save): only a draft checklist of this car.
        try {
          const document = await documentService.getDocument(launch.documentId);
          if (!active()) {
            return;
          }
          if (document?.type !== "ENTREGA_DEVOLUCAO_CHECKLIST" || Number(document.carId) !== launch.carId) {
            showErrorAlert("Este checklist não pertence a este veículo.");
            leaveLaunch(launch);
            return;
          }
          if (document.status !== "DRAFT") {
            showErrorAlert("Este checklist já foi finalizado: use Emitir 2ª via (PDF) em Inspeções.");
            leaveLaunch(launch);
            return;
          }
          loadDocumentIntoWizard(document);
          setActiveLaunch(launch);
          snapshotPendingRef.current = true;
        } catch (_error) {
          if (active()) {
            showErrorAlert("Não foi possível abrir o rascunho deste checklist.");
            leaveLaunch(launch);
          }
        }
        return;
      }
      try {
        const { data } = await api.get(endpoints.CAR({ pathVariables: { id: launch.carId } }));
        const car = data?.car ?? data; // GET /cars/{id} answers { car, ... } (as the car page reads it)
        if (!active() || !car?.id) {
          return;
        }
        let driver: DriverSearchModel | null = null;
        if (launch.checklistType === "DEVOLUCAO") {
          const { data: contracts } = await api.get<CarDriverModel[]>(endpoints.DRIVERS({ pathVariables: { id: car.id } }));
          const operating = (Array.isArray(contracts) ? contracts : []).filter((contract) => driverCarStatus(contract) === "ACTIVE");
          if (operating.length === 1 && operating[0].driver?.id) {
            const contractDriver = operating[0].driver;
            driver = { id: contractDriver.id as number, name: contractDriver.name || "", cpf: contractDriver.cpf || "", active: true };
          }
        }
        if (!active()) {
          return;
        }
        resetWizard();
        setChecklistPdfFailed(false);
        setWizardType("ENTREGA_DEVOLUCAO_CHECKLIST");
        setWizardCar({ id: car.id, plate: car.plate || "", model: car.model || "", active: true });
        setWizardCarQuery(car.plate || "");
        if (driver) {
          setWizardDriver(driver);
          setWizardDriverQuery(driver.name || "");
        }
        setWizardPayload({
          tipo: launch.checklistType,
          carPlate: car.plate || "",
          carModel: car.model || "",
          driverName: driver?.name || "",
          driverCpf: driver?.cpf || "",
        });
        // With the driver known the form opens directly; otherwise the first step asks for the driver.
        setWizardStep(driver ? 3 : 1);
        setActiveLaunch(launch);
        setIsWizardOpen(true);
        snapshotPendingRef.current = true;
      } catch (_error) {
        if (active()) {
          showErrorAlert("Não foi possível abrir o checklist deste veículo.");
        }
      }
    };
    void open();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location?.search]);

  /** PDF of a finalized checklist, from its stored document: documentary only, it never finalizes again. */
  const issueChecklistPdf = async (documentId: number): Promise<boolean> => {
    try {
      const detailed = await documentService.getDocument(documentId);
      await generateDocumentPdf(detailed);
      setSavedDocument(await documentService.generateDocumentPdf(documentId));
      setChecklistPdfFailed(false);
      return true;
    } catch (_error) {
      setChecklistPdfFailed(true);
      return false;
    }
  };

  /**
   * What a save would persist (normalized as saveDraft does, so the editor's own normalization never counts as a
   * change: an ion-input echoes the saved km 45678 back as "45678").
   */
  const checklistSnapshot = () => {
    const payload = wizardType ? normalizePayloadForApi(wizardType as DocumentType, wizardPayload) : { ...wizardPayload };
    const km = isStructuredChecklist ? parseChecklistKm(payload.km) : null;
    if (km !== null) {
      payload.km = km;
    }
    return JSON.stringify({
      type: wizardType,
      driver: wizardDriver?.id ?? null,
      car: wizardCar?.id ?? null,
      contract: checklistDriverCarId,
      payload,
      pendingFiles: wizardFiles.length + checklistNewPhotos.length,
    });
  };
  useEffect(() => {
    if (isWizardOpen && snapshotPendingRef.current) {
      snapshotPendingRef.current = false;
      savedSnapshotRef.current = checklistSnapshot();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isWizardOpen, wizardPayload, wizardDriver, wizardCar, wizardType, checklistDriverCarId, wizardFiles, checklistNewPhotos]);
  /** Unsaved changes in an open checklist (a finalized one has nothing left to lose). */
  const isChecklistDirty = () =>
    isWizardOpen && isChecklistWizard && !isChecklistFinal && savedSnapshotRef.current !== null && checklistSnapshot() !== savedSnapshotRef.current;
  const isChecklistDirtyRef = useRef(isChecklistDirty);
  isChecklistDirtyRef.current = isChecklistDirty;
  // A refresh or closing the tab with unsaved changes also asks first (the browser's own prompt). Browser Back is not
  // blocked: history.block cannot revert a Back after a reload (the router and the URL would disagree).
  useEffect(() => {
    if (!isWizardOpen) {
      return;
    }
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (isChecklistDirtyRef.current()) {
        event.preventDefault();
        event.returnValue = "Descartar as alterações não salvas deste checklist?";
      }
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [isWizardOpen]);

  /** forFinalize: a structured checklist needs its 2 contacts to be finalized, not to keep a draft (they are in part 2). */
  const validateWizard = (forFinalize = false) => {
    if (!wizardDriver?.id) {
      showErrorAlert("Selecione um motorista.");
      return false;
    }
    if (!wizardType) {
      showErrorAlert("Selecione o tipo de documento.");
      return false;
    }
    if (wizardRequiresCar && !wizardCar?.id) {
      showErrorAlert("Esse tipo exige carro.");
      return false;
    }

    if (wizardType === "CONFISSAO_DIVIDA") {
      if (!confissaoItems.length) {
        showErrorAlert("Adicione ao menos um item da dívida.");
        return false;
      }
      if (confissaoItems.some((item) => !`${item.typeNameSnapshot || ""}`.trim())) {
        showErrorAlert("Selecione o tipo de todos os itens da dívida.");
        return false;
      }
      if (confissaoItems.some((item) => (parseDecimal(item.valorItem) || 0) <= 0)) {
        showErrorAlert("Informe valor maior que zero para todos os itens da dívida.");
        return false;
      }
    }

    if (wizardType === "ENTREGA_DEVOLUCAO_CHECKLIST") {
      const contacts = serializeChecklistEmergencyContacts(wizardPayload?.emergencyContacts);
      const completeContacts = contacts.filter((contact) =>
        isChecklistEmergencyContactComplete(contact)
      );
      const hasPartial = contacts.some((contact) =>
        isChecklistEmergencyContactPartial(contact)
      );

      if (hasPartial) {
        showErrorAlert("Preencha nome e telefone em cada contato de emergência.");
        return false;
      }

      if ((!isStructuredChecklist || forFinalize) && completeContacts.length < MIN_EMERGENCY_CONTACTS) {
        showErrorAlert("Informe ao menos 2 contatos de emergência (nome e telefone).");
        return false;
      }

      const hasInvalidPhone = completeContacts.some((contact) => {
        const digits = sanitizeDigits(contact.telefone || "");
        return digits.length < 10 || digits.length > 11;
      });
      if (hasInvalidPhone) {
        showErrorAlert("Telefone de emergência deve conter 10 ou 11 dígitos.");
        return false;
      }

      if (isStructuredChecklist && !checklistType) {
        showErrorAlert("Informe se o checklist é de Entrega ou de Devolução.");
        return false;
      }
      if (isStructuredChecklist && checklistType === "DEVOLUCAO" && !checklistDriverCarId) {
        showErrorAlert(checklistBlockingMessage || "Selecione o vínculo que está sendo devolvido.");
        return false;
      }
    }

    return true;
  };

  const saveDraft = async (): Promise<DocumentModel | null> => {
    if (!validateWizard()) {
      return null;
    }

    setIsActionLoading(true);
    try {
      const payload = syncMetaPayload();
      const normalizedPayload = normalizePayloadForApi(wizardType as DocumentType, payload);
      if (isStructuredChecklist) {
        // The backend reads the km as a number; an invalid text is kept as typed and refused at the finalization.
        const km = parseChecklistKm(normalizedPayload.km);
        if (km !== null) {
          normalizedPayload.km = km;
        }
      }
      const request = {
        type: wizardType as DocumentType,
        status: "DRAFT" as DocumentStatus,
        driverId: wizardDriver!.id,
        carId: wizardRequiresCar ? wizardCar!.id : wizardCar?.id ?? null,
        payload: normalizedPayload,
        ...(isStructuredChecklist
          ? {
              checklistType: checklistType || null,
              ...(checklistType === "DEVOLUCAO" ? { driverCarId: checklistDriverCarId } : {}),
            }
          : {}),
      };

      logDocumentsDebug("saveDraft started", {
        mode: savedDocument?.id ? "update" : "create",
        type: wizardType,
        wizardFilesCount: wizardFiles.length,
        checklistNewPhotosCount: checklistNewPhotos.length,
      });

      let response = savedDocument?.id
        ? await documentService.updateDocument(savedDocument.id, request)
        : await documentService.createDocument(request);

      logDocumentsDebug("saveDraft persisted draft", {
        documentId: response?.id,
        attachmentsCount: response?.attachments?.length || 0,
      });

      let attachmentsAfterGenericUpload = response.attachments || [];
      if (wizardFiles.length && response.id) {
        response = await documentService.uploadDocumentAttachments(response.id, wizardFiles);
        attachmentsAfterGenericUpload = response.attachments || [];
        logDocumentsDebug("saveDraft uploaded generic attachments", {
          documentId: response.id,
          uploadedCount: wizardFiles.length,
          attachmentsCount: attachmentsAfterGenericUpload.length,
        });
      }

      let checklistUploadedRefs: string[] = [];
      if (
        wizardType === "ENTREGA_DEVOLUCAO_CHECKLIST" &&
        checklistNewPhotos.length &&
        response.id
      ) {
        response = await documentService.uploadDocumentAttachments(response.id, checklistNewPhotos);
        checklistUploadedRefs = resolveAttachmentDiff(
          response.attachments || [],
          attachmentsAfterGenericUpload
        );
        logDocumentsDebug("saveDraft uploaded checklist photos", {
          documentId: response.id,
          uploadedCount: checklistNewPhotos.length,
          newRefs: checklistUploadedRefs,
        });
      }

      let detailed = response;
      if (response.id) {
        detailed = await documentService.getDocument(response.id);
        logDocumentsDebug("saveDraft refreshed document", {
          documentId: detailed.id,
          attachmentsCount: detailed.attachments?.length || 0,
          payloadChecklistRefs: resolveChecklistPhotoRefsFromSource(detailed.payload || {}),
        });
      }

      if (wizardType === "ENTREGA_DEVOLUCAO_CHECKLIST" && detailed.id) {
        const currentPayload = detailed.payload || {};
        const refsStored = resolveChecklistPhotoRefsFromSource(currentPayload, detailed.attachments || []);
        const refsFromEditor = resolveChecklistPhotoRefsFromSource(normalizedPayload);
        const refsMerged = dedupeStringList([
          ...refsStored,
          ...refsFromEditor,
          ...checklistUploadedRefs,
        ]);

        if (!areStringListsEqual(refsStored, refsMerged)) {
          const payloadWithRefs = normalizePayloadForApi("ENTREGA_DEVOLUCAO_CHECKLIST", {
            ...currentPayload,
            checklistPhotoRefs: refsMerged,
            attachmentsChecklist: refsMerged,
          });
          detailed = await documentService.updateDocument(detailed.id, {
            payload: payloadWithRefs,
          });
          detailed = await documentService.getDocument(detailed.id as number);
          logDocumentsDebug("saveDraft persisted checklist photo refs", {
            documentId: detailed.id,
            refsMerged,
          });
        }
      }

      setSavedDocument(detailed);
      setWizardPayload(
        normalizePayloadForEditor(
          detailed.type,
          detailed.payload || normalizedPayload,
          detailed.attachments || []
        )
      );
      setWizardFiles([]);
      setChecklistNewPhotos([]);
      snapshotPendingRef.current = true;
      if (activeLaunch && !activeLaunch.documentId && detailed.id) {
        const next = withLaunchDocument(activeLaunch, detailed.id);
        handledLaunchRef.current = next.slice(next.indexOf("?"));
        setActiveLaunch({ ...activeLaunch, documentId: detailed.id });
        history?.replace(next, location?.state);
      }
      await loadDocuments();
      return detailed;
    } catch (error) {
      logDocumentsDebug("saveDraft failed", { error: String(error) });
      showErrorAlert(
        wizardType === "ENTREGA_DEVOLUCAO_CHECKLIST"
          ? getApiErrorMessage(error, "Falha ao salvar rascunho.", CHECKLIST_ERROR_MESSAGES)
          : "Falha ao salvar rascunho."
      );
      return null;
    } finally {
      setIsActionLoading(false);
    }
  };

  const generateWizardPdf = async () => {
    if (isStructuredChecklist && (!savedDocument?.id || savedDocument.status === "DRAFT")) {
      // The PDF is only the document of a finalized checklist: it never finalizes (nor moves contract / inspection).
      showErrorAlert("Finalize o checklist antes de gerar o PDF.");
      return;
    }
    if (isStructuredChecklist && savedDocument?.id) {
      // Finalized checklist: only the document's PDF (a retry after a failure included).
      setIsActionLoading(true);
      const issued = await issueChecklistPdf(savedDocument.id);
      setIsActionLoading(false);
      if (!issued) {
        showErrorAlert("Falha ao gerar o PDF. Tente novamente.");
      }
      return;
    }
    // A legacy checklist draft keeps the old flow (saved, finalized without automation, PDF).
    const draft = (await saveDraft()) || savedDocument;
    if (!draft?.id) {
      return;
    }

    setIsActionLoading(true);
    try {
      let activeDocument = draft;
      if (draft.status === "DRAFT") {
        activeDocument = await documentService.finalizeDocument(draft.id);
      }
      const detailed = await documentService.getDocument(activeDocument.id as number);
      await generateDocumentPdf(detailed);
      setSavedDocument(await documentService.generateDocumentPdf(activeDocument.id as number));
      await loadDocuments();
    } catch (_error) {
      showErrorAlert("Falha ao gerar PDF.");
    } finally {
      setIsActionLoading(false);
    }
  };

  /** Part 1 (condições do veículo) complete: the conference opens; otherwise the missing fields are named. */
  const goToChecklistConference = () => {
    const errors = structuredChecklistErrors(checklistType, wizardPayload, checklistDriverCarId);
    if (checklistBlockingMessage) {
      errors.push(checklistBlockingMessage);
    }
    if (errors.length) {
      showErrorAlert(errors.join(" "));
      return;
    }
    setChecklistPart(2);
  };

  /**
   * Finalizar (Entrega/Devolução): the operation itself, done by the backend in one transaction - contract created /
   * reused / concluded, inspection registered, document FINAL. Asks PERMANENT/RESERVE only on the backend's 409.
   */
  const finalizeChecklist = async (assignment?: DriverAssignmentType) => {
    if (!assignment) {
      if (!validateWizard(true)) {
        return;
      }
      const errors = structuredChecklistErrors(checklistType, wizardPayload, checklistDriverCarId);
      if (checklistBlockingMessage) {
        errors.push(checklistBlockingMessage);
      }
      if (errors.length) {
        showErrorAlert(errors.join(" "));
        return;
      }
      const effects =
        checklistType === "ENTREGA"
          ? "Finalizar a entrega vincula o motorista a este veículo (ou usa o vínculo já existente), registra a vistoria como inspeção do veículo e atualiza o hodômetro quando for a vistoria mais recente."
          : "Finalizar a devolução encerra o vínculo do motorista com este veículo (carro reserva: o motorista volta ao vínculo principal quando possível), registra a vistoria como inspeção do veículo e atualiza o hodômetro quando for a vistoria mais recente.";
      if (!window.confirm(`${effects} Depois de finalizado, o checklist não pode ser editado nem excluído. Deseja finalizar?`)) {
        return;
      }
    }

    let draft: DocumentModel | null = savedDocument;
    if (savedDocument?.id) {
      // A retry after a lost response: the document may already be final - then nothing is sent again.
      try {
        const current = await documentService.getDocument(savedDocument.id);
        if (current.status !== "DRAFT") {
          setSavedDocument(current);
          setChecklistFeedback("Este checklist já estava finalizado.");
          await loadDocuments();
          return;
        }
      } catch (_error) {
        // the save / finalization below reports the problem
      }
    }
    if (!assignment) {
      draft = await saveDraft();
    }
    if (!draft?.id) {
      return;
    }

    setIsActionLoading(true);
    try {
      const result = await documentService.finalizeDocument(draft.id, assignment);
      const detailed = await documentService.getDocument(draft.id);
      setSavedDocument(detailed);
      setChecklistFeedback(result?.reserveReturn ? describeReserveReturn(result.reserveReturn, wizardDriver?.name).message : "");
      showSuccessToast(checklistType === "DEVOLUCAO" ? "Devolução finalizada." : "Entrega finalizada.");
      snapshotPendingRef.current = true;
      await loadDocuments();
      // Only after the confirmed finalization: the PDF of the FINAL document (a failure keeps Gerar PDF to retry).
      if (!(await issueChecklistPdf(draft.id))) {
        showErrorAlert("Checklist finalizado, mas o PDF não pôde ser gerado agora. Use Gerar PDF para tentar novamente.");
      }
    } catch (error) {
      const conflict = getAssignmentConflict(error);
      if (conflict && !assignment) {
        setChecklistConflict(conflict);
        return;
      }
      showErrorAlert(getApiErrorMessage(error, "Falha ao finalizar o checklist.", CHECKLIST_ERROR_MESSAGES));
    } finally {
      setIsActionLoading(false);
    }
  };

  const loadDocumentIntoWizard = useCallback(
    (document: DocumentModel) => {
      const payload = document.payload || {};
      const payloadDriverName = `${payload.driverName || ""}`.trim();
      const payloadDriverCpf = `${payload.driverCpf || ""}`.trim();
      const payloadCarPlate = `${payload.carPlate || ""}`.trim();
      const payloadCarModel = `${payload.carModel || ""}`.trim();

      confissaoImportRequestIdRef.current += 1;
      setWizardType(document.type);
      setWizardStep(3);
      setSavedDocument(document);
      setChecklistDriverCarId(document.driverCarId ?? null);
      setChecklistFeedback("");
      setChecklistLegacyConversion(false);
      setChecklistPart(1);
      setWizardPayload(normalizePayloadForEditor(document.type, payload, document.attachments || []));
      setWizardFiles([]);
      setChecklistNewPhotos([]);
      setIsConfissaoPendenciesLoading(false);
      setConfissaoImportFeedback("");
      setConfissaoAutoImportedDriverId(document.type === "CONFISSAO_DIVIDA" ? document.driverId : null);
      setWizardDriverOptions([]);
      setWizardCarOptions([]);
      logDocumentsDebug("wizard hydrated from draft", {
        documentId: document.id,
        type: document.type,
        attachmentsCount: document.attachments?.length || 0,
        checklistRefs: resolveChecklistPhotoRefsFromSource(payload, document.attachments || []),
      });

      if (document.driverId) {
        const driver = {
          id: document.driverId,
          name: document.driverName || payloadDriverName,
          cpf: document.driverCpf || payloadDriverCpf,
          active: true,
        };
        setWizardDriver(driver);
        setWizardDriverQuery(driver.name || "");
      } else {
        setWizardDriver(null);
        setWizardDriverQuery(payloadDriverName);
      }

      if (document.carId) {
        const car = {
          id: document.carId,
          plate: document.carPlate || payloadCarPlate,
          model: document.carModel || payloadCarModel,
          active: true,
        };
        setWizardCar(car);
        setWizardCarQuery(car.plate || "");
      } else {
        setWizardCar(null);
        setWizardCarQuery(payloadCarPlate);
      }

      setIsWizardOpen(true);
      snapshotPendingRef.current = true;
    },
    [logDocumentsDebug, normalizePayloadForEditor]
  );

  const openEdit = async (id?: number) => {
    if (!id) {
      return;
    }
    setIsActionLoading(true);
    try {
      const document = await documentService.getDocument(id);
      if (document.status !== "DRAFT") {
        showErrorAlert("Somente rascunhos podem ser editados.");
        return;
      }
      loadDocumentIntoWizard(document);
    } catch (_error) {
      showErrorAlert("Falha ao abrir rascunho para edição.");
    } finally {
      setIsActionLoading(false);
    }
  };

  const deleteDocument = async (id?: number): Promise<boolean> => {
    if (!id) {
      return false;
    }
    const confirmed = window.confirm("Deseja excluir este documento?");
    if (!confirmed) {
      return false;
    }
    setIsActionLoading(true);
    try {
      await documentService.deleteDocument(id);
      if (viewDocument?.id === id) {
        setIsViewModalOpen(false);
        setViewDocument(null);
      }
      if (savedDocument?.id === id) {
        savedSnapshotRef.current = null; // deleted on purpose: nothing left to discard
        closeWizard();
      }
      await loadDocuments();
      showSuccessToast("Documento excluído.");
      return true;
    } catch (_error) {
      showErrorAlert("Falha ao excluir documento.");
      return false;
    } finally {
      setIsActionLoading(false);
    }
  };

  const deleteWizardDocument = async () => {
    if (!savedDocument?.id) {
      showErrorAlert("Salve o rascunho antes de excluir.");
      return;
    }
    await deleteDocument(savedDocument.id);
  };

  const openView = async (id?: number) => {
    if (!id) {
      return;
    }
    setIsActionLoading(true);
    try {
      setViewDocument(await documentService.getDocument(id));
      setIsViewModalOpen(true);
    } catch (_error) {
      showErrorAlert("Falha ao carregar documento.");
    } finally {
      setIsActionLoading(false);
    }
  };

  const openPdf = async (id?: number) => {
    if (!id) {
      return;
    }
    setIsActionLoading(true);
    try {
      const detailed = await documentService.getDocument(id);
      await generateDocumentPdf(detailed);
      await documentService.generateDocumentPdf(id);
    } catch (_error) {
      showErrorAlert("Falha ao abrir PDF.");
    } finally {
      setIsActionLoading(false);
    }
  };

  /** The contract the checklist operates on: the operation is explained before finalizing, never inferred silently. */
  const renderChecklistContract = () => {
    if (!wizardCar?.id || !wizardDriver?.id || !checklistType) {
      return null;
    }
    if (!checklistContractState.loaded) {
      return <p className="documents-hint">Carregando vínculos do veículo…</p>;
    }
    if (checklistBlockingMessage) {
      return (
        <p className="documents-warning" data-testid="checklist-contract-blocked">
          {checklistBlockingMessage}
        </p>
      );
    }
    const since = (contract: CarDriverModel) =>
      `Vínculo desde ${formatChecklistDate(contract.startDate) || "-"}${contract.reserve ? " (carro reserva)" : ""}`;
    if (checklistType === "ENTREGA") {
      const existing = checklistContractState.active[0];
      return (
        <p className="documents-hint" data-testid="checklist-contract-info">
          {existing
            ? `${since(existing)}: a entrega será registrada neste vínculo.`
            : "Ao finalizar, o motorista será vinculado a este veículo. Se ele já tiver vínculo com outro veículo, você escolherá entre transferência definitiva e carro reserva."}
        </p>
      );
    }
    const selected = checklistContractState.active.find((contract) => contract.id === checklistDriverCarId);
    return (
      <>
        {checklistContractState.active.length > 1 && (
          <SelectField
            label="Vínculo devolvido *"
            value={checklistDriverCarId ? `${checklistDriverCarId}` : ""}
            options={checklistContractState.active.map((contract) => ({ value: `${contract.id}`, label: since(contract) }))}
            onChange={(v) => setChecklistDriverCarId(Number(v) || null)}
          />
        )}
        {selected && (
          <p className="documents-hint" data-testid="checklist-contract-info">
            {selected.reserve
              ? `${since(selected)}: ao finalizar, o carro reserva é devolvido e o motorista volta ao vínculo principal quando o veículo principal estiver livre.`
              : `${since(selected)}: ao finalizar, este vínculo será encerrado na data da vistoria.`}
          </p>
        )}
      </>
    );
  };

  /** The two parts of the structured checklist form (the second opens once the first is complete). */
  const renderChecklistParts = () => (
    <div className="documents-checklist-parts" role="tablist" aria-label="Etapas do checklist">
      {[
        { part: 1 as const, label: "1. Condições do veículo" },
        { part: 2 as const, label: "2. Conferência e finalização" },
      ].map((item) => (
        <button
          key={item.part}
          type="button"
          role="tab"
          aria-selected={checklistPart === item.part}
          className={`documents-checklist-parts__tab${checklistPart === item.part ? " documents-checklist-parts__tab--active" : ""}`}
          onClick={() => setChecklistPart(item.part)}
        >
          {item.label}
        </button>
      ))}
    </div>
  );

  const checklistTirePositions = CHECKLIST_TIRE_POSITIONS.map(
    (label) => (checklistTires.positions || []).find((position) => position.posicao === label) || { posicao: label }
  );

  /** One tire position edited: the five positions are kept, marked as filled in this checklist. */
  const setChecklistTire = (label: string, patch: Partial<ChecklistTirePosition>) => {
    setWizardPayload((prev) => {
      const current = normalizeChecklistTiresForEditor(prev?.tires);
      const positions = CHECKLIST_TIRE_POSITIONS.map((position) => {
        const stored = (current.positions || []).find((item) => item.posicao === position) || { posicao: position };
        return position === label ? { ...stored, ...patch } : stored;
      });
      return { ...prev, tires: { source: "CHECKLIST", positions, observacoes: current.observacoes } };
    });
  };

  /** The user applies the last inspection's tires (then checks / edits them): never applied by itself. */
  const applyLastInspectionTires = () => {
    setWizardPayload((prev) => {
      const current = normalizeChecklistTiresForEditor(prev?.tires);
      const positions = CHECKLIST_TIRE_POSITIONS.map((position) => {
        const suggested = lastInspectionTires.find((item) => item.posicao === position);
        return {
          posicao: position,
          marca: suggested?.marca || "",
          estado: TIRE_INTEGRITY_OPTIONS.includes(`${suggested?.estado || ""}`) ? suggested?.estado : "",
        };
      });
      return { ...prev, tires: { source: "CHECKLIST", positions, observacoes: current.observacoes } };
    });
  };

  /** Part 1: cleaning (required) and the five tire positions (optional), with the scales of the inspection form. */
  const renderVehicleConditions = () => (
    <>
      <FormGroup title="Condições do veículo" columns>
        <SelectField
          label="Limpeza interna *"
          value={wizardPayload.limpezaInterna || ""}
          options={CLEANING_OPTIONS}
          onChange={(v) => setPayload("limpezaInterna", v)}
        />
        <SelectField
          label="Limpeza externa *"
          value={wizardPayload.limpezaExterna || ""}
          options={CLEANING_OPTIONS}
          onChange={(v) => setPayload("limpezaExterna", v)}
        />
      </FormGroup>
      <FormGroup title="Pneus (opcional por posição)">
        {lastInspectionTires.length > 0 && (
          <IonButton fill="outline" size="small" className="app-outline-btn" onClick={applyLastInspectionTires}>
            Usar pneus da última inspeção (conferir antes de finalizar)
          </IonButton>
        )}
        {checklistTirePositions.map((position) => (
          <div key={position.posicao} className="documents-checklist-tire" data-testid="checklist-tire-position">
            <p className="documents-checklist-subtitle">{position.posicao}</p>
            <div className="documents-form-group__fields documents-form-group__fields--grid">
              {/* The brand picker of the inspection form: its brands and the custom ones saved there (same storage). */}
              <FormSelectFilterAdd
                label={`${position.posicao} - Marca`}
                initialValue={position.marca || ""}
                options={TIRE_BRANDS}
                storageToken={TIRE_BRANDS_KEY}
                formCallBack={(value: string) => setChecklistTire(position.posicao, { marca: value || "" })}
              />
              <SelectField
                label={`${position.posicao} - Integridade`}
                value={position.estado || ""}
                options={[{ value: "", label: "Não verificado" }, ...TIRE_INTEGRITY_OPTIONS.map((value) => ({ value, label: value }))]}
                onChange={(v) => setChecklistTire(position.posicao, { estado: v || "" })}
              />
            </div>
          </div>
        ))}
      </FormGroup>
    </>
  );

  const confirmedDamages: ChecklistExistingDamage[] = Array.isArray(wizardPayload.existingDamages) ? wizardPayload.existingDamages : [];

  /** Part 2: damages already registered on the car, confirmed in this checklist (linked, never registered again). */
  const renderExistingDamages = () => (
    <FormGroup title="Danos já registrados no veículo">
      {carDamages.length === 0 ? (
        <p className="documents-hint" data-testid="checklist-no-damages">
          Nenhum dano ativo registrado para este veículo. Danos novos vão em Avarias e nas fotos.
        </p>
      ) : (
        carDamages.map((damage) => {
          const checked = confirmedDamages.some((item) => item.id === damage.id);
          return (
            <IonItem key={damage.id} lines="none" data-testid="checklist-existing-damage">
              <IonCheckbox
                slot="start"
                checked={checked}
                aria-label={`Confirmar dano ${damage.part || ""}`}
                onIonChange={(event) => {
                  const keep = confirmedDamages.filter((item) => item.id !== damage.id);
                  setPayload(
                    "existingDamages",
                    event.detail.checked ? [...keep, { id: damage.id, part: damage.part || "", date: damage.date || "" }] : keep
                  );
                }}
              />
              <IonLabel>
                {damage.part || "Dano"} {damage.date ? `(${formatChecklistDate(damage.date)})` : ""}
              </IonLabel>
            </IonItem>
          );
        })
      )}
    </FormGroup>
  );

  /** Part 2: what will be recorded, before finalizing. */
  const renderChecklistReview = () => {
    const tiresChecked = checklistTirePositions.filter((position) => position.marca || position.estado).length;
    return (
      <FormGroup title="Revisão">
        <p className="documents-hint" data-testid="checklist-review">
          {checklistTypeLabel(checklistType) || "-"} · Vistoria {formatChecklistDate(wizardPayload.dataVistoria, wizardPayload.horaVistoria) || "-"} ·
          KM {`${wizardPayload.km ?? "-"}`} · Combustível {fuelLevelLabel(wizardPayload.combustivel) || "-"} · Limpeza interna{" "}
          {cleaningLabel(wizardPayload.limpezaInterna) || "-"} / externa {cleaningLabel(wizardPayload.limpezaExterna) || "-"} · Pneus
          verificados: {tiresChecked} de 5 · Danos confirmados: {confirmedDamages.length}
        </p>
      </FormGroup>
    );
  };

  /** Finalized checklist: what the operation did; from here on the PDF is only the document. */
  const renderChecklistFinal = () => {
    const type = savedDocument?.checklistType;
    const payload = savedDocument?.payload || {};
    return (
      <div className="documents-checklist-final" data-testid="checklist-final">
        <p>
          <strong>{type ? `Checklist de ${checklistTypeLabel(type)} finalizado.` : "Checklist finalizado."}</strong>
        </p>
        {type && (
          <p>
            {type === "ENTREGA"
              ? "O motorista está vinculado ao veículo e a vistoria foi registrada como inspeção do veículo."
              : "A devolução foi registrada no vínculo e a vistoria foi registrada como inspeção do veículo."}
          </p>
        )}
        {checklistFeedback && <p data-testid="checklist-final-feedback">{checklistFeedback}</p>}
        {type && (
          <p>
            Vistoria: {formatChecklistDate(payload.dataVistoria, payload.horaVistoria) || "-"} · KM: {`${payload.km ?? "-"}`} ·
            Combustível: {fuelLevelLabel(payload.combustivel) || "-"}
            {payload.limpezaInterna ? ` · Limpeza: ${cleaningLabel(payload.limpezaInterna)} / ${cleaningLabel(payload.limpezaExterna)}` : ""}
          </p>
        )}
        {checklistPdfFailed && (
          <p className="documents-warning" role="alert" data-testid="checklist-pdf-failed">
            O PDF não foi gerado. Use Gerar PDF para tentar novamente: o checklist não é finalizado de novo.
          </p>
        )}
        <p className="documents-hint">Gerar o PDF não altera o vínculo, a inspeção nem o hodômetro.</p>
      </div>
    );
  };

  const renderTypeFields = () => {
    if (!wizardType) {
      return null;
    }

    if (wizardType === "MULTA") {
      return (
        <>
          <FormGroup title="Infração" columns>
            <TextField label="Data/Hora" value={wizardPayload.dataHora} onChange={(v) => setPayload("dataHora", v)} />
            <TextField label="Local" value={wizardPayload.local} onChange={(v) => setPayload("local", v)} />
            <TextField label="AIT" value={wizardPayload.ait} onChange={(v) => setPayload("ait", v)} />
            <TextField label="Órgão" value={wizardPayload.orgao} onChange={(v) => setPayload("orgao", v)} />
            <TextField
              label="Enquadramento"
              value={wizardPayload.enquadramento}
              onChange={(v) => setPayload("enquadramento", v)}
            />
          </FormGroup>
          <FormGroup title="Valor e pagamento" columns>
            <DecimalField label="Valor" value={wizardPayload.valor} onChange={(v) => setPayload("valor", v)} />
            <TextField label="Vencimento" value={wizardPayload.vencimento} onChange={(v) => setPayload("vencimento", v)} />
            <SelectField
              label="Responsável pelo pagamento"
              value={wizardPayload.responsavelPagamento || ""}
              options={[
                { value: "MOTORISTA", label: "Motorista" },
                { value: "EMPRESA", label: "Empresa" },
              ]}
              onChange={(v) => setPayload("responsavelPagamento", v)}
            />
          </FormGroup>
          <FormGroup title="Observações">
            <AreaField
              label="Observações"
              value={wizardPayload.observacoes}
              onChange={(v) => setPayload("observacoes", v)}
            />
          </FormGroup>
        </>
      );
    }

    if (wizardType === "MANUTENCAO_COMPARTILHADA") {
      return (
        <>
          <FormGroup title="Serviço" columns>
            <TextField label="Data" value={wizardPayload.data} onChange={(v) => setPayload("data", v)} />
            <TextField label="Descrição" value={wizardPayload.descricao} onChange={(v) => setPayload("descricao", v)} />
            <TextField label="Oficina" value={wizardPayload.oficina} onChange={(v) => setPayload("oficina", v)} />
          </FormGroup>
          <FormGroup title="Valores e divisão" columns>
            <DecimalField
              label="Valor total"
              value={wizardPayload.valorTotal}
              onChange={(v) => setPayload("valorTotal", v)}
            />
            <SelectField
              label="Forma de divisão"
              value={wizardPayload.formaDivisao || ""}
              options={[
                { value: "PERCENTUAL", label: "Percentual" },
                { value: "VALOR", label: "Valor" },
              ]}
              onChange={(v) => setPayload("formaDivisao", v)}
            />
            <DecimalField
              label="Parte motorista (valor)"
              value={wizardPayload.parteMotoristaValor}
              onChange={(v) => setPayload("parteMotoristaValor", v)}
            />
          </FormGroup>
          <FormGroup title="Observações">
            <AreaField
              label="Observações"
              value={wizardPayload.observacoes}
              onChange={(v) => setPayload("observacoes", v)}
            />
          </FormGroup>
        </>
      );
    }

    if (wizardType === "RECIBO_ALUGUEL") {
      return (
        <>
          <FormGroup title="Período" columns>
            <TextField label="Período início" value={wizardPayload.periodoInicio} onChange={(v) => setPayload("periodoInicio", v)} />
            <TextField label="Período fim" value={wizardPayload.periodoFim} onChange={(v) => setPayload("periodoFim", v)} />
          </FormGroup>
          <FormGroup title="Valores" columns>
            <DecimalField
              label="Valor aluguel"
              value={wizardPayload.valorAluguel}
              onChange={(v) => setPayload("valorAluguel", v)}
            />
            <DecimalField label="Descontos" value={wizardPayload.descontos} onChange={(v) => setPayload("descontos", v)} />
            <DecimalField
              label="Acréscimos"
              value={wizardPayload.acrescimos}
              onChange={(v) => setPayload("acrescimos", v)}
            />
            <DecimalField label="Valor final" value={wizardPayload.valorFinal} onChange={(v) => setPayload("valorFinal", v)} />
          </FormGroup>
          <FormGroup title="Pagamento" columns>
            <TextField label="Forma de pagamento" value={wizardPayload.formaPagamento} onChange={(v) => setPayload("formaPagamento", v)} />
            <TextField label="Data do pagamento" value={wizardPayload.dataPagamento} onChange={(v) => setPayload("dataPagamento", v)} />
          </FormGroup>
          <FormGroup title="Observações">
            <AreaField
              label="Observações"
              value={wizardPayload.observacoes}
              onChange={(v) => setPayload("observacoes", v)}
            />
          </FormGroup>
        </>
      );
    }

    if (wizardType === "CONFISSAO_DIVIDA") {
      const debtItemTypeOptions = debtItemTypes.map((item) => ({
        value: `${item.id}`,
        label: item.name,
      }));

      return (
        <>
          {isDebtItemTypesLoading && <p className="documents-warning">Carregando tipos de dívida...</p>}
          {isConfissaoPendenciesLoading && (
            <p className="documents-warning">Carregando pendências em aberto do motorista...</p>
          )}
          {!!confissaoImportFeedback && <p className="documents-warning">{confissaoImportFeedback}</p>}
          {!isDebtItemTypesLoading && !debtItemTypeOptions.length && (
            <p className="documents-warning">Nenhum tipo ativo encontrado. Cadastre em Tipos de Dívida.</p>
          )}
          <FormGroup title="Origem da dívida">
            <AreaField
              label="Origem da dívida (descrição geral)"
              value={wizardPayload.origemDaDivida}
              onChange={(v) => setPayload("origemDaDivida", v)}
            />
          </FormGroup>
          <FormGroup title="Itens da dívida">
          <div className="documents-debt-items">
            {confissaoItems.map((item, index) => (
              <div key={`confissao-item-${index}`} className="app-form-grid documents-debt-item-card">
                <SelectField
                  label={`Tipo do item #${index + 1}`}
                  value={item.typeId ? `${item.typeId}` : ""}
                  options={[{ value: "", label: "Selecione..." }, ...debtItemTypeOptions]}
                  onChange={(value) => {
                    const selectedType = findDebtItemTypeById(value);
                    updateConfissaoItem(index, {
                      typeId: selectedType?.id ?? null,
                      typeNameSnapshot: selectedType?.name || item.typeNameSnapshot || "Outros",
                    });
                  }}
                />
                {!item.typeId && item.typeNameSnapshot ? (
                  <p className="documents-warning">Item legado: {item.typeNameSnapshot}</p>
                ) : null}
                <TextField
                  label="Descrição do item (opcional)"
                  value={item.descricaoItem}
                  onChange={(value) => updateConfissaoItem(index, { descricaoItem: value })}
                />
                <DecimalField
                  label="Valor do item"
                  value={item.valorItem}
                  onChange={(value) => updateConfissaoItem(index, { valorItem: value })}
                />
                <div className="documents-debt-item-actions">
                  <IonButton
                    size="small"
                    className="app-danger-btn"
                    fill="outline"
                    onClick={() => removeConfissaoItem(index)}
                    disabled={confissaoItems.length <= 1}
                  >
                    <IonIcon icon={trashOutline} slot="start" />
                    Remover item
                  </IonButton>
                </div>
              </div>
            ))}
            <div className="documents-inline-actions">
              <IonButton fill="outline" className="app-outline-btn" onClick={addConfissaoItem} disabled={isDebtItemTypesLoading}>
                <IonIcon icon={add} slot="start" />
                Adicionar item
              </IonButton>
              <IonButton
                fill="outline"
                className="app-outline-btn"
                onClick={() => void createDebtItemTypeInline()}
                disabled={isDebtItemTypesLoading || isActionLoading}
              >
                <IonIcon icon={add} slot="start" />
                Criar tipo
              </IonButton>
              <IonButton
                fill="outline"
                className="app-outline-btn"
                onClick={() => void importDriverPendenciesToConfissao(wizardDriver, true)}
                disabled={!wizardDriver?.id || isDebtItemTypesLoading || isConfissaoPendenciesLoading}
              >
                <IonIcon icon={refreshOutline} slot="start" />
                Atualizar pendências
              </IonButton>
            </div>
          </div>
          </FormGroup>
          <FormGroup title="Pagamento" columns>
            <DecimalField
              label="Valor total (soma automática)"
              value={wizardPayload.valorTotal}
              onChange={() => undefined}
            />
            <SelectField
              label="Forma de pagamento"
              value={wizardPayload.formaPagamento || ""}
              options={[
                { value: "A_VISTA", label: "À vista" },
                { value: "PARCELADO", label: "Parcelado" },
              ]}
              onChange={(v) => setPayload("formaPagamento", v)}
            />
            <IntegerField label="Parcelas (qtd)" value={wizardPayload.parcelasQtd} onChange={(v) => setPayload("parcelasQtd", v)} />
            <DecimalField label="Valor parcela" value={wizardPayload.valorParcela} onChange={(v) => setPayload("valorParcela", v)} />
            <TextField label="Vencimento inicial" value={wizardPayload.vencimentoInicial} onChange={(v) => setPayload("vencimentoInicial", v)} />
          </FormGroup>
          <FormGroup title="Testemunhas" columns>
            <TextField label="Testemunha 1 nome" value={wizardPayload.testemunha1Nome} onChange={(v) => setPayload("testemunha1Nome", v)} />
            <TextField label="Testemunha 1 CPF" value={wizardPayload.testemunha1Cpf} onChange={(v) => setPayload("testemunha1Cpf", v)} />
            <TextField label="Testemunha 2 nome" value={wizardPayload.testemunha2Nome} onChange={(v) => setPayload("testemunha2Nome", v)} />
            <TextField label="Testemunha 2 CPF" value={wizardPayload.testemunha2Cpf} onChange={(v) => setPayload("testemunha2Cpf", v)} />
          </FormGroup>
          <FormGroup title="Observações">
            <AreaField
              label="Observações"
              value={wizardPayload.observacoes}
              onChange={(v) => setPayload("observacoes", v)}
            />
          </FormGroup>
        </>
      );
    }

    return (
      <>
        {isLegacyChecklist ? (
          <>
            <p className="documents-warning" data-testid="checklist-legacy-draft">
              Rascunho criado antes do checklist estruturado. Ele continua como era: ao gerar o PDF é finalizado sem criar
              vínculo nem inspeção. Para registrar a entrega/devolução no vínculo e na inspeção do veículo, converta-o.
            </p>
            <IonButton fill="outline" className="app-outline-btn" onClick={() => setChecklistLegacyConversion(true)}>
              Converter em checklist estruturado
            </IonButton>
            <FormGroup title="Entrega ou devolução" columns>
              <SelectField
                label="Tipo"
                value={wizardPayload.tipo || ""}
                options={CHECKLIST_TYPE_OPTIONS}
                onChange={(v) => setPayload("tipo", v)}
              />
              <TextField label="Data/Hora" value={wizardPayload.dataHora} onChange={(v) => setPayload("dataHora", v)} />
              <TextField label="KM" value={wizardPayload.km} onChange={(v) => setPayload("km", v)} />
              <TextField
                label="Combustível"
                value={wizardPayload.combustivel}
                onChange={(v) => setPayload("combustivel", v)}
              />
            </FormGroup>
          </>
        ) : (
          <>
        {renderChecklistParts()}
        {checklistPart === 1 && (
          <>
        <FormGroup title="Entrega ou devolução" columns>
          {savedDocument?.checklistType ? (
            <p className="documents-hint" data-testid="checklist-type-fixed">
              <strong>{checklistTypeLabel(savedDocument.checklistType)}</strong> — o tipo não muda depois de salvo; para a
              outra operação, crie um novo checklist.
            </p>
          ) : (
            <SelectField
              label="Tipo *"
              value={wizardPayload.tipo || ""}
              options={CHECKLIST_TYPE_OPTIONS}
              onChange={(v) => {
                setPayload("tipo", v);
                setChecklistDriverCarId(null);
              }}
            />
          )}
          <NativeField
            label="Data da vistoria *"
            type="date"
            field="dataVistoria"
            value={wizardPayload.dataVistoria}
            onChange={(v) => setPayload("dataVistoria", v)}
          />
          <NativeField
            label="Hora (opcional)"
            type="time"
            field="horaVistoria"
            value={wizardPayload.horaVistoria}
            onChange={(v) => setPayload("horaVistoria", v)}
          />
          <NativeField
            label="KM *"
            type="text"
            inputMode="decimal"
            field="km"
            value={wizardPayload.km}
            onChange={(v) => setPayload("km", `${v}`.replace(/[^\d.,]/g, ""))}
          />
          <SelectField
            label="Combustível *"
            value={wizardPayload.combustivel || ""}
            options={FUEL_LEVEL_OPTIONS}
            onChange={(v) => setPayload("combustivel", v)}
          />
        </FormGroup>
        {!!`${wizardPayload.dataHora || ""}`.trim() && !wizardPayload.dataVistoria && (
          <p className="documents-hint" data-testid="checklist-legacy-datetime">
            Data/Hora informada antes: {wizardPayload.dataHora}. Informe a data da vistoria no campo acima.
          </p>
        )}
        {renderChecklistContract()}
        {renderVehicleConditions()}
          </>
        )}
          </>
        )}
        {(isLegacyChecklist || checklistPart === 2) && (
          <>
        <FormGroup title="Checklist do veículo">
          <div className="documents-checklist-builder">
            {checklistItems.map((item) => (
              <div key={item.key} className="documents-checklist-row">
                <IonItem>
                  <IonCheckbox
                    slot="start"
                    checked={Boolean(item.ok)}
                    onIonChange={(event) =>
                      toggleChecklistItem(item.key, Boolean(event.detail.checked))
                    }
                  />
                  <IonLabel>{item.label}</IonLabel>
                  {!isChecklistDefaultKey(item.key) && (
                    <IonButton
                      slot="end"
                      fill="clear"
                      size="small"
                      className="app-danger-btn"
                      onClick={() => removeCustomChecklistItem(item.key)}
                    >
                      Remover
                    </IonButton>
                  )}
                </IonItem>

                {!item.ok && (
                  <IonItem className="documents-checklist-note">
                    <IonLabel position="stacked">Observação (opcional)</IonLabel>
                    <IonInput
                      value={item.note || ""}
                      onIonChange={(event) =>
                        updateChecklistItemNote(item.key, `${event.detail.value || ""}`)
                      }
                    />
                  </IonItem>
                )}
              </div>
            ))}

            <IonItem>
              <IonLabel position="stacked">Adicionar item personalizado</IonLabel>
              <IonInput
                value={newChecklistLabel}
                onIonChange={(event) => setNewChecklistLabel(`${event.detail.value || ""}`)}
              />
            </IonItem>
            <IonItem className="documents-checklist-add" lines="none">
              <IonButton fill="outline" size="small" onClick={addCustomChecklistItem}>
                <IonIcon icon={add} slot="start" />
                Adicionar item
              </IonButton>
            </IonItem>
          </div>
        </FormGroup>
        <FormGroup title="Contatos de emergência">
          {checklistEmergencyContacts.map((contact, index) => (
            <div key={`checklist-contact-${index}`} className="app-form-grid documents-checklist-contact-card">
              <IonItem>
                <IonLabel position="stacked">Contato {index + 1} - Nome *</IonLabel>
                <IonInput
                  value={contact.nome || ""}
                  onIonChange={(event) =>
                    updateChecklistEmergencyContact(index, {
                      nome: `${event.detail.value || ""}`,
                    })
                  }
                />
              </IonItem>
              <IonItem>
                <IonLabel position="stacked">Contato {index + 1} - Telefone *</IonLabel>
                <IonInput
                  type="tel"
                  inputmode="numeric"
                  value={maskPhone(contact.telefone || "")}
                  onIonChange={(event) =>
                    updateChecklistEmergencyContact(index, {
                      telefone: sanitizeDigits(`${event.detail.value || ""}`).slice(0, 11),
                    })
                  }
                />
              </IonItem>
              {checklistEmergencyContacts.length > MIN_EMERGENCY_CONTACTS && (
                <div className="documents-checklist-contact-actions">
                  <IonButton
                    size="small"
                    className="app-danger-btn"
                    fill="outline"
                    onClick={() => removeChecklistEmergencyContact(index)}
                  >
                    <IonIcon icon={trashOutline} slot="start" />
                    Remover contato
                  </IonButton>
                </div>
              )}
            </div>
          ))}
          <div className="documents-inline-actions">
            <IonButton fill="outline" className="app-outline-btn" onClick={addChecklistEmergencyContact}>
              <IonIcon icon={add} slot="start" />
              Adicionar contato
            </IonButton>
          </div>
        </FormGroup>

        <FormGroup title="Fotos do checklist">
          <IonItem lines="none">
            <IonLabel position="stacked">Adicionar fotos</IonLabel>
            <input
              className="documents-file-input"
              type="file"
              multiple
              accept="image/*"
              onChange={(event) => {
                const files = Array.from(event.target.files || []);
                setChecklistNewPhotos((current) => appendUniqueFiles(current, files));
                logDocumentsDebug("checklist photos selected", {
                  selectedCount: files.length,
                  names: files.map((file) => file.name),
                });
              }}
            />
          </IonItem>

          {checklistNewPhotos.length > 0 && (
            <div className="documents-checklist-files">
              <p className="documents-checklist-subtitle">Fotos novas (ainda não enviadas)</p>
              {checklistNewPhotoPreviews.map(({ file, previewUrl }, index) => (
                <div key={`checklist-new-photo-${index}`} className="documents-checklist-file-row">
                  <div className="documents-checklist-file-main">
                    <img
                      className="documents-checklist-file-thumb"
                      src={previewUrl}
                      alt={`Pré-visualização ${file.name}`}
                    />
                    <div className="documents-checklist-file-meta">
                      <span className="documents-checklist-file-name">{file.name}</span>
                    </div>
                  </div>
                  <IonButton
                    size="small"
                    className="app-danger-btn"
                    fill="clear"
                    onClick={() => removeChecklistNewPhoto(index)}
                  >
                    Remover
                  </IonButton>
                </div>
              ))}
            </div>
          )}

          {checklistPhotoRefs.length > 0 && (
            <div className="documents-checklist-files">
              <p className="documents-checklist-subtitle">Fotos já salvas</p>
              {checklistSavedPhotoItems.map(({ ref, candidates, previewUrl }, index) => (
                <div key={`checklist-photo-ref-${index}`} className="documents-checklist-file-row">
                  <div className="documents-checklist-file-main">
                    {previewUrl ? (
                      <img
                        className="documents-checklist-file-thumb"
                        src={previewUrl}
                        alt={`Foto do checklist ${index + 1}`}
                        onError={() => handleChecklistSavedPhotoLoadError(ref, candidates)}
                      />
                    ) : (
                      <div className="documents-checklist-file-thumb documents-checklist-file-thumb-placeholder">
                        Sem pré-visualização
                      </div>
                    )}
                    <div className="documents-checklist-file-meta">
                      <span className="documents-checklist-file-name">{ref}</span>
                      {!previewUrl && (
                        <span className="documents-checklist-file-hint">
                          Não foi possível carregar a imagem
                        </span>
                      )}
                    </div>
                  </div>
                  <IonButton
                    size="small"
                    className="app-danger-btn"
                    fill="clear"
                    onClick={() => removeChecklistPhotoRef(ref)}
                  >
                    Remover
                  </IonButton>
                </div>
              ))}
            </div>
          )}
        </FormGroup>

        {isLegacyChecklist && (
        <FormGroup title="Vistoria dos pneus">
          {checklistTires.source === "LAST_INSPECTION" && (
            <p className="documents-warning">Dados sugeridos a partir da última inspeção do carro.</p>
          )}
          <TextField
            label="Marca dos pneus"
            value={checklistTires.marca || ""}
            onChange={(value) => patchChecklistTires({ marca: value })}
          />
          <SelectField
            label="Estado dos pneus"
            value={checklistTires.estado || ""}
            options={[
              { value: "", label: "Selecione..." },
              { value: "BOM", label: "Bom" },
              { value: "MEIA_VIDA", label: "Meia vida" },
              { value: "RUIM", label: "Ruim" },
            ]}
            onChange={(value) =>
              patchChecklistTires({ estado: (value as ChecklistTireCondition) || "" })
            }
          />
          <AreaField
            label="Observações dos pneus"
            value={checklistTires.observacoes || ""}
            onChange={(value) => patchChecklistTires({ observacoes: value })}
          />
          {Array.isArray(checklistTires.positions) && checklistTires.positions.length > 0 && (
            <div className="documents-checklist-tire-positions">
              <p className="documents-checklist-subtitle">Resumo da última inspeção</p>
              {checklistTires.positions.map((position, index) => (
                <p key={`checklist-tire-position-${index}`}>
                  {position.posicao}: {position.marca || "-"} ({position.estado || "-"})
                </p>
              ))}
            </div>
          )}
        </FormGroup>
        )}
        {!isLegacyChecklist && renderExistingDamages()}
        <FormGroup title="Avarias">
          <AreaField
            label={isLegacyChecklist ? "Avarias" : "Avarias novas e observações"}
            value={wizardPayload.avariasTexto}
            onChange={(v) => setPayload("avariasTexto", v)}
          />
        </FormGroup>
        {!isLegacyChecklist && renderChecklistContract()}
        {!isLegacyChecklist && renderChecklistReview()}
          </>
        )}
      </>
    );
  };

  const showAttachmentInput = supportsAttachments(wizardType);
  const canGoNextPage = documents.length >= PAGE_SIZE;

  return (
    <IonPage id="documents-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonMenuButton menu="main-menu" autoHide={false} />
          </IonButtons>
          <IonTitle>Documentos</IonTitle>
          <IonButtons slot="end">
            <IonButton className="app-primary-btn" onClick={openWizard}>
              <IonIcon icon={add} slot="start" />
              Novo Documento
            </IonButton>
          </IonButtons>
        </IonToolbar>
        {(isLoading || isActionLoading) && <IonProgressBar type="indeterminate" />}
      </IonHeader>

      <IonContent fullscreen>
        <div className="app-shell app-shell--compact documents-shell">
          <section className="app-section">
            <div className="documents-section-head">
              <h2 className="app-section-title">Documentos</h2>
              <p className="app-section-subtitle">
                Filtre, acompanhe rascunhos e abra PDFs gerados.
              </p>
            </div>

            <FrottoCard className="documents-filter-card app-panel-card--soft">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon">
                  <IonIcon icon={filterOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">Filtros</IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Motorista, carro, tipo e status
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <div className="app-form-grid documents-filter-grid">
                  <Autocomplete
                    label="Motorista (CPF ou nome)"
                    value={filterDriverQuery}
                    options={filterDriverOptions}
                    getLabel={(item) => `${item.name}${item.cpf ? ` (${item.cpf})` : ""}`}
                    onChange={(value) => {
                      setFilterDriverQuery(value);
                      setFilterDriver(null);
                    }}
                    onSelect={(driver) => {
                      setFilterDriver(driver);
                      setFilterDriverQuery(driver.name || "");
                      setFilterDriverOptions([]);
                    }}
                    onClear={() => {
                      setFilterDriver(null);
                      setFilterDriverQuery("");
                    }}
                  />
                  <Autocomplete
                    label="Carro (placa)"
                    value={filterCarQuery}
                    options={filterCarOptions}
                    getLabel={(item) => `${item.plate || ""}${item.model ? ` - ${item.model}` : ""}`}
                    onChange={(value) => {
                      setFilterCarQuery(value);
                      setFilterCar(null);
                    }}
                    onSelect={(car) => {
                      setFilterCar(car);
                      setFilterCarQuery(car.plate || "");
                      setFilterCarOptions([]);
                    }}
                    onClear={() => {
                      setFilterCar(null);
                      setFilterCarQuery("");
                    }}
                  />
                  <SelectField
                    label="Tipo"
                    value={filterType}
                    options={[{ value: "", label: "Todos" }, ...DOCUMENT_TYPES]}
                    onChange={(value) => setFilterType((value as DocumentType) || "")}
                  />
                  <SelectField
                    label="Status"
                    value={filterStatus}
                    options={[{ value: "", label: "Todos" }, ...DOCUMENT_STATUSES]}
                    onChange={(value) => setFilterStatus((value as DocumentStatus) || "")}
                  />
                </div>
                <div className="app-actions-row app-actions-row--between documents-filter-actions">
                  <IonButton
                    className="app-primary-btn"
                    onClick={() => {
                      setListPage(0);
                      void loadDocuments(0);
                    }}
                  >
                    Buscar
                  </IonButton>
                  <IonButton fill="clear" className="app-outline-btn" onClick={resetFilters}>
                    <IonIcon icon={refreshOutline} slot="start" />
                    Limpar filtros
                  </IonButton>
                </div>
              </IonCardContent>
            </FrottoCard>

            {!isLoading && !documents.length ? (
              hasActiveFilters ? (
                <ItemNotFound
                  title="Nenhum resultado encontrado"
                  description="Ajuste os filtros para ver outros documentos."
                />
              ) : (
                <ItemNotFound
                  title="Nenhum documento cadastrado"
                  description="Crie o primeiro documento para começar."
                  actionLabel="Novo Documento"
                  onAction={openWizard}
                />
              )
            ) : (
              <div className="documents-result-list">
                {documents.map((item) => (
                  <FrottoCard key={`doc-${item.id}`} className="documents-result-card">
                    <IonCardHeader className="app-panel-header documents-result-header">
                      <div className={`app-soft-icon ${documentToneClass(item.status)}`.trim()}>
                        <IonIcon icon={DOCUMENT_TYPE_ICON[item.type] || documentTextOutline} />
                      </div>
                      <div className="app-panel-header__content">
                        <IonCardTitle className="app-panel-title">
                          {resolveTypeLabel(item.type)}
                        </IonCardTitle>
                        <IonCardSubtitle className="app-panel-subtitle">
                          {formatDate(item.createdAt)}
                        </IonCardSubtitle>
                      </div>
                      <FrottoBadge variant={DOCUMENT_STATUS_VARIANT[item.status] || "neutral"}>
                        {resolveStatusLabel(item.status)}
                      </FrottoBadge>
                    </IonCardHeader>
                    <IonCardContent>
                      <div className="documents-result-meta">
                        <p className="documents-meta-line">
                          <span>Motorista</span>
                          <strong>{item.driverName || "-"}</strong>
                        </p>
                        <p className="documents-meta-line">
                          <span>Carro</span>
                          <strong>{item.carPlate || "-"}</strong>
                        </p>
                      </div>
                      <div className="app-actions-row documents-result-actions">
                        {item.status === "DRAFT" ? (
                          <IonButton
                            size="small"
                            fill="clear"
                            className="app-outline-btn"
                            onClick={() => void openEdit(item.id)}
                          >
                            Editar
                          </IonButton>
                        ) : (
                          <IonButton
                            size="small"
                            fill="clear"
                            className="app-outline-btn"
                            onClick={() => void openView(item.id)}
                          >
                            Detalhes
                          </IonButton>
                        )}
                        {(item.status !== "DRAFT" || !!item.pdfUrl) && (
                          <IonButton
                            size="small"
                            className="app-primary-btn"
                            onClick={() => void openPdf(item.id)}
                          >
                            Abrir PDF
                          </IonButton>
                        )}
                        {!(item.checklistType && item.status !== "DRAFT") && (
                          <IonButton
                            size="small"
                            fill="clear"
                            className="app-danger-btn"
                            onClick={() => void deleteDocument(item.id)}
                          >
                            Excluir
                          </IonButton>
                        )}
                      </div>
                    </IonCardContent>
                  </FrottoCard>
                ))}
              </div>
            )}

            <FrottoCard className="documents-pagination-card app-panel-card--soft">
              <IonCardContent>
                <div className="app-actions-row app-actions-row--between documents-pagination-controls">
                  <IonButton
                    fill="clear"
                    className="app-outline-btn"
                    disabled={listPage === 0 || isLoading}
                    onClick={() => setListPage((current) => Math.max(0, current - 1))}
                  >
                    Página anterior
                  </IonButton>
                  <span>Página {listPage + 1}</span>
                  <IonButton
                    fill="clear"
                    className="app-outline-btn"
                    disabled={!canGoNextPage || isLoading}
                    onClick={() => setListPage((current) => current + 1)}
                  >
                    Próxima página
                  </IonButton>
                </div>
              </IonCardContent>
            </FrottoCard>
          </section>
        </div>
      </IonContent>

      <IonModal
        className="documents-modal"
        isOpen={isViewModalOpen}
        onDidDismiss={() => {
          setIsViewModalOpen(false);
          setViewDocument(null);
        }}
      >
        <IonHeader className="ion-no-border">
          <IonToolbar className="app-toolbar-clean">
            <IonTitle>Documento #{viewDocument?.id}</IonTitle>
            <IonButtons slot="end">
              <IonButton fill="clear" className="app-cancel-btn" aria-label="Fechar" onClick={() => setIsViewModalOpen(false)}>
                <IonIcon icon={closeCircleOutline} slot="icon-only" />
              </IonButton>
            </IonButtons>
          </IonToolbar>
        </IonHeader>
        <IonContent>
          <div className="app-shell app-shell--compact">
            <FrottoCard>
              <IonCardContent>
                <p><strong>Tipo:</strong> {resolveTypeLabel(viewDocument?.type || "MULTA")}</p>
                <p><strong>Status:</strong> {resolveStatusLabel(viewDocument?.status || "DRAFT")}</p>
                <p><strong>Motorista:</strong> {viewDocument?.driverName || "-"}</p>
                <p><strong>Carro:</strong> {viewDocument?.carPlate || "-"}</p>
                <p><strong>Criado:</strong> {formatDate(viewDocument?.createdAt)}</p>
                <p><strong>Atualizado:</strong> {formatDate(viewDocument?.updatedAt)}</p>
                <p><strong>Anexos:</strong> {viewDocument?.attachments?.length || 0}</p>
              </IonCardContent>
            </FrottoCard>
          </div>
        </IonContent>
        <IonFooter className="app-footer-bar">
          <IonToolbar>
            <div className="app-actions-row app-actions-row--end documents-modal-actions">
              {!(viewDocument?.checklistType && viewDocument.status !== "DRAFT") && (
                <IonButton fill="clear" className="app-danger-btn" onClick={() => void deleteDocument(viewDocument?.id)}>
                  <IonIcon icon={trashOutline} slot="start" />
                  Excluir
                </IonButton>
              )}
              {(viewDocument?.status !== "DRAFT" || !!viewDocument?.pdfUrl) && (
                <IonButton className="app-primary-btn" onClick={() => void openPdf(viewDocument?.id)}>
                  Abrir PDF
                </IonButton>
              )}
            </div>
          </IonToolbar>
        </IonFooter>
      </IonModal>

      <IonModal
        className="documents-modal documents-modal--wizard"
        isOpen={isWizardOpen}
        // An open checklist closes only by its buttons (Fechar asks before discarding changes), never by the backdrop / Esc.
        backdropDismiss={!(isChecklistWizard && !isChecklistFinal)}
        onDidDismiss={closeWizard}
      >
        <IonHeader className="ion-no-border">
          <IonToolbar className="app-toolbar-clean">
            <IonTitle>{savedDocument?.id ? "Editar Documento" : "Novo Documento"}</IonTitle>
            <IonButtons slot="end">
              <IonButton fill="clear" className="app-cancel-btn" aria-label="Fechar" onClick={closeWizard}>
                <IonIcon icon={closeCircleOutline} slot="icon-only" />
              </IonButton>
            </IonButtons>
          </IonToolbar>
          <IonToolbar className="documents-stepper-toolbar">
            <WizardStepper current={wizardStep} />
          </IonToolbar>
        </IonHeader>
        <IonContent>
          <div className="app-shell app-shell--compact documents-shell">
            {wizardStep === 1 && (
              <FrottoCard>
                <IonCardContent className="documents-step-body">
                  <WizardStepHead step={1} />
                  <div className="documents-form-group__fields documents-form-group__fields--grid">
                  <Autocomplete
                    label="Motorista"
                    value={wizardDriverQuery}
                    options={wizardDriverOptions}
                    getLabel={(item) => `${item.name}${item.cpf ? ` (${item.cpf})` : ""}`}
                    onChange={(value) => {
                      setWizardDriverQuery(value);
                      setWizardDriver(null);
                    }}
                    onSelect={(driver) => {
                      setWizardDriver(driver);
                      setWizardDriverQuery(driver.name || "");
                      setWizardDriverOptions([]);
                      setConfissaoImportFeedback("");
                      syncMetaPayload({
                        driverName: driver.name || "",
                        driverCpf: driver.cpf || "",
                      });
                    }}
                    onClear={() => {
                      confissaoImportRequestIdRef.current += 1;
                      setWizardDriver(null);
                      setWizardDriverQuery("");
                      setConfissaoAutoImportedDriverId(null);
                      setConfissaoImportFeedback("");
                      syncMetaPayload({
                        driverName: "",
                        driverCpf: "",
                      });
                    }}
                  />
                  <Autocomplete
                    label="Carro (opcional para confissão)"
                    value={wizardCarQuery}
                    options={wizardCarOptions}
                    getLabel={(item) => `${item.plate || ""}${item.model ? ` - ${item.model}` : ""}`}
                    onChange={(value) => {
                      setWizardCarQuery(value);
                      setWizardCar(null);
                    }}
                    onSelect={(car) => {
                      setWizardCar(car);
                      setWizardCarQuery(car.plate || "");
                      setWizardCarOptions([]);
                      syncMetaPayload({
                        carPlate: car.plate || "",
                        carModel: car.model || "",
                      });
                    }}
                    onClear={() => {
                      setWizardCar(null);
                      setWizardCarQuery("");
                      syncMetaPayload({
                        carPlate: "",
                        carModel: "",
                      });
                    }}
                  />
                  </div>
                </IonCardContent>
              </FrottoCard>
            )}

            {wizardStep === 2 && (
              <FrottoCard>
                <IonCardContent className="documents-step-body">
                  <WizardStepHead step={2} />
                  <SelectField
                    label="Tipo"
                    value={wizardType}
                    options={wizardTypeOptions(Boolean(savedDocument?.id), wizardType)}
                    onChange={(value) => setWizardType((value as DocumentType) || "")}
                  />
                  <p className="documents-hint" data-testid="documents-moved-to-pendencies">
                    Multas e manutenções compartilhadas são registradas em Pendências do motorista; o documento é emitido a
                    partir da pendência e fica arquivado aqui.
                  </p>
                  {wizardRequiresCar && !wizardCar?.id && (
                    <p className="documents-warning">Esse tipo exige vínculo com carro.</p>
                  )}
                </IonCardContent>
              </FrottoCard>
            )}

            {wizardStep === 3 && (
              <FrottoCard>
                <IonCardContent className="documents-step-body">
                  <WizardStepHead step={3} typeLabel={wizardType ? resolveTypeLabel(wizardType) : undefined} />
                  {isChecklistFinal ? renderChecklistFinal() : renderTypeFields()}

                  {showAttachmentInput && !isChecklistFinal && (
                    <FormGroup title="Anexos">
                    <IonItem lines="none" className="documents-attachments-item">
                      <IonLabel position="stacked">
                        {wizardType === "ENTREGA_DEVOLUCAO_CHECKLIST"
                          ? "Anexos gerais (opcional)"
                          : "Anexos (imagens/PDF)"}
                      </IonLabel>
                      <input
                        className="documents-file-input"
                        type="file"
                        multiple
                        accept="image/*,application/pdf"
                        onChange={(event) => {
                          const files = Array.from(event.target.files || []);
                          setWizardFiles(files);
                          logDocumentsDebug("generic attachments selected", {
                            selectedCount: files.length,
                            names: files.map((file) => file.name),
                          });
                        }}
                      />
                    </IonItem>
                    </FormGroup>
                  )}
                </IonCardContent>
              </FrottoCard>
            )}
          </div>
        </IonContent>
        <IonFooter className="app-footer-bar">
          <IonToolbar>
            {/* Hierarquia: Voltar (neutro) · Excluir (destrutivo) · Salvar rascunho (secundário) · Próximo/Gerar PDF (primário). */}
            <div
              className={`documents-wizard-footer documents-wizard-footer--step-${wizardStep}${
                wizardStep === 3 && savedDocument?.id ? " documents-wizard-footer--with-delete" : ""
              }`}
            >
              {wizardStep > 1 && (
                <IonButton
                  fill="outline"
                  className="app-neutral-btn documents-wizard-footer__back"
                  onClick={() => {
                    if (wizardStep === 3 && isStructuredChecklist && !isChecklistFinal && checklistPart === 2) {
                      setChecklistPart(1);
                      return;
                    }
                    setWizardStep((prev) => (prev === 1 ? 1 : ((prev - 1) as 1 | 2 | 3)));
                  }}
                >
                  Voltar
                </IonButton>
              )}
              {wizardStep === 3 && !!savedDocument?.id && !isChecklistFinal && (
                <IonButton
                  fill="clear"
                  className="app-danger-btn documents-wizard-footer__delete"
                  aria-label="Excluir documento"
                  disabled={isActionLoading}
                  onClick={() => void deleteWizardDocument()}
                >
                  <IonIcon icon={trashOutline} slot="start" />
                  <span className="documents-wizard-footer__delete-label">Excluir</span>
                </IonButton>
              )}
              <span className="documents-wizard-footer__spacer" aria-hidden="true" />
              {wizardStep < 3 ? (
                <IonButton
                  className="app-primary-btn documents-wizard-footer__primary"
                  onClick={() => setWizardStep((prev) => (prev === 3 ? 3 : ((prev + 1) as 1 | 2 | 3)))}
                >
                  Próximo
                </IonButton>
              ) : isStructuredChecklist ? (
                // Checklist: Finalizar is the operation; Gerar PDF exists only after it (documentary action).
                isChecklistFinal ? (
                  <IonButton
                    className="app-primary-btn documents-wizard-footer__primary"
                    onClick={() => void generateWizardPdf()}
                    disabled={isActionLoading}
                  >
                    Gerar PDF
                  </IonButton>
                ) : (
                  <>
                    <IonButton
                      fill="outline"
                      className="app-save-btn documents-wizard-footer__draft"
                      onClick={() => void saveDraft()}
                      disabled={isActionLoading}
                    >
                      Salvar rascunho
                    </IonButton>
                    {checklistPart === 1 ? (
                      <IonButton
                        className="app-primary-btn documents-wizard-footer__primary"
                        onClick={goToChecklistConference}
                        disabled={isActionLoading}
                      >
                        Continuar para conferência
                      </IonButton>
                    ) : (
                    <IonButton
                      className="app-primary-btn documents-wizard-footer__primary"
                      onClick={() => void finalizeChecklist()}
                      disabled={isActionLoading}
                    >
                      {checklistType === "DEVOLUCAO"
                        ? "Finalizar devolução"
                        : checklistType === "ENTREGA"
                        ? "Finalizar entrega"
                        : "Finalizar checklist"}
                    </IonButton>
                    )}
                  </>
                )
              ) : (
                <>
                  <IonButton
                    fill="outline"
                    className="app-save-btn documents-wizard-footer__draft"
                    onClick={() => void saveDraft()}
                    disabled={isActionLoading}
                  >
                    Salvar rascunho
                  </IonButton>
                  <IonButton
                    className="app-primary-btn documents-wizard-footer__primary"
                    onClick={() => void generateWizardPdf()}
                    disabled={isActionLoading}
                  >
                    Gerar PDF
                  </IonButton>
                </>
              )}
            </div>
          </IonToolbar>
        </IonFooter>
      </IonModal>
      <IonModal isOpen={!!checklistConflict} backdropDismiss={false} onDidDismiss={() => setChecklistConflict(null)}>
        {checklistConflict && (
          <DriverAssignmentChoice
            conflictingCarPlate={checklistConflict.conflictingCarPlate}
            onChoose={(assignment) => {
              setChecklistConflict(null);
              void finalizeChecklist(assignment);
            }}
            onCancel={() => setChecklistConflict(null)}
          />
        )}
      </IonModal>
    </IonPage>
  );
};

// ---- Apresentação do wizard (sem estado/lógica: só hierarquia visual) ----

export const WIZARD_STEPS: ReadonlyArray<{ step: 1 | 2 | 3; title: string; description: string }> = [
  { step: 1, title: "Motorista e carro", description: "Para quem é o documento." },
  { step: 2, title: "Tipo de documento", description: "Qual documento será gerado." },
  { step: 3, title: "Preenchimento", description: "Dados que vão no documento." },
];

/** Indicador de etapas: atual, concluídas e próximas. Não navega (a navegação continua no rodapé). */
export const WizardStepper: React.FC<{ current: 1 | 2 | 3 }> = ({ current }) => (
  <ol className="documents-stepper" aria-label="Etapas do documento">
    {WIZARD_STEPS.map(({ step, title }) => {
      const state = step < current ? "done" : step === current ? "current" : "upcoming";
      return (
        <li
          key={step}
          className={`documents-stepper__item documents-stepper__item--${state}`}
          aria-current={state === "current" ? "step" : undefined}
        >
          <span className="documents-stepper__index" aria-hidden="true">
            {state === "done" ? <IonIcon icon={checkmark} /> : step}
          </span>
          <span className="documents-stepper__label">{title}</span>
        </li>
      );
    })}
  </ol>
);

export const WizardStepHead: React.FC<{ step: 1 | 2 | 3; typeLabel?: string }> = ({ step, typeLabel }) => {
  const meta = WIZARD_STEPS[step - 1];
  return (
    <div className="documents-step-head">
      <p className="documents-step-head__eyebrow">Passo {step} de 3</p>
      <h3 className="documents-step-head__title">{step === 3 && typeLabel ? typeLabel : meta.title}</h3>
      <p className="documents-step-head__description">{meta.description}</p>
    </div>
  );
};

/** Grupo lógico de campos. `columns` usa grid responsivo só para campos curtos. */
const FormGroup: React.FC<{ title: string; columns?: boolean; children: React.ReactNode }> = ({
  title,
  columns,
  children,
}) => (
  <section className="documents-form-group" aria-label={title}>
    <h4 className="documents-form-group__title">{title}</h4>
    <div className={`documents-form-group__fields${columns ? " documents-form-group__fields--grid" : ""}`}>
      {children}
    </div>
  </section>
);

type FieldProps = {
  label: string;
  value: any;
  onChange: (value: any) => void;
};

const TextField: React.FC<FieldProps> = ({ label, value, onChange }) => (
  <FormInput
    label={label}
    initialValue={value || ""}
    changeCallback={(v: string) => onChange(v || "")}
  />
);

// Mantém a máscara decimal existente (sanitiza a cada tecla, formata no blur)
// — não usa FormCurrency porque essa máscara já é diferente (não é uma
// entrada monetária "R$" mascarada por dígito) e trocar mudaria o
// comportamento de digitação. Só o invólucro visual foi alinhado ao Form*.
const DecimalField: React.FC<FieldProps> = ({ label, value, onChange }) => (
  <div className="app-form-field">
    <IonItem className="app-form-item">
      <FormInputLabel name={label} />
      <IonInput
        type="text"
        inputmode="decimal"
        class="ion-text-end"
        color="primary"
        value={value || ""}
        onIonChange={(e) => onChange(sanitizeDecimalInput(e.detail.value || ""))}
        onIonBlur={() => onChange(formatDecimalInput(value || ""))}
      />
    </IonItem>
  </div>
);

const IntegerField: React.FC<FieldProps> = ({ label, value, onChange }) => (
  <div className="app-form-field">
    <IonItem className="app-form-item">
      <FormInputLabel name={label} />
      <IonInput
        type="number"
        inputmode="numeric"
        class="ion-text-end"
        color="primary"
        value={value || ""}
        onIonChange={(e) => onChange((e.detail.value || "").replace(/\D+/g, ""))}
      />
    </IonItem>
  </div>
);

/** Native date / time / text input (structured checklist fields); data-field identifies it in tests. */
const NativeField: React.FC<
  FieldProps & { type: "date" | "time" | "text"; field: string; inputMode?: "decimal" | "numeric" }
> = ({ label, value, onChange, type, field, inputMode }) => (
  <div className="app-form-field">
    <IonItem className="app-form-item">
      <FormInputLabel name={label} />
      <IonInput
        type={type}
        inputmode={inputMode}
        color="primary"
        data-field={field}
        value={value ?? ""}
        onIonChange={(e) => onChange(e.detail.value || "")}
      />
    </IonItem>
  </div>
);

/** Messages for the error codes of the checklist finalization (and of the contract rules it reuses). */
export const CHECKLIST_ERROR_MESSAGES: Record<string, string> = {
  ...DRIVER_CAR_ERROR_MESSAGES,
  checklisttyperequired: "Informe se o checklist é de Entrega ou de Devolução.",
  checklisttypeimmutable: "O tipo do checklist não pode ser alterado: crie um novo checklist.",
  checklistdrivercarrequired: "Selecione o vínculo que está sendo devolvido.",
  checklistdrivercarnotfound: "Vínculo não encontrado para esta conta.",
  checklistdrivercardriver: "O vínculo selecionado é de outro motorista.",
  checklistdrivercarcar: "O vínculo selecionado é de outro veículo.",
  checklistdrivercarconcluded: "Este vínculo já foi encerrado: a operação não pode ser registrada nele.",
  checklistdrivercarsuspended:
    "Este vínculo está suspenso (o motorista está em um carro reserva). Devolva o carro reserva ou reative o vínculo antes.",
  checklistalreadyfinalized: "Este vínculo já possui um checklist deste tipo finalizado.",
  checklistdaterequired: "Informe a data da vistoria.",
  checklistodometerinvalid: "Informe o KM do veículo (somente números).",
  checklistfuelrequired: "Selecione o nível de combustível.",
  checklistincomplete: "Checklist sem tipo, motorista ou veículo.",
  checklistfinalizerequired: "O checklist só é finalizado pelo botão Finalizar.",
  checklistfinalcannotbedeleted: "Um checklist finalizado registra a operação e não pode ser excluído.",
  activedriverexists:
    "O veículo já está vinculado a outro motorista. Registre a devolução ou encerre esse vínculo antes da entrega.",
};

const AreaField: React.FC<FieldProps> = ({ label, value, onChange }) => (
  <div className="app-form-field">
    <IonItem className="app-form-item app-form-item--textarea">
      <FormInputLabel name={label} />
      <IonTextarea value={value || ""} autoGrow onIonChange={(e) => onChange(e.detail.value || "")} />
    </IonItem>
  </div>
);

const SelectField: React.FC<FieldProps & { options: Array<{ value: string; label: string }> }> = ({
  label,
  value,
  onChange,
  options,
}) => (
  <FormSelect
    label={label}
    initialValue={value || ""}
    options={options}
    changeCallback={(v: string) => onChange(v)}
  />
);

const Autocomplete = <T,>({
  label,
  value,
  options,
  getLabel,
  onChange,
  onSelect,
  onClear,
}: {
  label: string;
  value: string;
  options: T[];
  getLabel: (item: T) => string;
  onChange: (value: string) => void;
  onSelect: (item: T) => void;
  onClear: () => void;
}) => (
  <div className="documents-autocomplete">
    <div className="app-form-field">
      <IonItem className="app-form-item">
        <FormInputLabel name={label} />
        {/* ionInput = digitação do usuário. Não usar ionChange: no Ionic 6 ele também
            dispara quando o código define `value` após selecionar uma entidade, e o
            onChange (que invalida a seleção) apagava o motorista/carro escolhido. */}
        <IonInput
          value={value}
          onIonInput={(event) => onChange(`${(event.target as HTMLIonInputElement | null)?.value ?? ""}`)}
        />
        {value ? (
          <IonButton fill="clear" slot="end" onClick={onClear}>
            Limpar
          </IonButton>
        ) : null}
      </IonItem>
    </div>
    {options.length > 0 && (
      <IonList className="documents-autocomplete-list">
        {options.map((item, index) => (
          <IonItem key={`${label}-option-${index}`} button onClick={() => onSelect(item)}>
            <IonLabel>{getLabel(item)}</IonLabel>
          </IonItem>
        ))}
      </IonList>
    )}
  </div>
);

function resolveTypeLabel(type: DocumentType): string {
  return DOCUMENT_TYPES.find((item) => item.value === type)?.label || type;
}

function resolveStatusLabel(status: DocumentStatus): string {
  return DOCUMENT_STATUSES.find((item) => item.value === status)?.label || status;
}

function sanitizeChecklistCustomKey(value: any): string {
  return `${value || ""}`
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

function normalizeLookupText(value: any): string {
  return `${value || ""}`
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase();
}

function getDriverPendencyOutstandingAmount(pendency: DriverPendencyModel): number {
  if (typeof pendency.remainingAmount === "number") {
    return Math.max(pendency.remainingAmount, 0);
  }
  if (typeof pendency.cost === "number" && typeof pendency.paidAmount === "number") {
    return Math.max(pendency.cost - pendency.paidAmount, 0);
  }
  if (typeof pendency.cost === "number") {
    return pendency.status === "PAID" ? 0 : pendency.cost;
  }
  return 0;
}

function buildConfissaoItemDescriptionFromPendency(pendency: DriverPendencyModel): string {
  const name = `${pendency.name || ""}`.trim();
  const note = `${pendency.note || ""}`.trim();
  if (name && note) {
    return `${name}. ${note}`;
  }
  return name || note;
}

function formatDate(value?: string) {
  if (!value) {
    return "-";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString("pt-BR");
}

function supportsAttachments(type: DocumentType | ""): boolean {
  return (
    type === "MULTA" ||
    type === "MANUTENCAO_COMPARTILHADA" ||
    type === "ENTREGA_DEVOLUCAO_CHECKLIST"
  );
}

function dedupeStringList(values: any[]): string[] {
  const unique: string[] = [];
  values.forEach((value) => {
    const normalized = `${value || ""}`.trim();
    if (!normalized || unique.includes(normalized)) {
      return;
    }
    unique.push(normalized);
  });
  return unique;
}

function appendUniqueFiles(current: File[], incoming: File[]): File[] {
  const existing = new Set(
    current.map((file) => `${file.name}|${file.size}|${file.lastModified}`)
  );
  const next = [...current];
  incoming.forEach((file) => {
    const signature = `${file.name}|${file.size}|${file.lastModified}`;
    if (existing.has(signature)) {
      return;
    }
    existing.add(signature);
    next.push(file);
  });
  return next;
}

function resolveAttachmentDiff(after: string[] = [], before: string[] = []): string[] {
  const counter = new Map<string, number>();
  before.forEach((item) => {
    const key = `${item || ""}`.trim();
    if (!key) {
      return;
    }
    counter.set(key, (counter.get(key) || 0) + 1);
  });

  const diff: string[] = [];
  after.forEach((item) => {
    const key = `${item || ""}`.trim();
    if (!key) {
      return;
    }
    const available = counter.get(key) || 0;
    if (available > 0) {
      counter.set(key, available - 1);
      return;
    }
    diff.push(key);
  });

  return dedupeStringList(diff);
}

function areStringListsEqual(a: string[] = [], b: string[] = []): boolean {
  if (a.length !== b.length) {
    return false;
  }
  return a.every((value, index) => value === b[index]);
}

function resolveChecklistPhotoRefsFromSource(
  source: any,
  fallbackAttachments: string[] = []
): string[] {
  if (Array.isArray(source)) {
    return dedupeStringList(source);
  }
  if (!source || typeof source !== "object") {
    return dedupeStringList(fallbackAttachments);
  }

  const payload = source as Record<string, any>;
  if (Array.isArray(payload.attachmentsChecklist)) {
    return dedupeStringList(payload.attachmentsChecklist);
  }
  if (Array.isArray(payload.checklistPhotoRefs)) {
    return dedupeStringList(payload.checklistPhotoRefs);
  }
  if (Array.isArray(payload.fotosChecklist)) {
    return dedupeStringList(payload.fotosChecklist);
  }
  if (Array.isArray(payload.fotos)) {
    return dedupeStringList(payload.fotos);
  }
  if (Array.isArray(payload.attachments)) {
    return dedupeStringList(payload.attachments);
  }
  return dedupeStringList(fallbackAttachments);
}

export function buildChecklistPhotoPreviewCandidates(ref: string, attachmentUrls?: Record<string, string>): string[] {
  const value = `${ref || ""}`.trim();
  if (!value) {
    return [];
  }

  // URL resolved by the backend ("" = unavailable); references it does not list keep the legacy candidates.
  if (attachmentUrls && Object.prototype.hasOwnProperty.call(attachmentUrls, value)) {
    const resolved = `${attachmentUrls[value] || ""}`.trim();
    return resolved ? [resolved] : [];
  }

  if (/^(https?:|blob:|data:)/i.test(value)) {
    return [value];
  }

  return dedupeStringList([
    resolveApiUrl(value),
    resolveApiUrl(value.startsWith("/") ? value : `/${value}`),
    resolveS3AssetUrl(value),
  ]);
}

function resolveS3AssetUrl(path: string): string {
  const value = `${path || ""}`.trim();
  if (!value) {
    return "";
  }

  const s3Base = `${process.env.REACT_APP_S3_URL || ""}`.trim().replace(/\/$/, "");
  if (!s3Base) {
    return "";
  }

  const normalizedPath = value.startsWith("/") ? value : `/${value}`;
  return `${s3Base}${normalizedPath}`;
}

function normalizeChecklistContact(source: any): ChecklistEmergencyContact {
  return {
    nome: `${source?.nome || source?.name || ""}`.trim(),
    telefone: sanitizeDigits(`${source?.telefone || source?.phone || ""}`).slice(0, 11),
  };
}

function serializeChecklistEmergencyContacts(source: any): ChecklistEmergencyContact[] {
  if (!Array.isArray(source)) {
    return [];
  }
  return source
    .map((item) => normalizeChecklistContact(item))
    .filter((item) => Boolean(item.nome || item.telefone));
}

// Estado do editor: mantém linhas vazias (ex.: recém-adicionadas por "Adicionar contato")
// e não apara o nome a cada tecla (senão o espaço entre nome e sobrenome some ao digitar).
// O payload enviado à API continua passando por serializeChecklistEmergencyContacts,
// que apara e descarta contatos vazios — o formato salvo não muda.
function hydrateChecklistEmergencyContactsForEditor(source: any): ChecklistEmergencyContact[] {
  const contacts: ChecklistEmergencyContact[] = Array.isArray(source)
    ? source.map((item) => ({
        ...normalizeChecklistContact(item),
        nome: `${item?.nome ?? item?.name ?? ""}`,
      }))
    : [];
  while (contacts.length < MIN_EMERGENCY_CONTACTS) {
    contacts.push({ nome: "", telefone: "" });
  }
  return contacts;
}

function hydrateChecklistEmergencyContacts(source: any): ChecklistEmergencyContact[] {
  const contacts = serializeChecklistEmergencyContacts(source);
  while (contacts.length < MIN_EMERGENCY_CONTACTS) {
    contacts.push({ nome: "", telefone: "" });
  }
  return contacts;
}

function isChecklistEmergencyContactComplete(contact: ChecklistEmergencyContact): boolean {
  return Boolean(`${contact.nome || ""}`.trim()) && Boolean(`${contact.telefone || ""}`.trim());
}

function isChecklistEmergencyContactPartial(contact: ChecklistEmergencyContact): boolean {
  const hasName = Boolean(`${contact.nome || ""}`.trim());
  const hasPhone = Boolean(`${contact.telefone || ""}`.trim());
  return hasName !== hasPhone;
}

function normalizeChecklistTireCondition(value: any): ChecklistTireCondition {
  const normalized = normalizeLookupText(value).replace(/_/g, " ");
  if (!normalized) {
    return "";
  }
  if (normalized.includes("ruim") || normalized.includes("careca")) {
    return "RUIM";
  }
  if (normalized.includes("meia") || normalized.includes("regular")) {
    return "MEIA_VIDA";
  }
  if (normalized.includes("bom") || normalized.includes("otimo") || normalized.includes("novo")) {
    return "BOM";
  }
  return "";
}

function normalizeChecklistTirePositions(source: any): ChecklistTirePosition[] {
  if (!Array.isArray(source)) {
    return [];
  }
  return source
    .map((item) => ({
      posicao: `${item?.posicao || item?.position || ""}`.trim(),
      marca: `${item?.marca || item?.brand || item?.model || ""}`.trim() || undefined,
      estado: `${item?.estado || item?.condition || item?.integrity || ""}`.trim() || undefined,
    }))
    .filter((item) => item.posicao || item.marca || item.estado);
}

function normalizeChecklistTiresForEditor(source: any): ChecklistTires {
  if (!source || typeof source !== "object") {
    return {};
  }

  const raw = source as Record<string, any>;
  const flag = `${raw.source || ""}`.toUpperCase();
  const sourceFlag = flag === "LAST_INSPECTION" ? "LAST_INSPECTION" : flag === "CHECKLIST" ? "CHECKLIST" : "MANUAL";
  const positions = normalizeChecklistTirePositions(raw.positions);
  const marca = `${raw.marca || raw.brand || raw.model || ""}`.trim();
  const estado = normalizeChecklistTireCondition(raw.estado || raw.condition || raw.integrity);
  const observacoes = `${raw.observacoes || raw.notes || ""}`.trim();

  return {
    marca: marca || undefined,
    estado,
    observacoes: observacoes || undefined,
    source: sourceFlag,
    positions: positions.length ? positions : undefined,
  };
}

function hasChecklistTiresData(tires: ChecklistTires | undefined): boolean {
  if (!tires) {
    return false;
  }
  return Boolean(
    `${tires.marca || ""}`.trim() ||
      `${tires.estado || ""}`.trim() ||
      `${tires.observacoes || ""}`.trim() ||
      (Array.isArray(tires.positions) && tires.positions.length)
  );
}

function normalizeChecklistTiresForApi(source: any): ChecklistTires | undefined {
  const normalized = normalizeChecklistTiresForEditor(source);
  if (!hasChecklistTiresData(normalized)) {
    return undefined;
  }
  return normalized;
}

function buildChecklistTiresFromInspection(inspection: any): ChecklistTires | null {
  if (!inspection || typeof inspection !== "object") {
    return null;
  }

  const source = inspection as Record<string, any>;
  const positionsMap = [
    { key: "leftFront", label: "Dianteiro esquerdo" },
    { key: "rightFront", label: "Dianteiro direito" },
    { key: "leftBack", label: "Traseiro esquerdo" },
    { key: "rightBack", label: "Traseiro direito" },
    { key: "spare", label: "Estepe" },
  ];

  const positions = positionsMap
    .map((item) => {
      const tire = source[item.key] || {};
      const marca = `${tire?.model || ""}`.trim();
      const estado = `${tire?.integrity || ""}`.trim();
      if (!marca && !estado) {
        return null;
      }
      return {
        posicao: item.label,
        marca: marca || undefined,
        estado: estado || undefined,
      } as ChecklistTirePosition;
    })
    .filter((item): item is ChecklistTirePosition => Boolean(item));

  if (!positions.length) {
    return null;
  }

  const marcas = dedupeStringList(positions.map((item) => item.marca || "").filter(Boolean));
  const estados = positions
    .map((item) => normalizeChecklistTireCondition(item.estado))
    .filter((item) => Boolean(item)) as ChecklistTireCondition[];
  const estadoPrincipal = estados[0] || "";
  const estadoVariado = dedupeStringList(estados).length > 1;

  return normalizeChecklistTiresForEditor({
    marca: marcas.join(" / "),
    estado: estadoPrincipal,
    observacoes: estadoVariado ? "Estados variam por posição (ver resumo)." : "",
    source: "LAST_INSPECTION",
    positions,
  });
}

export default DocumentsPage;
