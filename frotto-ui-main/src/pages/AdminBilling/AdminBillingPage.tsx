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
  IonPage,
  IonProgressBar,
  IonSearchbar,
  IonSegment,
  IonSegmentButton,
  IonSelect,
  IonSelectOption,
  IonSpinner,
  IonTextarea,
  IonTitle,
  IonToolbar,
  useIonToast,
} from "@ionic/react";
import { closeCircleOutline, searchOutline } from "ionicons/icons";
import { useCallback, useState } from "react";
import { Redirect } from "react-router-dom";
import {
  AdminBillingUser,
  AdminUserSearchResult,
  GRANTABLE_PLAN_CODES,
  GrandfatherPreview,
  GrandfatherResult,
  GrantablePlanCode,
  PLAN_LABELS,
  SOURCE_LABELS,
  STATUS_LABELS,
} from "../../constants/AdminBillingModels";
import adminBillingService from "../../services/adminBillingService";
import { getApiErrorMessage } from "../../services/apiErrorMessage";
import { useAlert } from "../../services/hooks/useAlert";
import { useAccountAuthorization } from "../../services/hooks/useAccountAuthorization";
import { buildGrantPayload, canRevokeSubscription, validateGrantForm } from "./adminBillingPageLogic";
import "./AdminBillingPage.css";

const formatDateTime = (value?: string | null): string => {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "—";
  }
  return date.toLocaleString("pt-BR");
};

const formatDate = (value?: string | null): string => {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "—";
  }
  return date.toLocaleDateString("pt-BR");
};

const formatMoney = (value?: number | null): string => {
  if (value === null || value === undefined) {
    return "—";
  }
  return value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
};

