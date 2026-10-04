import { IonBackButton, IonButton, IonButtons, IonContent, IonHeader, IonPage, IonTitle, IonToolbar } from "@ionic/react";
import { useEffect, useState } from "react";
import { Redirect } from "react-router-dom";
import { CarModel } from "../../constants/CarModels";
import endpoints from "../../constants/endpoints";
import api from "../../services/axios/axios";
import { useAccountAuthorization } from "../../services/hooks/useAccountAuthorization";
import { getApiErrorMessage } from "../../services/apiErrorMessage";
import { urlToS3Image } from "../../services/BodyImagePath";
import { resolveProfileImageSource } from "../../services/profileImageSource";

type DeletedRow = { car: CarModel; userId?: number; userLogin?: string };
type HistoryRow = { id?: number; date?: string; name?: string; description?: string; cost?: number; value?: number; driver?: { name?: string }; startDate?: string; title?: string; type?: string; imagePath?: string; imageUrl?: string | null; pdfUrl?: string };
type Section = { title: string; rows: HistoryRow[] };
const date = (value?: string) => value ? new Date(value).toLocaleDateString("pt-BR") : "Não registrado";
// Damage photo: the backend-resolved URL when present; payloads that only carry the key use the legacy bucket URL.
const damageImageHref = (row: HistoryRow) => resolveProfileImageSource(row.imageUrl, row.imagePath, urlToS3Image);

export const DeletedCarsView: React.FC<{ admin?: boolean }> = ({ admin = false }) => {
  const [rows, setRows] = useState<DeletedRow[]>([]);
  const [selected, setSelected] = useState<CarModel | null>(null);
  const [reason, setReason] = useState("");
  const [history, setHistory] = useState<Section[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const load = async () => {
    const response = await api.get(admin ? endpoints.ADMIN_CARS_DELETED() : endpoints.CARS_DELETED());
    setRows(admin ? response.data : response.data.map((car: CarModel) => ({ car })));
  };
  useEffect(() => { load().catch(() => setError("Não foi possível carregar os veículos excluídos.")); }, [admin]); // eslint-disable-line react-hooks/exhaustive-deps

  const showHistory = async (car: CarModel) => {
    setSelected(car); setHistory([]); setError(""); setBusy(true);
    const sources = [
      { title: "Receitas", endpoint: endpoints.INCOMES }, { title: "Despesas", endpoint: endpoints.CAR_EXPENSES },
      { title: "Motoristas anteriores", endpoint: endpoints.DRIVERS }, { title: "Inspeções", endpoint: endpoints.INSPECTIONS },
      { title: "Danos", endpoint: endpoints.BODY_DAMAGE }, { title: "Manutenções", endpoint: endpoints.MAINTENANCES },
      { title: "Lembretes", endpoint: endpoints.REMINDERS },
    ];
    try {
      const sections = await Promise.all(sources.map(async ({ title, endpoint }) => ({ title, rows: (await api.get(endpoint({ pathVariables: { id: car.id } }))).data })));
      const documents: HistoryRow[] = [];
      for (let page = 0; ; page++) {
        const response = await api.get(endpoints.DOCUMENTS(), { params: { carId: car.id, limit: 100, page } });
        documents.push(...response.data);
        if (response.data.length < 100) break;
      }
      setHistory([...sections, { title: "Documentos", rows: documents }]);
    } catch { setError("Não foi possível carregar todo o histórico. Tente novamente."); }
    finally { setBusy(false); }
  };
  const restore = async () => {
    if (!admin || !selected || !reason.trim() || busy) return;
    setBusy(true); setError("");
    try {
      await api.post(endpoints.ADMIN_CAR_RESTORE({ pathVariables: { id: selected.id } }), { reason: reason.trim() });
      setSelected(null); setReason(""); await load();
      setNotice("Veículo restaurado. A próxima mensalidade estimada do cliente foi atualizada. Nenhuma cobrança imediata foi gerada.");
    } catch (failure) { setError(getApiErrorMessage(failure, "Não foi possível restaurar o veículo. Confira o limite do plano e as operações de cobrança em andamento.")); }
    finally { setBusy(false); }
  };
  return <IonPage><IonHeader><IonToolbar><IonButtons slot="start"><IonBackButton defaultHref={admin ? "/menu/admin/billing" : "/menu/carros"} /></IonButtons><IonTitle>Veículos excluídos</IonTitle></IonToolbar></IonHeader><IonContent><div className="app-shell">
    <p>O histórico permanece disponível somente para consulta.</p>
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    {rows.length === 0 && <p>Nenhum veículo excluído.</p>}
    {rows.map(({ car, userId, userLogin }) => <article key={car.id} className="my-plan-alert">
      <h2>{car.name || car.model} — {car.plate}</h2><p>{car.brand} {car.model}</p><p>Excluído em: {date(car.deletedAt)}</p>
      {admin ? <><p>Usuário: {userLogin} ({userId})</p><p>Excluído por: {car.deletedByUserId ?? "Não registrado"}</p><p>Última restauração: {date(car.restoredAt)} por {car.restoredByUserId ?? "Não registrado"}</p><p>Motivo: {car.restoreReason || "Não registrado"}</p>{car.deleted ? <IonButton onClick={() => { setSelected(car); setReason(""); }}>Restaurar veículo</IonButton> : <p>Veículo restaurado</p>}</>
        : <IonButton onClick={() => void showHistory(car)}>Ver histórico</IonButton>}
    </article>)}
    {selected && <section aria-label={admin ? "Restaurar veículo" : "Histórico do veículo"}>
      <h2>{selected.plate}</h2>
      {admin ? <><label>Motivo obrigatório<textarea maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label><IonButton className="app-save-btn" disabled={!reason.trim() || busy} onClick={() => void restore()}>Confirmar restauração</IonButton></>
        : <>{busy && <p>Carregando histórico…</p>}{history.map(section => <section key={section.title}><h3>{section.title}</h3>{section.rows.length === 0 ? <p>Nenhum registro.</p> : <ul>{section.rows.map((row, index) => <li key={row.id ?? index}>{date(row.date || row.startDate)} — {row.driver?.name || row.name || row.title || row.description || row.type || "Registro"}{row.cost != null && ` — ${row.cost.toLocaleString("pt-BR", { style: "currency", currency: "BRL" })}`}{damageImageHref(row) && <a href={damageImageHref(row)} target="_blank" rel="noreferrer"> Ver imagem</a>}{row.pdfUrl && <a href={row.pdfUrl} target="_blank" rel="noreferrer"> Ver documento</a>}</li>)}</ul>}</section>)}</>}
      <IonButton fill="clear" className="app-cancel-btn" onClick={() => setSelected(null)}>Fechar</IonButton>
    </section>}
  </div></IonContent></IonPage>;
};

export const AdminDeletedCars: React.FC = () => {
  const auth = useAccountAuthorization();
  if (auth.isLoading) return <p>Carregando…</p>;
  if (!auth.isAdmin) return <Redirect to="/menu/carros" />;
  return <DeletedCarsView admin />;
};
export default DeletedCarsView;
