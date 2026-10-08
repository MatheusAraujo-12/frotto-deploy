import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonModal,
  IonPage,
  IonProgressBar,
  IonSearchbar,
  IonTitle,
  IonToolbar,
  useIonRouter,
  useIonViewWillLeave,
} from "@ionic/react";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { TEXT } from "../../constants/texts";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import { InspectionModel } from "../../constants/CarModels";
import { filterListObj } from "../../services/filterList";
import { RouteComponentProps } from "react-router";
import InspectionAdd from "./InspectionAddModal/InspectionAdd";
import { formatDateView } from "../../services/dateFormat";
import { currencyFormat } from "../../services/currencyFormat";
import ItemNotFound from "../../components/List/ItemNotFound";
import { clipboardOutline, documentTextOutline } from "ionicons/icons";
import FrottoBadge from "../../components/UI/FrottoBadge";
import documentService from "../../services/documentService";
import { generateDocumentPdf } from "../Documents/documentPdf";
import { ChecklistType, checklistTypeLabel } from "../Documents/checklistUtils";
import "./Inspections.css";

/**
 * Checklist type of the FINAL document an inspection came from (immutable once final): fetched once per document and
 * shared by every visit of the list, concurrent lookups included.
 */
const checklistTypeByDocument = new Map<number, Promise<ChecklistType | null>>();

function checklistTypeOf(documentId: number): Promise<ChecklistType | null> {
  let pending = checklistTypeByDocument.get(documentId);
  if (!pending) {
    pending = documentService
      .getDocument(documentId)
      .then((document) => (document?.checklistType as ChecklistType | undefined) ?? null)
      .catch(() => {
        checklistTypeByDocument.delete(documentId); // a failed lookup is retried on the next visit
        return null;
      });
    checklistTypeByDocument.set(documentId, pending);
  }
  return pending;
}

