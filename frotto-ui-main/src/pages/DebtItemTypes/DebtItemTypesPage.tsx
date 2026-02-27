import {
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
  IonPage,
  IonProgressBar,
  IonTitle,
  IonToggle,
  IonToolbar,
  useIonToast,
} from "@ionic/react";
import { add, closeCircleOutline, createOutline, trashOutline } from "ionicons/icons";
import { useCallback, useEffect, useState } from "react";
import { DebtItemTypeModel } from "../../constants/DebtItemTypeModels";
import debtItemTypeService from "../../services/debtItemTypeService";
import { useAlert } from "../../services/hooks/useAlert";
import "./DebtItemTypesPage.css";

const DebtItemTypesPage: React.FC = () => {
  const { showErrorAlert } = useAlert();
  const [presentToast] = useIonToast();

  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [types, setTypes] = useState<DebtItemTypeModel[]>([]);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingType, setEditingType] = useState<DebtItemTypeModel | null>(null);
  const [formName, setFormName] = useState("");
  const [formSortOrder, setFormSortOrder] = useState("0");
  const [formActive, setFormActive] = useState(true);

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

  const loadTypes = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await debtItemTypeService.listDebtItemTypes("all");
      setTypes(data);
    } catch (_error) {
      showErrorAlert("Não foi possível carregar os tipos de dívida.");
    } finally {
      setIsLoading(false);
    }
  }, [showErrorAlert]);

  useEffect(() => {
    void loadTypes();
  }, [loadTypes]);

  const resetForm = () => {
    setEditingType(null);
    setFormName("");
    setFormSortOrder("0");
    setFormActive(true);
  };

  const openCreateModal = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const openEditModal = (type: DebtItemTypeModel) => {
    setEditingType(type);
    setFormName(type.name || "");
    setFormSortOrder(String(type.sortOrder ?? 0));
    setFormActive(Boolean(type.active));
    setIsModalOpen(true);
  };

  const parseSortOrder = (value: string): number => {
    const normalized = `${value || ""}`.trim();
    if (!normalized) {
      return 0;
    }
    const parsed = Number(normalized);
    if (!Number.isFinite(parsed)) {
      return 0;
    }
    return Math.trunc(parsed);
  };

  const resolveErrorMessage = (error: any, fallback: string): string => {
    const status = error?.response?.status;
    if (status === 409) {
      return "Já existe um tipo com esse nome.";
    }
    return fallback;
  };

  const submitForm = async () => {
    const trimmedName = formName.trim();
    if (!trimmedName) {
      showErrorAlert("Informe um nome para o tipo.");
      return;
    }

    setIsSaving(true);
    try {
      const payload = {
        name: trimmedName,
        active: formActive,
        sortOrder: parseSortOrder(formSortOrder),
      };

      if (editingType?.id) {
        await debtItemTypeService.updateDebtItemType(editingType.id, payload);
        showSuccessToast("Tipo atualizado.");
      } else {
        await debtItemTypeService.createDebtItemType(payload);
        showSuccessToast("Tipo criado.");
      }

      setIsModalOpen(false);
      resetForm();
      await loadTypes();
    } catch (error: any) {
      showErrorAlert(resolveErrorMessage(error, "Não foi possível salvar o tipo."));
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleActive = async (type: DebtItemTypeModel, nextActive: boolean) => {
    if (!nextActive) {
      const confirmed = window.confirm(
        "Desativar tipo? Não afetará documentos já gerados."
      );
      if (!confirmed) {
        return;
      }
    }

    setIsSaving(true);
    try {
      await debtItemTypeService.updateDebtItemType(type.id, { active: nextActive });
      await loadTypes();
      showSuccessToast(nextActive ? "Tipo ativado." : "Tipo desativado.");
    } catch (error: any) {
      showErrorAlert(resolveErrorMessage(error, "Não foi possível atualizar o tipo."));
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeactivate = async (type: DebtItemTypeModel) => {
    const confirmed = window.confirm(
      "Desativar tipo? Não afetará documentos já gerados."
    );
    if (!confirmed) {
      return;
    }

    setIsSaving(true);
    try {
      await debtItemTypeService.deactivateDebtItemType(type.id);
      await loadTypes();
      showSuccessToast("Tipo desativado.");
    } catch (error: any) {
      showErrorAlert(resolveErrorMessage(error, "Não foi possível desativar o tipo."));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <IonPage id="debt-item-types-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonMenuButton menu="main-menu" autoHide={false} />
          </IonButtons>
          <IonTitle>Tipos de Dívida</IonTitle>
          <IonButtons slot="end">
            <IonButton onClick={openCreateModal}>
              <IonIcon icon={add} slot="start" />
              Adicionar tipo
            </IonButton>
          </IonButtons>
        </IonToolbar>
        {(isLoading || isSaving) && <IonProgressBar type="indeterminate" />}
      </IonHeader>

      <IonContent fullscreen>
        <div className="section-shell debt-item-types-shell">
          <IonCard>
            <IonCardContent>
              <h3>Tipos cadastrados</h3>
              <IonList>
                {types.map((type) => (
                  <IonItem key={`debt-item-type-${type.id}`} className="debt-item-types-row">
                    <IonLabel className="ion-text-wrap">
                      <h2>{type.name}</h2>
                      <p>Ordem: {type.sortOrder ?? 0}</p>
                      <p>Status: {type.active ? "Ativo" : "Inativo"}</p>
                    </IonLabel>
                    <div className="debt-item-types-actions">
                      <IonItem lines="none" className="debt-item-types-toggle">
                        <IonLabel>Ativo</IonLabel>
                        <IonToggle
                          checked={Boolean(type.active)}
                          onIonChange={(event) => void handleToggleActive(type, event.detail.checked)}
                        />
                      </IonItem>
                      <IonButton size="small" fill="outline" onClick={() => openEditModal(type)}>
                        <IonIcon icon={createOutline} slot="start" />
                        Editar
                      </IonButton>
                      {type.active && (
                        <IonButton
                          size="small"
                          color="danger"
                          fill="outline"
                          onClick={() => void handleDeactivate(type)}
                        >
                          <IonIcon icon={trashOutline} slot="start" />
                          Desativar
                        </IonButton>
                      )}
                    </div>
                  </IonItem>
                ))}
                {!isLoading && !types.length && (
                  <IonItem>
                    <IonLabel>Nenhum tipo encontrado.</IonLabel>
                  </IonItem>
                )}
              </IonList>
            </IonCardContent>
          </IonCard>
        </div>
      </IonContent>

      <IonModal
        isOpen={isModalOpen}
        onDidDismiss={() => {
          setIsModalOpen(false);
          resetForm();
        }}
      >
        <IonHeader>
          <IonToolbar>
            <IonTitle>{editingType ? "Editar tipo" : "Adicionar tipo"}</IonTitle>
            <IonButtons slot="end">
              <IonButton
                onClick={() => {
                  setIsModalOpen(false);
                  resetForm();
                }}
              >
                <IonIcon icon={closeCircleOutline} slot="icon-only" />
              </IonButton>
            </IonButtons>
          </IonToolbar>
        </IonHeader>
        <IonContent>
          <div className="section-shell">
            <IonCard>
              <IonCardContent>
                <IonItem>
                  <IonLabel position="stacked">Nome</IonLabel>
                  <IonInput
                    value={formName}
                    maxlength={120}
                    onIonChange={(event) => setFormName(event.detail.value || "")}
                  />
                </IonItem>
                <IonItem>
                  <IonLabel position="stacked">Ordem</IonLabel>
                  <IonInput
                    type="number"
                    inputmode="numeric"
                    value={formSortOrder}
                    onIonChange={(event) =>
                      setFormSortOrder((event.detail.value || "").replace(/[^\d-]/g, ""))
                    }
                  />
                </IonItem>
                <IonItem lines="none">
                  <IonLabel>Ativo</IonLabel>
                  <IonToggle checked={formActive} onIonChange={(event) => setFormActive(event.detail.checked)} />
                </IonItem>
                <div className="debt-item-types-modal-actions">
                  <IonButton fill="outline" onClick={() => setIsModalOpen(false)}>
                    Cancelar
                  </IonButton>
                  <IonButton onClick={() => void submitForm()} disabled={isSaving}>
                    Salvar
                  </IonButton>
                </div>
              </IonCardContent>
            </IonCard>
          </div>
        </IonContent>
      </IonModal>
    </IonPage>
  );
};

export default DebtItemTypesPage;
