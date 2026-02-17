import {
  IonBadge,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonMenuButton,
  IonModal,
  IonProgressBar,
  IonSelect,
  IonSelectOption,
  IonTextarea,
  IonTitle,
  IonToolbar,
  IonPage,
} from "@ionic/react";
import { add, closeCircleOutline, refreshOutline } from "ionicons/icons";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  CarSearchModel,
  DOCUMENT_STATUSES,
  DOCUMENT_TYPES,
  DOCUMENT_TYPES_REQUIRING_CAR,
  DocumentModel,
  DocumentStatus,
  DocumentType,
  DriverSearchModel,
} from "../../constants/DocumentModels";
import documentService from "../../services/documentService";
import { useAlert } from "../../services/hooks/useAlert";
import { createDocumentPdfBlob, generateDocumentPdf } from "./documentPdf";
import "./DocumentsPage.css";

const AUTOCOMPLETE_DELAY = 300;
const PAGE_SIZE = 30;

const DocumentsPage: React.FC = () => {
  const { showErrorAlert } = useAlert();

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
  const [savedDocument, setSavedDocument] = useState<DocumentModel | null>(null);

  const wizardRequiresCar = useMemo(
    () => Boolean(wizardType && DOCUMENT_TYPES_REQUIRING_CAR.includes(wizardType)),
    [wizardType]
  );

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
    const query = filterDriverQuery.trim();
    if (!query || filterDriver?.name === query) {
      setFilterDriverOptions([]);
      return;
    }

    const handle = setTimeout(async () => {
      try {
        setFilterDriverOptions(await documentService.searchDrivers(query));
      } catch (_error) {
        setFilterDriverOptions([]);
      }
    }, AUTOCOMPLETE_DELAY);

    return () => clearTimeout(handle);
  }, [filterDriver?.name, filterDriverQuery]);

  useEffect(() => {
    const query = filterCarQuery.trim();
    if (!query || filterCar?.plate === query) {
      setFilterCarOptions([]);
      return;
    }

    const handle = setTimeout(async () => {
      try {
        setFilterCarOptions(await documentService.searchCars(query));
      } catch (_error) {
        setFilterCarOptions([]);
      }
    }, AUTOCOMPLETE_DELAY);

    return () => clearTimeout(handle);
  }, [filterCar?.plate, filterCarQuery]);

  useEffect(() => {
    const query = wizardDriverQuery.trim();
    if (!query || wizardDriver?.name === query) {
      setWizardDriverOptions([]);
      return;
    }

    const handle = setTimeout(async () => {
      try {
        setWizardDriverOptions(await documentService.searchDrivers(query));
      } catch (_error) {
        setWizardDriverOptions([]);
      }
    }, AUTOCOMPLETE_DELAY);

    return () => clearTimeout(handle);
  }, [wizardDriver?.name, wizardDriverQuery]);

  useEffect(() => {
    const query = wizardCarQuery.trim();
    if (!query || wizardCar?.plate === query) {
      setWizardCarOptions([]);
      return;
    }

    const handle = setTimeout(async () => {
      try {
        setWizardCarOptions(await documentService.searchCars(query));
      } catch (_error) {
        setWizardCarOptions([]);
      }
    }, AUTOCOMPLETE_DELAY);

    return () => clearTimeout(handle);
  }, [wizardCar?.plate, wizardCarQuery]);

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
    setWizardStep(1);
    setWizardType("");
    setWizardDriver(null);
    setWizardCar(null);
    setWizardDriverQuery("");
    setWizardCarQuery("");
    setWizardPayload({});
    setWizardFiles([]);
    setSavedDocument(null);
  };

  const closeWizard = () => {
    setIsWizardOpen(false);
    resetWizard();
  };

  const openWizard = () => {
    resetWizard();
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

  const validateWizard = () => {
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
    return true;
  };

  const saveDraft = async (): Promise<DocumentModel | null> => {
    if (!validateWizard()) {
      return null;
    }

    setIsActionLoading(true);
    try {
      const payload = syncMetaPayload();
      const request = {
        type: wizardType as DocumentType,
        status: "DRAFT" as DocumentStatus,
        driverId: wizardDriver!.id,
        carId: wizardRequiresCar ? wizardCar!.id : wizardCar?.id ?? null,
        payload,
      };

      let response = savedDocument?.id
        ? await documentService.updateDocument(savedDocument.id, request)
        : await documentService.createDocument(request);

      if (wizardFiles.length && response.id) {
        response = await documentService.uploadDocumentAttachments(response.id, wizardFiles);
      }

      setSavedDocument(response);
      await loadDocuments();
      return response;
    } catch (_error) {
      showErrorAlert("Falha ao salvar rascunho.");
      return null;
    } finally {
      setIsActionLoading(false);
    }
  };

  const finalizeDocument = async () => {
    const draft = (await saveDraft()) || savedDocument;
    if (!draft?.id) {
      return;
    }

    setIsActionLoading(true);
    try {
      setSavedDocument(await documentService.finalizeDocument(draft.id));
      await loadDocuments();
    } catch (_error) {
      showErrorAlert("Falha ao finalizar documento.");
    } finally {
      setIsActionLoading(false);
    }
  };

  const generateWizardPdf = async () => {
    const draft = (await saveDraft()) || savedDocument;
    if (!draft?.id) {
      return;
    }

    setIsActionLoading(true);
    try {
      const detailed = await documentService.getDocument(draft.id);
      await generateDocumentPdf(detailed);
      setSavedDocument(await documentService.generateDocumentPdf(draft.id));
      await loadDocuments();
    } catch (_error) {
      showErrorAlert("Falha ao gerar PDF.");
    } finally {
      setIsActionLoading(false);
    }
  };

  const shareAndSend = async () => {
    const draft = (await saveDraft()) || savedDocument;
    if (!draft?.id) {
      return;
    }

    setIsActionLoading(true);
    try {
      const detailed = await documentService.getDocument(draft.id);
      const blob = await createDocumentPdfBlob(detailed);
      const file = new File([blob], `documento_${draft.id}.pdf`, { type: "application/pdf" });
      const navAny = navigator as any;
      if (navAny.share && (!navAny.canShare || navAny.canShare({ files: [file] }))) {
        await navAny.share({ files: [file], title: "Documento Frotto" });
      } else {
        await generateDocumentPdf(detailed);
      }

      setSavedDocument(await documentService.markDocumentSent(draft.id));
      await loadDocuments();
    } catch (_error) {
      showErrorAlert("Falha ao compartilhar/enviar.");
    } finally {
      setIsActionLoading(false);
    }
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

  const renderTypeFields = () => {
    if (!wizardType) {
      return null;
    }

    if (wizardType === "MULTA") {
      return (
        <>
          <TextField label="Data/Hora" value={wizardPayload.dataHora} onChange={(v) => setPayload("dataHora", v)} />
          <TextField label="Local" value={wizardPayload.local} onChange={(v) => setPayload("local", v)} />
          <TextField label="AIT" value={wizardPayload.ait} onChange={(v) => setPayload("ait", v)} />
          <TextField label="Órgão" value={wizardPayload.orgao} onChange={(v) => setPayload("orgao", v)} />
          <TextField
            label="Enquadramento"
            value={wizardPayload.enquadramento}
            onChange={(v) => setPayload("enquadramento", v)}
          />
          <NumberField label="Valor" value={wizardPayload.valor} onChange={(v) => setPayload("valor", v)} />
          <TextField label="Vencimento" value={wizardPayload.vencimento} onChange={(v) => setPayload("vencimento", v)} />
          <SelectField
            label="Responsável pagamento"
            value={wizardPayload.responsavelPagamento || ""}
            options={[
              { value: "MOTORISTA", label: "Motorista" },
              { value: "EMPRESA", label: "Empresa" },
            ]}
            onChange={(v) => setPayload("responsavelPagamento", v)}
          />
          <AreaField
            label="Observações"
            value={wizardPayload.observacoes}
            onChange={(v) => setPayload("observacoes", v)}
          />
        </>
      );
    }

    if (wizardType === "MANUTENCAO_COMPARTILHADA") {
      return (
        <>
          <TextField label="Data" value={wizardPayload.data} onChange={(v) => setPayload("data", v)} />
          <TextField label="Descrição" value={wizardPayload.descricao} onChange={(v) => setPayload("descricao", v)} />
          <TextField label="Oficina" value={wizardPayload.oficina} onChange={(v) => setPayload("oficina", v)} />
          <NumberField label="Valor total" value={wizardPayload.valorTotal} onChange={(v) => setPayload("valorTotal", v)} />
          <SelectField
            label="Forma divisão"
            value={wizardPayload.formaDivisao || ""}
            options={[
              { value: "PERCENTUAL", label: "Percentual" },
              { value: "VALOR", label: "Valor" },
            ]}
            onChange={(v) => setPayload("formaDivisao", v)}
          />
          <NumberField
            label="Parte motorista (valor)"
            value={wizardPayload.parteMotoristaValor}
            onChange={(v) => setPayload("parteMotoristaValor", v)}
          />
          <AreaField
            label="Observações"
            value={wizardPayload.observacoes}
            onChange={(v) => setPayload("observacoes", v)}
          />
        </>
      );
    }

    if (wizardType === "RECIBO_ALUGUEL") {
      return (
        <>
          <TextField label="Período início" value={wizardPayload.periodoInicio} onChange={(v) => setPayload("periodoInicio", v)} />
          <TextField label="Período fim" value={wizardPayload.periodoFim} onChange={(v) => setPayload("periodoFim", v)} />
          <NumberField label="Valor aluguel" value={wizardPayload.valorAluguel} onChange={(v) => setPayload("valorAluguel", v)} />
          <NumberField label="Descontos" value={wizardPayload.descontos} onChange={(v) => setPayload("descontos", v)} />
          <NumberField label="Acréscimos" value={wizardPayload.acrescimos} onChange={(v) => setPayload("acrescimos", v)} />
          <NumberField label="Valor final" value={wizardPayload.valorFinal} onChange={(v) => setPayload("valorFinal", v)} />
          <TextField label="Forma pagamento" value={wizardPayload.formaPagamento} onChange={(v) => setPayload("formaPagamento", v)} />
          <TextField label="Data pagamento" value={wizardPayload.dataPagamento} onChange={(v) => setPayload("dataPagamento", v)} />
          <AreaField
            label="Observações"
            value={wizardPayload.observacoes}
            onChange={(v) => setPayload("observacoes", v)}
          />
        </>
      );
    }

    if (wizardType === "CONFISSAO_DIVIDA") {
      return (
        <>
          <NumberField label="Valor total" value={wizardPayload.valorTotal} onChange={(v) => setPayload("valorTotal", v)} />
          <AreaField
            label="Origem da dívida"
            value={wizardPayload.origemDaDivida}
            onChange={(v) => setPayload("origemDaDivida", v)}
          />
          <SelectField
            label="Forma pagamento"
            value={wizardPayload.formaPagamento || ""}
            options={[
              { value: "A_VISTA", label: "À vista" },
              { value: "PARCELADO", label: "Parcelado" },
            ]}
            onChange={(v) => setPayload("formaPagamento", v)}
          />
          <NumberField label="Parcelas (qtd)" value={wizardPayload.parcelasQtd} onChange={(v) => setPayload("parcelasQtd", v)} />
          <NumberField label="Valor parcela" value={wizardPayload.valorParcela} onChange={(v) => setPayload("valorParcela", v)} />
          <TextField label="Vencimento inicial" value={wizardPayload.vencimentoInicial} onChange={(v) => setPayload("vencimentoInicial", v)} />
          <TextField label="Testemunha 1 nome" value={wizardPayload.testemunha1Nome} onChange={(v) => setPayload("testemunha1Nome", v)} />
          <TextField label="Testemunha 1 CPF" value={wizardPayload.testemunha1Cpf} onChange={(v) => setPayload("testemunha1Cpf", v)} />
          <TextField label="Testemunha 2 nome" value={wizardPayload.testemunha2Nome} onChange={(v) => setPayload("testemunha2Nome", v)} />
          <TextField label="Testemunha 2 CPF" value={wizardPayload.testemunha2Cpf} onChange={(v) => setPayload("testemunha2Cpf", v)} />
          <AreaField
            label="Observações"
            value={wizardPayload.observacoes}
            onChange={(v) => setPayload("observacoes", v)}
          />
        </>
      );
    }

    return (
      <>
        <SelectField
          label="Tipo"
          value={wizardPayload.tipo || ""}
          options={[
            { value: "ENTREGA", label: "Entrega" },
            { value: "DEVOLUCAO", label: "Devolução" },
          ]}
          onChange={(v) => setPayload("tipo", v)}
        />
        <TextField label="Data/Hora" value={wizardPayload.dataHora} onChange={(v) => setPayload("dataHora", v)} />
        <TextField label="KM" value={wizardPayload.km} onChange={(v) => setPayload("km", v)} />
        <TextField label="Combustível" value={wizardPayload.combustivel} onChange={(v) => setPayload("combustivel", v)} />
        <AreaField
          label="Checklist (JSON)"
          value={JSON.stringify(wizardPayload.checklistItens || [], null, 2)}
          onChange={(v) => {
            try {
              setPayload("checklistItens", JSON.parse(v));
            } catch (_error) {
              setPayload("checklistItens", []);
            }
          }}
        />
        <AreaField
          label="Avarias"
          value={wizardPayload.avariasTexto}
          onChange={(v) => setPayload("avariasTexto", v)}
        />
      </>
    );
  };

  const showAttachmentInput =
    wizardType === "MULTA" ||
    wizardType === "MANUTENCAO_COMPARTILHADA" ||
    wizardType === "ENTREGA_DEVOLUCAO_CHECKLIST";
  const canGoNextPage = documents.length >= PAGE_SIZE;

  return (
    <IonPage id="documents-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonMenuButton menu="main-menu" />
          </IonButtons>
          <IonTitle>Documentos</IonTitle>
          <IonButtons slot="end">
            <IonButton onClick={openWizard}>
              <IonIcon icon={add} slot="start" />
              Novo Documento
            </IonButton>
          </IonButtons>
        </IonToolbar>
        {(isLoading || isActionLoading) && <IonProgressBar type="indeterminate" />}
      </IonHeader>

      <IonContent fullscreen>
        <div className="section-shell documents-shell">
          <IonCard>
            <IonCardContent>
              <h3>Filtros</h3>
              <Autocomplete
                label="Motorista (CPF ou nome)"
                value={filterDriverQuery}
                options={filterDriverOptions}
                getLabel={(item) => `${item.name}${item.cpf ? ` (${item.cpf})` : ""}`}
                onChange={setFilterDriverQuery}
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
                onChange={setFilterCarQuery}
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
              <div className="documents-filter-actions">
                <IonButton
                  onClick={() => {
                    setListPage(0);
                    void loadDocuments(0);
                  }}
                >
                  Buscar
                </IonButton>
                <IonButton fill="outline" onClick={resetFilters}>
                  <IonIcon icon={refreshOutline} slot="start" />
                  Limpar filtros
                </IonButton>
              </div>
            </IonCardContent>
          </IonCard>
          <IonList>
            {documents.map((item) => (
              <IonItem key={`doc-${item.id}`} className="documents-list-item">
                <IonLabel className="ion-text-wrap">
                  <h2>{resolveTypeLabel(item.type)}</h2>
                  <p>Motorista: {item.driverName || "-"}</p>
                  <p>Carro: {item.carPlate || "-"}</p>
                  <p>Data: {formatDate(item.createdAt)}</p>
                </IonLabel>
                <div className="documents-item-actions">
                  <IonBadge color={resolveStatusColor(item.status)}>{resolveStatusLabel(item.status)}</IonBadge>
                  <IonButton size="small" fill="outline" onClick={() => void openView(item.id)}>
                    Ver
                  </IonButton>
                  <IonButton size="small" onClick={() => void openPdf(item.id)}>
                    Abrir PDF
                  </IonButton>
                </div>
              </IonItem>
            ))}
            {!isLoading && !documents.length && (
              <IonItem>
                <IonLabel>Nenhum documento encontrado.</IonLabel>
              </IonItem>
            )}
          </IonList>
          <div className="documents-pagination">
            <IonButton
              fill="outline"
              disabled={listPage === 0 || isLoading}
              onClick={() => setListPage((current) => Math.max(0, current - 1))}
            >
              Página anterior
            </IonButton>
            <span>Página {listPage + 1}</span>
            <IonButton
              fill="outline"
              disabled={!canGoNextPage || isLoading}
              onClick={() => setListPage((current) => current + 1)}
            >
              Próxima página
            </IonButton>
          </div>
        </div>
      </IonContent>

      <IonModal isOpen={isViewModalOpen} onDidDismiss={() => setIsViewModalOpen(false)}>
        <IonHeader>
          <IonToolbar>
            <IonTitle>Documento #{viewDocument?.id}</IonTitle>
            <IonButtons slot="end">
              <IonButton onClick={() => setIsViewModalOpen(false)}>
                <IonIcon icon={closeCircleOutline} slot="icon-only" />
              </IonButton>
            </IonButtons>
          </IonToolbar>
        </IonHeader>
        <IonContent>
          <div className="section-shell">
            <IonCard>
              <IonCardContent>
                <p><strong>Tipo:</strong> {resolveTypeLabel(viewDocument?.type || "MULTA")}</p>
                <p><strong>Status:</strong> {resolveStatusLabel(viewDocument?.status || "DRAFT")}</p>
                <p><strong>Motorista:</strong> {viewDocument?.driverName || "-"}</p>
                <p><strong>Carro:</strong> {viewDocument?.carPlate || "-"}</p>
                <p><strong>Criado:</strong> {formatDate(viewDocument?.createdAt)}</p>
              </IonCardContent>
            </IonCard>
            <IonCard>
              <IonCardContent>
                <h4>Payload</h4>
                <pre>{JSON.stringify(viewDocument?.payload || {}, null, 2)}</pre>
              </IonCardContent>
            </IonCard>
          </div>
        </IonContent>
      </IonModal>

      <IonModal isOpen={isWizardOpen} onDidDismiss={closeWizard}>
        <IonHeader>
          <IonToolbar>
            <IonTitle>Novo Documento - Passo {wizardStep}/3</IonTitle>
            <IonButtons slot="end">
              <IonButton onClick={closeWizard}>
                <IonIcon icon={closeCircleOutline} slot="icon-only" />
              </IonButton>
            </IonButtons>
          </IonToolbar>
        </IonHeader>
        <IonContent>
          <div className="section-shell documents-shell">
            {wizardStep === 1 && (
              <IonCard>
                <IonCardContent>
                  <h3>Passo 1 - Motorista/Carro</h3>
                  <Autocomplete
                    label="Motorista"
                    value={wizardDriverQuery}
                    options={wizardDriverOptions}
                    getLabel={(item) => `${item.name}${item.cpf ? ` (${item.cpf})` : ""}`}
                    onChange={setWizardDriverQuery}
                    onSelect={(driver) => {
                      setWizardDriver(driver);
                      setWizardDriverQuery(driver.name || "");
                      setWizardDriverOptions([]);
                      syncMetaPayload();
                    }}
                    onClear={() => {
                      setWizardDriver(null);
                      setWizardDriverQuery("");
                    }}
                  />
                  <Autocomplete
                    label="Carro (opcional para confissão)"
                    value={wizardCarQuery}
                    options={wizardCarOptions}
                    getLabel={(item) => `${item.plate || ""}${item.model ? ` - ${item.model}` : ""}`}
                    onChange={setWizardCarQuery}
                    onSelect={(car) => {
                      setWizardCar(car);
                      setWizardCarQuery(car.plate || "");
                      setWizardCarOptions([]);
                      syncMetaPayload();
                    }}
                    onClear={() => {
                      setWizardCar(null);
                      setWizardCarQuery("");
                    }}
                  />
                </IonCardContent>
              </IonCard>
            )}

            {wizardStep === 2 && (
              <IonCard>
                <IonCardContent>
                  <h3>Passo 2 - Tipo</h3>
                  <SelectField
                    label="Tipo"
                    value={wizardType}
                    options={DOCUMENT_TYPES}
                    onChange={(value) => setWizardType((value as DocumentType) || "")}
                  />
                  {wizardRequiresCar && !wizardCar?.id && (
                    <p className="documents-warning">Esse tipo exige vínculo com carro.</p>
                  )}
                </IonCardContent>
              </IonCard>
            )}

            {wizardStep === 3 && (
              <IonCard>
                <IonCardContent>
                  <h3>Passo 3 - Formulário</h3>
                  {renderTypeFields()}

                  {showAttachmentInput && (
                    <IonItem lines="none">
                      <IonLabel position="stacked">Anexos (imagens/PDF)</IonLabel>
                      <input
                        className="documents-file-input"
                        type="file"
                        multiple
                        accept="image/*,application/pdf"
                        onChange={(event) => {
                          setWizardFiles(Array.from(event.target.files || []));
                        }}
                      />
                    </IonItem>
                  )}

                  <div className="documents-wizard-actions">
                    <IonButton fill="outline" onClick={() => void saveDraft()} disabled={isActionLoading}>
                      Salvar rascunho
                    </IonButton>
                    <IonButton onClick={() => void finalizeDocument()} disabled={isActionLoading}>
                      Finalizar
                    </IonButton>
                    <IonButton fill="outline" onClick={() => void generateWizardPdf()} disabled={isActionLoading}>
                      Gerar PDF
                    </IonButton>
                    <IonButton color="success" onClick={() => void shareAndSend()} disabled={isActionLoading}>
                      Compartilhar/Enviar
                    </IonButton>
                  </div>
                </IonCardContent>
              </IonCard>
            )}
          </div>
        </IonContent>
        <IonToolbar>
          <div className="documents-step-footer">
            <IonButton fill="outline" disabled={wizardStep === 1} onClick={() => setWizardStep((prev) => (prev === 1 ? 1 : ((prev - 1) as 1 | 2 | 3)))}>
              Voltar
            </IonButton>
            <IonButton disabled={wizardStep === 3} onClick={() => setWizardStep((prev) => (prev === 3 ? 3 : ((prev + 1) as 1 | 2 | 3)))}>
              Próximo
            </IonButton>
          </div>
        </IonToolbar>
      </IonModal>
    </IonPage>
  );
};

type FieldProps = {
  label: string;
  value: any;
  onChange: (value: any) => void;
};

const TextField: React.FC<FieldProps> = ({ label, value, onChange }) => (
  <IonItem>
    <IonLabel position="stacked">{label}</IonLabel>
    <IonInput value={value || ""} onIonChange={(e) => onChange(e.detail.value || "")} />
  </IonItem>
);

const NumberField: React.FC<FieldProps> = ({ label, value, onChange }) => (
  <IonItem>
    <IonLabel position="stacked">{label}</IonLabel>
    <IonInput type="number" value={value || ""} onIonChange={(e) => onChange(e.detail.value || "")} />
  </IonItem>
);

const AreaField: React.FC<FieldProps> = ({ label, value, onChange }) => (
  <IonItem>
    <IonLabel position="stacked">{label}</IonLabel>
    <IonTextarea value={value || ""} autoGrow onIonChange={(e) => onChange(e.detail.value || "")} />
  </IonItem>
);

const SelectField: React.FC<FieldProps & { options: Array<{ value: string; label: string }> }> = ({
  label,
  value,
  onChange,
  options,
}) => (
  <IonItem>
    <IonLabel position="stacked">{label}</IonLabel>
    <IonSelect value={value || ""} onIonChange={(event) => onChange(event.detail.value)}>
      {options.map((option) => (
        <IonSelectOption key={`${label}-${option.value}`} value={option.value}>
          {option.label}
        </IonSelectOption>
      ))}
    </IonSelect>
  </IonItem>
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
    <IonItem>
      <IonLabel position="stacked">{label}</IonLabel>
      <IonInput value={value} onIonChange={(event) => onChange(event.detail.value || "")} />
      {value ? (
        <IonButton fill="clear" slot="end" onClick={onClear}>
          Limpar
        </IonButton>
      ) : null}
    </IonItem>
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

function resolveStatusColor(status: DocumentStatus): "warning" | "success" | "medium" | "danger" {
  if (status === "DRAFT") return "warning";
  if (status === "FINAL") return "success";
  if (status === "SENT") return "medium";
  return "danger";
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

export default DocumentsPage;