const AdminBillingPage: React.FC = () => {
  const { showErrorAlert } = useAlert();
  const [presentToast] = useIonToast();

  const { isAdmin: isAuthorized, isLoading: isCheckingAuthorization } = useAccountAuthorization();

  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<AdminUserSearchResult[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  const [selectedUser, setSelectedUser] = useState<AdminBillingUser | null>(null);
  const [isLoadingUser, setIsLoadingUser] = useState(false);

  const [isGrantModalOpen, setIsGrantModalOpen] = useState(false);
  const [grantPlanCode, setGrantPlanCode] = useState<GrantablePlanCode | "">("");
  const [grantHasExpiry, setGrantHasExpiry] = useState(false);
  const [grantExpiresAtDate, setGrantExpiresAtDate] = useState("");
  const [grantReason, setGrantReason] = useState("");
  const [grantErrors, setGrantErrors] = useState<{ planCode?: string; expiresAt?: string }>({});
  const [isSavingGrant, setIsSavingGrant] = useState(false);
  const [isRevoking, setIsRevoking] = useState(false);

  const [grandfatherPreview, setGrandfatherPreview] = useState<GrandfatherPreview | null>(null);
  const [grandfatherResult, setGrandfatherResult] = useState<GrandfatherResult | null>(null);
  const [isPreviewingGrandfather, setIsPreviewingGrandfather] = useState(false);
  const [isApplyingGrandfather, setIsApplyingGrandfather] = useState(false);

  const showSuccessToast = useCallback(
    (message: string) => {
      presentToast({ message, color: "success", duration: 2200, position: "top" });
    },
    [presentToast]
  );

  const loadUserBilling = useCallback(
    async (userId: number) => {
      setIsLoadingUser(true);
      setGrandfatherPreview(null);
      setGrandfatherResult(null);
      try {
        const data = await adminBillingService.getUserBilling(userId);
        setSelectedUser(data);
      } catch (error) {
        showErrorAlert(getApiErrorMessage(error, "Não foi possível carregar o Billing deste usuário."));
      } finally {
        setIsLoadingUser(false);
      }
    },
    [showErrorAlert]
  );

  const handleSearch = useCallback(
    async (query: string) => {
      const trimmed = query.trim();
      if (trimmed.length < 2) {
        setSearchResults([]);
        return;
      }
      setIsSearching(true);
      try {
        const results = await adminBillingService.searchUsers(trimmed);
        setSearchResults(results);
      } catch (error) {
        showErrorAlert(getApiErrorMessage(error, "Não foi possível pesquisar usuários."));
      } finally {
        setIsSearching(false);
      }
    },
    [showErrorAlert]
  );

  const openGrantModal = () => {
    setGrantPlanCode("");
    setGrantHasExpiry(false);
    setGrantExpiresAtDate("");
    setGrantReason("");
    setGrantErrors({});
    setIsGrantModalOpen(true);
  };

  const submitGrant = async () => {
    if (!selectedUser) {
      return;
    }

    const errors = validateGrantForm(grantPlanCode, grantHasExpiry, grantExpiresAtDate);
    setGrantErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setIsSavingGrant(true);
    try {
      await adminBillingService.grantPlan(
        buildGrantPayload(selectedUser.userId, grantPlanCode as GrantablePlanCode, grantHasExpiry, grantExpiresAtDate, grantReason)
      );
      showSuccessToast("Plano concedido com sucesso.");
      setIsGrantModalOpen(false);
      await loadUserBilling(selectedUser.userId);
    } catch (error) {
      showErrorAlert(getApiErrorMessage(error, "Não foi possível conceder o plano."));
    } finally {
      setIsSavingGrant(false);
    }
  };

  const handleRevoke = async () => {
    if (!selectedUser?.currentSubscriptionId) {
      return;
    }
    const confirmed = window.confirm("Revogar a cortesia (ADMIN_GRANT) deste usuário?");
    if (!confirmed) {
      return;
    }

    setIsRevoking(true);
    try {
      await adminBillingService.revokeGrant(selectedUser.currentSubscriptionId);
      showSuccessToast("Cortesia revogada.");
      await loadUserBilling(selectedUser.userId);
    } catch (error) {
      showErrorAlert(getApiErrorMessage(error, "Não foi possível revogar a cortesia."));
    } finally {
      setIsRevoking(false);
    }
  };

  const handlePreviewGrandfathering = async () => {
    if (!selectedUser) {
      return;
    }
    setIsPreviewingGrandfather(true);
    setGrandfatherResult(null);
    try {
      const preview = await adminBillingService.previewGrandfathering(selectedUser.userId);
      setGrandfatherPreview(preview);
    } catch (error) {
      showErrorAlert(getApiErrorMessage(error, "Não foi possível simular o grandfathering."));
    } finally {
      setIsPreviewingGrandfather(false);
    }
  };

  const handleApplyGrandfathering = async () => {
    if (!selectedUser) {
      return;
    }
    const confirmed = window.confirm(
      "Aplicar grandfathering para este usuário? Isso pode criar uma nova assinatura (GRANDFATHERED)."
    );
    if (!confirmed) {
      return;
    }

    setIsApplyingGrandfather(true);
    try {
      const result = await adminBillingService.applyGrandfathering(selectedUser.userId);
      setGrandfatherResult(result);
      if (result.subscriptionCreated) {
        showSuccessToast("Grandfathering aplicado.");
        await loadUserBilling(selectedUser.userId);
      } else {
        showSuccessToast("Nenhuma alteração necessária.");
      }
    } catch (error) {
      showErrorAlert(getApiErrorMessage(error, "Não foi possível aplicar o grandfathering."));
    } finally {
      setIsApplyingGrandfather(false);
    }
  };

  if (isCheckingAuthorization) {
    return (
      <IonPage id="admin-billing-page">
        <IonContent fullscreen className="admin-billing-loading">
          <IonSpinner name="dots" />
        </IonContent>
      </IonPage>
    );
  }

  if (!isAuthorized) {
    return <Redirect to="/menu/carros" />;
  }

  const canRevoke = canRevokeSubscription(selectedUser);

  const planMismatch = Boolean(selectedUser && selectedUser.planCode !== selectedUser.requiredPlanCode);

  return (
    <IonPage id="admin-billing-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonMenuButton menu="main-menu" autoHide={false} />
          </IonButtons>
          <IonTitle>Billing (Admin)</IonTitle>
        </IonToolbar>
        {(isSearching || isLoadingUser) && <IonProgressBar type="indeterminate" />}
      </IonHeader>

      <IonContent fullscreen>
        <div className="section-shell admin-billing-shell">
          <IonCard>
            <IonCardContent>
              <h3>Pesquisar usuário</h3>
              <IonSearchbar
                value={searchQuery}
                debounce={400}
                placeholder="Login ou e-mail"
                onIonChange={(event) => {
                  const value = event.detail.value || "";
                  setSearchQuery(value);
                  void handleSearch(value);
                }}
              />
              {searchResults.length > 0 && (
                <IonList className="admin-billing-search-results">
                  {searchResults.map((user) => (
                    <IonItem
                      key={`user-${user.id}`}
                      button
                      onClick={() => void loadUserBilling(user.id)}
                      className="admin-billing-search-result"
                    >
                      <IonIcon icon={searchOutline} slot="start" />
                      <IonLabel>
                        <h2>{user.login}</h2>
                        <p>{user.email || "—"}</p>
                      </IonLabel>
                    </IonItem>
                  ))}
                </IonList>
              )}
              {!isSearching && searchQuery.trim().length >= 2 && searchResults.length === 0 && (
                <div className="app-empty-state">
                  <strong>Nenhum usuário encontrado.</strong>
                  <span>Tente pesquisar por outro login ou e-mail.</span>
                </div>
              )}
            </IonCardContent>
          </IonCard>

          {selectedUser && (
            <>
              <IonCard>
                <IonCardContent>
                  <div className="admin-billing-user-header">
                    <div>
                      <h3>{selectedUser.userLogin}</h3>
                      <p className="admin-billing-muted">{selectedUser.userEmail || "—"}</p>
                    </div>
                    <div className="admin-billing-user-actions">
                      <IonButton size="small" onClick={openGrantModal}>
                        Conceder plano
                      </IonButton>
                      {canRevoke && (
                        <IonButton size="small" color="danger" fill="outline" disabled={isRevoking} onClick={() => void handleRevoke()}>
                          Revogar cortesia
                        </IonButton>
                      )}
                    </div>
                  </div>

                  <div className="admin-billing-facts">
                    <div className="admin-billing-fact">
                      <span className="admin-billing-fact__label">Plano efetivo</span>
                      <span className="admin-billing-fact__value">
                        {selectedUser.planName} ({selectedUser.planCode})
                      </span>
                    </div>
                    <div className="admin-billing-fact">
                      <span className="admin-billing-fact__label">Plano necessário</span>
                      <span className={`admin-billing-fact__value${planMismatch ? " admin-billing-fact__value--warning" : ""}`}>
                        {selectedUser.requiredPlanName} ({selectedUser.requiredPlanCode})
                      </span>
                    </div>
                    <div className="admin-billing-fact">
                      <span className="admin-billing-fact__label">Carros ativos</span>
                      <span className="admin-billing-fact__value">
                        {selectedUser.activeVehicleCount} / {selectedUser.vehicleLimit ?? "Ilimitado"}
                      </span>
                    </div>
                    <div className="admin-billing-fact">
                      <span className="admin-billing-fact__label">Origem</span>
                      <span className="admin-billing-fact__value">
                        {selectedUser.source ? SOURCE_LABELS[selectedUser.source] : "—"}
                      </span>
                    </div>
                    <div className="admin-billing-fact">
                      <span className="admin-billing-fact__label">Status</span>
                      <span className="admin-billing-fact__value">
                        {selectedUser.subscriptionStatus ? STATUS_LABELS[selectedUser.subscriptionStatus] : "—"}
                      </span>
                    </div>
                    <div className="admin-billing-fact">
                      <span className="admin-billing-fact__label">Validade</span>
                      <span className="admin-billing-fact__value">
                        {selectedUser.source === "ADMIN_GRANT"
                          ? selectedUser.grantExpiresAt
                            ? formatDateTime(selectedUser.grantExpiresAt)
                            : "Sem expiração"
                          : formatDateTime(selectedUser.currentPeriodEnd)}
                      </span>
                    </div>
                  </div>

                  <div className="admin-billing-badges">
                    <IonBadge color={selectedUser.canAddVehicle ? "success" : "danger"}>
                      {selectedUser.canAddVehicle ? "Pode cadastrar veículo" : "Precisa de upgrade"}
                    </IonBadge>
                  </div>
                </IonCardContent>
              </IonCard>

              <IonCard>
                <IonCardContent>
                  <h3>Histórico resumido</h3>
                  {selectedUser.history.length === 0 ? (
                    <div className="app-empty-state">
                      <strong>Nenhum histórico ainda.</strong>
                      <span>Este usuário nunca teve uma assinatura registrada.</span>
                    </div>
                  ) : (
                    <IonList>
                      {selectedUser.history.map((entry) => (
                        <IonItem key={`history-${entry.id}`} className="admin-billing-history-row">
                          <IonLabel className="ion-text-wrap">
                            <h2>
                              {entry.planName} · {SOURCE_LABELS[entry.source]}
                            </h2>
                            <p>
                              Status: {STATUS_LABELS[entry.status]} · Início: {formatDate(entry.startDate)}
                              {entry.canceledAt ? ` · Encerrada: ${formatDate(entry.canceledAt)}` : ""}
                            </p>
                            {entry.grantReason && <p>Motivo: {entry.grantReason}</p>}
                            {entry.grantedByLogin && <p>Concedido por: {entry.grantedByLogin}</p>}
                            <p>Preço contratado: {formatMoney(entry.contractedPrice)}</p>
                          </IonLabel>
                        </IonItem>
                      ))}
                    </IonList>
                  )}
                </IonCardContent>
              </IonCard>

              <IonCard>
                <IonCardContent>
                  <h3>Cliente legado</h3>
                  <p className="admin-billing-muted">
                    Simule antes de aplicar. Nenhuma alteração é feita até você confirmar explicitamente.
                  </p>
                  <div className="admin-billing-grandfather-actions">
                    <IonButton
                      size="small"
                      fill="outline"
                      disabled={isPreviewingGrandfather}
                      onClick={() => void handlePreviewGrandfathering()}
                    >
                      Simular grandfathering
                    </IonButton>
                    {grandfatherPreview && grandfatherPreview.wouldCreateSubscription && (
                      <IonButton
                        size="small"
                        color="warning"
                        disabled={isApplyingGrandfather}
                        onClick={() => void handleApplyGrandfathering()}
                      >
                        Aplicar grandfathering
                      </IonButton>
                    )}
                  </div>

                  {grandfatherPreview && (
                    <div className="admin-billing-grandfather-preview">
                      <p>Carros ativos: {grandfatherPreview.activeVehicleCount}</p>
                      <p>
                        Plano resultante: {grandfatherPreview.requiredPlanName} ({grandfatherPreview.requiredPlanCode})
                      </p>
                      <p>
                        {grandfatherPreview.wouldCreateSubscription
                          ? "Uma assinatura GRANDFATHERED seria criada. Nenhuma alteração ainda."
                          : "Nenhuma alteração seria feita (usuário já possui assinatura ou já está dentro do FREE)."}
                      </p>
                    </div>
                  )}

                  {grandfatherResult && (
                    <div className="admin-billing-grandfather-preview">
                      <p>
                        {grandfatherResult.subscriptionCreated
                          ? "Grandfathering aplicado com sucesso."
                          : "Nenhuma alteração foi necessária."}
                      </p>
                    </div>
                  )}
                </IonCardContent>
              </IonCard>
            </>
          )}
        </div>
      </IonContent>

      <IonModal
        isOpen={isGrantModalOpen}
        onDidDismiss={() => {
          setIsGrantModalOpen(false);
        }}
      >
        <IonHeader>
          <IonToolbar>
            <IonTitle>Conceder plano</IonTitle>
            <IonButtons slot="end">
              <IonButton onClick={() => setIsGrantModalOpen(false)}>
                <IonIcon icon={closeCircleOutline} slot="icon-only" />
              </IonButton>
            </IonButtons>
          </IonToolbar>
        </IonHeader>
        <IonContent>
          <div className="section-shell">
            <IonCard>
              <IonCardContent>
                <div className={`app-form-field${grantErrors.planCode ? " app-form-field--invalid" : ""}`}>
                  <IonItem className="app-form-item">
                    <IonLabel position="stacked">Plano</IonLabel>
                    <IonSelect
                      value={grantPlanCode}
                      placeholder="Selecione"
                      onIonChange={(event) => setGrantPlanCode(event.detail.value)}
                    >
                      {GRANTABLE_PLAN_CODES.map((code) => (
                        <IonSelectOption key={code} value={code}>
                          {PLAN_LABELS[code]}
                        </IonSelectOption>
                      ))}
                    </IonSelect>
                  </IonItem>
                  {grantErrors.planCode && <div className="app-form-error">{grantErrors.planCode}</div>}
                </div>

                <div className="admin-billing-validity-field">
                  <IonLabel className="admin-billing-validity-label">Validade</IonLabel>
                  <IonSegment
                    value={grantHasExpiry ? "expires" : "forever"}
                    onIonChange={(event) => setGrantHasExpiry(event.detail.value === "expires")}
                  >
                    <IonSegmentButton value="forever">
                      <IonLabel>Sem expiração</IonLabel>
                    </IonSegmentButton>
                    <IonSegmentButton value="expires">
                      <IonLabel>Data definida</IonLabel>
                    </IonSegmentButton>
                  </IonSegment>
                </div>

                {grantHasExpiry && (
                  <div className={`app-form-field${grantErrors.expiresAt ? " app-form-field--invalid" : ""}`}>
                    <IonItem className="app-form-item">
                      <IonLabel position="stacked">Expira em</IonLabel>
                      <IonInput
                        type="date"
                        value={grantExpiresAtDate}
                        onIonChange={(event) => setGrantExpiresAtDate(event.detail.value || "")}
                      />
                    </IonItem>
                    {grantErrors.expiresAt && <div className="app-form-error">{grantErrors.expiresAt}</div>}
                  </div>
                )}

                <div className="app-form-field">
                  <IonItem className="app-form-item app-form-item--textarea">
                    <IonLabel position="stacked">Motivo</IonLabel>
                    <IonTextarea
                      value={grantReason}
                      maxlength={500}
                      rows={3}
                      onIonChange={(event) => setGrantReason(event.detail.value || "")}
                    />
                  </IonItem>
                </div>

                <div className="admin-billing-modal-actions">
                  <IonButton fill="outline" onClick={() => setIsGrantModalOpen(false)}>
                    Cancelar
                  </IonButton>
                  <IonButton onClick={() => void submitGrant()} disabled={isSavingGrant}>
                    Confirmar
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

export default AdminBillingPage;