interface InspectionDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Inspections: React.FC<InspectionDetail> = ({ match }) => {
  const history = useIonRouter();
  const { showErrorAlert, showSuccessAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [modalInspection, setModalInspection] = useState<InspectionModel>({});
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [inspectionList, setInspectionsList] = useState<InspectionModel[]>([]);
  const [checklistTypes, setChecklistTypes] = useState<Record<number, ChecklistType | null>>({});
  const [issuingDocumentId, setIssuingDocumentId] = useState<number | null>(null);

  // Label of the inspections created by a checklist (originDocumentId): one lookup per origin document.
  useEffect(() => {
    let active = true;
    const ids = Array.from(
      new Set(inspectionList.map((inspection) => inspection.originDocumentId).filter((id): id is number => typeof id === "number"))
    );
    ids.forEach((id) => {
      void checklistTypeOf(id).then((type) => {
        if (active) {
          setChecklistTypes((current) => (current[id] === type ? current : { ...current, [id]: type }));
        }
      });
    });
    return () => {
      active = false;
    };
  }, [inspectionList]);

  /** 2ª via: the PDF of the original FINAL document (no new document, inspection or contract; never finalized again). */
  const issueSecondCopy = async (documentId: number) => {
    setIssuingDocumentId(documentId);
    try {
      const document = await documentService.getDocument(documentId);
      await generateDocumentPdf(document);
      await documentService.generateDocumentPdf(documentId);
      showSuccessAlert("2ª via do checklist gerada.");
    } catch (_error) {
      showErrorAlert("Não foi possível emitir a 2ª via do checklist. Tente novamente.");
    } finally {
      setIssuingDocumentId(null);
    }
  };

  const loadInspections = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(
        endpoints.INSPECTIONS({
          pathVariables: {
            id: match.params.id,
          },
        })
      );
      setisLoading(false);
      if (data) {
        setInspectionsList(data);
      } else {
        history.push("/menu", "none", "replace");
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadInspectionsFailed);
    }
  };

  useEffect(() => {
    loadInspections();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useIonViewWillLeave(() => {
    setIsModalOpen(false);
  }, []);

  const filteredList = useMemo(() => {
    return filterListObj(inspectionList, searchValue);
  }, [inspectionList, searchValue]);

  const closeModal = useCallback((response?: InspectionModel) => {
    setIsModalOpen(false);

    if (!response) return;

    setInspectionsList((prev) => {
      if (response.delete && response.id !== undefined) {
        return prev.filter((item) => item.id !== response.id);
      }
      const exists = prev.some((item) => item.id === response.id);
      if (exists) {
        return prev.map((item) => (item.id === response.id ? response : item));
      }
      return [response, ...prev];
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <IonPage id="car-inspections-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonBackButton defaultHref="/menu" />
          </IonButtons>
          <IonTitle>{TEXT.inspections}</IonTitle>
        </IonToolbar>
        <IonToolbar className="app-subtoolbar">
          <IonSearchbar
            debounce={500}
            placeholder={TEXT.search}
            value={searchValue}
            onIonChange={(e) => setSearchValue(e.detail.value ?? undefined)}
          ></IonSearchbar>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="app-shell app-shell--compact">
          <section className="app-section">
            <div className="inspections-section-head">
              <h2 className="app-section-title">{TEXT.inspections}</h2>
              <p className="app-section-subtitle">
                Histórico de inspeções registradas para este veículo.
              </p>
            </div>

            <div className="inspections-list">
              {filteredList.map((inspection: InspectionModel, index) => {
                const originDocumentId = inspection.originDocumentId ?? null;
                const originType = originDocumentId ? checklistTypes[originDocumentId] : undefined;
                const originLabel = originType ? `Checklist de ${checklistTypeLabel(originType)}` : "Checklist";
                return (
                  <div className="inspection-list-entry" key={inspection.id ?? `inspection-${index}`}>
                  <IonItem
                    button
                    detail={false}
                    lines="none"
                    className="inspection-list-item"
                    onClick={() => {
                      setModalInspection(inspection);
                      setIsModalOpen(true);
                    }}
                  >
                    <div className="inspection-list-item__wrap">
                      <div className="app-soft-icon inspection-list-item__icon">
                        <IonIcon icon={clipboardOutline} />
                      </div>

                      <div className="inspection-list-item__content">
                        <div className="inspection-list-item__main ion-text-wrap">
                          <h3 className="inspection-list-item__title">
                            {formatDateView(inspection.date)}
                          </h3>
                          <p className="inspection-list-item__meta">
                            {inspection.driverName || TEXT.inspection}
                          </p>
                          <p className="inspection-list-item__hint">
                            {`${inspection.odometer || 0} ${TEXT.km}`}
                          </p>
                          {originDocumentId && (
                            <FrottoBadge variant="info" className="inspection-list-item__origin">
                              {originLabel}
                            </FrottoBadge>
                          )}
                        </div>

                        <div className="inspection-list-item__aside ion-text-wrap">
                          <p className="inspection-list-item__value app-text-financial-negative">
                            {currencyFormat(inspection.cost)}
                          </p>
                          <p className="inspection-list-item__meta">
                            {TEXT.totalCost}
                          </p>
                        </div>
                      </div>
                    </div>
                  </IonItem>
                  {originDocumentId && (
                    // Outside the item (no button inside a button): the document's own action.
                    <div className="inspection-list-entry__actions">
                      <IonButton
                        size="small"
                        fill="outline"
                        className="app-outline-btn"
                        disabled={issuingDocumentId !== null}
                        aria-label={`Emitir 2ª via (PDF) do ${originLabel.toLowerCase()} de ${formatDateView(inspection.date)}`}
                        onClick={() => void issueSecondCopy(originDocumentId)}
                      >
                        <IonIcon icon={documentTextOutline} slot="start" />
                        {issuingDocumentId === originDocumentId ? "Gerando..." : "Emitir 2ª via (PDF)"}
                      </IonButton>
                    </div>
                  )}
                  </div>
                );
              })}
            </div>

            {!isLoading && filteredList.length === 0 && (
              <ItemNotFound
                title={TEXT.noInspection}
                description="Nenhuma inspeção encontrada para os filtros atuais."
              />
            )}
          </section>
        </div>
      </IonContent>
      <IonModal isOpen={isModalOpen} backdropDismiss={false}>
        <InspectionAdd
          carId={match.params.id}
          closeModal={closeModal}
          initialValues={modalInspection}
        />
      </IonModal>
    </IonPage>
  );
};

export default Inspections;
