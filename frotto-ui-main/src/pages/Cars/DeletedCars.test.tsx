import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { DeletedCarsView } from "./DeletedCars";
import api from "../../services/axios/axios";
jest.mock("../../services/axios/axios");
jest.mock("@ionic/react", () => {
  const React = jest.requireActual("react");
  const component = (tag: string) => ({ children, defaultHref, ...props }: any) => React.createElement(tag, props, children);
  return { IonBackButton: component("button"), IonButton: component("button"), IonButtons: component("div"), IonContent: component("main"),
    IonHeader: component("header"), IonPage: component("div"), IonTitle: component("h1"), IonToolbar: component("div") };
});
const mockedApi = api as jest.Mocked<typeof api>;
const car = { id: 3, name: "Veículo teste", plate: "ABC1234", brand: "Marca", model: "Modelo", deleted: true, deletedAt: "2026-09-29T12:00:00Z" };
beforeEach(() => jest.resetAllMocks());
it("mostra excluídos e histórico somente leitura sem restaurar", async () => {
  mockedApi.get.mockResolvedValueOnce({ data: [car] }).mockResolvedValue({ data: [] });
  render(<DeletedCarsView />);
  fireEvent.click(await screen.findByRole("button", { name: "Ver histórico" }));
  expect(await screen.findByRole("heading", { name: "Receitas" })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "Motoristas anteriores" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Restaurar veículo" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /Editar|Adicionar/ })).not.toBeInTheDocument();
  expect(mockedApi.post).not.toHaveBeenCalled();
});
it("admin precisa informar motivo antes de restaurar e vê a auditoria", async () => {
  mockedApi.get.mockResolvedValueOnce({ data: [{ car, userId: 1, userLogin: "cliente" }] }).mockResolvedValue({ data: [] });
  mockedApi.post.mockResolvedValue({ data: { ...car, deleted: false } });
  render(<DeletedCarsView admin />);
  fireEvent.click(await screen.findByRole("button", { name: "Restaurar veículo" }));
  expect(screen.getByRole("button", { name: "Confirmar restauração" })).toBeDisabled();
  fireEvent.change(screen.getByLabelText("Motivo obrigatório"), { target: { value: "Exclusão acidental" } });
  fireEvent.click(screen.getByRole("button", { name: "Confirmar restauração" }));
  await waitFor(() => expect(mockedApi.post).toHaveBeenCalledWith(expect.stringContaining("/admin/cars/3/restore"), { reason: "Exclusão acidental" }));
  expect(await screen.findByRole("status")).toHaveTextContent("Nenhuma cobrança imediata");
});
it("link da foto da avaria usa a URL resolvida pelo backend", async () => {
  const signed = "https://api-staging.frotto.test/files/car-damages/2026/10/a.png?exp=1&sig=s";
  mockedApi.get.mockImplementation(async (url: any) => {
    if (`${url}`.includes("/cars/deleted")) return { data: [car] };
    if (`${url}`.includes("/car-body-damages/car/3")) return { data: [{ id: 7, date: "2026-09-01", part: "Porta", imagePath: "car-damages/2026/10/a.png", imageUrl: signed }] };
    return { data: [] };
  });
  render(<DeletedCarsView />);
  fireEvent.click(await screen.findByRole("button", { name: "Ver histórico" }));
  const link = await screen.findByRole("link", { name: /Ver imagem/ });
  expect(link).toHaveAttribute("href", signed);
});
